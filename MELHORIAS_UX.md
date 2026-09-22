# Melhorias de acesso e interação infantil

Implementação e validação: 20/09/2026.

## Acesso e família

- Cadastro inicial reduzido a nome/apelido e faixa etária; aparência e companheiro opcionais.
- Botão de criação fixo e personalização da aventura recolhida inicialmente.
- Perfis infantis com estantes separadas e inclusão pela área dos responsáveis.
- Cada livro preserva o perfil original do protagonista, mesmo após trocar ou editar o perfil ativo.
- Exclusão recuperável por lixeira; exclusão definitiva exige confirmação na área dos responsáveis.

## Leitura e participação

- Narração das opções, botão para ouvi-las novamente e destaque da escolha sendo narrada.
- Símbolos de ação nos botões e rolagem até as escolhas durante sua narração.
- Histórias offline mais curtas para 3–5 anos e consequências relacionadas às decisões.
- Alternância entre texto e imagens; palavras novas com explicação e leitura em voz alta.
- Modo “Hora de dormir” escuro, sem animação da cena ou confete. A velocidade da voz continua configurável separadamente.
- Ao voltar para escolher outro caminho, o caminho anterior é preservado como outro livro, com cópias independentes das imagens.
- Pergunta final para conversar sobre a parte favorita da história.

## Dados e indicadores

- Migração do banco da versão 3 para a 4 preserva histórias e sessões existentes.
- Páginas antigas sem registro de abertura não são presumidas como lidas.
- Tempo registrado considera o leitor em primeiro plano; indicadores são atividade de uso, não medida de aprendizagem.
- Cópias de caminhos não duplicam a cota de criação nem a contagem das mesmas páginas abertas.

## Validação realizada

- `testDebugUnitTest`: 72 testes aprovados.
- `connectedDebugAndroidTest`: 2 testes aprovados em emulador Android API 36.1, cobrindo migração, perfil original, troca de perfil, caminhos, imagens, lixeira e restauração.
- `assembleDebug`: compilação final aprovada.
- Conferência da interface no emulador: cadastro rápido, entrada na estante e tela de criação com botão fixo.
- `git diff --check`: sem erros de espaços em branco.

APK de desenvolvimento: `app/build/outputs/apk/debug/app-debug.apk`.

## Limites e próximos passos

As chamadas reais de IA e voz externa não foram validadas nesta etapa. A conferência da interface não substitui testes com crianças e responsáveis, TalkBack ou diferentes tamanhos de tela. Os símbolos de escolha são emojis, não ilustrações geradas por ação. A avaliação com famílias deve observar compreensão das opções, autonomia para começar a leitura e necessidade de ajuda durante a navegação.
