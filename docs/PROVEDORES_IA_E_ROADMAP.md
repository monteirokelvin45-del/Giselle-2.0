# Provedores de IA e mapa de funcionalidades

Este documento registra (1) como a IA da Giselle é alimentada, (2) as referências
pesquisadas no GitHub e (3) o estado real de cada item da lista de funcionalidades.
"Feito" significa código no repositório com teste automatizado. Nada aqui foi
validado num aparelho com a nova configuração.

## 1. Cadeia de IA (backend-proxy/providers.js) — Groq é o principal

O app fala sempre o formato da Anthropic. O Worker traduz e tenta os provedores
nesta ordem; cada um só entra se a sua chave ou binding existir:

| Ordem | Provedor | Segredo no Worker | Modelo padrão | Endpoint |
|---|---|---|---|---|
| 2 | Anthropic (reserva) | `ANTHROPIC_API_KEY` | (pedido do app, `claude-sonnet-4-6`) | api.anthropic.com |
| 1 | Groq (principal) | `GROQ_API_KEY` | `llama-3.3-70b-versatile` (var `GROQ_MODEL`) | api.groq.com/openai/v1 |
| 3 | NVIDIA NIM | `NVIDIA_API_KEY` | `meta/llama-3.3-70b-instruct` (var `NIM_MODEL`) | integrate.api.nvidia.com/v1 |
| 4 | Workers AI | binding `AI` | `@cf/meta/llama-3.3-70b-instruct-fp8-fast` | Cloudflare |

Visão (screenshot): NVIDIA NIM → Anthropic → Gemini (Gemini só como última reserva, por privacidade: o plano gratuito pode usar dados para melhorar produtos)
(`meta/llama-3.2-11b-vision-instruct`, var `NIM_VISION_MODEL`) → Anthropic.

Regras de reserva: erros de rede e 401/403/408/425/429/5xx passam ao próximo
provedor. Erro 400 (pedido inválido) não é repetido. Se todos falharem, o Worker
responde 502 com a lista de tentativas (sem chaves).

**Chaves nunca vão para o APK nem para o GitHub.** Configure no Worker:

```bash
cd backend-proxy
npx wrangler secret put GROQ_API_KEY      # chave em https://console.groq.com/keys
npx wrangler secret put NVIDIA_API_KEY    # chave nvapi-... em build.nvidia.com
```

Depois de publicar (`npx wrangler deploy`), o app não precisa de nova versão:
o Worker passa a usar os novos provedores automaticamente.

Testes: `cd backend-proxy && npm test` (30 testes, sem rede).

## 2. Referências pesquisadas

- **NVIDIA NIM** — endpoint OpenAI-compatível `https://integrate.api.nvidia.com/v1`,
  chave `nvapi-`, sem cartão no plano de teste. Referência de integração:
  [NezerkC/opencode-provider-nvidia-nim](https://github.com/NezerkC/opencode-provider-nvidia-nim)
  e [miztertea/nim-proxy](https://github.com/miztertea/nim-proxy). O segundo indica
  um limite aproximado de 40 requisições por minuto por chave no plano gratuito;
  por isso NIM é reserva, não o primeiro provedor.
- **Groq** — endpoint `https://api.groq.com/openai/v1`, mesmo formato do OpenAI.
  Modelos citados em fontes de 2026: `llama-3.3-70b-versatile`, `llama-3.1-8b-instant`,
  modelos gpt-oss e Qwen.
- **On-device LLM (modo offline)** — duas referências Android, ainda **não integradas**:
  - [shreeharsh-patil/On-Device-LLM](https://github.com/shreeharsh-patil/On-Device-LLM):
    Kotlin, llama.cpp + LiteRT-LM, modelos Qwen e Dolphin. É a mais próxima da nossa base.
  - [sebytzr16/on-device-llm](https://github.com/sebytzr16/on-device-llm): Flutter,
    llama.cpp, Gemma 4 e Qwen, com ações no telefone que exigem toque do usuário.
  
  Integrar exige compilar llama.cpp com o NDK e baixar modelos de vários GB, o que
  não é possível validar neste ambiente. Fica como próxima fase. Hoje o `LocalLlm.kt`
  é um esqueleto (MediaPipe, carregado por reflexão) que só funciona com um arquivo
  `local-llm.task` no aparelho.

## 3. Mapa das funcionalidades pedidas

Legenda: **Feito** = código e teste; **Parcial** = existe, mas incompleto ou sem
validação no aparelho; **Planejado** = ainda não existe no código.

### 1. Voz e conversa
| Item | Estado | Observação |
|---|---|---|
| Wake word "Oi JARVIS" com tela apagada | Parcial | Existe `WakeWordService` com Vosk, mas a palavra configurada é "Giselle". Trocar exige modelo/gramática; testar no aparelho. |
| Reconhecimento contínuo (SpeechRecognizer / Whisper / Vosk) | Parcial | SpeechRecognizer e Vosk existem; Whisper só aparece como referência. |
| Conversa com memória | Parcial | `memory/` com embeddings reais via Worker (`op: "embed"`, `ProxyEmbedder`). Sem proxy, a busca semântica fica desligada (antes usava hash sem significado). Memórias antigas de hash são ignoradas. Falta validar a qualidade no aparelho. |
| Resposta falada (TTS) | Feito | Voz nativa com escolha automática de voz pt-BR (`VoiceSelection.kt`). |
| TTS premium (ElevenLabs, Cartesia) | Planejado | Exige chave e custo; não iniciado. |
| Modo mudo (responder por texto) | Planejado | A UI tem caixa de texto, mas não há modo mudo explícito. |
| Reconhecer o dono da voz | Planejado | Não há verificação de locutor. |

### 2. Controle do sistema Android
| Item | Estado | Observação |
|---|---|---|
| Abrir/fechar apps | Parcial | `AppLauncher` existe; teste `AppLauncherTest` pendente. |
| Ligações | Feito | `ActionType.CALL` com resolução de contato. |
| Mensagens: SMS | Feito | `SEND_SMS`. |
| Mensagens: WhatsApp / Telegram | Parcial | `APP_MESSAGE` via acessibilidade; depende de cada app. |
| Ler notificações em voz alta | Feito | `READ_NOTIFICATIONS`. |
| Responder notificações | Parcial | `REPLY_NOTIFICATION`; depende do app que expõe a resposta. |
| Alarmes, timers e lembretes | Feito | `SET_ALARM`, `SET_TIMER`, `SET_ROUTINE`. |
| Eventos na agenda | Planejado | Só leitura de agenda no resumo diário. |
| Wi-Fi, Bluetooth, brilho, volume, avião, não perturbe, rotação, screenshot, gravação | Parcial | Volume e lanterna existem. Os demais exigem permissões/APIs de sistema específicas. |
| Música (Spotify, YouTube Music) | Planejado | Não iniciado. |
| Navegação / GPS | Planejado | Só há `OPEN_URL`; sem integração de rota. |
| Buscar fotos, contatos, arquivos | Planejado | Só contatos para ligação. |
| Digitar por voz em qualquer app | Planejado | Exige teclado próprio (IME); não iniciado. |
| Acessibilidade (clicar e ler a tela) | Feito | `accessibility/` e `IntentExecutor`. Usar só com permissão explícita do usuário. |

### 3. Nível JARVIS
| Item | Estado | Observação |
|---|---|---|
| Rotinas por contexto | Parcial | Rotinas por horário; sem gatilhos de carro, chegada em casa etc. |
| Comando de voz para modo ("modo trabalho") | Parcial | `STUDY_MODE` e skills aprendidas; sem perfis gerais. |
| LLM converte para ação | Feito | `ClaudeClient` + `IntentExecutor`. |
| Agentes multi-passo | Parcial | `AgentOrchestrator` executa passos, com confirmação do `SafetyGate`. |
| Monitorar sensores | Parcial | Passos e movimento via `SensorManager`; sem regras de automação. |
| Ler e interagir com qualquer tela | Parcial | Acessibilidade e captura de tela; preencher formulários exige confirmação. |

Decisão mantida: nenhuma automação contorna o `SafetyGate`, e o LLM não altera
permissões nem código sem revisão.

### 4. Conhecimento e informação
| Item | Estado | Observação |
|---|---|---|
| Pesquisa na web com fontes | Parcial | Backend pronto: Worker `op: "search"` (Brave, depois Tavily), com título, URL e trecho. Falta a ação `WEB_SEARCH` no app e a fala com as fontes. |
| Perguntar sobre a tela ("o que é isso?") | Feito | `DESCRIBE_SCREEN` com captura e visão. |
| Ler e resumir PDFs, artigos, e-mails | Planejado | Só texto da tela; sem leitor de PDF. |
| Tradução ao vivo | Parcial | Via LLM sobre a tela; sem modo contínuo. |
| Clima, notícias, câmbio, trânsito, futebol | Planejado | Sem fontes de dados. |
| Dúvidas por foto | Feito | Visão via Gemini, NVIDIA NIM ou Anthropic. |
| Scanner de documentos | Planejado | Não iniciado. |

### 5. Produtividade
| Item | Estado | Observação |
|---|---|---|
| Notas e listas por voz | Planejado | Só despesas e lembretes. |
| E-mail (ler, resumir, redigir) | Planejado | Não iniciado. |
| Resumir conversas do WhatsApp | Planejado | Depende de acesso às notificações/tela. |
| Transcrever áudios recebidos | Planejado | Não iniciado. |
| Redação no seu estilo | Parcial | Possível via LLM; sem perfil de estilo. |
| Lembretes por contexto | Planejado | Só por horário. |

### 6. Casa e dispositivos (IoT)
| Item | Estado | Observação |
|---|---|---|
| Home Assistant | Planejado | Não iniciado. |
| Controlar o PC pelo celular | Planejado | Não iniciado. |
| Matter / Thread / Tuya / Zigbee | Planejado | Só via Home Assistant. |
| Interfone | Planejado | Não iniciado. |

### 7. Segurança e privacidade
| Item | Estado | Observação |
|---|---|---|
| Localização do celular | Planejado | Não iniciado. |
| Modo offline completo | Planejado | Ver seção 2 (on-device LLM). Hoje a IA usa a nuvem. |
| Detecção de sons | Planejado | Não iniciado. |
| Câmera de vigilância | Planejado | Não iniciado. Exige cuidado legal e de privacidade. |

### 8. Proatividade
| Item | Estado | Observação |
|---|---|---|
| Antecipar trânsito, chuva, aniversário | Planejado | Sem fontes de dados. |
| Filtrar spam e destacar o importante | Parcial | Feed e notificações; sem classificação por LLM. |
| Aprender rotinas | Parcial | `tracker/` com insights; sem sugestões de automação. |
| Rotina da manhã/noite | Parcial | `DAILY_BRIEF` (agenda e gastos); sem clima e notícias. |

## 4. Próximos passos sugeridos

1. Publicar o Worker e configurar `GROQ_API_KEY` e `NVIDIA_API_KEY` (passos da seção 1).
2. Testar no aparelho: resposta da IA, a palavra de ativação e a voz escolhida.
3. Escolher a próxima fase entre: pesquisa na web com fontes, modo offline com
   llama.cpp, ou TTS premium.

## 4. Revisão de qualidade (rodada de 2026-11-04)

Revisão por áreas (backend, IA, memória, Android, privacidade e documentação). Achados e estado:

| Área | Achado | Ação |
|---|---|---|
| Privacidade (IA) | Visão mandava a tela ao Gemini primeiro | Ordem alterada: NIM → Anthropic → Gemini |
| Memória | `DevFallbackEmbedder` (hash) estava em produção | Trocado por `ProxyEmbedder`; `recall` ignora vetores de dimensão diferente e não compara com vetor vazio |
| Atualizações | `MANIFEST_URL` apontava para `SEU_USUARIO/giselle-updates`, que não existe; 404 gerava novas tentativas | URL aponta para `updates/manifest.json` deste repositório; 404 = sem atualização |
| Build | `rootProject.name = "Jarvis"` | Renomeado para `Giselle` |
| Build | `isMinifyEnabled = false` na release | Mantido: R8 precisa de regras para MediaPipe (reflexão) e JNA/Vosk, e não é testável sem o aparelho |
| Repositório | `giselle-completo.zip` (250 KB) na raiz | Não removido: pode ser o único backup. Decidir com o dono |
| Mapeamento | Os 29 tipos de `ActionType` batem com `SYSTEM_PROMPT`, e todos têm tratamento (`IntentExecutor` ou `AgentOrchestrator`) | Sem lacunas |
| Testes | Worker: 44 testes (17 Worker, 14 providers, 13 tools) | `npm test` |

Ainda não feito: ação `WEB_SEARCH` no app, Cloudflare Vectorize para busca vetorial no servidor,
LiteRT/llama.cpp no aparelho, wake word offline "Oi JARVIS". Os nomes de modelos de embedding
(`@cf/baai/bge-m3`) e os endpoints de Brave/Tavily ainda não foram validados contra as APIs reais.

