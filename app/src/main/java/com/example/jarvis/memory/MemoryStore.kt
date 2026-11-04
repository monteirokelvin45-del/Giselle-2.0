// ============================================================
// memory/MemoryStore.kt
// ------------------------------------------------------------
// Memória de longo prazo. Isso é o que faz o Jarvis "evoluir":
// cada correção do usuário, cada padrão repetido, vira uma
// entrada aqui com peso de confiança que cresce ou decai.
//
// Implementação: Room (SQLite) para persistência local,
// com um índice de embeddings simplificado em memória para
// busca por similaridade (troque por um vetor DB real como
// ObjectBox Vector ou SQLite+sqlite-vec se escalar).
// ============================================================

package com.example.jarvis.memory

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.jarvis.core.AuditLogDao
import com.example.jarvis.core.AuditLogEntity
import com.example.jarvis.core.MemoryCategory
import com.example.jarvis.core.MemoryEntry
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

// -----------------------------------------------------
// Entidade Room (persistência)
// -----------------------------------------------------
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val category: String,
    val embeddingBlob: ByteArray,
    val createdAt: Long,
    val lastReinforced: Long,
    val confidence: Float
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY lastReinforced DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories")
    suspend fun getAll(): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY lastReinforced DESC LIMIT :limit")
    suspend fun getForRecall(limit: Int): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE memories SET confidence = :confidence, lastReinforced = :ts WHERE id = :id")
    suspend fun reinforce(id: String, confidence: Float, ts: Long)

    @Query("DELETE FROM memories WHERE id IN (SELECT id FROM memories ORDER BY confidence DESC, lastReinforced DESC LIMIT -1 OFFSET :keep)")
    suspend fun prune(keep: Int)
}

@Database(entities = [MemoryEntity::class, AuditLogEntity::class], version = 2, exportSchema = true)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile private var instance: JarvisDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE memories_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        content TEXT NOT NULL,
                        category TEXT NOT NULL,
                        embeddingBlob BLOB NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastReinforced INTEGER NOT NULL,
                        confidence REAL NOT NULL
                    )
                """.trimIndent())
                val insert = db.compileStatement(
                    "INSERT INTO memories_new (id, content, category, embeddingBlob, createdAt, lastReinforced, confidence) VALUES (?, ?, ?, ?, ?, ?, ?)"
                )
                db.query(
                    "SELECT id, content, category, embeddingCsv, createdAt, lastReinforced, confidence FROM memories"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val floats = cursor.getString(3).split(",").mapNotNull { it.toFloatOrNull() }
                        val bytes = ByteBuffer.allocate(floats.size * Float.SIZE_BYTES)
                            .order(ByteOrder.LITTLE_ENDIAN).apply {
                                floats.forEach { putFloat(it) }
                            }.array()
                        insert.clearBindings()
                        insert.bindString(1, cursor.getString(0))
                        insert.bindString(2, cursor.getString(1))
                        insert.bindString(3, cursor.getString(2))
                        insert.bindBlob(4, bytes)
                        insert.bindLong(5, cursor.getLong(4))
                        insert.bindLong(6, cursor.getLong(5))
                        insert.bindDouble(7, cursor.getDouble(6))
                        insert.executeInsert()
                    }
                }
                db.execSQL("DROP TABLE memories")
                db.execSQL("ALTER TABLE memories_new RENAME TO memories")
            }
        }

        fun getInstance(context: android.content.Context): JarvisDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, JarvisDatabase::class.java, "jarvis.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}

// -----------------------------------------------------
// API de alto nível usada pelo Orchestrator
// -----------------------------------------------------
class MemoryStore(
    private val dao: MemoryDao,
    private val embedder: TextEmbedder
) {

    /**
     * Salva uma nova memória. CORRECTION tem peso inicial mais alto
     * porque representa o usuário ativamente ensinando o sistema.
     */
    suspend fun remember(content: String, category: MemoryCategory) {
        val embedding = embedder.embed(content)
        val initialConfidence = when (category) {
            MemoryCategory.CORRECTION -> 0.9f
            MemoryCategory.FACT -> 0.85f
            else -> 0.6f
        }
        dao.upsert(
            MemoryEntity(
                id = UUID.randomUUID().toString(),
                content = content,
                category = category.name,
                embeddingBlob = encode(embedding),
                createdAt = System.currentTimeMillis(),
                lastReinforced = System.currentTimeMillis(),
                confidence = initialConfidence
            )
        )
        dao.prune(500)
    }

    /**
     * Busca as memórias mais relevantes para o contexto atual,
     * por similaridade de cosseno. É isso que dá "continuidade"
     * ao raciocínio do LLM entre sessões diferentes.
     *
     * Query em branco = listagem geral (usada pela tela de Memórias),
     * sem aplicar o filtro de relevância semântica.
     */
    suspend fun recall(query: String, topK: Int = 5): List<MemoryEntry> {
        val all = dao.getForRecall(500)
        if (query.isBlank()) {
            return all.sortedByDescending { it.lastReinforced }
                .take(topK)
                .map { it.toMemoryEntry() }
        }

        val queryEmbedding = embedder.embed(query)
        // Sem embedding da consulta (proxy indisponível) não há busca semântica.
        if (queryEmbedding.isEmpty()) return emptyList()
        return all.mapNotNull { entity ->
            val entityEmbedding = decode(entity.embeddingBlob)
            // Vetores de outro modelo/dimensão (ex.: hash antigo) não são comparáveis: ignora.
            if (entityEmbedding.size != queryEmbedding.size) return@mapNotNull null
            val ageDays = ((System.currentTimeMillis() - entity.lastReinforced).coerceAtLeast(0L) / 86_400_000f)
            val freshness = exp(-ageDays / 180f)
            val similarity = cosineSimilarity(queryEmbedding, entityEmbedding) *
                (0.5f + 0.5f * entity.confidence) * freshness
            entity to similarity
        }
            .sortedByDescending { it.second }
            .take(topK)
            .filter { it.second > 0.3f } // threshold mínimo de relevância
            .map { (entity, _) -> entity.toMemoryEntry() }
    }

    /**
     * Reforça uma memória existente quando o padrão se repete
     * (aumenta confiança) — é assim que o sistema "aprende" de fato,
     * distinguindo coincidência de hábito consistente.
     */
    suspend fun reinforce(memoryId: String, currentConfidence: Float) {
        val newConfidence = (currentConfidence + (1 - currentConfidence) * 0.2f).coerceAtMost(0.99f)
        dao.reinforce(memoryId, newConfidence, System.currentTimeMillis())
    }

    suspend fun prune() = dao.prune(500)

    private fun encode(values: FloatArray): ByteArray =
        ByteBuffer.allocate(values.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach(::putFloat)
        }.array()

    private fun decode(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty() || bytes.size % Float.SIZE_BYTES != 0) return FloatArray(0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.getFloat() }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float = VectorMath.cosineSimilarity(a, b)

    private fun MemoryEntity.toMemoryEntry() = MemoryEntry(
        id = id,
        content = content,
        category = MemoryCategory.valueOf(category),
        createdAt = createdAt,
        lastReinforced = lastReinforced,
        confidence = confidence
    )
}

// -----------------------------------------------------
// Interface de embedding — trocável (local ou via API)
// -----------------------------------------------------
interface TextEmbedder {
    suspend fun embed(text: String): FloatArray
}
