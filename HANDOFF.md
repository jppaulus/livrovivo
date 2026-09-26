# Livro Vivo — contexto para retomar em um novo chat

> Documento de passagem de bastão. Atualizado em 23/09/2026.
> **Novo chat? Leia primeiro o `NOVO_CHAT.md`** (resumo do estado atual e texto pronto para colar).
> Para começar rápido: leia as seções **1**, **5** e **6**.

---

## 1. Resumo do projeto

App Android nativo (Kotlin + Jetpack Compose) de **histórias infantis interativas com IA**: a criança é a
protagonista, escolhe os rumos da aventura, e cada página é **escrita, ilustrada e narrada** na hora.

- **Pasta local:** a pasta principal do projeto no PC do desenvolvedor
- **Repositório:** https://github.com/jppaulus/livrovivo (branch `main`)
- **Público:** pais de crianças de 3 a 9 anos. Modelo freemium (3 histórias grátis).
- **Estado:** compila, 175 testes unitários e 9 instrumentados passando, rodou no emulador. As integrações de IA
  **ainda não foram testadas com chaves reais**.

---

## 2. Ambiente de desenvolvimento

| Item | Valor |
|---|---|
| JDK | 17 (`C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot`) |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` (platforms 34/35/36) |
| Gradle / AGP / Kotlin | wrapper 8.13 / AGP 8.7.3 / Kotlin 2.0.21 |
| minSdk / targetSdk | 26 / 35 |
| Emulador | AVD `Medium_Phone_API_36.1` (do usuário) e `LivroVivo_Teste` (só para testes, porta 5582) |

```bash
./gradlew testDebugUnitTest   # 175 testes
./gradlew assembleDebug       # APK em app/build/outputs/apk/debug/
./gradlew installDebug        # instala no aparelho/emulador conectado

# Testes no emulador de testes LivroVivo_Teste (criado em 23/09, com aprovação), nunca no do usuário:
emulator -avd LivroVivo_Teste -no-window -no-audio -no-snapshot -no-boot-anim -port 5582
adb -s emulator-5582 shell settings put global sys_storage_threshold_percentage 1
ANDROID_SERIAL=emulator-5582 ./gradlew connectedDebugAndroidTest   # instala e depois desinstala o app
# Ele roda junto com o emulador do usuário (Medium_Phone_API_36.1). Cópias "-read-only" do AVD do usuário
# não abrem enquanto o dele estiver aberto. Se o servidor do adb reiniciar no meio de um teste, o emulador
# pode travar (aparece em "adb devices", mas não responde): "adb -s emulator-5582 emu kill" e suba de novo.
```

⚠️ **O usuário costuma usar o app no emulador em paralelo.** Se a tela mudar sozinha (narrador trocado,
página avançada), provavelmente foi ele, não bug. Avise antes de reinstalar o APK, porque o app reinicia.

---

## 3. Mapa dos arquivos

| Pasta / arquivo | Responsabilidade |
|---|---|
| `core/ai/GeminiService.kt` | Cliente `generateContent` (texto JSON, imagem, voz), validação da chave, headers de app Android |
| `core/ai/ModelFallback.kt` | Tenta modelos em sequência: 400 → modo "lite", 5xx → 1 retry, 403/404/429 → próximo modelo, chave inválida → para |
| `core/ai/AiException.kt` | Tipos de erro (chave inválida, conta bloqueada, API desativada, chave restrita, sem permissão, faturamento, cota…) e mensagens em PT-BR |
| `core/ai/StoryPrompts.kt` | Prompts de sistema/capítulo, arco da história, regras de segurança infantil, schema JSON |
| `core/ai/StoryWriter.kt` | Orquestra IA vs offline; `ChapterSanitizer.kt` valida a resposta do modelo |
| `core/ai/OfflineStoryEngine.kt` | Histórias escritas à mão (8 temas + tema livre) para quando não há IA |
| `core/ai/ElevenLabsService.kt` | TTS premium + listagem de vozes da conta |
| `core/audio/AudioPlayerController.kt` | Narração: escolhe motor, **gera em partes**, toca com ExoPlayer, destaque de leitura, música de ninar, cache |
| `core/audio/NarrationText.kt` | `NarrationChunker` (divide a página) e `NarrationTimeline` (frases para o destaque) |
| `core/audio/NarrationEngines.kt` | Motores: ElevenLabs, Gemini TTS, voz do aparelho (com escolha de voz por narrador) |
| `core/audio/VoicePersona.kt` | 4 narradores + direção de atuação. **Não reordenar** (ver seção 4) |
| `core/audio/LullabySynth.kt` | Caixinha de música sintetizada ("Brilha, Brilha, Estrelinha") |
| `core/illustration/IllustrationService.kt` | Ilustração por página, consistência via imagem anterior, 5 estilos |
| `core/ui/SceneArt.kt` | 8 cenas desenhadas em Canvas (fallback offline e capa) |
| `core/database/` | Room v6; migrações 2→3, 3→4, 4→5 e 5→6 **preservam histórias antigas** (testes em `androidTest/`) |
| `core/literacy/TrailParser.kt` | Lê e valida `assets/alfabetizacao/trilha.json` (Trilha da Leitura) |
| `core/literacy/LiteracyRules.kt` | Regras puras da trilha: estrelas, desbloqueio em ordem, `LiteracyKnowledge`, quem vê a trilha (9+ escondida), peças que formam a resposta |
| `core/literacy/DecodableValidator.kt` | Juiz dos livros "Eu leio": palavras que a criança ainda não lê e problemas de formato (4 páginas, 1–2 frases de 3–7 palavras, maiúsculas, só `. , ! ?`) |
| `core/literacy/OfflineDecodableEngine.kt` | Livro "Eu leio" sem IA: roteiros Coleção, Cadê? e Adivinha, com a criança como personagem |
| `core/literacy/LiteracyBookWriter.kt` | Escreve os livros "Eu leio": IA com lista fechada de palavras, 1 nova tentativa e o motor offline como garantia |
| `data/repository/EuLeioRepositoryImpl.kt` | Cria os livros ganhos, guarda "Li sozinho!" e marca o livro como lido |
| `presentation/literacy/EasyReaderScreen.kt` | Leitor "Eu leio": tocar lê a palavra, segurar separa em sílabas, "Ouvir a página", "Li sozinho!" |
| `core/literacy/VoiceClips.kt` + `SoundPlayer.kt` | Voz da trilha: toca a fala gravada antes (`assets/voz`, índice `voz/indice.json`) ou, se ela faltar, a voz do app com dica de pronúncia ("bá, de bala") |
| `core/literacy/TrailPhrases.kt` | Frases fixas das telas da trilha ("Muito bem!", resultados, "Chame um adulto"); a mesma lista está no script de gravação |
| `ferramentas/gerar_audios.py` | Grava as 370 falas fixas da trilha com a voz do Capitão (Azure, seção 4 item 12) e cria `ferramentas/revisao_audios.html` para ouvir |
| `ferramentas/amostras_vozes.py` | Grava o mesmo trecho em todas as vozes pt-BR da Azure (`ferramentas/amostras_vozes.html`, fora do git) para escolher de ouvido |
| `core/literacy/ReadingInsights.kt` | Seção "Leitura" do painel dos pais: fases, livros, "Li sozinho", letras/sílabas trabalhadas, fases com mais erros e ideia fora da tela |
| `core/literacy/FeedbackSounds.kt` | Sons de acerto e "tente de novo" sintetizados na hora (sem arquivos) |
| `presentation/literacy/` | Trilha: `TrailScreen` (mapa), `PhaseListScreen`, `ActivityScreen` + `activity/` (um Composable por tipo), `PhaseResultScreen`; `ActivitySession` é a lógica pura de uma fase |
| `data/repository/LiteracyRepositoryImpl.kt` | Conteúdo da trilha (em cache) + progresso por fase na tabela `literacy_progress` |
| `core/settings/SettingsManager.kt` | DataStore: chaves, motor de voz, modelos, estilo, contador de histórias, `applyPendingDefaults()` |
| `presentation/reader/` | Leitor: páginas, escolhas, voltar e trocar de caminho, comemoração, barra de narração |
| `presentation/{home,creation,onboarding,parent,settings,paywall}/` | Demais telas |
| `supabase/functions/ai-gateway/index.ts` | Proxy de IA para produção (chaves ficam no servidor) |

**Fluxo de uma página:** escolha da criança → `ContinueStoryUseCase` → `StoryWriter` (IA ou offline) →
capítulo salvo no Room → leitor mostra o texto → em paralelo: narração (em partes) e ilustração.

---

## 4. Decisões já tomadas (não refazer)

1. **`gemini-1.5-flash` foi desativado pelo Google.** Era a causa raiz de "histórias repetidas": o app caía
   sempre no motor offline. Hoje usa modelos atuais com troca automática.
2. **Modelos padrão** (em `AiModelDefaults`): texto `gemini-2.5-flash`; imagem `gemini-3.1-flash-image`;
   voz `gemini-3.1-flash-tts-preview`; ElevenLabs `eleven_v3`. Cada um tem lista de reserva, e o que
   funcionar vira o novo padrão salvo.
3. **Plano gratuito do Gemini:** texto e TTS são gratuitos; **geração de imagens não é** (precisa
   faturamento ativo). Por isso erro de imagem é reportado como "exige faturamento".
4. **Narrador padrão: Capitão Aventura** (`DEFAULT_PERSONA_ID = "aventureiro"`), escolha do usuário.
   Aplicado uma vez até para quem já tinha outro narrador, via `applyPendingDefaults()` +
   `DEFAULTS_VERSION` (chamado em `AppStartViewModel`).
   ⚠️ **Não reordenar o enum `VoicePersona`:** o `ordinal` decide qual voz do aparelho cada narrador usa, e
   o usuário aprovou justamente a voz atual do Capitão.
5. **Narração em partes:** a primeira parte (~180 caracteres) toca em poucos segundos; o resto é gerado em
   segundo plano e entra na fila do ExoPlayer. Cortes só em parágrafo/frase. **Desde 24/09, com a chave do Gemini a
   página inteira vai num pedido só em streaming** (`streamGenerateContent`, `GeminiService.streamSpeech` +
   `StreamingPcmPlayer`): o som começa em ~1,3 s (medido no emulador) e toca enquanto chega; a página inteira fica
   guardada em `files/narration` e "Ouvir de novo" toca o arquivo. Medido no PC: gerar 180 letras de uma vez leva 9 a
   12 s (quase o tempo da fala), e o app antigo desistia em 8 s e caía na voz robótica. Sem o primeiro pedaço em 6 s,
   volta ao caminho antigo. O servidor (`ai-gateway`) ainda não repassa em partes: com ele, usa o caminho antigo.
9. **Área dos Pais sem chaves (24/09):** a tela virou "Narração e ilustrações" (narrador, velocidade, ilustrações,
   privacidade). Chaves, motor de voz, ElevenLabs, modelos e testes ficam numa seção "Desenvolvedor" fechada, que só
   existe na versão de teste (`BuildConfig.DEBUG`); a chave de desenvolvimento vem do `local.properties`. Saiu da tela
   inicial o cartão "Para os pais: ative a magia completa". ⚠️ O APK de teste leva a chave do `local.properties` dentro:
   não compartilhar.
10. **Sons da trilha sem "parar tudo":** cada tela cancela só o próprio pedido de som (`SoundPlayer` não tem mais
   `stop()` público). Antes, a lista de fases, ao sair, parava o som que a fase tinha acabado de começar, e a
   instrução era cortada no "Toque..." (vídeo do usuário em celular de verdade, 24/09).
6. **Limite gratuito conta histórias criadas** (`storiesCreated` no DataStore), então apagar histórias não
   devolve as grátis.
7. **Segredos fora do repositório:** `.gitignore` exclui `local.properties`, `*.env`, keystores, APKs e
   pastas de build. Já foi feita varredura por chaves antes do commit.
8. **Escolhas ligadas a virtudes** (coragem, empatia, criatividade, curiosidade, calma, cooperação) — elas
   alimentam as conquistas da criança e o painel dos pais.
11. **Vozes (23/09/2026):** a voz do aparelho soa robótica ("tipo Google Maps") e assusta na hora de dormir, e os pais
   não vão conseguir criar chaves de API. O usuário ouviu e **recusou vozes neurais que rodam no celular** (Supertonic 3,
   Kokoro, Piper). As vozes que ele quer são as do Gemini que ouviu com a chave dele: **Capitão = Puck, Ursinho =
   Algieba**. Caminho escolhido:
   - Tudo o que é fixo é **gravado antes** e vai dentro do app (`assets/voz`): instruções da trilha, letras, sílabas,
     palavras dos livros "Eu leio" e frases das telas. Toca na hora, sem internet e sem chave.
   - Histórias (texto que muda) vão ser narradas por um **servidor do app** (`supabase/functions/ai-gateway`), que guarda
     a chave; os pais não configuram nada. A tela de chaves sai da Área dos Pais.
   - Em produção a voz vem do **Google Cloud**, não do AI Studio: a API do Gemini do AI Studio (a chave atual) proíbe
     apps usados por menores de 18 e serve só para testes. Nos termos do Google Cloud não há essa proibição; modelos em
     prévia são só para teste, **exceto os listados na Agent Platform (antigo Vertex AI)**, e o Gemini 3.1 Flash TTS está
     na lista. Por isso as falas fixas foram gravadas pela Agent Platform com `gemini-3.1-flash-tts-preview` (US$ 0,03 por
     minuto de áudio; o estável `gemini-2.5-flash-tts` custa metade, mas nos testes de 24/09 repetiu "Muito bem" e
     embolou "bola"). Essas vozes **não aceitam chave de API** no Google Cloud: precisa de login (token OAuth).
   - Também testados e recusados pelo usuário (23–24/09): ElevenLabs (a política de uso proíbe disponibilizar o serviço
     para menores de 13, então não pode narrar ao vivo no app) e os modelos abertos grandes Qwen3-TTS e Chatterbox,
     rodando na RTX 3060 do PC ("ainda robóticos, com sotaque de Portugal").
   - Sem internet: página já narrada toca do cache; página nova não usa voz robótica (proposta: aviso para os pais lerem).
   - **Google Cloud:** projeto "Livro Vivo" (o mesmo da chave do AI Studio; o ID fica em `googlecloud.projeto` no
     `local.properties`, fora do git), com as APIs Cloud Text-to-Speech e Agent Platform ativadas em 24/09. O usuário
     mantém o faturamento **desligado** por precaução: liga só quando precisa e desliga depois. Há um alerta de
     orçamento (alerta não bloqueia gasto). Login no PC pelo Google Cloud CLI (`gcloud auth application-default login`).
12. **Vozes, correção (25/09/2026): Google fora da versão da loja.** O item 11 estava errado sobre o Google Cloud. Os
   termos específicos do Google Cloud, seção "Generative AI Services", item "d. Age Restrictions" (existe desde pelo
   menos 07/2024), proíbem usar qualquer serviço de IA generativa em site ou app "directed towards or is likely to be
   accessed by individuals under the age of 18", e o Google pode suspender na hora. Vale para Vertex AI, Firebase AI
   Logic e qualquer recurso de IA generativa (inclusive as vozes do Gemini no Cloud Text-to-Speech). Então **Gemini
   (texto, imagens, vozes Puck/Algieba) só em testes**; na loja, outros fornecedores.
   - **Voz: Microsoft Azure AI Speech.** Os termos da Microsoft não proíbem app infantil (pedem aviso claro aos pais de
     que a voz é sintética); só o plano **pago S0** dá direito de usar o áudio no app; prévias de IA generativa (ex.:
     vozes MAI-Voice) não são para produção. Conta de avaliação gratuita (crédito de US$ 200, limite de gasto: não
     cobra o cartão sem "upgrade"; **nunca** clicar em "Atualizar para pagamento conforme o uso"). Recurso
     `livro-vivo-voz` (East US, S0). Chave em `azure.speechKey` / `azure.speechRegion` no `local.properties`.
   - **Narradores escolhidos pelo usuário (ouviu `ferramentas/amostras_vozes.py`):** Capitão Aventura =
     `pt-BR-Macerio:DragonHDLatestNeural`; Ursinho Gentil = `pt-BR-ValerioNeural` (ele preferia o
     `pt-BR-Pedro:MAI-Voice-2-Flash`, que é prévia); Vovó Contadora = `pt-BR-ThalitaMultilingualNeural`; Fada Encantada
     = `pt-BR-LeticiaNeural`.
   - **Trilha regravada em 25/09** com a voz do Capitão: frases e instruções com o Macerio HD; letras, sílabas e
     palavras soltas com `pt-BR-MacerioMultilingualNeural` (mesmo locutor, voz comum). As vozes HD **inventam palavras
     em falas curtas** (teste de 25/09: "bu" virou uma frase inteira, "I" virou "Ai, deixa eu te ver...") e erram o
     nome da letra O; o gravador regrava falas longas demais para o texto. Conferência pelo Whisper (`%TEMP%\lvtts2`).
   - **No app (25/09):** `AzureSpeechService` + `AzureNarrationEngine` (primeiro motor no modo automático, narra a
     página inteira em streaming: 1º som em 0,5 a 0,8 s no PC, 0,35 a 1 s no emulador). Só a versão de teste tem a
     chave (`DEV_AZURE_SPEECH_KEY`); a da loja precisa do servidor. A amostra de cada narrador na Área dos Pais é uma
     saudação gravada (`assets/voz/narradores/<id>.ogg`, `ferramentas/gravar_narradores.py`).
   - **Correções de 25/09 (vídeo do usuário):** (1) o `StreamingPcmPlayer` descartava o áudio que chegava com a
     narração pausada ou antes do play (o AudioTrack devolve 0 com o buffer cheio): a história abria e tocava só
     "Glub" em vez de "Glub, glub!", e pulava trechos depois de pausar; agora a escrita espera. (2) "Ouvir de novo"
     durante a voz em partes abria o ExoPlayer por cima do tocador ao vivo (dois áudios, pausa sem efeito, destaque
     piscando); agora `stopStream()` desliga o tocador ao vivo antes. (3) O destaque do texto segue as **pausas da
     voz** (`SentenceAligner`): erro médio de 0,09 s contra 0,9 s da estimativa por letras (conferido com o Whisper);
     os inícios das frases ficam em `files/narration/<hash>.marks` ao lado do áudio guardado. O peso das frases no
     `NarrationTimeline` contava cada ponto das reticências como uma pausa ("Blub..." pesava como uma frase inteira).
   - **Texto das histórias:** proposta ainda não decidida: Claude (Anthropic permite produtos para menores com
     proteções e aviso de IA). **Ilustrações por IA:** sem fornecedor liberado ainda; na loja, ficam desligadas.

---

## 5. Estado do Git

- **GitHub (público desde 24/09):** `main`, `feature/trilha-da-leitura` e `claude/projeto-conforme-md-8263f2`.
- **Histórico reescrito em 25/09:** para tirar o e-mail pessoal e caminhos do PC, todos os commits ganharam
  códigos novos (o conteúdo é o mesmo). Códigos citados em documentos ou chats de antes de 25/09 não existem mais;
  os principais: `5876283` → `91f0697`, `17460eb` → `a226e2f`, `2958223` → `cef52f7`, `41e3558` → `b368abc`,
  `59ae891` → `0de69f1`. Os commits usam o e-mail privado do GitHub (`user.email` na configuração deste repositório).
- **`main`:** `a226e2f`, com o trabalho até 22/09 (narração em partes, erros 403, Capitão padrão, revisão editorial,
  perfis da família, Room v4).
- **`feature/trilha-da-leitura`:** a partir do `a226e2f`, trabalho da Trilha da Leitura (`ALFABETIZACAO.md`) e as
  vozes de 24/09. Fica no worktree `.claude/worktrees/leitura-docs-iniciais-62ddbc`.
- ⚠️ **Linha paralela não integrada:** a branch `claude/projeto-conforme-md-8263f2` (10 commits até 19/09) saiu do
  mesmo `91f0697` e tem Google Play Billing real, álbum de figurinhas, ritual da hora de dormir, sons da página e
  memória do companheiro, que **não estão no `main`**. As duas linhas mexem em 35 arquivos em comum (inclusive
  perfis de criança, feitos de jeitos diferentes) e o banco dela está na versão 3. Integrar exige decisão do
  usuário antes da etapa 8 da trilha (assinatura).

## 5.1. Trilha da Leitura — andamento

| Etapa | Estado |
|---|---|
| 0. Commit, testes, branch | ✅ 22/09 |
| 1. Conteúdo e dados | ✅ 22/09: `trilha.json` em assets, `TrailParser` (mesmas regras do script Python), modelos em `domain/model/Literacy.kt`, `LiteracyRepository`, tabela `literacy_progress` e coluna `stories.kind` (Room 4→5) |
| 2. Trilha jogável | ✅ 22/09: mapa, fases, os 5 tipos de atividade, resultado com estrelas e cartão "Aprender a ler" na Home. Jogado no emulador: vogais_a, silabas_b, palavras_01, ditado_01 |
| 3. Progresso | ✅ 22/09: estrelas salvas, módulos e fases liberam em ordem, `LiteracyKnowledge`. Conferido no emulador: progresso continua depois de fechar o app à força; Consoantes só abre depois das 5 vogais |
| 4. Validador + livro offline | ✅ 22/09: `DecodableValidator`, `OfflineDecodableEngine` e `LiteracyRules.booksEarned`. Os testes geram mais de 10 mil livros pela trilha inteira (vários nomes, os 4 companheiros, 25 sementes) e todos passam no validador |
| 5. Leitor "Eu leio" | ✅ 23/09: livro criado ao ganhar, aviso no resultado, leitor completo, livros na lista de fases e selo "Eu li!" na estante. Lido do início ao fim no emulador de testes (O DOCE DE LIA) |
| 6. Livro com IA | ✅ 23/09: `decodablePrompt`, `LiteracyBookWriter` com nova tentativa e queda para o offline, livro escrito em segundo plano e ilustrações da IA no leitor. Testado com respostas simuladas do Gemini; **não testado com chave real** |
| 7. Voz das sílabas | ✅ 23/09 (código): `SoundPlayer` + `Pronunciation` + `gerar_audios.py`. **Os 83 áudios ainda não foram gravados** (falta chave) e a pronúncia não foi ouvida (o emulador de testes não tem som) |
| 8. Pais e assinatura | ✅ 23/09: seção "Leitura" no painel, fases pagas bloqueadas, livros com IA só para assinantes, argumento de venda no paywall e a trilha para 9+ nas configurações dos pais |

**A Trilha da Leitura (etapas 0 a 8) está completa na branch `feature/trilha-da-leitura`.** O que falta está no fim desta seção.

Detalhes da etapa 1:
- O conteúdo nunca fica no Kotlin: `ferramentas/gerar_conteudo.py` gera `trilha.json` e `lista_imagens.txt`;
  copie o JSON para `app/src/main/assets/alfabetizacao/`. `TrailParserTest` falha se o arquivo mudar de forma inválida.
- `recordAttempt` guarda a **melhor** nota (0–3), soma tentativas e erros e grava `completedAt` só na primeira vez
  com ≥ 1 estrela. O cálculo das estrelas a partir dos acertos e o desbloqueio são da etapa 3.
- `Story.kind`: `aventura` (padrão) ou `eu_leio`.

Detalhes da etapa 2 (o que ainda falta está nas etapas seguintes):
- Nome usado: **"Aprender a ler"**; trilha escondida para 9+ (`LiteracyRules.isTrailVisible`). Decisões da seção 10
  do `ALFABETIZACAO.md` ainda em aberto; trocar é só mudar o texto e o parâmetro.
- Um "acerto" é a pergunta certa **na primeira tentativa**; depois de errar a criança tenta de novo até acertar.
  Com 2 erros na mesma pergunta, a resposta (ou a próxima peça) pisca como dica.
- As atividades usam fonte **sem serifa** (`LetterFont`): o tema do app usa serifa nas histórias.
- A narração lê a instrução em minúsculas ("forme ba") para a voz não soletrar a sílaba; a voz certa é da etapa 7.
- Fases de assinantes mostram o selo, mas jogam (bloqueio na etapa 8).
- ⚠️ Sem as 55 figuras, a pergunta "figura e palavra" mostra a palavra escrita (como a especificação pede), então
  vira um jogo de achar a palavra igual até as figuras existirem.

Detalhes da etapa 3:
- O resultado é salvo **antes** de a tela sair: a `ActivityScreen` só navega quando `savedStars` chega (a atividade
  sai da pilha e o ViewModel dela é destruído; salvar depois perderia o resultado).
- `ActivityViewModel` recusa abrir fase trancada, mesmo que alguém chegue nela pela rota.
- `LiteracyRules` usa os ids dos módulos (`vogais`, `consoantes`, `silabas`, `palavras`) para saber o que o "ensina"
  significa. Se o `gerar_conteudo.py` mudar esses ids, mude as constantes também (os testes avisam).
- Fase já concluída sempre continua liberada para jogar de novo; jogar pior não tira estrelas.
- `LiteracyUiState.knowledge` já expõe o que a criança sabe, para a etapa 4 (livros "Eu leio").

Detalhes da etapa 4:
- **Campos novos no `trilha.json`** (gerados pelo script, listas `LUGARES` e `NAO_OBJETOS` em `gerar_conteudo.py`):
  `objeto` e `lugar` em cada palavra do vocabulário. Sem eles o motor escrevia "LIA TEM UM DEDO", "LIA TEM UMA
  FACA" ou "O DADO ESTÁ NA BOCA". O resto do JSON saiu idêntico ao anterior. Para mudar, edite as listas e rode o script.
- O validador também aceita palavras aprendidas numa fase de palavras, junta acentos escritos separados (NFC) e
  separa o nome em partes só com letras ("Ana-Luísa" → ANA, LUÍSA; "Lia2" → LIA). O livro usa a primeira parte.
- Companheiro: entra no livro só quando a criança já lê o nome (LU-NA, PI-PO-CA); Bento e Aurora nunca entram.
- Livros ganhos: o 1º com 3 fases de Sílabas; depois, 1 a cada 2 fases de sílabas, palavras ou ditado (16 na trilha toda).
  A regra existe (`booksEarned`), mas ainda não cria o livro nem mostra o aviso: isso é a etapa 5, junto com o leitor.
- O motor usa `seed` para variar (use o número do livro) e `focusSyllables` para pôr as sílabas mais novas no livro.

Detalhes da etapa 5:
- **Criação:** depois de salvar uma fase, o `ActivityViewModel` chama `EuLeioRepository.ensureEarnedBooks`, que escreve
  os livros ganhos e ainda não criados (a trilha também chama ao abrir, para quem já tinha progresso). Cada livro usa o
  que a criança sabia na fase que o liberou (`knowledgeUpTo`) e treina as sílabas daquela fase (`phaseSyllables`).
  A contagem inclui a lixeira: jogar um livro fora não gera outro. Apagar **definitivamente** faz o mesmo livro voltar.
- Livro salvo como `Story` com `kind = "eu_leio"`, 4 páginas sem escolhas, `themeId` = módulo em que foi ganho
  (a lista de fases mostra os livros do módulo) e a palavra principal de cada página em `newWords` (a figura dela).
- **Cota e painel:** `getStoryCount()` só conta aventuras, e o `buildInsights` ignora os livros "Eu leio" (seção
  própria na etapa 8). O tempo de leitura (`reading_sessions`) inclui os dois.
- **Banco v6:** tabela `literacy_page_reads` (páginas marcadas "Li sozinho!", com quantas vezes).
- **Leitor (`EasyReaderScreen`):** sem narração automática e sem música ambiente. Tocar lê a palavra (destaque dourado);
  segurar mostra "BO · LA" e lê as sílabas e a palavra; "Ouvir a página" usa o destaque de frase do leitor normal.
  A voz recebe o texto em minúsculas (lê melhor); as sílabas soltas ainda saem pela voz do aparelho (etapa 7 corrige).
  O botão diz "Li sozinha!" para meninas. No fim: confete, "Quer ler de novo?" e o selo "Eu li!" na estante.

Detalhes da etapa 6:
- **Prompt:** `StoryPrompts.DECODABLE_SYSTEM_PROMPT` + `decodablePrompt(...)` com a **lista fechada** (palavras de apoio,
  palavras com figura que a criança lê com o artigo, o nome e o companheiro se ele for legível). As regras de segurança
  viraram `StoryPrompts.SAFETY_RULES`, usadas pelos dois prompts (o das aventuras continua com o mesmo texto).
- **Conferência:** a resposta passa para maiúsculas e tem os espaços juntados (não é motivo de recusa); depois o
  `DecodableValidator` confere título e páginas. Recusou: **uma** nova tentativa dizendo as palavras proibidas e os
  problemas de formato. Recusou de novo, erro de rede/cota ou sem IA: livro offline na hora. Logs na tag `LivroVivoIA`.
- **Sem espera:** o livro é escrito em segundo plano (`EuLeioRepository.requestEarnedBooks`). O resultado da fase mostra
  as estrelas na hora, "Escrevendo um livro só para você..." e depois o aviso (rota com `booksBefore`).
- **Ilustrações:** livros da IA guardam `sceneImagePrompt` por página e `characterSheet`; o leitor pede as ilustrações em
  ordem pelo `IllustrationService`. Se falhar (imagem exige faturamento), para em silêncio e mostra a figura da palavra.
- **Falta (etapa 8):** livros com IA e ilustração devem ser só de assinantes; hoje valem para quem tiver a IA ligada.
- **Para testar com chave real (etapa 6):** configure a chave (Área dos Pais ou `gemini.apiKey` no `local.properties`), conclua a
  3ª fase de sílabas e veja o livro; `adb logcat -s LivroVivoIA` mostra recusas e erros. Lembrete: os termos da API do
  Gemini vetam apps para menores de 18; em produção os livros precisam de outro provedor (mesma questão das aventuras).

- Roteiro de teste no emulador: `scratchpad/play.py <phase_id>` joga uma fase lendo as respostas do JSON (não
  está no repositório).

## 6. Pendências (em ordem de prioridade)

### 6.1. Erro 403 do Gemini — **não resolvido**
O usuário criou uma chave no AI Studio e recebeu:
`"a chave de api parece invalida ou sem permissão. (http 403) the caller does not have permission"`

Já feito: 403 deixou de ser tratado como "chave inválida"; o app tenta outros modelos gratuitos, valida a
chave separadamente e mostra o erro por modelo.

**Falta descobrir em qual botão o erro apareceu:**
- Em "Gerar ilustração de teste" → esperado: imagens exigem faturamento ativo.
- Em "Testar conexão" ou ao criar história → provável verificação de conta Google (idade, telefone,
  verificação em duas etapas), conta de escola/empresa, ou chave recém-criada.

**Como diagnosticar:** instalar a versão nova → Área dos Pais → IA, vozes e ilustrações → **Testar conexão**
→ ler a mensagem (agora detalha modelo + causa). Com o aparelho conectado, `adb logcat -s LivroVivoIA`
mostra modelo, código HTTP e mensagem do Google (a chave nunca é registrada).

### 6.2. Tempo até começar a narrar — **precisa medir**
Era cerca de 30s. Com a narração em partes deve cair para poucos segundos, mas **não foi medido no aparelho**.
Se ainda passar de ~8s: gerar as duas primeiras partes em paralelo e encurtar a primeira
(`NarrationChunker.chunk(firstMaxChars = ...)`).

### 6.3. Nunca testado com chaves reais
Gemini (texto, imagem, TTS) e ElevenLabs (TTS, listagem de vozes). Os formatos seguem a documentação atual,
e cada integração tem botão "Testar" nas configurações.

### 6.4. Outras pendências
- **Google Play Billing real:** a assinatura hoje é simulada e salva no DataStore (`BillingRepositoryImpl`).
- **Segurança do backend:** `ai-gateway` não tem autenticação por usuário nem limite de uso; adicionar
  Supabase Auth + cota por usuário antes de publicar.
- **Chaves em texto puro** no DataStore; migrar para Android Keystore.
- **Um perfil de criança só**; falta suporte a irmãos e sincronização na nuvem.
- **Sem testes instrumentados de UI** (só unitários).
- Política Famílias do Google Play: revisar antes de publicar (portão parental já existe).

---

## 7. Texto pronto para começar o novo chat

> Estou retomando o app Livro Vivo (Android/Kotlin/Compose), em
> na pasta principal do projeto, repo https://github.com/jppaulus/livrovivo.
> Leia o `HANDOFF.md` na raiz do projeto: ele tem a arquitetura, as decisões já tomadas e as pendências.
> Há mudanças ainda não commitadas (narração em partes, diagnóstico do erro 403 e narrador padrão).
> Minha prioridade agora é: [ex.: descobrir o erro 403 da minha chave / medir o tempo da narração /
> commitar o que está pendente].

---

## 8. Onde ficam as chaves e configurações

- **No app (recomendado para testar):** cadeado na tela inicial → resolver a conta → **IA, vozes e ilustrações**.
- **`local.properties`** (só no build de debug, fora do git):
  ```properties
  gemini.apiKey=AIza...
  elevenlabs.apiKey=sk_...
  supabase.url=https://SEU-PROJETO.supabase.co
  supabase.anonKey=eyJ...
  ```
- **Servidor (produção):** `supabase/.env` a partir do `.env.example` → `supabase secrets set --env-file supabase/.env`
  → `supabase functions deploy ai-gateway`.

Links úteis: chave do Gemini em aistudio.google.com/apikey · limites em aistudio.google.com/rate-limit ·
chave da ElevenLabs em elevenlabs.io/app/settings/api-keys

---

## 9. Backup do código original (antes das melhorias)

Ficou numa pasta temporária de um chat antigo e pode já ter sido apagada. O histórico confiável é o commit
`91f0697` no GitHub.

Detalhes da etapa 7:
- **Pronúncia (`Pronunciation`):** letras pelo nome ("B" → "bê", "F" → "éfe", como em "Toque na letra B"); sílabas
  com a vogal marcada ("BA" → "bá", "BE" → "bê"); quando o vocabulário tem palavra que começa com a letra/sílaba, a
  voz do app diz o exemplo ("bá, de bala"). O `gerar_audios.py` tem a mesma regra e um teste confere as 83.
- **`SoundPlayer`:** toca a fala gravada em `assets/voz` (ver `VoiceClips`, mudança de 23/09 que substituiu o antigo
  `res/raw/som_xx`) se existir; senão, a voz do app com a dica. Só termina quando o som acaba, para encadear. Registra no
  log (tag `LivroVivoSom`) o que usou em cada fala.
- **Onde soa:** nas 137 instruções que terminam em letra/sílaba ("Toque na sílaba BE", "Junte as letras e forme BA",
  "Cadê a letra A?"), a frase sai pela voz do app e o "BE" pelo `SoundPlayer`. No leitor "Eu leio", segurar uma
  palavra toca cada sílaba pelo `SoundPlayer` (acendendo junto) e depois lê a palavra inteira.
- **Conferido no emulador de testes** com dois áudios provisórios (um bipe em `som_la` e `som_do`, apagados depois):
  LA e DO saíram do arquivo; LE, LI, CE pela dica; LO e LU com exemplo ("lô, de lobo"). **Ninguém ouviu ainda**.
- **Falas gravadas (23/09, regravadas com a Azure em 25/09):** o `gerar_audios.py` grava 370 falas: 200 instruções inteiras (a pergunta
  "Toque na sílaba BE" toca de uma vez, sem emenda), 18 letras, 65 sílabas, 72 palavras (vocabulário, palavras de apoio
  com contexto para "o" não virar "ó", LUNA e PIPOCA) e 15 frases das telas. Chaves do índice: `fala:`, `letra:`,
  `silaba:`, `palavra:` e `frase:` + texto (NFC; letras, sílabas e palavras em maiúsculas). Áudio em `.ogg` (ffmpeg),
  volume nivelado, silêncio das pontas cortado. O script continua de onde parou se o limite do dia do Gemini acabar.
- **Para gravar (24/09):** faturamento ligado no projeto, login do Google Cloud CLI feito e, em `ferramentas/`,
  `python gerar_audios.py --teste` e depois `python gerar_audios.py` (padrão: `--provedor vertex`, modelo 3.1, uma
  fala por pedido). O `--provedor gemini` usa a chave do AI Studio só para testes: no plano gratuito são 3 pedidos por
  minuto e uns 5 por dia. Gravar várias falas num pedido (`--lote`) economiza pedidos, mas o modelo às vezes para no
  meio do lote, e uma fala pulada trocaria o áudio de uma sílaba por outra. Ouça tudo antes do commit e regrave com
  `--so "texto" --refazer`. O teste `VoiceClipsTest` confere que toda fala da trilha tem áudio.
- **Pronúncia na gravação:** letra vai como a própria letra ("F", com o nome "éfe" só na direção de voz), porque escrito
  "éfe" a voz soletrava o acento; palavras sem "pronunciar cada sílaba" na direção (senão "bola" saía "bo-la").
- **Conferência pelo Whisper:** fora do repositório, em `%TEMP%\lvtts2` (ambiente `qwen`,
  `conferir.py`), o Whisper large-v3-turbo transcreve cada fala na placa de vídeo e marca as que não batem com o texto.
- **Conferido no emulador de testes (23/09)** com bipes provisórios no lugar das falas da fase Letra A (apagados depois):
  instruções, "Tente de novo!", "Muito bem!" e o resultado tocaram do arquivo assim que a tela pediu; na fase sem
  áudio, a voz do aparelho levou 3,9 s para começar a primeira instrução. O nome da criança e "Ouvir a página" ainda
  usam a voz do app (vão para o servidor).

Detalhes da etapa 8 (decisões que a especificação não cobria):
- **Progressão sem assinatura:** um módulo libera quando todas as fases **que a criança pode jogar** do anterior têm
  estrela (`LiteracyRules.unlockedPhaseIds(..., subscriber)`). Sem isso, quem não assina nunca chegaria às fases grátis
  de Palavras e Ditado, já que não pode concluir as fases pagas de Sílabas.
- **Livros sem assinatura:** 1 por módulo (Sílabas, Palavras, Ditado) ao concluir as fases grátis dele, se a criança já
  lê ao menos 3 palavras (`MIN_BOOK_WORDS`). Com só B e C ela lê "BOCA", então na prática são 2 livros (Palavras e
  Ditado). Sempre offline: sem assinatura a IA nem é chamada e o leitor não pede ilustração.
- **Nunca tirar o que foi ganho:** fase concluída continua aberta e livros continuam na estante se a assinatura acabar.
- **Fase paga tocada pela criança:** aviso "Chame um adulto" (falado) sem preço nem link; "Sou adulto" abre o portão
  parental e só então a assinatura (regra "nenhuma cobrança durante a atividade").
- **Painel dos pais, seção "Leitura":** números de uso (não de aprendizagem), letras e 65 sílabas trabalhadas, "Vale
  praticar juntos" (3 fases com mais erros) e ideia fora da tela ligada à última fase. Para 9+, chave "Mostrar
  'Aprender a ler' para 9+ anos" (`showTrailForOlderKids`, vale para todos os perfis).
- **Paywall:** "Seu filho aprende a ler com histórias em que ele é o personagem" e "Trilha da Leitura completa".
- A assinatura continua **simulada** (`BillingRepositoryImpl` grava no DataStore); o Billing real está na outra branch.

## 5.2. O que falta depois da Trilha da Leitura
1. Testar os livros com IA com uma chave real (etapa 6) e ouvir a voz das sílabas num aparelho com som (etapa 7).
2. O usuário ouvir as 370 falas regravadas em 25/09 com a Azure (decisão 12 da seção 4) e regravar as que ficarem ruins
   (`python gerar_audios.py --so "texto" --refazer`).
3. Criar as 55 figuras (`ferramentas/lista_imagens.txt`, estilo guache do `DIRECAO_EDITORIAL.md`); até lá o app mostra
   cartões com a palavra.
4. Juntar a branch `claude/projeto-conforme-md-8263f2` (Billing real, álbum, ritual de dormir, sons) com esta linha.
   Ao juntar: os preços do paywall do `main` estão fixos no código; lá eles vêm do Google Play.
5. Provedor de IA para produção (termos do Gemini vetam apps para menores de 18) — vale para aventuras e livros.
6. Decisões da seção 10 do `ALFABETIZACAO.md` ainda em aberto: nome do modo (hoje "Aprender a ler") e nome na loja.
