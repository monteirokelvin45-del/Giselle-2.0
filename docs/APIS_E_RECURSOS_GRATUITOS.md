# APIs, chaves e recursos gratuitos para a Giselle (pesquisa de 2026-11-04)

> **Aviso importante.** Os planos gratuitos mudam com frequência. Quase todas as
> fontes abaixo são artigos de comparação de terceiros, não a página oficial de cada
> provedor. **Antes de depender de qualquer limite, confira o site oficial.**
> Nenhuma chave foi criada, testada ou guardada neste repositório.

## Regras de segurança que valem para tudo

- Chaves **nunca** vão para o APK nem para o repositório. Ficam como segredos do Worker
  (`wrangler secret put ...`). É o que já fazemos com Groq e NVIDIA.
- Provedores com "dados usados para melhorar o produto" no plano gratuito (ex.: Google
  Gemini no plano gratuito) não devem receber conversas ou telas com dados pessoais.
- Qualquer chamada que faça uma ação no celular continua passando pelo `SafetyGate`.
  Dados de API não podem contornar a confirmação.

## 1. IA (LLM)

| Provedor | Plano gratuito (segundo as fontes) | Cartão? | Observação |
|---|---|---|---|
| **Groq** | 30 req/min, 1.000 req/dia, 200 mil tokens/dia por modelo (gpt-oss-120b, gpt-oss-20b, Qwen). Também Whisper: 2.000 req/dia | Não | Já é nosso provedor principal. Limite de tokens por dia costuma acabar antes do de requisições. |
| **Google Gemini (AI Studio)** | Plano gratuito nos modelos Flash e Flash-Lite. Limites variam por projeto e modelo | Não | Google diz que conteúdo do plano gratuito pode ser usado para melhorar produtos. Não usar com dados pessoais. Já temos Gemini para visão no Worker. |
| **NVIDIA NIM** | Plano de teste gratuito, com limite de cerca de 40 req/min por chave | Verificação por telefone | Já integrado como reserva. |
| **OpenRouter** | Modelos com sufixo `:free`: 20 req/min e 50 req/dia. Sobe para 1.000 req/dia depois de uma compra única de US$ 10 em créditos | Não | Muitos modelos diferentes com uma só chave. Bom candidato a reserva. |
| **Cerebras** | **Mudou**: não tem mais plano gratuito permanente. Hoje é US$ 5 de crédito, com cartão, válido 30 dias | Sim | Antes era gratuito (1 milhão de tokens/dia). Não contar com ele como gratuito. |
| **Mistral** | Plano gratuito com créditos mensais; o valor exato deixou de ser publicado | Não | Conferir no painel. |
| **Cloudflare Workers AI** | 10.000 neurons/dia | Não | Já é nossa última reserva no Worker. |
| **Hugging Face** | Cerca de US$ 0,10/mês | Não | Serve para testes. |

**Recomendação:** manter Groq como principal, NVIDIA e OpenRouter como reservas, e
Workers AI como último recurso. Não contar com Cerebras como gratuito.

## 2. Voz (TTS e STT)

### Texto para voz (TTS) em nuvem

| Provedor | Plano gratuito | Observação |
|---|---|---|
| **ElevenLabs** | 10.000 caracteres/mês, sem cartão. **Sem uso comercial** no plano gratuito | Melhor qualidade citada. Serve para testes, não para publicar. |
| **Cartesia** | 20.000 créditos/mês, sem cartão. Sem uso comercial no gratuito | Latência baixa (cerca de 40 a 100 ms). Bom para voz em tempo real. |
| **Deepgram Aura-2** | Crédito único de US$ 200 | Uso comercial não informado nas fontes. |
| **Google Cloud TTS** | 1 milhão de caracteres/mês nas vozes padrão, com faturamento ativado | Uma das opções gratuitas mais duradouras. |
| **Amazon Polly** | 5 milhões de caracteres/mês, só no primeiro ano | Depois disso, paga. |
| **OpenAI TTS** | Sem plano gratuito permanente | Pago desde o primeiro caractere. |

### Voz no próprio aparelho (sem internet e sem custo por uso)

- **sherpa-onnx** é o motor de inferência (k2-fsa). Roda Piper, Kokoro, Kitten, Matcha
  e outros modelos no Android.
- **HayaiTTS** (`HayaiApp/HayaiTTS`) é um app de TTS em Kotlin que usa sherpa-onnx e
  tem mais de 600 vozes. **Atenção: é GPL-3.** Não copiar código dele sem decidir a
  licença do nosso projeto.
- **VoxSherpa TTS** também usa sherpa-onnx com Kokoro e Piper. Serve como exemplo.
- **Kokoro-82M** é Apache-2.0 e roda em CPU (segundo o `awesome-jarvis`).
- **Piper** tem uma versão `piper1-gpl`, que é GPL. Conferir a licença antes de usar.

Vale a pena para o item "voz premium e natural" sem depender de nuvem. Exige testar a
velocidade no aparelho.

### Reconhecimento de voz (STT)

| Provedor | Plano gratuito | Observação |
|---|---|---|
| **Groq (Whisper)** | Whisper large-v3 e turbo, cerca de 2.000 req/dia, sem cartão | Já temos a chave da Groq; é a opção mais simples. Não tem streaming em tempo real. |
| **AssemblyAI** | 185 horas gravadas e 333 horas em tempo real, sem cartão | Maior franquia gratuita. Não oferece mais Whisper. |
| **Deepgram** | Crédito único de US$ 200 | Streaming em tempo real. |
| **Azure Speech** | 5 horas/mês por 12 meses | Depois do período, paga. |
| **Vosk** (local) | Gratuito, offline | Já usamos para a palavra de ativação. |
| **Whisper local** (whisper.cpp) | Gratuito, offline | Pesado para o celular; testar no aparelho. |

## 3. Busca na web (com fontes)

| Provedor | Plano gratuito | Observação |
|---|---|---|
| **Brave Search API** | 2.000 consultas/mês, 1 consulta/s, sem cartão | Índice próprio. Bom para o requisito de "pesquisa com fontes". |
| **Tavily** | 1.000 créditos/mês, sem cartão | Já devolve texto resumido e fontes. Feito para agentes. |
| **Serper** | 2.500 consultas (uma vez, no cadastro, segundo um artigo; outro diz mensal) | Resultados do Google. Conferir se é único ou mensal. |
| **Exa** | Crédito de US$ 10 no cadastro | Busca semântica. |
| **SearXNG** | Gratuito, auto-hospedado | Metasearch. Exige servidor próprio. |
| **DuckDuckGo** (sem chave) | Sem API oficial estável | Usado por alguns projetos; não é recomendado para produção. |

**Recomendação:** Tavily ou Brave no Worker, com fontes mostradas na tela e não lidas em voz alta.

## 4. Clima, notícias e câmbio

| Serviço | Plano gratuito | Observação |
|---|---|---|
| **Open-Meteo** | Sem chave. 10.000 chamadas/dia e 300 mil/mês. **Só uso não comercial** | Ideal para protótipo. Para produção, há assinatura. |
| **MET Norway** | Sem cota fixa; regras de uso justo; exige atribuição | Dados abertos. Exige cache. |
| **WeatherAPI** | 1 milhão de chamadas/mês com chave; uso comercial permitido segundo uma fonte | Opção com chave e plano mais generoso. |
| **OpenWeatherMap** | 1.000 chamadas/dia (One Call) | Muito usado. |
| **Frankfurter** | Sem chave, sem limite declarado. Dados do Banco Central Europeu (cerca de 33 moedas, dias úteis) | Ótimo para câmbio. Não cobre todas as moedas. Para o real e o metical, conferir a cobertura. |
| **ExchangeRate-API** | 1.500 req/mês | Cobre mais de 160 moedas. |
| **NewsAPI / GNews** | NewsAPI: 100 req/dia, só desenvolvimento. GNews: não confirmado nas fontes | NewsAPI não serve para produção no plano gratuito. |

## 5. Futebol

| Serviço | Plano gratuito | Observação |
|---|---|---|
| **football-data.org** | 12 competições, 10 req/min, sem cartão. Placares **com atraso**, sem jogadores nem estatísticas | Competições incluídas: Champions League, Premier League, La Liga, Bundesliga, Serie A, Ligue 1, Eredivisie, Primeira Liga, Championship, Brasileirão Série A, Copa do Mundo, Eurocopa. |
| **API-Football (API-Sports)** | 100 req/dia, com ligas amplas | Bom para ter mais ligas. Conferir a licença. |
| **TheSportsDB** | Sem placar ao vivo no plano gratuito | Dados de times e ligas. |

## 6. Mapas, rotas e lugares

| Serviço | Plano gratuito | Observação |
|---|---|---|
| **Nominatim** (OpenStreetMap) | Gratuito, 1 req/s no servidor público. Sem autocomplete nem uso em massa | Pode ser auto-hospedado. Cache obrigatório. |
| **Mapbox** | 100 mil requisições de geocodificação/mês | Exige usar o SDK de mapas em alguns casos. |
| **OpenRouteService** | Cota gratuita de rotas e geocodificação, com chave | Não verifiquei o limite exato. |
| **HERE** | 250 mil requisições/mês (segundo um artigo) | Não verificado no site oficial. |
| **Google Maps Platform** | Crédito mensal, com cartão | Melhor cobertura de endereços; cobra por uso. |

Para "levar-me para casa", o caminho mais simples é abrir o app de navegação do próprio
celular com um `Intent` de navegação, sem API nenhuma.

## 7. Música e mídia

- **Spotify Web API**: em 2026 ficou mais restrita. Para criar um app é preciso ter Spotify
  Premium. Apps em modo de desenvolvimento aceitam só 5 usuários autorizados. Acesso
  ampliado só para organizações com pelo menos 250 mil usuários ativos mensais.
  **Não é viável para um app público agora.**
- **YouTube Music**: não encontrei API pública oficial para controlar reprodução de
  terceiros. A alternativa é um `Intent` de busca/reprodução ou o controle de mídia do
  Android (`MediaSession`).
- **Mídia do próprio aparelho**: `MediaSessionManager` e intents de reprodução funcionam
  sem API externa.

## 8. Mensagens

- **WhatsApp Business Cloud API** (oficial): gratuita para configurar. Meta dá 1.000
  conversas de atendimento iniciadas pelo cliente por mês. Outras conversas são pagas.
  É para **atendimento de empresas**, não para automatizar a conta pessoal de um usuário.
  Automatizar a conta pessoal viola os termos do WhatsApp.
- **Telegram Bot API**: não pesquisei especificamente nesta rodada; é um caminho oficial
  conhecido, mas confirme os termos antes de usar.
- **SMS e ligações**: já temos pelo próprio Android (`IntentExecutor`); não exigem API.

## 9. Casa inteligente

- **Home Assistant REST API**: gratuita, com token de longa duração, rodando na própria
  casa do usuário. Não há custo de API.
- **Wyoming** (protocolo do ecossistema Home Assistant) conecta palavra de ativação,
  voz e satélites.

## 10. Agentes e memória (ferramentas gratuitas)

- **Mem0**: Apache 2.0, plano gratuito "Hobby" na nuvem, versão auto-hospedada.
- **Zep / Graphiti**: Graphiti é Apache 2.0, mas precisa de banco de grafos.
- **Qdrant Edge**: banco vetorial para celular em beta privado (só por convite).
- **Cloudflare Vectorize**: 5 milhões de dimensões armazenadas e 30 milhões consultadas
  por mês, no plano gratuito do Workers.

## Próximos passos sugeridos (por valor e risco)

1. **Busca na web com fontes:** Tavily ou Brave no Worker. Ganho alto e risco baixo.
2. **Câmbio e clima:** Frankfurter e Open-Meteo, sem chave. Ganho alto e risco
   baixo, mas ambos têm limites de uso comercial a confirmar.
3. **Voz natural:** testar sherpa-onnx com Kokoro no aparelho antes de decidir. Verificar a licença.
4. **Futebol:** football-data.org para as 12 competições.
5. **Casa inteligente:** Home Assistant, apenas para quem tiver o servidor próprio.

**Não recomendado agora:** Spotify como integração pública, automação da conta pessoal do
WhatsApp e qualquer uso de Cerebras como se fosse gratuito.
