# Backlog de ideias

Ideias registradas para avaliação. Nenhuma está em desenvolvimento. Todas devem respeitar as regras do projeto: nada envia mensagem sozinho, nada contorna o `SafetyGate`, e nenhuma chave passa pelo código ou pelo chat.

## Experiência e voz
1. Fala em trechos: começar a falar a primeira frase enquanto o modelo ainda gera o resto.
2. Ajuste de velocidade e tom da voz nas Definições, com prévia de áudio.
3. Modo silencioso automático à noite, com horário configurável, que só mostra texto.
4. Confirmação por voz para ações sensíveis ("Confirmo?"), além do toque.
5. Atalho na tela de bloqueio para ditar sem abrir o app (sem desbloquear o aparelho).

## Mascote e tela inicial
6. Reação a notificações importantes.
7. Humor do mascote pela hora do dia (bom dia, boa noite), sem rede.
8. Texto "Um momento…" enquanto o provedor responde.
9. Modo economia de bateria que desliga animações com pouca bateria.

## Provedores e custo
10. Painel de uso mostrando as requisições gratuitas restantes em cada provedor, lendo os cabeçalhos de limite.
11. Cache de respostas para perguntas idênticas, para poupar cotas gratuitas.
12. Teste de saúde dos provedores no Worker, sem gastar requisições reais.

## Negócio e rotina
13. Resumo diário do módulo de negócio: pendências, aprovações e prazos, sempre para revisão antes de qualquer envio.
14. Lembretes de prazo como notificação, sem enviar nada automaticamente.
15. Exportação de pendências em CSV ou PDF para revisão fora do app.

## Privacidade e segurança
16. Painel de privacidade: quais dados foram enviados a cada provedor e quando.
17. Apagar memória por período (última hora, último dia, tudo), com confirmação.
18. Bloqueio por biometria para abrir o cofre de memórias e dados de conta.

## Qualidade e manutenção
19. Teste de fumaça no CI: sobe o Worker localmente com um provedor falso e chama a rota de chat, para pegar erros de rota antes do deploy.
20. Registro de erros local e exportável, sem dados pessoais, para diagnóstico no aparelho.

## Solicitações não planejadas

- **Desbloqueio do celular por comando de voz ("GISELLE desbloqueia o celular").** Não será implementado. Motivo: qualquer pessoa que fale a frase perto do aparelho teria acesso ao celular; a voz pode ser gravada ou imitada; e o Android não oferece a um app uma forma segura de desbloquear a tela.
  - Alternativa a avaliar: ao receber o comando, a Giselle abre a confirmação biométrica nativa do Android (impressão digital ou rosto). Só o dono do aparelho pode concluir o desbloqueio. Ainda precisa de decisão de segurança antes de qualquer implementação.

- **Desbloqueio por voz e rosto reconhecidos pelo app ("só obedecer ao meu comando").** Não será implementado. Motivo: reconhecimento de voz e de rosto pode ser enganado com gravação ou foto, e o Android não permite que um app desbloqueie a tela.
  - Alternativas a avaliar:
    - Comandos por voz só depois que o celular já estiver desbloqueado.
    - Verificação de voz dentro do app, como segunda confirmação para ações sensíveis (abrir o cofre de memórias, por exemplo). Não protege o aparelho inteiro e precisa de avaliação de segurança antes de qualquer implementação.
