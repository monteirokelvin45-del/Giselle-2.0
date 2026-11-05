# Checklist: chaves gratuitas de IA para o Worker

Este documento lista provedores com plano gratuito, os limites encontrados na pesquisa e os passos para você criar **a sua própria chave**. Nenhuma chave vai para o chat, para o repositório ou para o APK. Cada chave fica como segredo do Worker (`npx wrangler secret put NOME`).

> **Aviso sobre as fontes:** os limites abaixo vieram de blogs e agregadores (pesquisa de outubro de 2026), não de conferência direta nos painéis. Alguns números se contradizem entre fontes. Confira sempre no painel do provedor antes de confiar em um limite. Onde as fontes divergiram, isso está indicado.

## Resumo da ordem recomendada

| Ordem | Provedor | Segredo | Modelo sugerido | Usa seus dados para treinar no plano grátis? | Pode receber conversas com dados pessoais? |
|---|---|---|---|---|---|
| 1 | Groq (principal) | `GROQ_API_KEY` | `llama-3.3-70b-versatile` | Não verificado nesta pesquisa | A confirmar (ver passo 1) |
| 2 | OpenRouter (reserva) | `OPENROUTER_API_KEY` | `meta-llama/llama-3.3-70b-instruct:free` | Depende do modelo; alguns podem treinar | Só modelos que não treinam |
| 3 | NVIDIA NIM (reserva) | `NVIDIA_API_KEY` | `meta/llama-3.3-70b-instruct` | Não verificado | A confirmar |
| 4 | Mistral (reserva) | `MISTRAL_API_KEY` | `mistral-small-latest` | **Sim, por padrão no plano grátis (com opt-out)** | **Não**, enquanto o treino estiver ativo |
| 5 | SambaNova (reserva) | `SAMBANOVA_API_KEY` | `Meta-Llama-3.3-70B-Instruct` | Não verificado | Não, até confirmar a política |
| 6 | Z.ai (candidato) | `ZAI_API_KEY` | `glm-4.7-flash` | Não verificado | Não, até confirmar a política |
| — | Gemini (texto) | `GEMINI_API_KEY` | `gemini-2.5-flash` | **Sim**: o plano grátis usa os dados para melhorar produtos (fora de EEE/Suíça/Reino Unido) | **Não**. Só pedidos sem dados pessoais |

## Passo a passo: criar cada chave

Faça só o que for usar. A ordem de prioridade é Groq, OpenRouter e NVIDIA.

### 1. Groq
- Crie a conta em `console.groq.com` e gere a chave em `console.groq.com/keys`.
- Limites (fontes divergentes, confirme no painel *Limits*): a maioria das fontes indica 30 req/min e 14.400 req/dia. Uma fonte mais recente (outubro/2026) indica 30 req/min, 1.000 req/dia e 200.000 tokens/dia em modelos de texto. Os limites são **por organização**, então criar mais chaves não aumenta a cota.
- Antes de usar com dados pessoais: abra a página de dados/privacidade da conta e confirme se há treino com seus dados.
- Comando: `npx wrangler secret put GROQ_API_KEY`.

### 2. OpenRouter
- Crie a conta em `openrouter.ai` e gere a chave em `openrouter.ai/keys`. Não precisa de cartão.
- Limites de modelos `:free` (informação consistente nas fontes): 20 req/min; 50 req/dia com menos de US$ 10 em créditos comprados ao longo da vida da conta; 1.000 req/dia depois de comprar pelo menos US$ 10 uma vez. Saldo negativo gera erro 402 mesmo nos modelos grátis.
- **Privacidade:** a política depende de cada modelo. Antes de usar um modelo `:free` com dados pessoais, confira na página do modelo se ele pode treinar com as requisições.
- Comando: `npx wrangler secret put OPENROUTER_API_KEY` (opcional: `OPENROUTER_MODEL`).

### 3. NVIDIA NIM
- Crie a conta no `build.nvidia.com` (Developer Program, sem cartão) e gere uma chave `nvapi-` em qualquer modelo, em "Get API Key".
- Limites: as fontes falam em 1.000 créditos na inscrição e 40 req/min. Uma fonte do fórum oficial diz que o sistema de créditos foi substituído por limites por modelo, não publicados. Confira no painel da conta.
- Comando: `npx wrangler secret put NVIDIA_API_KEY`.

### 4. Mistral (reserva, ativa só com a chave)
- Chave em `console.mistral.ai`.
- **Atenção:** o plano grátis pode treinar com entradas e saídas, com desligamento manual no console. Antes de definir `MISTRAL_API_KEY`, desligue o treino no console ou aceite a condição conscientemente.
- Comando: `npx wrangler secret put MISTRAL_API_KEY`.

### 5. SambaNova (reserva, ativa só com a chave)
- Chave em `cloud.sambanova.ai`.
- Limites: as fontes divergem (30 req/min permanente; ou 20 req/min e 20 req/dia sem cartão; ou nenhum plano anunciado). **A política de dados não foi encontrada.** Não use com dados pessoais até confirmar.
- Comando: `npx wrangler secret put SAMBANOVA_API_KEY`.

### 6. Z.ai (candidato, ainda não integrado)
- Chave em `z.ai` (painel da API). Modelo `glm-4.7-flash`, listado como gratuito na documentação de preços (fontes de 2026).
- Limites: uma fonte indica 1 requisição concorrente; outra indica cerca de 1.000 req/dia. Confirme no painel de limites da conta.
- **Política de dados não verificada.** Não integrar sem essa verificação.

### Gemini (apenas texto sem dados pessoais)
- Não é prioridade. O plano grátis da API pode usar prompts e respostas para melhorar produtos do Google. Pela regra do projeto, só serve para pedidos sem dados pessoais e sem memória.
- Limites diferem muito entre fontes (de 20 a 1.500 req/dia dependendo do modelo e da data). Confirme no AI Studio.

## Regras de segurança (valem para todos)

- Nunca cole a chave no chat, em arquivos do repositório ou em issues. Use só `wrangler secret put`.
- Se uma chave for exposta, revogue-a no painel do provedor e crie outra.
- Não use chaves encontradas em terceiros ou em repositórios públicos.
- Um provedor só entra na cadeia quando a chave dele existe. Sem chave, ele é ignorado.
- Conversas com dados pessoais e memória só vão para provedores cuja política de plano grátis não treina com os dados.

## Depois de criar as chaves

1. Defina os segredos com `npx wrangler secret put NOME` dentro de `backend-proxy/`.
2. Rode `npx wrangler deploy` em `backend-proxy/`, ou use o workflow de deploy do GitHub quando ele estiver configurado.
3. A cadeia usa os provedores na ordem da tabela acima, pulando os que não têm chave.

## Pendências desta checklist

- Confirmar nos painéis oficiais os limites de Groq, NVIDIA e Z.ai (as fontes divergem).
- Confirmar a política de treino de Groq, NVIDIA, SambaNova e Z.ai antes de liberar dados pessoais.
- Confirmar a política de dados do SambaNova.
