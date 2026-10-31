# Giselle — Assistente de IA para Android

Assistente nativo Android (Kotlin/Compose) pensado para telemóveis sem serviços Google (ex.: Huawei).
Ouve pelo nome "Giselle", percebe o ecrã, raciocina e age nos apps. (Evoluiu do antigo projeto "Jarvis";
o pacote interno `com.example.jarvis` foi mantido de propósito para não quebrar atualizações.)

## Arquitetura

```
Voz (Vosk offline, wake word "Giselle")  ou  texto
                 │
                 ▼
        AgentOrchestrator  ← o maestro
   recall → contexto → cérebro (via proxy) → SafetyGate → executa ou pede confirmação → log
        │            │                │
   3 executores   MemoryStore      SafetyGate
 Intents → Acessibilidade → Visão   (Room + vetor)  (hard-coded, o LLM não decide isto)
```

| Pasta | Função |
|---|---|
| `agent/` | Orquestrador e `IntentExecutor` (ligar, SMS, alarme, temporizador, volume, rotinas, dinheiro móvel, modo estudo…) |
| `accessibility/` | Ler o ecrã e agir (toque, texto, swipe) |
| `vision/` | Captura de ecrã e "o que estou a ver?" |
| `voice/` | Wake word e STT offline com Vosk, TTS nativo |
| `visual/` | Ondas nas bordas e avatar interativo (humor, skins) |
| `memory/` | Memória de longo prazo (Room + similaridade) |
| `skills/` | Skills em JSON, incluindo aprender por demonstração |
| `notifications/` | Ler e responder notificações, atender/recusar chamadas |
| `selfupdate/` | Autodiagnóstico e atualização de APK |
| `backend-proxy/` | Cloudflare Worker: guarda as chaves e autentica o app (HMAC + anti-replay) |

## Segurança (fundação, não feature)

O `SafetyGate` é determinístico: dinheiro, terceiros, dados sensíveis e ações irreversíveis **sempre**
pedem confirmação, mesmo que o modelo ache que não precisa. Dinheiro móvel (`MOBILE_MONEY`) só abre
o discador com o menu USSD; a Giselle nunca digita PIN nem valores.

## Configuração

Copie `local.properties.template` para `local.properties` e preencha `PROXY_BASE_URL` e `PROXY_SHARED_SECRET`.
O app não compila chaves de IA: tudo passa pelo proxy.

Códigos USSD de dinheiro móvel (padrão M-Pesa `*150#`, e-Mola `*898#`) ficam em SharedPreferences
`giselle_cfg` (`ussd_mpesa`, `ussd_emola`). **Confirme os códigos com o seu operador.**

## Backend proxy — `backend-proxy/`

Modo **grátis** (sem `ANTHROPIC_API_KEY`): Cloudflare Workers AI (Llama 3.3) para texto e
`GEMINI_API_KEY` opcional para visão. Modo pago: `wrangler secret put ANTHROPIC_API_KEY`.

```
npm install -g wrangler && wrangler login
python3 provision.py                      # gera o PROXY_SHARED_SECRET
wrangler deploy
wrangler secret put PROXY_SHARED_SECRET   # valor do provision.py
wrangler secret put GEMINI_API_KEY        # opcional (visão grátis)
```
Rotação sem downtime: `python3 provision.py --rotate`. Auditoria: `python3 provision.py --check`.

## Como compilar

1. `./setup.sh`, abra no Android Studio, configure `local.properties`.
2. `./gradlew assembleDebug` (ou o workflow "Build Giselle APK" no GitHub Actions).
3. Instale o APK e ative em Definições → Acessibilidade → Giselle.
4. Conceda microfone, notificações e (para atualizações) "instalar apps desconhecidos".
5. **Huawei:** ative início automático e tire a Giselle da otimização de bateria.

Testes: `./gradlew testDebugUnitTest`.

## O que ainda falta (sem fingir que está pronto)

Não implementado: voz própria (Piper), tradutora ao vivo, modo carro, painel web, gémeo digital,
câmara, equipa de agentes e modo sonho. Dinheiro móvel e modo estudo estão implementados na versão
mínima (discador USSD e temporizador de foco). Este código foi revisto, mas **ainda não foi compilado
nem testado em aparelho real** — o primeiro build pode pedir pequenos ajustes.
