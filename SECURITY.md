# Política de segurança

## Reportar uma vulnerabilidade

Não abras issues públicas para vulnerabilidades. Usa o **Security Advisories** privado do GitHub
(separador *Security → Report a vulnerability*) ou contacta o mantenedor diretamente.

Inclui, se possível:

- versão do app (`versionName`) e modelo do dispositivo e versão de Android;
- passos para reproduzir e impacto esperado;
- se envolve o proxy, o endpoint e o tipo de pedido (sem incluir segredos reais).

Pedimos que aguardes a correção antes de divulgar publicamente, para protegermos os utilizadores.

## Âmbito

Em âmbito: o código em `app/`, `backend-proxy/` e os workflows em `.github/`.

Fora de âmbito: problemas que exijam acesso físico ao dispositivo já desbloqueado, ou que dependam de
credenciais vazadas por quem as configurou.

## Segredos

- Nunca commits `local.properties`, `*.jks`, `*.keystore` ou ficheiros `.dev.vars`.
- `ANTHROPIC_API_KEY`, `GEMINI_API_KEY` e `PROXY_SHARED_SECRET` vivem só no Cloudflare
  (`wrangler secret put`). Se suspeitares de fuga, roda com `python3 provision.py --rotate`.
- A keystore de release é a identidade do app: se for comprometida, gera uma nova e comunica
  aos utilizadores, porque as atualizações deixam de ser compatíveis.
