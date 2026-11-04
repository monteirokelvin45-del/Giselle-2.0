# Pesquisa: projetos Android de assistente / agente parecidos com a Giselle

Data da pesquisa: 2026-11-04. Fontes: buscas no GitHub (tópicos `android-automation`,
`phone-control`, `voice-assistant`, `mobile-agent`, `accessibility-service`) e
leitura dos READMEs. **Não auditei o código de cada projeto**; as afirmações
abaixo vêm das descrições públicas, e algumas podem estar desatualizadas.
Licenças e números de estrelas devem ser conferidos nos repositórios.

## 1. Projetos encontrados e o que têm de bom

| Projeto | Licença / linguagem | Destaques que valem a pena |
|---|---|---|
| **Operit** (AAswordman/Operit) | Kotlin, projeto grande | Substitui o assistente padrão do Android; permissões por ferramenta (permitir / perguntar / negar, padrão = perguntar); memória em grafo com documentos; browser agent; workflows acionados por voz, Tasker ou agendamento; TTS com ONNX local; wake word em segundo plano; Shizuku e root; MCP; loja unificada de skills e ferramentas. |
| **OAssist** (outsidepro-arts/OAssist) | MIT, Kotlin + Compose | 55 ferramentas em três níveis de privilégio (intents, acessibilidade, Shizuku/root); cliente MCP; memória de longo prazo; skills em Markdown; foco em acessibilidade total (TalkBack, WCAG 2.2). |
| **PokeClaw** (agents-io/PokeClaw) | Kotlin, LiteRT | Controle do celular com Gemma 4 **no próprio aparelho**, sem nuvem nem chave. Segredos do projeto cifrados com SOPS + age. |
| **Friday** (n1dlee/friday-mobile) | Kotlin + Compose, Groq | Wake word offline (Vosk); **verificação de voz do dono** (ONNX, no aparelho); Whisper; roteamento em duas camadas (comandos simples direto, o resto pelo agente, que **aprende frases** para o roteador); tela de **diagnóstico** com um toque para corrigir cada item; ferramentas que não podem funcionar são ocultadas do modelo; cancelamento real de chamadas (corrotinas e `channelFlow`); logs sem conteúdo do usuário; assinatura de release que falha fechada sem chave. |
| **Marionette** (zlotny/marionette) | Kotlin + Compose | Palavra de ativação **personalizada** (qualquer frase, qualquer idioma), detecção no aparelho; frases de parada que cancelam a ação; funciona com a tela desligada; fala o progresso enquanto trabalha; ferramentas de navegação e lugares, mensagens e ligações (com confirmação), notificações (ler, responder, dispensar), música em qualquer app, calendário, contatos; chaves cifradas com a Android Keystore; modelos e preços vindos do OpenRouter. |
| **OpenDroid** (JMAN730/opendroid) | Kotlin, Hilt | 13 provedores de LLM com cadeia de fallback; memória em 4 níveis; planos de execução; ElevenLabs para voz premium; Room com 7 DAOs; chaves no Keystore. |
| **AndroLLM** (ShadowSafin/AndroLLM) | Kotlin | LiteRT-LM local com **aceleração GPU (OpenCL) e fallback para CPU**; cache KV para conversa longa; memória com embeddings em SQLite; voz offline completa (wake word → ASR → LLM → TTS) com **interrupção por voz (barge-in)** por VAD; Piper para TTS. |
| **Sanna** (sannabotdev/sannabotapp) | React Native + Kotlin | Sub-agentes para agendamentos e notificações; aprendizado de fluxos específicos de cada app; personalidade editável (SOUL); skills em Markdown; sem backend. |
| **Hemanth "My Assistant"** | Kotlin | Ação com confirmação para ligações e mensagens; chamadas de busca na web mostradas com fontes clicáveis (sem ler as fontes em voz alta); modo de conversa contínua que para com "obrigado"; painel compacto por cima de outros apps; comandos locais offline para o que é simples. |
| **ReisJuliano/assistent** | Kotlin | Rosto animado com física de molas a 60 fps e 10 emoções; ativação por voz offline com várias frases; **webhooks** para cada ação (Home Assistant, n8n); API local com painel web; vários aparelhos com QR code; relógio de 7 segmentos; "vida própria" quando ocioso. |
| **Mobilerun / DroidRun** (droidrun) | MIT, Python (com app Portal em Kotlin) | Framework de referência: árvore de acessibilidade combinada com captura de tela; modo de visão; modo de raciocínio em dois níveis; traces (Phoenix, Langfuse); saída estruturada; cartões de app que ensinam o agente. |
| **Dina / Oxxi / Sirius / DeVA / OpenClaw assistant** | Kotlin | Variações menores: voz local em espanhol, Vosk, Porcupine, integração com OpenClaw e Gemini. |

## 2. Comparação com a Giselle

Legenda: **Sim** = existe no código atual; **Parcial** = existe com limite; **Não** = não existe.

| Recurso | Giselle | Referências |
|---|---|---|
| Palavra de ativação offline | Parcial (Vosk, palavra fixa "Giselle") | Sim, com palavra personalizada (Marionette, Friday) |
| Verificação de voz do dono | Não | Sim (Friday) |
| Interrupção por voz (barge-in) | Não | Sim (AndroLLM, Hemanth) |
| Parada por frase de voz | Não | Sim (Marionette) |
| Botão de parada de emergência | Não | Sim, em projetos de supervisão |
| Registro como assistente padrão | Não | Sim (Operit, OAssist) |
| Permissão por ferramenta (permitir / perguntar / negar) | Parcial (fila de confirmação global) | Sim (Operit) |
| Diagnóstico com correção de um toque | Não | Sim (Friday) |
| Ferramentas ocultadas quando indisponíveis | Não | Sim (Friday) |
| LLM local no aparelho | Não (esqueleto MediaPipe) | Sim (PokeClaw, AndroLLM, Operit, OpenDroid) |
| Aceleração GPU local | Não | Sim (AndroLLM) |
| Cadeia de provedores com reserva | Sim (Worker: Groq → Anthropic → NIM → Workers AI) | Sim (OpenDroid, 13 provedores) |
| Ações no celular via acessibilidade | Sim | Sim (todos) |
| Confirmação antes de ações sensíveis | Sim (SafetyGate, auditoria) | Sim (Marionette, Hemanth, Friday) |
| Ligação, SMS, alarme, timer, volume | Sim | Sim |
| Ler e responder notificações | Sim | Sim (Marionette) |
| Música em qualquer app | Não | Sim (Marionette, Operit) |
| Navegação e lugares | Não | Sim (Marionette) |
| Calendário | Parcial (resumo diário) | Sim (Marionette) |
| Busca na web com fontes | Não | Sim (Hemanth, Operit, ReisJuliano) |
| Memória de longo prazo | Parcial (busca por embedding falso) | Sim, com grafo ou camadas (Operit, OpenDroid, AndroLLM) |
| Memória por embeddings reais | Não (`DevFallbackEmbedder`) | Sim (AndroLLM, OpenDroid) |
| Documentos e RAG | Não | Sim (Operit, AndroLLM) |
| Skills ensinadas | Sim (com confirmação) | Sim (Operit, OAssist, Sanna) |
| Loja de skills / ferramentas | Não | Sim (Operit) |
| Cliente MCP | Não | Sim (Operit, OAssist) |
| Webhooks para casa inteligente | Não | Sim (ReisJuliano) |
| API local com painel web | Não | Sim (ReisJuliano, Operit) |
| Rosto ou avatar animado | Sim (raposa em Canvas) | Sim (ReisJuliano, Operit) |
| Logs sem conteúdo do usuário | Parcial (auditoria sem redação revisada) | Sim (Friday) |
| Chaves guardadas no Keystore | Não (chaves no Worker; nada no APK) | Sim (Marionette, OpenDroid) |
| Assinatura de release que falha fechada | Não | Sim (Friday) |
| Cancelamento de requisições | Parcial (timeouts) | Sim (Friday) |
| Voz premium (ElevenLabs etc.) | Não | Sim (OpenDroid, Operit) |
| TTS local (Piper, Kokoro, ONNX) | Não | Sim (AndroLLM, Operit, ReisJuliano) |

## 3. Lacunas mais importantes, em ordem sugerida

1. **Parada imediata e barge-in** (AndroLLM, Marionette, Hemanth): é o que faz o assistente parecer vivo e seguro. Hoje, a fala e a escuta não se interrompem.
2. **Permissão por ferramenta** (Operit): troca a fila de confirmação global por uma política por tipo de ação (permitir, perguntar, negar).
3. **Embeddings reais na memória** (AndroLLM, OpenDroid): corrige o problema que já identificamos (`DevFallbackEmbedder`).
4. **Diagnóstico com correção de um toque e ferramentas ocultadas quando indisponíveis** (Friday): reduz a frustração do usuário e evita o modelo tentar ações impossíveis.
5. **Verificação de voz do dono** (Friday): cumpre o requisito "só obedecer ao dono".
6. **Palavra de ativação personalizada** (Marionette): hoje o nome é fixo.
7. **Busca na web com fontes** (Hemanth, Operit): mostrar as fontes na tela sem lê-las em voz alta.
8. **LLM local com LiteRT-LM** (PokeClaw, AndroLLM): fase separada, por causa do tamanho do modelo.
9. **Assinatura de release e logs sem conteúdo do usuário** (Friday): segurança de publicação.
10. **Modo de conversa contínua que para com uma frase** (Hemanth): hoje a conversa precisa de toque.

## 4. O que a Giselle já faz melhor ou diferente

- Bloqueio de ações sensíveis com fila de confirmação, auditoria e limite de taxa (o `SafetyGate` é mais explícito que a maioria dos projetos acima).
- Chaves só no Worker, com HMAC, janela de tempo e rotação de segredo. Os projetos que guardam chave no aparelho ficam com um risco maior.
- Avatar em Canvas, sem Lottie, com máquina de estados única e limite de 30 fps em repouso.

## 5. Ressalvas

- Muitos desses projetos têm poucas semanas de vida, e alguns são experimentais ("vibe coded", como o OAssist se descreve).
- Vários exigem root, Shizuku ou ADB, o que a Giselle não deve exigir do usuário comum.
- Licenças variam: MIT, Apache, "source-available", ou sem licença clara. Antes de copiar qualquer trecho, confira a licença de cada um.
- Não testamos nenhum desses apps no aparelho. Esta comparação não valida desempenho nem qualidade real.
