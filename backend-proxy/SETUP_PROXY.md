# Proxy de IA — publicação e configuração

## Atenção: o segredo do cliente vai parar no APK

O `PROXY_SHARED_SECRET` é usado para assinar pedidos HMAC, mas o valor é
compilado no aplicativo Android. Qualquer pessoa que receba o APK pode
extraí-lo; HMAC e anti-replay não tornam um segredo embutido no cliente secreto.
Como o repositório `Giselle-2.0` é público, não publiques um APK com esse valor
até existir autenticação adequada por utilizador/dispositivo e limites de uso
no servidor.

O workflow atual compila um APK de depuração sem credenciais do proxy. A
interface e os recursos locais podem ser testados, mas as chamadas de IA ficam
indisponíveis até a autenticação do proxy ser resolvida. Não coloques valores
de secrets em código, no `local.properties` versionado ou nesta documentação.

## Publicar o Worker

Requisitos: uma conta Cloudflare, Node.js e Wrangler autenticado nessa conta.

```sh
cd backend-proxy
npm install -g wrangler
wrangler login
wrangler deploy
```

O `wrangler.toml` configura Workers AI e o Durable Object de proteção. O
endereço `workers.dev` aparece no fim do deploy. Antes de aceitar pedidos, o
Worker também precisa do secret do lado do servidor:

```sh
wrangler secret put PROXY_SHARED_SECRET
```

Não uses uma chave de provedor de IA no aplicativo; se escolheres Anthropic ou
Gemini, configura a chave apenas como secret no Worker. Workers AI é a opção
configurada por omissão, sujeita aos limites e preços atuais da Cloudflare.

## Próximo passo para uma versão pública

Antes de gerar um APK público com IA, substitui o segredo compartilhado embutido
por autenticação de utilizador/dispositivo que o Worker possa validar sem
confiar numa chave estática incluída no APK. Depois disso, guarda credenciais
somente em secrets do GitHub/Cloudflare e volta a executar o workflow.
