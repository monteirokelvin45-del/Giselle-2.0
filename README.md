# Giselle 2.0 — Assistente de IA para Android

A Giselle é um assistente de IA nativo para Android (Kotlin + Jetpack Compose), pensado para
telemóveis **sem serviços Google** (ex.: Huawei). Ouve a palavra-chave "Giselle", percebe o ecrã,
raciocina e age nas apps — com confirmação do utilizador para ações sensíveis.

> Evoluiu do projeto "Jarvis". O pacote interno `com.example.jarvis` foi mantido de propósito
> para não quebrar atualizações já instaladas.

## Funcionalidades

| Área | O que faz | Pasta |
|---|---|---|
| Voz | Wake word "Giselle" e reconhecimento de fala **offline** (Vosk) | `voice/` |
| Agente | Orquestrador: memória → contexto → LLM → **SafetyGate** → executa ou pede confirmação → log | `agent/`, `core/` |
| Ações no telemóvel | Ligar, SMS, alarme, temporizador, volume, rotinas, modo estudo, apps | `agent/IntentExecutor.kt` |
| Ecrã | Ler e atuar no ecrã via Acessibilidade (toque, texto, swipe), com filtro de privacidade | `accessibility/` |
| Visão | Captura de ecrã e "o que estou a ver?" | `vision/` |
| Notificações | Ler e responder notificações, atender/recusar chamadas | `notifications/` |
| Memória | Memória de longo prazo (Room + similaridade vetorial) | `memory/` |
| Skills | Skills em JSON, incluindo aprender por demonstração | `skills/` |
| Rotinas | Agendador de rotinas e resumo diário | `routine/` |
| Visual | Ondas nas bordas do ecrã e avatar interativo | `visual/` |
| Tracker | Registos físicos, mentais, financeiros e de estudo, com onboarding | `tracker/` |
| Autoatualização | Atualização de APK assinado e autodiagnóstico com revisão de patches | `selfupdate/`, `updates/` |

## Arquitetura

```
Voz (Vosk offline, wake word "Giselle")  ou  texto
                 │
                 ▼
        AgentOrchestrator
   recall → contexto → cérebro (via proxy) → SafetyGate → executa ou pede confirmação → log
        │            │                │
   executores   MemoryStore      SafetyGate
 Intents → Acessibilidade → Visão   (Room + vetor)  (regras fixas — o LLM não decide isto)
```

Princípios de segurança:

- **Chaves de provedores nunca estão no APK.** A `ANTHROPIC_API_KEY` (ou a `GEMINI_API_KEY`) vive
  só no Cloudflare Worker (`backend-proxy/`).
- **O app autentica-se no proxy com HMAC-SHA256** sobre timestamp + nonce + client-id + corpo,
  com proteção contra replay, janela de ±5 min e limites de taxa por cliente e por IP.
- **O SafetyGate é código fixo**, não uma decisão do modelo. Chaves como `safetyGate`,
  `permissions` e `accessibility` nunca podem ser alteradas por configuração remota.
- **Configuração remota é assinada pela mesma keystore** e entra em modo "pendente" até um ciclo
  bem-sucedido; arranques falhados fazem rollback automático.

## Requisitos

- Android Studio (Hedgehog ou mais recente) ou JDK 17 + Android SDK (API 34)
- Android 9+ (minSdk 28) — o dispositivo precisa de permissões de Acessibilidade e de notificações
- Node.js 18+ (testes do Worker) e conta Cloudflare (gratuita) para o proxy
- Python 3.9+ (script de provisionamento do proxy; só biblioteca padrão)

## Começar

### 1. Gradle Wrapper

O `gradle-wrapper.jar` é binário. Se faltar, `./setup.sh` tenta gerá-lo com um Gradle instalado,
ou abre o projeto no Android Studio, que o regenera na primeira sincronização.

### 2. Publicar o proxy de IA

```bash
cd backend-proxy
npm test                       # 17 testes do Worker, sem rede
npx wrangler secret put ANTHROPIC_API_KEY        # opcional; sem ela usa Workers AI (grátis)
npx wrangler secret put PROXY_SHARED_SECRET
npx wrangler deploy
python3 provision.py --check   # auditoria dos segredos
```

Guarda a URL que o `wrangler deploy` imprime.

### 3. Configurar o app

Copia `local.properties.template` para `local.properties` e preenche:

```properties
sdk.dir=/caminho/para/o/Android/sdk
PROXY_BASE_URL=https://o-teu-worker.workers.dev
PROXY_SHARED_SECRET=o-mesmo-valor-do-worker
```

`local.properties` está no `.gitignore` e nunca deve ser commitado.

### 4. Compilar e testar

```bash
./gradlew testDebugUnitTest    # testes unitários do app
./gradlew assembleDebug        # APK de depuração
```

Para um APK de release assinado, configura a keystore (ver `ATUALIZACOES.md`, secção 2).
**Guarda a keystore com backup**: sem ela não há atualizações futuras.

## Testes e CI

| Workflow | Quando corre | O que valida |
|---|---|---|
| `tests.yml` | push e PR, qualquer branch | Testes unitários Android (`testDebugUnitTest`) |
| `worker-tests.yml` | push e PR, qualquer branch | Sintaxe e testes do Worker (`npm test`) |
| `build.yml` | push em `main` ou manual | APK debug e, com segredos configurados, APK release |

## Estrutura do repositório

```
app/                 App Android (Kotlin/Compose)
backend-proxy/       Cloudflare Worker + testes + provisionamento
.github/workflows/   CI
ANDROID_README.md    Detalhes da arquitetura Android
ATUALIZACOES.md      Configuração de atualizações e keystore
GRADLE_WRAPPER_SETUP.md  Notas sobre o Gradle Wrapper
SECURITY.md          Como reportar vulnerabilidades
CONTRIBUTING.md      Como contribuir
```

## Estado atual e limitações conhecidas

- O URL de atualizações em `app/.../updates/GiselleUpdater.kt` ainda tem o placeholder
  `SEU_USUARIO`; tem de ser trocado antes de a verificação de atualizações funcionar.
- Não há ainda um ficheiro de licença. Sem licença, o código não é open source por defeito.
- O ficheiro `giselle-completo.zip` é um snapshot antigo do projeto; a fonte de verdade é o código
  versionado em `app/` e `backend-proxy/`.
- A suíte Android não corre no ambiente de desenvolvimento sem Android SDK; use o CI ou o
  Android Studio.
