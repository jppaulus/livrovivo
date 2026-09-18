# Livro Vivo — contexto para retomar em um novo chat

> Documento de passagem de bastão. Atualizado em 17/09/2026.
> Para começar rápido: leia as seções **1**, **5** e **6**.

---

## 1. Resumo do projeto

App Android nativo (Kotlin + Jetpack Compose) de **histórias infantis interativas com IA**: a criança é a
protagonista, escolhe os rumos da aventura, e cada página é **escrita, ilustrada e narrada** na hora.

- **Pasta local:** `%USERPROFILE%\Desktop\Creates\Livro Vivo`
- **Repositório:** https://github.com/jppaulus/livrovivo (branch `main`)
- **Público:** pais de crianças de 3 a 9 anos. Modelo freemium (3 histórias grátis).
- **Estado:** compila, 83 testes unitários passando, rodou no emulador. O Gemini (texto) já foi
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
./gradlew testDebugUnitTest   # 62 testes
./gradlew assembleDebug       # APK em app/build/outputs/apk/debug/
./gradlew installDebug        # instala no aparelho/emulador conectado
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
| `core/ui/ChildSwitcher.kt` | Avatar, chip e folha de troca de criança (irmãos) |
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
| *(este)* | Google Play Billing de verdade |

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

### 6.4. Outras pendências
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
> Minha prioridade agora é: [ex.: cadastrar as assinaturas no Play Console / medir o tempo da narração /
> proteger o ai-gateway com Supabase Auth].

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
