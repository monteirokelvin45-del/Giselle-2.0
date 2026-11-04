# Como contribuir

Obrigado por querer ajudar. A Giselle lida com voz, ecrã e ações no telemóvel, por isso
mantemos critérios de segurança altos.

## Fluxo

1. Cria um branch a partir de `main`.
2. Faz alterações pequenas e focadas, com uma mensagem de commit clara em português ou inglês.
3. Garante que os testes passam:
   - `cd backend-proxy && npm test`
   - `./gradlew testDebugUnitTest` (no Android Studio ou com JDK 17 + SDK)
4. Abre um Pull Request descrevendo o problema, a solução e como testaste.

## Regras

- **Não contornes o SafetyGate.** Qualquer ação que altere dinheiro, mensagens, permissões ou
  configuração de segurança precisa de regra fixa em código e de testes.
- **Não coloques chaves de API no código, no APK ou em `BuildConfig`.** Chaves de provedores
  pertencem ao Worker.
- **Novos comportamentos precisam de teste.** Preferimos fakes a chamadas de rede reais.
- Mantém a compatibilidade com o pacote `com.example.jarvis` e com a keystore de release.
- Código, comentários e documentação em português (ou inglês, se for uma API pública).

## Estilo

- Kotlin: convenções oficiais (`kotlin.code.style=official`).
- JavaScript do Worker: ES modules, sem dependências de runtime.
- `.editorconfig` define indentação e finais de linha.
