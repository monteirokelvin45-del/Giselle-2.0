// ============================================================
// tracker/data/TrackerModels.kt
// ------------------------------------------------------------
// Módulo "Homem 10/10" — isolado do núcleo do Jarvis.
// Nenhum arquivo de accessibility/, voice/ ou agent/ (fora do
// branch TrackerContext) depende disto. Deletar este pacote
// inteiro não quebra o Jarvis original.
//
// PRINCÍPIO DE DESIGN (não negociável, herdado do SafetyGate):
// nenhuma tabela aqui é preenchida automaticamente pela IA.
// Todo registro nasce de uma ação explícita do usuário. Onde
// o dado é "plano" (ex: rotina de treino) em vez de "fato
// confirmado" (ex: treino realizado), isso é modelado como
// tabelas SEPARADAS — nunca a mesma tabela com uma flag que
// pode ser esquecida.
// ============================================================

package com.example.jarvis.tracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// -----------------------------------------------------
// Perfil e controle de onboarding
// -----------------------------------------------------

/**
 * Perfil oficial do usuário. status="draft" enquanto o onboarding
 * está em andamento; só vira "confirmado" na tela final, e só
 * nesse momento os dados contam como perfil real para o resto
 * do app (dashboard, IA, KPIs).
 */
@Entity(tableName = "config_perfil")
@Serializable
data class ConfigPerfil(
    @PrimaryKey val id: Int = 0, // single-user local: sempre 0
    val nome: String? = null,
    val idade: Int? = null,
    val alturaCm: Int? = null,
    val pesoInicialKg: Double? = null,
    val objetivoPrincipal: String? = null,
    val nivelExperienciaTreino: String? = null,
    val moeda: String = "MZN",
    val temaUi: String = "escuro",
    val status: String = "draft", // "draft" | "confirmado"
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Estado bruto do onboarding em progresso. respostasJson guarda
 * tudo que já foi respondido, mesmo em etapas incompletas — se o
 * usuário fecha o app no meio, retoma exatamente daqui.
 */
@Entity(tableName = "onboarding_state")
data class OnboardingState(
    @PrimaryKey val id: Int = 0, // single-user local: sempre 0
    val etapaAtual: Int, // 1 a 7, ver OnboardingStep
    val respostasJson: String,
    val status: String, // "em_andamento" | "confirmado"
    val updatedAt: Long
)

enum class OnboardingStep(val numero: Int, val titulo: String) {
    PERFIL(1, "Perfil básico"),
    TREINO(2, "Rotina de treino"),
    SONO_HABITOS(3, "Sono e hábitos"),
    ESTUDOS_CARREIRA(4, "Estudos e carreira"),
    FINANCAS(5, "Finanças"),
    METAS(6, "Metas 10/10"),
    CONFIRMACAO(7, "Resumo e confirmação")
}

// -----------------------------------------------------
// Metas — separado do perfil porque muda com menos
// frequência que registros, mas mais que o perfil básico
// -----------------------------------------------------

@Entity(tableName = "metas")
@Serializable
data class Metas(
    @PrimaryKey val id: Int = 0,
    val pesoIdealKg: Double? = null,
    val gorduraIdealPercentual: Double? = null,
    val rendaMensalAlvo: Double? = null,
    val horasEstudoSemanaAlvo: Int? = null,
    val confortoSocialDesejado: String? = null,
    val reservaFinanceiraAlvo: Double? = null,
    val visao3Meses: String? = null,
    val visao12Meses: String? = null,
    val updatedAt: Long
)

// -----------------------------------------------------
// Físico — plano (o que pretende fazer) separado de
// registro (o que de fato aconteceu, confirmado por você)
// -----------------------------------------------------

/** A rotina de treino cadastrada é um PLANO, nunca histórico. */
@Entity(tableName = "plano_treino")
data class PlanoTreino(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val diaSemana: Int, // 1-7
    val grupoMuscular: String, // ex: "peito, tríceps e ombros"
    val ativo: Boolean = true
)

/** Só existe uma linha aqui quando o usuário CONFIRMA que treinou. */
@Entity(tableName = "registro_fisico")
@Serializable
data class RegistroFisico(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val pesoKg: Double? = null,
    val gorduraPercentual: Double? = null,
    val medidasCinturaCm: Double? = null,
    val medidasBracoCm: Double? = null,
    val treinoDiaResumo: String? = null,
    val treinoConfirmado: Boolean, // false = "parcialmente concluído"
    val observacoes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "registro_exercicio",
    foreignKeys = [
        ForeignKey(
            entity = RegistroFisico::class,
            parentColumns = ["id"],
            childColumns = ["registroFisicoId"],
            onDelete = ForeignKey.CASCADE // exercício não existe sem o registro do dia
        )
    ],
    indices = [Index("registroFisicoId")] // Room/SQLite exigem índice na FK por performance
)
@Serializable
data class RegistroExercicio(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registroFisicoId: Long, // FK real -> RegistroFisico.id (CASCADE no delete)
    val nomeExercicio: String,
    val series: Int,
    val repeticoes: Int,
    val cargaKg: Double? = null,
    val rpe: Int? = null // percepção de esforço, opcional
)

// -----------------------------------------------------
// Mental & Disciplina
// -----------------------------------------------------

@Entity(tableName = "registro_mental")
@Serializable
data class RegistroMental(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val horaDormir: String? = null,
    val horaAcordar: String? = null,
    val horasSono: Double? = null,
    val qualidadeSono: Int? = null, // 1-5
    val notaDisciplina: Int? = null, // 1-5
    val interacoesSociaisQtd: Int? = null,
    val conversas3MinPlusQtd: Int? = null,
    val notaConfortoSocial: Int? = null, // 1-5
    val habitosConcluidosJson: String? = null, // lista de hábitos marcados no dia
    val createdAt: Long,
    val updatedAt: Long
)

// -----------------------------------------------------
// Protocolo Ayanokoji — 24 semanas, avanço nunca automático
// -----------------------------------------------------

enum class AyanokojiEstado { NAO_INICIADO, EM_ANDAMENTO, PAUSADO, CONCLUIDO, ARQUIVADO }

@Entity(tableName = "ayanokoji_progresso")
data class AyanokojiProgresso(
    @PrimaryKey val id: Int = 0,
    val semanaAtual: Int = 1,
    val estado: String = AyanokojiEstado.NAO_INICIADO.name,
    val updatedAt: Long
)

@Entity(tableName = "ayanokoji_registro_diario")
data class AyanokojiRegistroDiario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val semana: Int,
    val respiracaoBoxFeita: Boolean = false,
    val treinoTomNeutroFeito: Boolean = false,
    val diarioInteracoesFeito: Boolean = false,
    val notaControleEmocional: Int? = null, // 1-5
    val observacoes: String? = null
)

// -----------------------------------------------------
// Estudos & Carreira
// -----------------------------------------------------

@Entity(tableName = "registro_estudo")
@Serializable
data class RegistroEstudo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val area: String,
    val topico: String? = null,
    val duracaoMinutos: Int,
    val faseMue: String? = null, // fase do Modelo Universal de Estudo
    val praticaExecutada: Boolean = false,
    val resumo: String? = null,
    val receitaRelacionada: Double? = null,
    val createdAt: Long
)

// -----------------------------------------------------
// Leitura
// -----------------------------------------------------

@Entity(tableName = "registro_leitura")
@Serializable
data class RegistroLeitura(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val livro: String,
    val capituloLido: String? = null,
    val minutosLeitura: Int? = null,
    val aplicacaoPraticaFeita: Boolean = false,
    val anotacoesResumo: String? = null,
    val leituraBiblicaFeita: Boolean = false,
    val createdAt: Long
)

// -----------------------------------------------------
// Finanças — moeda vem do ConfigPerfil, nunca hard-coded aqui
// -----------------------------------------------------

enum class TipoTransacao { RECEITA, DESPESA, INVESTIMENTO, DIVIDA, TRANSFERENCIA }

@Entity(tableName = "registro_financeiro")
@Serializable
data class RegistroFinanceiro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val tipo: String, // ver TipoTransacao — TRANSFERENCIA nunca conta como receita/despesa nos KPIs
    val categoria: String,
    val valorCentavos: Long, // menor unidade da moeda, nunca Double (evita erro de arredondamento binário acumulado)
    val descricao: String? = null,
    val contaOrigem: String? = null,
    val projetoRelacionado: String? = null,
    val createdAt: Long
)

// -----------------------------------------------------
// Tabelas de controle — o que o Jarvis original só tinha
// pela metade (ActionLog); aqui ficam completas e servem
// tanto para o modo "tela" quanto para o modo "tracker"
// -----------------------------------------------------

/**
 * Toda sugestão da IA (nos dois modos do orquestrador) vira uma
 * linha aqui. feedback é preenchido depois, quando o usuário
 * aceita/recusa/ignora — é isso que alimenta o aprendizado real,
 * não o Jarvis "achando" que aprendeu algo.
 */
@Entity(tableName = "ai_events")
data class AiEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val origem: String, // "tela" | "tracker"
    val tipo: String, // ex: "insight_peso", "diagnostico_semanal"
    val dadosObservadosJson: String,
    val sugestaoTexto: String,
    val feedback: String? = null, // "aceita" | "recusada" | "ignorada"
    val resultadoPosterior: String? = null,
    val createdAt: Long
)

/**
 * Toda alteração feita pela IA (nunca pelo usuário direto — edição
 * manual não precisa passar por aqui) fica registrada. Isso é o que
 * torna impossível a IA mudar um dado silenciosamente.
 */
@Entity(tableName = "audit_log")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tabela: String,
    val registroId: Long,
    val campoAlterado: String,
    val valorAnterior: String?,
    val valorNovo: String,
    val origem: String, // "usuario" | "ia"
    val timestamp: Long
)

@Entity(tableName = "backups")
data class BackupRegistro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String, // "manual" | "pre_exclusao" | "automatico"
    val caminhoArquivo: String,
    val hashVersao: String,
    val createdAt: Long
)
