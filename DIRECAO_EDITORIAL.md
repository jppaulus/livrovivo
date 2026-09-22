# Revisão editorial e de desempenho — 20/09/2026

## Diagnóstico confirmado no código

A abertura offline de 3–5 anos repetia a mesma luzinha em todos os temas. Três temas de crianças maiores também compartilhavam uma estrutura genérica. A geração online pedia uma apresentação convencional e duplicava o texto inteiro no campo de narração. Cada continuação começava a ser gerada somente após o toque. A voz podia esperar até 150 segundos por chamada remota, e a voz do aparelho priorizava variantes que usam rede. Os cenários locais eram formas geométricas desenhadas em Canvas.

## Implementado nesta revisão

- **Coleção pronta:** opção explícita na criação para abrir texto local sem chamada de geração; separada de “Criar com IA”. A disponibilidade da voz do aparelho ainda depende do Android.
- **Oito livros para 3–5 anos:** conflitos próprios de cada tema, três aberturas por livro e consequências específicas para cada decisão. As aberturas consecutivas do mesmo tema alternam. Os 64 percursos de escolhas (oito por livro) terminam em quatro páginas. Não são 64 histórias inteiramente diferentes: as ramificações voltam ao arco principal do livro.
- **Continuidade dos livros antigos:** textos já salvos não são substituídos. O motor antigo continua concluindo livros antigos, inclusive a trama da luzinha.
- **Direção literária online:** abertura em ação, desejo e obstáculo concretos, humor de personagem, desfecho que retoma a abertura, sem elogiar toda escolha nem enumerar virtudes. Seis direções de abertura variam por edição. São instruções para a IA, não garantia de qualidade de toda resposta.
- **Menos texto redundante:** a resposta estruturada deixa de exigir uma cópia integral para narração. Faixas por página passam a 45–75, 90–130 e 130–180 palavras; são objetivos de geração, não cortes automáticos no conteúdo.
- **Continuações preparadas:** até duas possibilidades imediatas são escritas durante a leitura. Cache limitado, compartilhamento de chamadas em andamento e nenhum registro no banco antes da escolha. Cancelamento ao sair do leitor. A opção pode ser desligada nas configurações; aumenta o consumo de texto porque uma possibilidade pode não ser usada.
- **Narração:** primeira parte menor, até duas partes seguintes em preparação paralela e ordenada, prazo inicial total de oito segundos para tentativas remotas antes da alternativa local. Esse prazo não é uma promessa de início em oito segundos: a voz local precisa inicializar e sintetizar. Partes remotas seguintes têm prazo de vinte segundos. A voz local instalada tem prioridade sobre sua variante de rede. A retomada de uma página interrompida volta a preparar os trechos que faltam.
- **Limite de espera de texto:** trinta segundos para o conjunto de tentativas de geração, sem acumular o prazo de cada modelo. Isso limita uma falha lenta; não demonstra redução da latência de uma resposta bem-sucedida.
- **Arte:** oito cenários ilustrados incorporados ao app, aplicados aos temas, às capas e ao leitor. JPEG qualidade 92, mesmas dimensões, total de 4.348.322 bytes contra 22.667.598 bytes nos PNGs originais. Decodificação em segundo plano e cache limitado a 16 MiB, com imagens menores nos cartões. Cartões têm maior área para a ilustração, sem emoji sobreposto.
- **Ilustração por IA:** direção de composição, anatomia, luz, profundidade e ação específica de cada página; referências anteriores continuam sendo usadas quando disponíveis.
- **Diagnóstico:** builds de desenvolvimento registram duração por chamada e por trecho de voz em `LivroVivoPerf`, sem nomes, texto da criança ou credenciais.

## Validação

- 77 testes unitários aprovados, incluindo cache concorrente, cancelamento, falhas recuperáveis, oito temas, três aberturas e 64 percursos.
- 3 testes Android API 36.1 aprovados: migração e preservação familiar; caminhos, imagens e lixeira; preparação sem mutação e reutilização no toque.
- O teste de preparação usa HTTP simulado: duas chamadas geram os caminhos e o toque não gera uma terceira.
- APK debug compilado em `app/build/outputs/apk/debug/app-debug.apk`.
- Sem chaves Gemini, ElevenLabs ou backend na configuração local de desenvolvimento. Não foram feitos benchmarks de provedores reais; os prazos acima são limites de implementação, não resultados medidos de velocidade.

## O que ainda separa esta versão de um lançamento profissional

A arte incorporada é um cenário por tema, não uma sequência completa ilustrada para cada página e caminho. A coleção de 6–8 e 9+ ainda usa os textos anteriores; a nova coleção editorial desta revisão cobre 3–5. Temas livres precisam de IA para corresponder fielmente ao pedido. A qualidade e a latência da voz neural precisam ser medidas com a configuração real. Streaming de áudio do provedor não foi implementado nesta revisão.

Antes de publicação, precisamos medir início da leitura, primeiro áudio e resposta às escolhas em aparelhos físicos e redes variadas, revisar histórias e imagens geradas de ponta a ponta e observar primeiras sessões com crianças e responsáveis. O Billing continua sendo o protótipo existente. Testes técnicos aprovados não demonstram retenção ou qualidade editorial validada com leitores.

Referências consultadas: [documentação oficial de geração de voz Gemini](https://ai.google.dev/gemini-api/docs/speech-generation) e [leitura interativa de livros ilustrados — Reading Rockets](https://www.readingrockets.org/topics/comprehension/articles/repeated-interactive-read-alouds-preschool-and-kindergarten). A segunda orienta a avaliação futura de leitura e discussão; nenhuma afirmação de ganho de aprendizagem foi usada como resultado desta implementação.

## Arte gerada: método, arquivos e prompts

Ferramenta incorporada `image_gen`, via habilidade `imagegen`; não foi usado CLI/API externo. Arquivos finais: `app/src/main/res/drawable-nodpi/book_{night,school,space,party,home,ocean,dinosaurs,forest}.jpg`. Os PNGs originais permanecem na pasta de imagens geradas do Codex.

Prompt de noite: “Create a polished children's picture-book illustration, landscape 4:3. Asset: built-in bedtime setting for a reading app. A cozy bedroom at blue hour, a small wooden bed with rumpled ochre quilt, an oversized indigo shadow of a stuffed rabbit cast gently onto the wall by a warm bedside lamp, open window framing a crescent moon, tiny slippers, rich tactile gouache on paper, sophisticated editorial illustration, convincing perspective, layered lighting, restrained midnight blue/ochre palette, expressive atmosphere, professional published picture book finish. No people, no words or typography, no logos, no emoji, no primitive geometric clipart. This is a setting, not a screenshot. Save the generated image for use in the app.”

Prompt comum dos demais: “Use case: illustration-story. Create one polished professional children's picture-book setting illustration for an Android reading app, landscape 4:3. [SCENE] Sophisticated hand-painted gouache on textured paper, convincing perspective and anatomy, layered foreground middle ground and distance, carefully composed narrative detail, editorial publishing quality, gentle mood, restrained rich colors. No human characters, no text, no logos, no emojis, no generic geometric clipart, no frames, no collages. Full bleed image.”

Substituições de [SCENE]:

- **school:** A welcoming empty classroom before the first day: a tiny yellow backpack on a wooden chair, an unfinished red block bridge on a round table, picture-book shelf and morning sunlight. Coral, teal, butter yellow.
- **space:** A tiny whimsical silver rocket parked on a lavender moon, Saturn-like rings and a small floating red mitten, enormous deep indigo cosmos, luminous amber portholes. Scientific adventure and comic wonder.
- **party:** A toy-room at floor level, an elaborate wooden toy train with one missing wheel, plush bear inspecting the wheel, paper bunting, blocks and miniature tea cups. Warm coral and turquoise.
- **home:** A warm family living room, rocking chair beside a tiny bassinet with soft blanket, a big and tiny pair of socks together on an ottoman, a half-built pillow fort nearby. Tender afternoon light and ochre, sage, terracotta.
- **ocean:** An underwater coral garden, a small orange octopus carefully arranging shells as instruments, a shy blue whale calf in the distance, shafts of sunlight, flowing sea grass, air bubbles. Jewel teal with coral orange.
- **dinosaurs:** A friendly young long-neck dinosaur carefully peering at a huge spotted egg in a fern nest; tiny footprints curve around a pond, layered prehistoric plants, morning mist. Sage green, warm clay, golden light.
- **forest:** An old woodland tree with a tiny wooden door, an inquisitive squirrel carrying an acorn beside a winding brook, a wooden bridge of mismatched planks, dappled light through tall canopy. Deep emerald, amber and russet.

## Conferência no emulador — 22/09/2026

Percorrido o cadastro de Lia (perfil fictício de 3–5 anos), seleção “Coleção pronta” e as quatro páginas de “O Coelho que Cresceu na Parede”. Confirmadas as consequências de mover a luminária e fazer uma borboleta, além do encerramento na quarta página. A tela de temas foi inspecionada visualmente; captura em `artifacts/qa/editorial-themes.png`.

O emulador sem rede não tinha voz portuguesa utilizável offline. O app exibiu a falha sem impedir as escolhas. A revisão passou a explicar a instalação da voz Android, evita tentar uma voz de rede sem conexão e revela o texto automaticamente quando a narração da página falha. A narração audível continua pendente de verificação com voz instalada ou provedor configurado.
