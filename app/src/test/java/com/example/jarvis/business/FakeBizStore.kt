package com.example.jarvis.business

/** Banco em memória para testar regras sem Android. */
class FakeBizStore : BizStore {
    private val kv = HashMap<String, String>()
    private val ledger = mutableListOf<Triple<Long, String, Long>>()
    private val approvals = mutableListOf<ApprovalItem>()
    private val seen = HashSet<String>()

    override fun getKv(key: String) = kv[key]
    override fun setKv(key: String, value: String) { kv[key] = value }

    override fun addLedger(kind: String, cents: Long, note: String, ts: Long): Long {
        ledger += Triple(ts, kind, cents)
        return ledger.size.toLong()
    }

    override fun sumLedger(kind: String, fromTs: Long, toTs: Long): Long =
        ledger.filter { it.second == kind && it.first in fromTs..toTs }.sumOf { it.third }

    override fun addApproval(kind: String, text: String, cents: Long, ts: Long): Long {
        val id = approvals.size + 1L
        approvals += ApprovalItem(id, kind, text, cents, Status.PENDING, ts)
        return id
    }

    override fun approval(id: Long): ApprovalItem? = approvals.firstOrNull { it.id == id }

    override fun resolveApproval(id: Long, approve: Boolean): Boolean {
        val i = approvals.indexOfFirst { it.id == id && it.status == Status.PENDING }
        if (i < 0) return false
        approvals[i] = approvals[i].copy(status = if (approve) Status.APPROVED else Status.DENIED)
        return true
    }

    override fun pendingApprovals(): List<ApprovalItem> = approvals.filter { it.status == Status.PENDING }
    override fun pendingCount(): Int = pendingApprovals().size
    override fun isSeen(jobId: String): Boolean = jobId in seen
    override fun markSeen(jobId: String) { seen += jobId }
}
