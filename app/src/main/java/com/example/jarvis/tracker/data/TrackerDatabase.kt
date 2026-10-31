// ============================================================
// tracker/data/TrackerDatabase.kt
// ------------------------------------------------------------
// Banco isolado do JarvisDatabase (que guarda memórias e o log
// do núcleo de accessibility). Propositalmente separado: este
// módulo pode ser removido sem tocar no banco do Jarvis original,
// e vice-versa — mantém a promessa do cabeçalho de TrackerModels.kt
// de que "deletar este pacote inteiro não quebra o Jarvis".
// ============================================================

package com.example.jarvis.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConfigPerfil::class,
        OnboardingState::class,
        Metas::class,
        PlanoTreino::class,
        RegistroFisico::class,
        RegistroExercicio::class,
        RegistroMental::class,
        AyanokojiProgresso::class,
        AyanokojiRegistroDiario::class,
        RegistroEstudo::class,
        RegistroLeitura::class,
        RegistroFinanceiro::class,
        AiEvent::class,
        AuditLog::class,
        BackupRegistro::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun configPerfilDao(): ConfigPerfilDao
    abstract fun onboardingStateDao(): OnboardingStateDao
    abstract fun metasDao(): MetasDao
    abstract fun planoTreinoDao(): PlanoTreinoDao
    abstract fun registroFisicoDao(): RegistroFisicoDao
    abstract fun registroExercicioDao(): RegistroExercicioDao
    abstract fun registroMentalDao(): RegistroMentalDao
    abstract fun ayanokojiDao(): AyanokojiDao
    abstract fun registroEstudoDao(): RegistroEstudoDao
    abstract fun registroLeituraDao(): RegistroLeituraDao
    abstract fun registroFinanceiroDao(): RegistroFinanceiroDao
    abstract fun aiEventDao(): AiEventDao
    abstract fun trackerAuditLogDao(): TrackerAuditLogDao
    abstract fun backupRegistroDao(): BackupRegistroDao

    companion object {
        @Volatile private var instance: TrackerDatabase? = null

        // POLÍTICA: toda mudança de schema ganha Migration explícita aqui,
        // mesmo trivial. Nunca fallbackToDestructiveMigration() — este app
        // promete não perder dado. Padrão pro próximo passo real:
        // val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("ALTER TABLE registro_fisico ADD COLUMN x TEXT")
        //     }
        // }
        // ...e .addMigrations(MIGRATION_1_2) no builder abaixo.

        fun getInstance(context: Context): TrackerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackerDatabase::class.java,
                    "tracker.db" // nome de arquivo distinto de "jarvis.db" de propósito
                ).build().also { instance = it }
            }
    }
}
