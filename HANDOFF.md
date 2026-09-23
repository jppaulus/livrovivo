# Livro Vivo — contexto para retomar em um novo chat

> Documento de passagem de bastão. Atualizado em 22/09/2026.
> Para começar rápido: leia as seções **1**, **5** e **6**.

---

## 1. Resumo do projeto

App Android nativo (Kotlin + Jetpack Compose) de **histórias infantis interativas com IA**: a criança é a
protagonista, escolhe os rumos da aventura, e cada página é **escrita, ilustrada e narrada** na hora.

- **Pasta local:** `%USERPROFILE%\Desktop\Creates\Livro Vivo`
- **Repositório:** https://github.com/jppaulus/livrovivo (branch `main`)
- **Público:** pais de crianças de 3 a 9 anos. Modelo freemium (3 histórias grátis).
- **Estado:** compila, 108 testes unitários e 5 instrumentados passando, rodou no emulador. As integrações de IA
  **ainda não foram testadas com chaves reais**.

---

## 2. Ambiente de desenvolvimento

| Item | Valor |
|---|---|
| JDK | 17 (`C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot`) |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` (platforms 34/35/36) |
| Gradle / AGP / Kotlin | wrapper 8.13 / AGP 8.7.3 / Kotlin 2.0.21 |
| minSdk / targetSdk | 26 / 35 |
| Emulador | AVD `Medium_Phone_API_36.1` |

```bash
./gradlew testDebugUnitTest   # 108 testes
./gradlew assembleDebug       # APK em app/build/outputs/apk/debug/
./gradlew installDebug        # instala no aparelho/emulador conectado

# Testes instrumentados (banco/migrações) numa cópia descartável do emulador, sem tocar no do usuário:
emulator -avd Medium_Phone_API_36.1 -read-only -no-window -no-audio -no-snapshot -port 5582
adb -s emulator-5582 shell settings put global sys_storage_threshold_percentage 1   # AVD quase cheio
ANDROID_SERIAL=emulator-5582 ./gradlew connectedDebugAndroidTest
# Outra sessão pode estar usando uma cópia na 5580: use outra porta (o teste instrumentado desinstala o app).
# Se o servidor do adb reiniciar no meio do teste, a cópia pode travar (aparece em "adb devices", mas não
# responde): "adb -s emulator-5582 emu kill" e suba de novo.
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
| `core/database/` | Room v5; migrações 2→3, 3→4 e 4→5 **preservam histórias antigas** (testes em `androidTest/`) |
| `core/literacy/TrailParser.kt` | Lê e valida `assets/alfabetizacao/trilha.json` (Trilha da Leitura) |
| `core/literacy/LiteracyRules.kt` | Regras puras da trilha: estrelas, desbloqueio em ordem, `LiteracyKnowledge`, quem vê a trilha (9+ escondida), peças que formam a resposta |
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
5. **Narração em partes:** a primeira parte (~260 caracteres) toca em poucos segundos; o resto é gerado em
   segundo plano e entra na fila do ExoPlayer. Cortes só em parágrafo/frase.
6. **Limite gratuito conta histórias criadas** (`storiesCreated` no DataStore), então apagar histórias não
   devolve as grátis.
7. **Segredos fora do repositório:** `.gitignore` exclui `local.properties`, `*.env`, keystores, APKs e
   pastas de build. Já foi feita varredura por chaves antes do commit.
8. **Escolhas ligadas a virtudes** (coragem, empatia, criatividade, curiosidade, calma, cooperação) — elas
   alimentam as conquistas da criança e o painel dos pais.

---

## 5. Estado do Git

- **No GitHub (`origin/main`):** commit `5876283`.
- **`main` local:** `17460eb`, com todo o trabalho que estava sem commit até 22/09 (narração em partes, erros 403,
  Capitão padrão, revisão editorial, perfis da família, Room v4). **Ainda sem push.**
- **`feature/trilha-da-leitura`:** a partir do `17460eb`, trabalho da Trilha da Leitura (`ALFABETIZACAO.md`).
  Fica no worktree `.claude/worktrees/leitura-docs-iniciais-62ddbc`.
- ⚠️ **Linha paralela não integrada:** a branch `claude/projeto-conforme-md-8263f2` (10 commits até 19/09) saiu do
  mesmo `5876283` e tem Google Play Billing real, álbum de figurinhas, ritual da hora de dormir, sons da página e
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
| 4 a 8 | Pendentes |

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
> `%USERPROFILE%\Desktop\Creates\Livro Vivo`, repo https://github.com/jppaulus/livrovivo.
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

`%LOCALAPPDATA%\Temp\claude\C--Users-usuario-Desktop-Creates-Livro-Vivo\4db335ba-e2af-4cbd-93b1-f66b33f8ba7a\scratchpad\original`

É uma pasta temporária e pode ser apagada pelo sistema. O histórico confiável é o commit `5876283` no GitHub.
