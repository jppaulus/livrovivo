# Livro Vivo 📖✨

> Contador de histórias infantil com IA: a criança é a protagonista, escolhe os rumos da aventura e a história é escrita, ilustrada e narrada página a página.

---

## ✨ O que o app faz

- **Histórias que se constroem com as escolhas** – cada página termina com duas escolhas (cada uma ligada a uma virtude: coragem, empatia, criatividade, curiosidade, calma ou cooperação). A IA escreve a próxima página a partir do que a criança decidiu, seguindo um arco completo (início → desafios → clímax → final acolhedor). Dá para **voltar a qualquer página e escolher outro caminho**.
- **Narradores com voz natural** – quatro personagens (Capitão Aventura, que é o padrão, Fada Encantada, Ursinho Gentil e Vovó Contadora) com direção de atuação: sussurros, risadinhas e suspense. A página é narrada em partes: a primeira sai em poucos segundos e o resto é gerado enquanto a criança já está ouvindo. Motores de voz, do melhor para o offline:
  1. **ElevenLabs** (`eleven_v3`, com marcações de emoção como `[whispers]` e `[giggles]`);
  2. **Gemini TTS** (vozes neurais controladas por prompt, com a mesma chave da IA);
  3. **Voz do aparelho** (offline, escolhendo a melhor voz PT-BR instalada).
  O áudio fica salvo no aparelho (AAC) para ouvir de novo sem internet.
- **Destaque de leitura** – a frase narrada fica destacada na tela (estilo karaokê), o que ajuda quem está aprendendo a ler.
- **Ilustrações em cada página** – geradas pelo modelo de imagem do Gemini, usando a página anterior como referência para manter os personagens iguais do começo ao fim. São cinco estilos (aquarela, lápis de cor, animação 3D, papel recortado e desenho animado). Sem IA, o app desenha **cenas animadas próprias** para cada tema.
- **Personalização** – nome, idade, gênero (para a concordância no texto), aparência (para as ilustrações), companheiro mágico e interesses.
- **Música de ninar** – caixinha de música sintetizada ("Brilha, Brilha, Estrelinha"), que abaixa sozinha enquanto o narrador fala.
- **Área dos Pais** (protegida por conta de multiplicação por extenso) – tempo de leitura, páginas, virtudes escolhidas, palavras novas, sugestão de conversa pós-história e configurações de IA com botões de teste.
- **Modo offline completo** – histórias escritas à mão para 8 temas e para temas livres, com final que celebra as virtudes escolhidas.

---

## 🚀 Ativando a IA (voz natural + ilustrações)

### Opção 1 — Pelo próprio app (mais rápido para testar)
1. Crie uma chave gratuita em <https://aistudio.google.com/apikey>.
2. No app, toque no **cadeado** → resolva a conta → **IA, vozes e ilustrações**.
3. Cole a chave, toque em **Testar conexão** e depois em ▶ para ouvir cada narrador.
4. (Opcional) Adicione uma chave da **ElevenLabs** e toque em **Carregar minhas vozes** para escolher a voz de cada narrador.
   Dica: adicione à sua conta vozes com sotaque brasileiro pela biblioteca da ElevenLabs.
5. Use **Gerar ilustração de teste** para validar as imagens.

> A geração de imagens pode exigir faturamento ativo na conta do Google AI Studio. Sem isso, o app continua funcionando com as cenas desenhadas por ele mesmo.

### Opção 2 — `local.properties` (desenvolvimento)
As chaves abaixo são lidas **somente no build de debug** e nunca vão para o release:
```properties
gemini.apiKey=AIza...
elevenlabs.apiKey=sk_...
```

### Opção 3 — Produção com Supabase (chaves no servidor)
Um app publicado não deve pedir chaves aos pais. A Edge Function `ai-gateway` guarda as chaves no servidor e repassa as chamadas. Os prompts continuam no app; o servidor valida modelos e rotas, limita tamanhos e reforça os filtros de segurança infantis.
```bash
supabase secrets set --env-file supabase/.env   # GEMINI_API_KEY e ELEVENLABS_API_KEY
supabase functions deploy ai-gateway
```
Depois, no `local.properties`:
```properties
supabase.url=https://SEU-PROJETO.supabase.co
supabase.anonKey=eyJ...
```
Com o backend configurado, o app usa o `ai-gateway` automaticamente (uma chave digitada na Área dos Pais tem prioridade).

---

## 🧠 Modelos usados

| Recurso | Padrão | Alternativas automáticas |
|---|---|---|
| Texto das histórias | `gemini-2.5-flash` | `gemini-flash-latest` |
| Ilustrações | `gemini-3.1-flash-image` | `gemini-2.5-flash-image`, `gemini-3.1-flash-lite-image` |
| Voz Gemini | `gemini-3.1-flash-tts-preview` | `gemini-2.5-flash-preview-tts`, `gemini-2.5-pro-preview-tts` |
| Voz ElevenLabs | `eleven_v3` | `eleven_multilingual_v2`, `eleven_flash_v2_5` |

Se o Google desativar um modelo (como aconteceu com o `gemini-1.5-flash`), o app tenta o próximo da lista sozinho. Os modelos podem ser trocados em **Área dos Pais → Avançado**.

---

## 🏗️ Arquitetura

- **Kotlin + Jetpack Compose (Material 3)**, Clean Architecture + MVVM (`StateFlow`, coroutines), injeção com **Koin**.
- **Room** (v3, com migração que preserva histórias antigas) e **DataStore** para as configurações.
- **Media3/ExoPlayer** para a narração; `AudioTrack` para a música de ninar.

```
app/src/main/java/com/livrovivo/app/
├── core/
│   ├── ai/            # GeminiService, ElevenLabsService, StoryPrompts, StoryWriter,
│   │                  # ChapterSanitizer (valida a resposta da IA), OfflineStoryEngine
│   ├── audio/         # AudioPlayerController, motores de voz, personas, destaque de leitura,
│   │                  # codificação AAC/WAV e caixinha de música
│   ├── illustration/  # IllustrationService (geração + consistência entre páginas)
│   ├── database/      # Room (DAOs, migração 2→3)
│   ├── settings/      # Configurações e modelos de IA
│   ├── parentalgate/  # Portão parental
│   └── ui/            # Tema, cenas desenhadas, componentes mágicos
├── data/              # Entidades, mappers e repositórios
├── domain/            # Modelos, interfaces e casos de uso
└── presentation/      # onboarding, home, creation, reader, parent, settings, paywall
supabase/functions/ai-gateway/   # Proxy seguro de IA para produção
```

---

## 🛠️ Como executar

Requisitos: **JDK 17** e **Android SDK 35**.
```bash
./gradlew testDebugUnitTest   # testes unitários
./gradlew assembleDebug       # APK de debug
```

---

## 📌 Próximos passos recomendados

- Integrar de verdade o **Google Play Billing** (hoje a assinatura é simulada e salva no aparelho).
- Adicionar **Supabase Auth** e limite de uso por usuário no `ai-gateway`.
- Suporte a **vários perfis de crianças** e sincronização das histórias na nuvem.
- Guardar as chaves digitadas com criptografia (Android Keystore).
