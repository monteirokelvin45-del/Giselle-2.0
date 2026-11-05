# Roteiro de teste — HUAWEI nova Y62 (EVE-LX9)

Aparelho de referência: EMUI 12.0.0, tela 1600×720 px, largura útil 360 dp, 4 GB de RAM, 128 GB de armazenamento.
Não registrar aqui IMEI, número de série, MAC, IP ou outros identificadores do aparelho.

## Antes de começar

- [ ] Liberar pelo menos 1 GB de armazenamento (hoje restam ~2,7 GB).
- [ ] Instalar o APK de teste (release "apk-…" ou o artefato `giselle-apk-release` do CI).
- [ ] **Atenção:** o APK publicado pelo workflow "Publicar APK" é de **depuração** e dorme em 3 s. Para testar o tempo final (40 s), use o APK **release** do workflow "Build Giselle APK".
- [ ] Ativar "Depuração USB" só se for usar o computador (Opções do desenvolvedor).

## 1. Permissões de segundo plano (EMUI)

- [ ] Abrir **Definições → Permitir execução em segundo plano** no app. Deve abrir a lista de inicialização ou de apps protegidos do EMUI. Se cair nos detalhes do app, ajustar manualmente em *Bateria* e *Inicialização de apps*.
- [ ] Ativar "Gerenciamento manual" para a Giselle: iniciar automaticamente, executar em segundo plano e manter ativa.
- [ ] Anotar: os nomes das telas da Huawei não foram verificados em todos os modelos. Se a tela abrir mas for outra, registrar o nome exato.

## 2. Tela inicial (360 dp de largura)

- [ ] Saudação e orbe não se sobrepõem em modo retrato.
- [ ] Orbe menor (190 dp) nesta largura; o elle (150 dp) aparece abaixo da saudação.
- [ ] Com conversa ativa: orbe e elle (84 dp) lado a lado, sem cortar o texto.
- [ ] Caixa de entrada: placeholder "Digite ou segure para falar" inteiro, botões + / microfone / onda visíveis.
- [ ] Menu lateral ocupa cerca de 97% da largura; a faixa à esquerda fecha o menu ao toque.

## 3. Elle (mascote)

- [ ] Pisca a cada ~2,5 s, por ~180 ms.
- [ ] Sem interação, dorme após o tempo definido (3 s em debug; 40 s em release).
- [ ] Toque no elle: pula e acorda.
- [ ] Digitar uma letra na caixa: acorda.
- [ ] Enquanto a Giselle fala: a boca alterna com a pose parada e para junto com a voz.
- [ ] Enquanto ouve, pensa ou executa: pisca mais rápido e não dorme.

## 4. Segundo plano (teste de bateria)

- [ ] Com o motor de negócio ligado (Definições → Negócio), deixar o aparelho bloqueado por 1 h, com bateria acima de 30%.
- [ ] Verificar se o ciclo rodou (aba Artefatos ou Notificações). Se não rodou, a permissão do item 1 não foi suficiente.
- [ ] Registrar nível de bateria antes e depois.

## 5. Voz

- [ ] Ditar pelo microfone com a tela ligada.
- [ ] Conversa contínua (botão de onda) com a tela bloqueada: verificar se continua ou se o EMUI encerra o serviço.

## 6. Desempenho

- [ ] A animação do orbe e do elle não trava (sem quedas visíveis).
- [ ] Abrir e fechar o menu lateral várias vezes sem atraso perceptível.

## Registro de resultado

| Item | Resultado | Observação |
|---|---|---|
| 1. Segundo plano | | |
| 2. Tela inicial | | |
| 3. Elle | | |
| 4. Bateria 1 h | | |
| 5. Voz | | |
| 6. Desempenho | | |
