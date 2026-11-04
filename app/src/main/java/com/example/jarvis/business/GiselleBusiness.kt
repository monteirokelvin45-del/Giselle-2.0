// business/GiselleBusiness.kt — porta de entrada do módulo.
// init uma vez; start/stop agendam o motor; runNow força um ciclo; command e status são suspensos
// (correm em IO, fora da thread principal). Os avisos saem por `events`.
package com.example.jarvis.business

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.jarvis.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object GiselleBusiness {
    const val WORK_NAME = "giselle-business"

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val lock = Mutex()
    @Volatile private var services: Services? = null

    private class Services(
        val store: BizStore,
        val guard: BrainGuard,
        val gate: ApprovalGate,
        val balances: Balances,
        val runner: CycleRunner,
        val hunter: FreelanceHunter,
    )

    private fun emit(msg: String) {
        _events.tryEmit(msg)
    }

    /** Chama uma vez. É seguro chamar de novo. */
    fun init(context: Context) {
        if (services != null) return
        synchronized(this) {
            if (services != null) return
            val app = context.applicationContext
            val store = BizDb(app)
            val guard = BrainGuard(store, ProxyBrain(
                proxyBaseUrl = BuildConfig.PROXY_BASE_URL.ifBlank { null },
                proxySharedSecret = BuildConfig.PROXY_SHARED_SECRET.ifBlank { null },
                clientId = com.example.jarvis.llm.ProxyAuth.clientId(app),
            ))
            val gate = ApprovalGate(store)
            val balances = Balances(store)
            val hunter = FreelanceHunter(
                ask = { s, u, m -> guard.ask(s, u, m) },
                store = store,
                gate = gate,
                skills = { store.getKv(BizKeys.SKILLS)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList() },
                feeds = FreelanceHunter.DEFAULT_FEEDS,
            )
            val runner = CycleRunner(store, guard, gate, hunter, balances, System::currentTimeMillis, ::emit)
            services = Services(store, guard, gate, balances, runner, hunter)
        }
    }

    private fun requireServices(): Services {
        val s = services ?: error("GiselleBusiness.init(context) não foi chamado")
        return s
    }

    /** Liga o motor: de hora a hora, só com rede. O worker devolve sempre success. */
    fun start(context: Context) {
        init(context)
        val request = PeriodicWorkRequestBuilder<CycleWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        emit("Motor ligado: corre de hora a hora, com rede.")
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        emit("Motor parado.")
    }

    /** Corre um ciclo já, ignorando o intervalo do nível. */
    suspend fun runNow(context: Context): String {
        init(context)
        return lock.withLock {
            val s = requireServices()
            val tier = currentTier(context, s)
            val failed = s.runner.cycle(tier)
            if (failed.isEmpty()) "Ciclo concluído (nível ${tier.name})." else "Ciclo com falhas: ${failed.joinToString()}."
        }
    }

    /** Usado pelo CycleWorker: respeita o intervalo do nível. */
    suspend fun cycleIfDue(context: Context) {
        init(context)
        lock.withLock {
            val s = requireServices()
            val tier = currentTier(context, s)
            val last = s.store.getKv(BizKeys.LAST_RUN)?.toLongOrNull() ?: 0L
            val due = System.currentTimeMillis() - last >= tier.intervalHours * 60 * 60 * 1000
            if (due) s.runner.cycle(tier)
        }
    }

    suspend fun command(context: Context, text: String): String = withContext(Dispatchers.IO) {
        init(context)
        lock.withLock { handle(CommandParser.parse(text), requireServices()) }
    }

    suspend fun status(context: Context): String = withContext(Dispatchers.IO) {
        init(context)
        lock.withLock { statusText(requireServices()) }
    }

    private fun currentTier(context: Context, s: Services): Tier {
        val intent = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
        return TierPolicy.of(pct, s.guard.modelErrors())
    }

    private fun statusText(s: Services): String {
        val pending = s.store.pendingApprovals()
        val pendingLine = if (pending.isEmpty()) "sem pendências"
        else "pendências: " + pending.joinToString(" · ") { "#${it.id} ${it.kind.lowercase()}" }
        return buildString {
            appendLine("Fase ${s.runner.phase().number} · cofre ${s.balances.vault().format()}")
            appendLine("Tua parte por conciliar: ${s.balances.ownerBalance().format()}")
            appendLine(pendingLine)
            s.store.getKv(BizKeys.REFLECTION)?.let { appendLine("Última reflexão: $it") }
        }.trimEnd()
    }

    private fun handle(cmd: Command, s: Services): String = when (cmd) {
        Command.Status -> statusText(s)
        Command.Help -> CommandParser.HELP
        is Command.Invalid -> cmd.reason
        is Command.Decide -> decide(cmd, s)
        is Command.Received -> received(cmd, s)
        is Command.SpentReal -> {
            if (cmd.amount.value <= 0) "valor inválido"
            else {
                s.store.addLedger(Ledger.SPEND_REAL, cmd.amount.value, cmd.note, System.currentTimeMillis())
                "Gasto registado: ${cmd.amount.format()}. Cofre: ${s.balances.vault().format()}."
            }
        }
        is Command.PlanSpend -> when (val r = s.gate.request(Kind.SPEND, cmd.note.ifBlank { "gasto planeado" }, cmd.amount.value)) {
            is ApprovalGate.Outcome.Ok -> "Pedido #${r.item.id}: gastar ${cmd.amount.format()}. Aprova com \"aprovar ${r.item.id}\"."
            is ApprovalGate.Outcome.Rejected -> "Não posso pedir: ${r.reason}."
        }
        Command.CompanyReady -> {
            s.store.setKv(BizKeys.PHASE, Phase.SCALE.number.toString())
            emit("Fase 3 ativa: divisão 30/30/40 para o dinheiro novo.")
            "Fase 3 ativa."
        }
        Command.PlanApproved -> approvePlanLatest(s)
        is Command.Goal -> {
            s.store.setKv(BizKeys.GOAL, cmd.cents.value.toString())
            "Meta definida: ${cmd.cents.format()}."
        }
        is Command.Skills -> {
            s.store.setKv(BizKeys.SKILLS, cmd.list.joinToString(","))
            "Habilidades guardadas: ${cmd.list.joinToString(", ")}."
        }
    }

    private fun received(cmd: Command.Received, s: Services): String {
        if (cmd.gross.value <= 0) return "valor inválido"
        if (cmd.fee.value < 0 || cmd.fee.value >= cmd.gross.value) return "a taxa tem de ser menor que o valor recebido"
        val net = cmd.gross - cmd.fee
        val split = Treasury.split(net, s.runner.phase())
        val now = System.currentTimeMillis()
        s.store.addLedger(Ledger.RECEIVED, cmd.gross.value, "bruto", now)
        if (cmd.fee.value > 0) s.store.addLedger(Ledger.SPEND_REAL, cmd.fee.value, "taxa", now)
        if (split.foundation.value > 0) s.store.addLedger(Ledger.FOUNDATION, split.foundation.value, "recebido", now)
        if (split.reinvest.value > 0) s.store.addLedger(Ledger.REINVEST, split.reinvest.value, "recebido", now)
        if (split.idea.value > 0) s.store.addLedger(Ledger.IDEA, split.idea.value, "recebido", now)
        if (split.owner.value > 0) s.store.addLedger(Ledger.OWNER_SHARE, split.owner.value, "recebido", now)
        return "Recebido ${cmd.gross.format()} (líquido ${net.format()}). Cofre: ${s.balances.vault().format()}."
    }

    private fun decide(cmd: Command.Decide, s: Services): String {
        if (!cmd.approve) {
            return when (val r = s.gate.deny(cmd.id)) {
                is ApprovalGate.Outcome.Ok -> "Pedido #${cmd.id} negado."
                is ApprovalGate.Outcome.Rejected -> r.reason
            }
        }
        return when (val r = s.gate.approve(cmd.id)) {
            is ApprovalGate.Outcome.Rejected -> r.reason
            is ApprovalGate.Outcome.Ok -> afterApprove(r.item, s)
        }
    }

    /** Efeitos colaterais só depois de a aprovação ser gravada (uma vez). */
    private fun afterApprove(item: ApprovalItem, s: Services): String {
        val now = System.currentTimeMillis()
        return when (item.kind) {
            Kind.SPEND -> {
                s.store.addLedger(Ledger.SPEND_APPROVED, item.cents, item.text, now)
                "Gasto aprovado: ${Cents(item.cents).format()}. Faz tu o pagamento e depois escreve \"gastei X\"."
            }
            Kind.PLAN -> {
                s.store.setKv("approved_plan_id", item.id.toString())
                s.store.setKv(BizKeys.PLAN_APPROVED, "1")
                "Plano #${item.id} aprovado. Vou preparar os posts."
            }
            Kind.PAYOUT -> {
                s.store.addLedger(Ledger.OWNER_RECONCILED, item.cents, "conciliação #${item.id}", now)
                "Conciliação registada: ${Cents(item.cents).format()}."
            }
            Kind.PROPOSAL -> "Proposta #${item.id} aprovada. Lembrete: envia tu, na plataforma, com a tua conta."
            Kind.POST -> "Posts #${item.id} aprovados. Publica tu, nas tuas contas."
            else -> "Pedido #${item.id} aprovado."
        }
    }

    private fun approvePlanLatest(s: Services): String {
        val plan = s.store.pendingApprovals().lastOrNull { it.kind == Kind.PLAN }
            ?: return "Não há plano pendente. Espera pelo próximo ciclo ou escreve \"aprovar N\"."
        return handle(Command.Decide(plan.id, approve = true), s)
    }
}
