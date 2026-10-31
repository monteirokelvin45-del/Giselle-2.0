// ============================================================
// selfupdate/SelfUpdateDatabase.kt
// ------------------------------------------------------------
// Banco isolado dos outros dois (jarvis.db, tracker.db) — mesmo
// princípio de separação já usado no projeto: erro de schema
// aqui não deve arriscar dado de memória nem do Tracker.
// ============================================================

package com.example.jarvis.selfupdate

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ErrorLogEntity::class, PatchSuggestionEntity::class], version = 1, exportSchema = true)
abstract class SelfUpdateDatabase : RoomDatabase() {
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun patchSuggestionDao(): PatchSuggestionDao

    companion object {
        @Volatile private var instance: SelfUpdateDatabase? = null

        fun getInstance(context: Context): SelfUpdateDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, SelfUpdateDatabase::class.java, "selfupdate.db"
                ).build().also {
                    instance = it
                }
            }
    }
}
