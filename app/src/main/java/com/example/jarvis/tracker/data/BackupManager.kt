// ============================================================
// tracker/data/BackupManager.kt
// ------------------------------------------------------------
// Exportação e restauração validadas, em modo de mesclagem não destrutivo.
// ============================================================

package com.example.jarvis.tracker.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
data class BackupPayload(
    val exportadoEm: Long,
    val configPerfil: ConfigPerfil?,
    val metas: Metas?,
    val registrosFisicos: List<RegistroFisico>,
    val registrosExercicios: List<RegistroExercicio> = emptyList(),
    val registrosMentais: List<RegistroMental>,
    val registrosEstudo: List<RegistroEstudo>,
    val registrosLeitura: List<RegistroLeitura> = emptyList(),
    val registrosFinanceiros: List<RegistroFinanceiro>,
    val versao: Int = 2
)

@Serializable
private data class BackupEnvelope(
    val versao: Int,
    val payload: BackupPayload,
    val sha256: String
)

data class RestoreResult(val insertedRecords: Int, val mergedProfile: Boolean)

class BackupManager(private val context: Context, private val db: TrackerDatabase) {
    private val jsonCodec = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Gera o arquivo, registra em BackupRegistro, retorna o File criado. */
    suspend fun exportarBackup(tipo: String = "manual"): File {
        val payload = BackupPayload(
            exportadoEm = System.currentTimeMillis(),
            configPerfil = db.configPerfilDao().get(),
            metas = db.metasDao().get(),
            registrosFisicos = db.registroFisicoDao().getByRange(0, Long.MAX_VALUE),
            registrosExercicios = db.registroExercicioDao().getAll(),
            registrosMentais = db.registroMentalDao().getByRange(0, Long.MAX_VALUE),
            registrosEstudo = db.registroEstudoDao().getByRange(0, Long.MAX_VALUE),
            registrosLeitura = db.registroLeituraDao().getAll(),
            registrosFinanceiros = db.registroFinanceiroDao().getByRange(0, Long.MAX_VALUE)
        )

        val payloadJson = jsonCodec.encodeToString(BackupPayload.serializer(), payload)
        val json = jsonCodec.encodeToString(
            BackupEnvelope.serializer(),
            BackupEnvelope(versao = 2, payload = payload, sha256 = sha256(payloadJson))
        )
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, "backup_${payload.exportadoEm}.json")
        val temp = File(dir, "${file.name}.tmp")
        temp.writeText(json)
        check(temp.renameTo(file)) { "Não foi possível finalizar o arquivo de backup." }

        db.backupRegistroDao().insert(
            BackupRegistro(
                tipo = tipo,
                caminhoArquivo = file.absolutePath,
                hashVersao = sha256(json),
                createdAt = payload.exportadoEm
            )
        )
        return file
    }

    /** Valida checksum e conteúdo antes de mesclar; nunca apaga registros existentes. */
    suspend fun restaurarBackup(file: File): RestoreResult {
        require(file.isFile && file.length() in 1..MAX_BACKUP_BYTES) { "Arquivo de backup inválido ou demasiado grande." }
        val raw = file.readText(Charsets.UTF_8)
        val root = jsonCodec.parseToJsonElement(raw)
        val payload: BackupPayload
        val digest: String
        if (root is kotlinx.serialization.json.JsonObject && "payload" in root) {
            val envelope = jsonCodec.decodeFromString(BackupEnvelope.serializer(), raw)
            require(envelope.versao in 1..2) { "Versão de backup incompatível." }
            val payloadJson = jsonCodec.encodeToString(BackupPayload.serializer(), envelope.payload)
            require(sha256(payloadJson).equals(envelope.sha256, ignoreCase = true)) {
                "O backup está corrompido ou foi alterado."
            }
            payload = envelope.payload
            digest = sha256(raw)
        } else {
            // Importa também os arquivos antigos sem envelope/checksum.
            payload = jsonCodec.decodeFromString(BackupPayload.serializer(), raw)
            digest = sha256(raw)
        }
        require(payload.versao in 1..2) { "Versão de backup incompatível." }
        require(
            listOf(
                payload.registrosFisicos.size, payload.registrosMentais.size,
                payload.registrosExercicios.size,
                payload.registrosEstudo.size, payload.registrosLeitura.size,
                payload.registrosFinanceiros.size
            ).all { it <= MAX_RECORDS }
        ) { "O backup excede o limite de registros." }

        var inserted = 0
        var mergedProfile = false
        db.withTransaction {
            if (payload.configPerfil != null && db.configPerfilDao().get() == null) {
                db.configPerfilDao().upsert(payload.configPerfil)
                mergedProfile = true
            }
            if (payload.metas != null && db.metasDao().get() == null) {
                db.metasDao().upsert(payload.metas)
            }
            payload.registrosFisicos.forEach { if (db.registroFisicoDao().restore(it) != -1L) inserted++ }
            payload.registrosExercicios.forEach { if (db.registroExercicioDao().restore(it) != -1L) inserted++ }
            payload.registrosMentais.forEach { if (db.registroMentalDao().restore(it) != -1L) inserted++ }
            payload.registrosEstudo.forEach { if (db.registroEstudoDao().restore(it) != -1L) inserted++ }
            payload.registrosLeitura.forEach { if (db.registroLeituraDao().restore(it) != -1L) inserted++ }
            payload.registrosFinanceiros.forEach { if (db.registroFinanceiroDao().restore(it) != -1L) inserted++ }
            db.backupRegistroDao().insert(
                BackupRegistro(
                    tipo = "restauracao",
                    caminhoArquivo = file.absolutePath,
                    hashVersao = digest,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        return RestoreResult(insertedRecords = inserted, mergedProfile = mergedProfile)
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 64L * 1024 * 1024
        const val MAX_RECORDS = 100_000
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
