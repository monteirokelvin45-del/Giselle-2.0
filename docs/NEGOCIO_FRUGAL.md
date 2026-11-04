# Módulo de negócio frugal e supervisionado (`com.example.jarvis.business`)

## Ideia central

Um agente frugal e supervisionado. A IA faz o trabalho de pesquisa e redação, e tu decides tudo o que
tem consequência real. O app nunca envia propostas, nunca move dinheiro e nunca cria contas. Só prepara,
avisa e regista.

## Estrutura

| Ficheiro | Função |
|---|---|
| `GiselleBusiness.kt` | Porta de entrada: `init`, `start`, `stop`, `runNow`, `command`, `status`, e o fluxo `events` |
| `CycleWorker.kt` / `CycleRunner.kt` | Motor de hora a hora (WorkManager) e um ciclo com passos isolados |
| `Constitution.kt` | As três leis, em código: I não prejudicar, II aprovação humana, III não enganar |
| `LlmClient.kt` | `Brain`: fala com a IA pelo Worker (proxy assinado). Conta falhas para o nível |
| `FreelanceHunter.kt` | Lê feeds RSS (We Work Remotely, RemoteOK) e redige propostas em inglês |
| `InjectionDefense.kt` | Limpa HTML e controlo; descarta texto suspeito; envolve o resto como dado externo |
| `ApprovalGate.kt` | Pendências (máx. 10), tetos verificados de novo ao aprovar, aprovação que conta uma vez |
| `SpendPolicy.kt` | Tetos: 25 USD por transação, 50 por dia, 200 por mês |
| `Treasury.kt` / `PhaseManager.kt` | Divisão do dinheiro, saldos a partir do razão, transições de fase |
| `BusinessModule.kt` | Plano de micro empresa para Maputo e lote de 5 posts por dia |
| `Tier.kt` | Nível por bateria e saúde do modelo |
| `GiselleDb.kt` | SQLite próprio: `kv`, `ledger`, `approvals`, `seen` |
| `Money.kt` | Todo o dinheiro em centavos inteiros |
| `Commands.kt` | Interpretação dos comandos de texto (pura, testada) |

## Ciclo

1. O WorkManager acorda o motor a cada hora, só com rede. O worker devolve sempre `success`.
2. O nível define o intervalo: normal 1 h, baixo 3 h, crítico 6 h. No crítico não há chamadas à IA.
3. Transição: se o cofre atingiu a meta, passa da fase 1 para a 2.
4. Conciliação: se a tua parte acumulada passar de 25 USD, pede uma aprovação.
5. Trabalho, conforme a fase. Reflexão: uma vez por dia, até 60 palavras.
6. Cada passo corre isolado: uma falha não derruba os outros.

## Fases

- **Fase 1:** propostas de freelance (até 3 por ciclo), só com as habilidades que escreveste. Cada proposta
  chega como pendência com número, por exemplo `#12`. Sem habilidades, o motor não redige nada.
- **Fase 2:** plano de micro negócio (nicho, produto, preço, canal, plano de 7 dias, custo). Depois de
  `plano aprovado`, gera 5 posts por dia, também como pendências.
- **Fase 3:** ativada só por `empresa pronta`. O dinheiro novo divide-se em 30% reinvestimento,
  30% "ideia milionária" e 40% tua (os centavos de arredondamento ficam contigo).

## Dinheiro

- Líquido = bruto − taxa. A divisão trabalha sobre o líquido.
- Fases 1 e 2: 100% do líquido vai para a fundação.
- Cofre = fundação + reinvestimento − gastos reais. É sempre calculado a partir do razão.
- `recebi X` é manual. Propostas ganhas não contam como receita até escreveres `recebi`.

## Decisões que tomei onde a proposta conflitava com as regras do projeto

1. **Chave do OpenRouter não fica no aparelho.** A proposta guardava a chave em texto simples no SQLite.
   Isso viola a regra do projeto de que chaves ficam só como segredos do Worker. O módulo chama a IA pelo
   proxy assinado, e o OpenRouter entrou como provedor do Worker (`OPENROUTER_API_KEY`, reserva).
2. **Contradição do gasto na fase 1.** Os tetos se aplicam a gastos *planeados* (`planear gasto X nota`,
   que cria uma pendência). `gastei X nota` registra um gasto que já fizeste e nunca é bloqueado, porque o
   app não paga nada. Assim a contabilidade continua alinhada com o mundo real.
3. **Aprovar não envia nada.** Aprovar uma proposta a marca como aprovada. Quem envia és tu, na plataforma,
   com a tua conta. O mesmo vale para os posts.
4. **Meta por omissão de USD 300.** A proposta não dizia o valor. Muda com `meta X`.
5. **Mês = últimos 30 dias** nos tetos de gasto aprovado, para não depender de calendário.

## Pontos fracos (da proposta, mantidos)

- A defesa contra injeção é uma lista de padrões. É contornável. A tua aprovação é a barreira real.
- Os modelos gratuitos mudam de nome. Um erro de modelo se resolve no Worker (`OPENROUTER_MODEL`, `GROQ_MODEL`, `NIM_MODEL`).
- Os feeds são só de programação remota. Sem a habilidade "programação", o motor rende pouco.

## Integração

```kotlin
GiselleBusiness.init(context)            // uma vez
GiselleBusiness.start(context)           // liga o motor (de hora a hora)
GiselleBusiness.runNow(context)          // ciclo imediato
val resposta = GiselleBusiness.command(context, "recebi 150 taxa 10")   // suspenso, fora da UI
GiselleBusiness.events.collect { aviso -> /* mostrar na UI */ }
```

`MainActivity` do módulo original é só uma tela de teste e não foi trazida. Nada está ligado à interface
principal ainda; isso é decisão tua.

## Estado

- Código e testes (regras puras e porteiro com banco em memória) no repositório.
- Não validado no aparelho: o SQLite, o WorkManager e os feeds reais.
- Os URLs de feed (`weworkremotely.com`, `remoteok.com`) não foram verificados desde o aparelho.
