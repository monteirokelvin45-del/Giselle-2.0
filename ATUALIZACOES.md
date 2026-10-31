# Giselle atualizações — configuração

## 1. Repositório de updates (GitHub)
Cria um repo público `giselle-updates` com `manifest.json` e `config/` e troca `SEU_USUARIO` em
`app/src/main/java/com/example/jarvis/updates/GiselleUpdater.kt` (constante `MANIFEST_URL`).
Os APKs vão em **Releases**. Hosts aceites (cada salto, https): `raw.githubusercontent.com`, `github.com`,
`objects.githubusercontent.com`, `release-assets.githubusercontent.com`.

## 2. Keystore de release (item 9 — OBRIGATÓRIO)
O Android só instala a atualização se for assinada pela MESMA chave. Se perderes a keystore, nunca mais
atualizas: só desinstalando (e perdes os dados).
```
keytool -genkeypair -v -keystore giselle.jks -alias giselle -keyalg RSA -keysize 4096 -validity 10000
```
- **Faz backup** de `giselle.jks` e das palavras-passe em 2 sítios (nunca no Git; `*.jks` já está no `.gitignore`).
- Local: põe em `local.properties`: `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- CI: `base64 -w0 giselle.jks` → Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Publica sempre o APK **release** (`giselle-apk-release`). O APK debug tem outra assinatura e será recusado.
- `versionCode` sobe sozinho no CI (`github.run_number`).

## 3. Como a config remota funciona
- `systemPrompt` na config é **anexado** ao prompt base (limite 4000 caracteres); não o substitui.
- Chaves proibidas: `safetyGate`, `permissions`, `accessibility`.
- Config nova fica "pendente" até 1 ciclo bem-sucedido do agente; 3 arranques falhados (< 60 s) → rollback
  automático e a versão ruim não é reoferecida (publica uma versão MAIOR para corrigir).
- Menu ⋮ → Atualizações para verificar/instalar manualmente.
