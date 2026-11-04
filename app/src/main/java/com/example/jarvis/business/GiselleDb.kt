// business/GiselleDb.kt — base SQLite do módulo (kv, razão, aprovações, vagas vistas).
// Separada do Room principal para não mexer no esquema do app.
package com.example.jarvis.business

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

object Kind {
    const val PROPOSAL = "PROPOSAL"
    const val PLAN = "PLAN"
    const val POST = "POST"
    const val SPEND = "SPEND"
    const val PAYOUT = "PAYOUT"
}

object Ledger {
    const val RECEIVED = "RECEIVED"
    const val FOUNDATION = "FOUNDATION"
    const val REINVEST = "REINVEST"
    const val IDEA = "IDEA"
    const val OWNER_SHARE = "OWNER_SHARE"
    const val SPEND_REAL = "SPEND_REAL"
    const val SPEND_APPROVED = "SPEND_APPROVED"
    const val OWNER_RECONCILED = "OWNER_RECONCILED"
}

object Status {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val DENIED = "DENIED"
}

object BizKeys {
    const val PHASE = "phase"
    const val GOAL = "goal_cents"
    const val SKILLS = "skills"
    const val PLAN_APPROVED = "plan_approved"
    const val LAST_RUN = "last_run_ms"
    const val LAST_REFLECTION = "last_reflection_ms"
    const val LAST_POSTS_DAY = "last_posts_day"
    const val MODEL_ERRORS = "model_errors"
    const val REFLECTION = "reflection"
}

data class ApprovalItem(
    val id: Long,
    val kind: String,
    val text: String,
    val cents: Long,
    val status: String,
    val ts: Long,
)

interface BizStore {
    fun getKv(key: String): String?
    fun setKv(key: String, value: String)
    fun addLedger(kind: String, cents: Long, note: String, ts: Long): Long
    fun sumLedger(kind: String, fromTs: Long, toTs: Long): Long
    fun addApproval(kind: String, text: String, cents: Long, ts: Long): Long
    fun approval(id: Long): ApprovalItem?
    /** Só muda se o estado for PENDING. Devolve false se já foi resolvido (botão duplo). */
    fun resolveApproval(id: Long, approve: Boolean): Boolean
    fun pendingApprovals(): List<ApprovalItem>
    fun pendingCount(): Int
    fun isSeen(jobId: String): Boolean
    fun markSeen(jobId: String)
}

class BizDb(context: Context) : SQLiteOpenHelper(context.applicationContext, "giselle_business.db", null, 1), BizStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT NOT NULL)")
        db.execSQL("CREATE TABLE ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, kind TEXT NOT NULL, cents INTEGER NOT NULL, note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE approvals (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, kind TEXT NOT NULL, text TEXT NOT NULL, cents INTEGER NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE seen (job_id TEXT PRIMARY KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun getKv(key: String): String? =
        readableDatabase.rawQuery("SELECT v FROM kv WHERE k = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    override fun setKv(key: String, value: String) {
        writableDatabase.insertWithOnConflict("kv", null,
            ContentValues().apply { put("k", key); put("v", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun addLedger(kind: String, cents: Long, note: String, ts: Long): Long =
        writableDatabase.insert("ledger", null, ContentValues().apply {
            put("ts", ts); put("kind", kind); put("cents", cents); put("note", note)
        })

    override fun sumLedger(kind: String, fromTs: Long, toTs: Long): Long =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(cents), 0) FROM ledger WHERE kind = ? AND ts >= ? AND ts <= ?",
            arrayOf(kind, fromTs.toString(), toTs.toString()),
        ).use { c -> c.moveToFirst(); c.getLong(0) }

    override fun addApproval(kind: String, text: String, cents: Long, ts: Long): Long =
        writableDatabase.insert("approvals", null, ContentValues().apply {
            put("ts", ts); put("kind", kind); put("text", text); put("cents", cents); put("status", Status.PENDING)
        })

    override fun approval(id: Long): ApprovalItem? =
        readableDatabase.rawQuery(
            "SELECT id, kind, text, cents, status, ts FROM approvals WHERE id = ?", arrayOf(id.toString()),
        ).use { c -> if (c.moveToFirst()) row(c) else null }

    override fun resolveApproval(id: Long, approve: Boolean): Boolean {
        val status = if (approve) Status.APPROVED else Status.DENIED
        val rows = writableDatabase.update("approvals", ContentValues().apply { put("status", status) },
            "id = ? AND status = ?", arrayOf(id.toString(), Status.PENDING))
        return rows == 1
    }

    override fun pendingApprovals(): List<ApprovalItem> =
        readableDatabase.rawQuery(
            "SELECT id, kind, text, cents, status, ts FROM approvals WHERE status = ? ORDER BY id",
            arrayOf(Status.PENDING),
        ).use { c -> buildList { while (c.moveToNext()) add(row(c)) } }

    override fun pendingCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM approvals WHERE status = ?", arrayOf(Status.PENDING))
            .use { c -> c.moveToFirst(); c.getInt(0) }

    override fun isSeen(jobId: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM seen WHERE job_id = ?", arrayOf(jobId)).use { it.moveToFirst() }

    override fun markSeen(jobId: String) {
        writableDatabase.insertWithOnConflict("seen", null,
            ContentValues().apply { put("job_id", jobId) }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun row(c: android.database.Cursor) = ApprovalItem(
        id = c.getLong(0), kind = c.getString(1), text = c.getString(2),
        cents = c.getLong(3), status = c.getString(4), ts = c.getLong(5),
    )
}
