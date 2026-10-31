#!/usr/bin/env bash
# ============================================================
# setup.sh — roda no SEU computador, não no ambiente do Claude.
#
# Resolve o único bloqueador que não pode ser resolvido em código:
# o gradle-wrapper.jar (binário compilado, precisa de Gradle real
# ou Android Studio instalado). Depois disso, valida o resto do
# setup que já está pronto no projeto.
#
# Uso:
#   chmod +x setup.sh && ./setup.sh
# ============================================================

set -e

echo "== 1/4: Gradle Wrapper =="
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "  OK — já existe."
else
    if command -v gradle >/dev/null 2>&1; then
        echo "  Gradle encontrado, gerando wrapper..."
        gradle wrapper --gradle-version 8.7
        echo "  OK — gradle-wrapper.jar criado."
    else
        echo "  Gradle não está instalado no PATH."
        echo "  → Abra este projeto no Android Studio: ele detecta o"
        echo "    wrapper incompleto e regenera sozinho na primeira sincronização."
        echo "  → Ou instale Gradle (https://gradle.org/install) e rode este script de novo."
    fi
fi

echo ""
echo "== 2/4: Proxy de IA =="
if ! grep -q "^PROXY_BASE_URL=https://[^[:space:]]\\+" local.properties 2>/dev/null; then
    echo "  ATENÇÃO — configure PROXY_BASE_URL após publicar o Worker."
    echo "  O app não envia chaves de provedores nem chama APIs diretamente."
else
    echo "  URL do proxy configurada."
fi

echo ""
echo "== 3/4: Segredo do proxy =="
if grep -q "^PROXY_SHARED_SECRET=.\+" local.properties 2>/dev/null; then
    echo "  Presente."
else
    echo "  ATENÇÃO — configure PROXY_SHARED_SECRET."
fi

echo ""
echo "== 4/4: Permissões do wrapper =="
chmod +x gradlew 2>/dev/null && echo "  gradlew marcado como executável."

echo ""
echo "== Resumo =="
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "  Pronto para: ./gradlew build"
else
    echo "  Ainda falta resolver o wrapper (ver passo 1 acima) antes de buildar."
fi
