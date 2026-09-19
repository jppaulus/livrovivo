# Livro Vivo — contexto para retomar em um novo chat

> Documento de passagem de bastão. Atualizado em 18/09/2026.
> Para começar rápido: leia as seções **1**, **5** e **6**.

---

## 1. Resumo do projeto

App Android nativo (Kotlin + Jetpack Compose) de **histórias infantis interativas com IA**: a criança é a
protagonista, escolhe os rumos da aventura, e cada página é **escrita, ilustrada e narrada** na hora.

- **Pasta local:** `%USERPROFILE%\Desktop\Creates\Livro Vivo`
- **Repositório:** https://github.com/jppaulus/livrovivo (branch `main`)
- **Público:** pais de crianças de 3 a 9 anos. Modelo freemium (3 histórias grátis).
- **Estado:** compila, 151 testes unitários passando, rodou no emulador. O Gemini (texto) já foi
  validado com chave real pelo usuário; imagem e ElevenLabs continuam sem teste real.

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
./gradlew testDebugUnitTest   # 151 testes
./gradlew assembleDebug       # APK em app/build/outputs/apk/debug/
./gradlew installDebug        # instala no aparelho/emulador conectado
```

**Testar sem mexer no emulador do usuário:** suba uma cópia descartável do mesmo AVD, sem janela.
Tudo o que acontece nela é jogado fora ao fechar; os dados do usuário no AVD não mudam.
```bash
emulator -avd Medium_Phone_API_36.1 -read-only -no-window -no-audio -no-snapshot -port 5580
adb -s emulator-5580 shell cmd alarm set-timezone Australia/Brisbane   # simula a noite (ritual de dormir)
adb -s emulator-5580 shell settings put global sys_storage_threshold_percentage 1   # o AVD está quase cheio
```
Lembre de rodar `assembleDebug` antes de instalar — `testDebugUnitTest` não gera o APK.

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
| `core/audio/AudioPlayerController.kt` | Narração: escolhe motor, **gera em partes**, toca com ExoPlayer, destaque de leitura, cache; som de fundo com troca suave (`refreshBackground()`) |
| `core/audio/NarrationText.kt` | `NarrationChunker` (divide a página) e `NarrationTimeline` (frases para o destaque) |
| `core/audio/NarrationEngines.kt` | Motores: ElevenLabs, Gemini TTS, voz do aparelho (com escolha de voz por narrador) |
| `core/audio/VoicePersona.kt` | 4 narradores + direção de atuação. **Não reordenar** (ver seção 4) |
| `core/audio/LullabySynth.kt` | Caixinha de música sintetizada ("Brilha, Brilha, Estrelinha") |
| `core/audio/AmbienceSynth.kt` | Sons da página sintetizados (grilos, lareira, vento, passarinhos, riacho, brilhinhos, ondas) e a regra `Ambience.forPage(mood, cena)` |
| `core/audio/BackgroundSound.kt` | O que toca por baixo da narração (sons da página, ninar, nada) e a migração da antiga preferência |
| `core/illustration/IllustrationService.kt` | Ilustração por página, consistência via imagem anterior, 5 estilos |
| `core/ui/SceneArt.kt` | 8 cenas desenhadas em Canvas (fallback offline e capa) |
| `core/ui/ChildSwitcher.kt` | Avatar, chip e folha de troca de criança (irmãos) |
| `domain/model/AdventureMemory.kt` | O que o companheiro lembra das aventuras anteriores (título, tema, escolhas) |
| `core/bedtime/Bedtime.kt` | Regras da hora de dormir: janela que cruza a meia-noite, histórias da noite, hora de acordar |
| `presentation/bedtime/` | Ritual (`BedtimeScreen`), o que é dito nele (`BedtimeScript`) e o modo dormindo (`SleepOverlay`) |
| `domain/model/StickerAlbum.kt` | Álbum de figurinhas: catálogo fixo de 34, o que se ganha lendo, o que é novidade |
| `core/album/StickerBook.kt` | Junta as regras do álbum com o que já foi colado (DataStore) e as histórias da criança |
| `core/ui/StickerArt.kt` e `presentation/album/` | Desenho das figurinhas e a tela do álbum |
| `presentation/home/CompanionGreeting.kt` | Saudação da Home: lembra uma escolha recente ou cumprimenta pela hora do dia |
| `core/database/` | Room v3 + migração 2→3 que **preserva histórias antigas** |
| `core/billing/BillingManager.kt` | Google Play Billing: conexão, planos, compra, confirmação e restauração |
| `core/billing/BillingModels.kt` | Planos, estados, e regras puras (o que libera Premium, mensagens de erro) |
| `core/settings/SettingsManager.kt` | DataStore: chaves, motor de voz, modelos, estilo, contador de histórias, `activeChildId`, `applyPendingDefaults()` |
| `presentation/reader/` | Leitor: páginas, escolhas, voltar e trocar de caminho, comemoração, barra de narração |
| `presentation/{home,creation,onboarding,parent,settings,paywall}/` | Demais telas |
| `presentation/onboarding/` | Formulário do perfil em 3 modos (`OnboardingMode`): primeiro uso, irmão novo, edição |
| `supabase/functions/ai-gateway/index.ts` | Proxy de IA para produção (chaves ficam no servidor) |

**Fluxo de uma página:** escolha da criança → `ContinueStoryUseCase` → `StoryWriter` (IA ou offline) →
capítulo salvo no Room → leitor mostra o texto → em paralelo: narração (em partes) e ilustração.

**Quem é "a criança" em cada ponto:** a estante, as métricas e a criação de histórias usam a criança
**ativa** (`activeChildId` no DataStore); ler, continuar e ilustrar uma história usam a criança **dona
dela** (`story.childId`). Misturar os dois é o que faz o nome trocar no meio da narrativa.

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
9. **Google Play Billing de verdade.** Decisões que valem a pena não refazer:
   - **Preço nunca é escrito no app.** Título, preço, período e teste grátis vêm do `ProductDetails`
     do Play, então promoções e outros países funcionam sem tocar no código.
   - **O Play é a fonte da verdade; o `isPremium` do DataStore é só espelho local**, para o Premium
     funcionar sem internet. Quando a consulta ao Play **falha**, o valor guardado é mantido — nunca
     se tira o Premium de quem pagou por causa de rede ruim.
   - **Compras são confirmadas (`acknowledgePurchase`)**, senão o Google estorna em 3 dias.
   - **Compra pendente não libera Premium** (boleto, aprovação dos pais); a tela avisa que está em análise.
   - **Sem produtos no Play Console, a tela diz "Assinaturas indisponíveis"** e desliga o botão, em vez
     de mostrar preço inventado. Há um "Destravar Premium" **só no build de debug** para testes.
   - **`BillingRepository` não abre a compra**: precisa de Activity e o resultado é assíncrono. A tela
     de assinatura fala direto com o `BillingManager` (mesmo padrão do `AudioPlayerController`).
   - **`testImplementation(libs.org.json)`** é obrigatório: com `unitTests.isReturnDefaultValues = true`,
     o `org.json` do Android vira stub e qualquer parse de compra do Play silenciosamente vira lixo.
10. **Vários perfis de crianças (irmãos).** Decisões que valem a pena não refazer:
   - **Não precisou de migração do Room.** A tabela `child_profiles` já aceitava vários registros e
     `stories`/`reading_sessions` já tinham `childId`; o que limitava era o DAO (`LIMIT 1`). O banco
     continua na **versão 3** e nenhuma história antiga é tocada.
   - **A criança ativa fica no DataStore** (`activeChildId`), não no banco. Se o id salvo não existir
     mais, cai no primeiro perfil em vez de deixar o app sem criança.
   - **O limite gratuito é do aparelho, não por criança.** Cadastrar irmãos não multiplica as 3
     histórias grátis (`countGeneratedStories()` continua global). Há teste cobrindo isso.
   - **O último perfil não é apagado** — o app não abre sem nenhuma criança cadastrada.
     Apagar um perfil leva junto histórias, ilustrações e sessões de leitura dele.
   - **"Apagar todas as histórias" na Área dos Pais só apaga as da criança em foco**, para bater com
     a lista mostrada logo acima do botão.
   - **Trocar de criança é ação da criança** (chip na Home, sem portão); **cadastrar, editar e apagar
     são ações dos pais** (Área dos Pais, atrás do portão parental).

11. **Memória do companheiro.** Decisões que valem a pena não refazer:
   - **Montada com o que já está salvo** (título, tema e escolhas de cada história): nenhuma chamada
     extra à IA e nenhuma coluna nova no banco (`AdventureMemory.recent`).
   - **Só as aventuras da própria criança** — um irmão não "lembra" do que o outro viveu.
   - **Só no prompt de abertura**, pedindo no máximo uma referência; os capítulos seguintes já veem o
     capítulo 1 com a lembrança feita. O prompt manda ignorar instruções escondidas nas memórias.
   - **Quem estava na aventura diz "eu lembro"; um companheiro novo "ouviu falar"** — vale para o
     prompt, o motor offline e a saudação da Home.
   - **Nunca culpa como gancho:** nada de "sumiu", "saudade" ou "fiquei triste". A saudação só cita
     aventuras dos últimos 3 dias; depois disso volta a ser a da hora do dia (há teste para isso).
   - **As escolhas começam com verbo no infinitivo**, então entram direto na frase ("você escolheu
     acender a lanterna"); se alguma fugir disso, vai entre aspas.
12. **O diálogo do limite gratuito não vende para a criança.** Não cita o Premium nem pede que ela
    convença um adulto (CDC, art. 37, §2º): leva a criança a descobrir outros finais, que não contam no
    limite, e deixa a oferta só atrás do portão. Se a estante dela estiver vazia (um irmão usou as
    histórias grátis), pede um adulto sem anunciar nada.
13. **Ritual da hora de dormir.** Decisões que valem a pena não refazer:
   - **A noite vai do horário dos pais (padrão 19h30, com opção "Desligado") até as 6h**, cruzando a
     meia-noite: histórias antes e depois da meia-noite contam como a mesma noite.
   - **Dois modos:** sem limite, o fim da história à noite só *oferece* o boa-noite em destaque; com
     "histórias por noite", ao atingir o limite ele vira o *único* caminho (fim da história, botão da Home
     e tela de criação).
   - **O limite conta finais de história por criança** (inclui "Outro final"), guardados no DataStore
     (`storyEndings`, últimos 10 por criança). O fim é gravado *antes* da página final, para o card não piscar.
   - **Modo dormindo = `sleepUntil` no DataStore + sobreposição por cima do NavHost** (não no lugar dele),
     para a navegação continuar válida. Bloqueia toques e o voltar, sobrevive a fechar o app, acorda sozinho
     às 6h ou por um adulto no portão; ao acordar volta para a estante (não reabre o ritual).
   - **A caixinha do ritual toca mesmo com a música de fundo desligada, sem mudar essa preferência**, e
     some aos poucos 3 minutos depois do boa-noite. Sair no meio do ritual para fala e música.
   - **O leitor não interrompe o ritual ao sair de cena** (a navegação o desmonta depois): falas do ritual
     usam chaves `bedtime#` e `onReaderStopped()` as respeita.
   - **Brilho baixo só na janela do app** no modo dormindo; a configuração do aparelho não é tocada.
   - **O ritual segue o mesmo princípio de engajamento:** o companheiro está com sono, nunca triste nem
     pedindo para a criança ficar; nada com concordância de gênero (há teste para as duas coisas).
14. **Álbum de figurinhas.** Decisões que valem a pena não refazer:
   - **Catálogo fixo de 34 figurinhas numeradas** (9 mundos, 18 selos de virtude em bronze/prata/ouro,
     7 conquistas), e não ilustrações da IA: imagem exige faturamento (muitas famílias não teriam nenhuma)
     e a graça do álbum é ver os espaços vazios que faltam, o que exige saber o catálogo antes. Os mundos
     usam as cenas que o app já desenha (`SceneArt`), então o álbum funciona até offline.
   - **Ganha-se lendo; nunca comprando, por sorteio ou por sequência.** "Sete dias" são dias diferentes,
     não seguidos. Há teste que barra palavras como "compra", "sorte" e "seguidos" nas regras.
   - **Figurinha colada não sai mais** (`collectedStickers` no DataStore): apagar ou reescrever histórias
     não tira nada. Só apagar o perfil da criança limpa o álbum dela (`forgetChild`, que também limpa os
     finais usados pela hora de dormir).
   - **"Nova" = o que a leitura garante e ainda não estava colado**, calculado quando a página final
     aparece. Quem já lia antes do álbum recebe como novidade só o que a história atual trouxe; o resto
     entra sem festa.
   - **Uma história conta como terminada quando tem a página final**, mesmo antes de `isCompleted` ser
     gravado (evita perder a figurinha numa corrida com o banco).
   - **Espaço vazio mostra o ícone apagado, o número no canto e quanto falta** ("2 de 6") — para quem
     ainda não lê. Na hora de dormir obrigatória, o fim mostra as figurinhas novas mas não o botão do álbum.
15. **Sons da página.** Decisões que valem a pena não refazer:
   - **Sintetizados no aparelho, sem arquivo de áudio** (mesma ideia da caixinha): o APK não cresce, não há
     licença de gravação para conferir e funciona offline. Cada som é um loop de 16 s que emenda sem
     estalo (ruído periódico, filtros em duas passadas, LFOs com número inteiro de ciclos e eventos
     circulares); há teste para a emenda, o volume e o pico.
   - **O som vem do `mood` da página, com o lugar vencendo quando é marcante:** espaço → brilhinhos,
     mar → ondas, noite → grilos (vento se misteriosa, lareira se aconchegante); "emocionante" → brilhinhos.
     Nos demais lugares: sonolento → grilos, aconchegante → lareira, misterioso → vento, alegre →
     passarinhos, aventura → riacho. Sem `mood`: lareira em casa, passarinhos no resto.
   - **Um botão de três estados no leitor** (sons da página → música de ninar → nada), com um aviso curto
     do que ficou ligado. A preferência antiga (`ambient_music`) só é lida para migrar: quem tinha ligado
     a caixinha continua com ela; **os demais passam a ouvir os sons da página**, que é o novo padrão.
   - **A caixinha continua sendo a música do ritual**, qualquer que seja a escolha no leitor (decisão 13).
   - **Troca suave entre páginas:** o som anterior some em 0,6 s enquanto o novo entra em 0,9 s; página
     com o mesmo som não reinicia o loop. Volume 0,45 (0,16 com o narrador falando), um pouco abaixo da
     caixinha, porque é fundo e não trilha. Os três últimos sons ficam em cache (~700 KB cada).
   - **A saída suave parte do volume real da faixa** (`backgroundVolume`), nunca do volume da camada
     nova: calcular pela camada nova fazia a caixinha, já muda no fim do ritual, voltar baixinho por
     meio segundo com a criança dormindo, e cortava o som ao sair do livro. O `scope` é
     `Main.immediate`, então um fade cancelado roda o `finally` na hora: ele não mexe no volume.
   - **Só toca dentro do leitor** (e no ritual): sair do livro desliga o fundo.
   - ⚠️ **Ninguém ouviu ainda:** o emulador de teste roda sem áudio. O teste confirmou qual som toca, a
     troca e o desligar (via `dumpsys audio` e o log `LivroVivoAudio`), mas o gosto de cada som precisa de
     ouvido humano. `AmbienceSamplesExport` grava os sete em WAV se `AMBIENCE_WAV_DIR` estiver definida.
---

## 5. Estado do Git

- **Branch de trabalho:** `claude/projeto-conforme-md-8263f2` (worktree em `.claude/worktrees/`).
- **No GitHub (`main`):** commit `5876283` — "Livro Vivo: histórias com IA, vozes naturais e ilustrações".
- **Já commitado na branch, ainda não enviado para o GitHub:**

| Commit | Conteúdo |
|---|---|
| `5d0a608` | Narração em partes, diagnóstico dos erros da IA e Capitão como narrador padrão |
| `3112bd4` | Versiona este `HANDOFF.md` |
| `700c9ec` | Vários perfis de crianças (irmãos) |
| `1248148` | Google Play Billing de verdade |
| `deeab08` | Memória do companheiro e diálogo do limite sem venda para a criança |
| `163a123` | Ritual da hora de dormir e modo dormindo |
| `b307d45` | Álbum de figurinhas por criança |
| *(este)* | Sons da página pelo clima e lugar de cada página |

⚠️ O checkout principal (`%USERPROFILE%\Desktop\Creates\Livro Vivo`) ainda tem as mesmas mudanças
do `5d0a608` soltas na cópia de trabalho. Depois de juntar a branch na `main`, dá para descartá-las lá
(`git checkout -- .`) — elas já estão no histórico.

---

## 6. Pendências (em ordem de prioridade)

### 6.1. Cadastrar as assinaturas no Play Console — **pendente do usuário, não do código**
O código de cobrança está pronto e testado, mas os produtos precisam existir no Google Play Console:
`livro_vivo_annual` (anual, 7 dias de teste grátis) e `livro_vivo_monthly` (mensal). Enquanto não
existirem, a tela mostra "Assinaturas indisponíveis" — de propósito, para não inventar preço.

Para testar de verdade: faixa de **teste interno** + conta de testador de licença + instalar pela Play
Store. Emulador sem Google Play não compra. No build de debug há um "Destravar Premium" para testar os
recursos Premium sem nada disso.

Falta também decidir o que fazer quando a assinatura **expira com histórias acima do limite gratuito**:
hoje elas continuam no aparelho e só a criação de novas é bloqueada.

### 6.2. Tempo até começar a narrar — **precisa medir**
Era cerca de 30s. Com a narração em partes deve cair para poucos segundos, mas **não foi medido no aparelho**.
Se ainda passar de ~8s: gerar as duas primeiras partes em paralelo e encurtar a primeira
(`NarrationChunker.chunk(firstMaxChars = ...)`).

### 6.3. Integrações de IA ainda sem teste real
O **Gemini de texto já foi validado** com a chave do usuário (o antigo erro 403 está resolvido).
Continuam sem teste com chave real: **imagem** do Gemini (lembrando que exige faturamento ativo),
**TTS** do Gemini e a **ElevenLabs** (voz e listagem de vozes). Cada uma tem botão "Testar" nas configurações.

### 6.4. Engajamento da criança — próximos passos
Princípio adotado: a retenção vem de **ritual** (os pais abrem toda noite porque funciona) e de
**antecipação** (a criança pede a próxima história) — nunca de culpa, sequência que zera, recompensa
aleatória ligada a pagamento ou história emendada sozinha. Quem paga são os pais, e o uso principal é
na hora de dormir. Já feito e testado no emulador: memória do companheiro, diálogo do limite, ritual de
dormir, álbum de figurinhas e sons da página. Na fila, em ordem:
1. **Páginas que reagem ao toque** (tocar na ilustração faz algo pequeno: um brilho, um som, o
   companheiro dizendo uma frase), sem virar jogo que tira a atenção da história.
2. Depois: aventura dos irmãos, coautoria por voz (só com reconhecimento no aparelho e consentimento
   dos pais — LGPD, art. 14), datas especiais e boletim semanal para os pais.
- **Ouvir os sons da página num aparelho de verdade** e ajustar o que soar estranho (volume relativo à
  voz, lareira e ondas são os mais difíceis de sintetizar). Os ajustes de cada som (volume de cada
  camada, frequências, estalos por segundo) ficam na função dele em `AmbienceSynth.kt`; o volume de
  todos juntos é o `PAGE_SOUND_VOLUME` do `AudioPlayerController`.
- Detalhe de UX: a criança só descobre o limite gratuito depois de escolher o tema na criação; dá para
  checar ao abrir a tela (o limite da noite já é checado ao abrir).
- Possível limpeza: no fim da história, "Conquistas desta história" (selos com a contagem) e
  "Figurinhas novas" mostram as mesmas virtudes; dá para juntar num bloco só.
- Não verificado no aparelho: a voz das falas do ritual (o emulador de teste roda sem áudio) e o acordar
  sozinho às 6h (coberto por teste unitário da regra de horário).

### 6.5. Outras pendências
- **Sem validação no servidor das compras:** o app confia no Google Play do aparelho. Para barrar
  aparelhos com root/apps de bypass, seria preciso validar o token da compra pela Google Play Developer
  API a partir de um servidor (dá para usar o Supabase).
- **Segurança do backend:** `ai-gateway` não tem autenticação por usuário nem limite de uso; adicionar
  Supabase Auth + cota por usuário antes de publicar.
- **Chaves em texto puro** no DataStore; migrar para Android Keystore.
- **Sem sincronização na nuvem:** perfis e histórias vivem só no aparelho; trocar de celular perde tudo.
- **Sem testes instrumentados de UI** (só unitários). O seletor de criança e a gestão de perfis não têm
  teste de interface.
- Política Famílias do Google Play: revisar antes de publicar (portão parental já existe).

---

## 7. Texto pronto para começar o novo chat

> Estou retomando o app Livro Vivo (Android/Kotlin/Compose), em
> `%USERPROFILE%\Desktop\Creates\Livro Vivo`, repo https://github.com/jppaulus/livrovivo.
> Leia o `HANDOFF.md` na raiz do projeto: ele tem a arquitetura, as decisões já tomadas e as pendências.
> O trabalho recente está na branch `claude/projeto-conforme-md-8263f2`, ainda não enviada ao GitHub.
> Minha prioridade agora é: [ex.: páginas que reagem ao toque / cadastrar as assinaturas no Play Console /
> medir o tempo da narração].

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
