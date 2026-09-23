# Livro Vivo — continuar em um novo chat

> Criado em 23/09/2026, ao fim do chat que implementou a **Trilha da Leitura** (`ALFABETIZACAO.md`, etapas 0 a 8).
> Leia este arquivo inteiro antes de mexer em qualquer coisa. Responda sempre em **português do Brasil**.
> O usuário está começando em programação: explique o que mudou e como testar, uma etapa por vez.

---

## 1. Onde está o código (importante: não é o `main`)

| O quê | Onde |
|---|---|
| Pasta do projeto | `%USERPROFILE%\Desktop\Creates\Livro Vivo` (branch `main`, commit `17460eb`) |
| **Trilha da Leitura** | branch **`feature/trilha-da-leitura`**, commits até **`2958223`** + este arquivo |
| Pasta de trabalho da trilha | worktree `%USERPROFILE%\Desktop\Creates\Livro Vivo\.claude\worktrees\leitura-docs-iniciais-62ddbc` |
| GitHub | https://github.com/jppaulus/livrovivo — **nada foi enviado** (`origin/main` = `5876283`) |

- Este arquivo e o `HANDOFF.md` **atualizado** estão na branch da trilha
  (`git show feature/trilha-da-leitura:NOVO_CHAT.md` e `git show feature/trilha-da-leitura:HANDOFF.md`).
  O `HANDOFF.md` da pasta principal (branch `main`) é de antes da trilha e está desatualizado.
- A branch `feature/trilha-da-leitura` está "em uso" pelo worktree acima. Para continuar num worktree novo, crie
  uma branch a partir dela: `git switch -c feature/trilha-ajustes feature/trilha-da-leitura` (ou trabalhe na
  pasta do worktree antigo).
- Worktree novo não tem o `local.properties` (fica fora do git): copie o da pasta principal, senão o Gradle não
  acha o SDK. Ele **não tem chaves** de API hoje.

### Commits da trilha (em cima do `main` local)
```
17460eb  (main) Revisão editorial, perfis da família, narração em partes e diagnóstico da IA  ← etapa 0 (commit do que estava pendente)
a206c6e  Etapa 1: conteúdo e dados
ccf4eec  Etapa 2: trilha jogável
44530f0  Etapa 3: progresso e desbloqueio
1ddeea7  Etapa 4: validador e livro offline
e568ad0  Etapa 5: leitor "Eu leio"
edf2dab  HANDOFF: data e emulador de testes
dab7d70  Etapa 6: livro "Eu leio" com IA
e8b2c2c  Etapa 7: voz das letras e sílabas
2958223  Etapa 8: painel dos pais e assinatura
```

### Pendências de git (decidir com o usuário)
1. **`gradle/libs.versions.toml` alterado e sem commit na pasta principal (`main`)**: AGP `8.7.3` → `8.13.2`, feito
   em 22/09 às 22:06, **não foi o Claude** (provavelmente o Android Studio). A pergunta ficou aberta: testar (build +
   testes) e commitar, ou desfazer. A branch da trilha continua com `8.7.3`.
2. **Outra linha nunca juntada:** branch `claude/projeto-conforme-md-8263f2` (`41e3558`, até 19/09) com Google Play
   Billing **real**, álbum de figurinhas, ritual da hora de dormir, sons da página e memória do companheiro. Ela e o
   `main` saíram de `5876283` e mexem em 35 arquivos em comum. O usuário escolheu terminar a trilha no `main` e juntar
   depois. O banco dela é igual ao da base na versão 3 (as migrações 3→4→5→6 funcionam por cima dela).
3. Juntar `feature/trilha-da-leitura` no `main` e enviar ao GitHub: ainda não pedido.

---

## 2. O que o usuário pediu por último

Depois de receber o APK, ele disse: **"preciso de alterações"** — e pediu este arquivo antes de dizer quais.
**Comece perguntando quais alterações ele quer** (e a decisão do item 1 acima).

---

## 3. O que existe hoje (Trilha da Leitura completa)

| Etapa | Entrega |
|---|---|
| 1 | `trilha.json` em `app/src/main/assets/alfabetizacao/` (gerado por `ferramentas/gerar_conteudo.py`), `TrailParser`, `LiteracyRepository`, Room 4→5 (`literacy_progress`, `stories.kind`) |
| 2 | Telas `TrailScreen` (mapa), `PhaseListScreen`, `ActivityScreen` + `activity/` (5 tipos), `PhaseResultScreen`; cartão "Aprender a ler" na Home |
| 3 | Estrelas salvas, desbloqueio em ordem, `LiteracyKnowledge` |
| 4 | `DecodableValidator`, `OfflineDecodableEngine` (roteiros Coleção, Cadê?, Adivinha), `booksEarned` |
| 5 | Livros "Eu leio" criados ao ganhar, aviso no resultado, `EasyReaderScreen` (tocar lê, segurar separa sílabas, "Ouvir a página", "Li sozinho!"), selo "Eu li!", Room 5→6 (`literacy_page_reads`) |
| 6 | Livro com IA: `StoryPrompts.decodablePrompt` (lista fechada), `LiteracyBookWriter` (1 nova tentativa, offline como garantia), livro escrito em segundo plano, ilustrações da IA no leitor |
| 7 | Voz: `Pronunciation` + `SoundPlayer` (áudio `res/raw/som_xx` ou voz do app com dica "bá, de bala"), `ferramentas/gerar_audios.py` |
| 8 | Fases pagas bloqueadas, livros com IA só para assinantes, seção "Leitura" no painel dos pais, trilha para 9+ nas configurações, argumento no paywall |

**Arquivos principais:** `core/literacy/` (regras, validador, motor offline, escritor, voz, resumo do painel),
`presentation/literacy/` (telas e ViewModels), `data/repository/LiteracyRepositoryImpl.kt` e `EuLeioRepositoryImpl.kt`,
`ferramentas/` (scripts Python). O mapa completo está no `HANDOFF.md` desta branch.

---

## 4. Decisões já tomadas (não refazer sem falar com o usuário)

- Nome do modo: **"Aprender a ler"**. Trilha **escondida para 9+**, com chave nos pais ("Mostrar 'Aprender a ler' para 9+ anos").
- Vocabulário ganhou `objeto` e `lugar` no `trilha.json` (listas `LUGARES` e `NAO_OBJETOS` no `gerar_conteudo.py`) para
  o motor offline não escrever "LIA TEM UMA FACA" nem "O DADO ESTÁ NA BOCA". Conteúdo da trilha **nunca** no Kotlin.
- Companheiro só entra no livro quando a criança já lê o nome (LUNA, PIPOCA); Bento e Aurora nunca.
- **Sem assinatura:** módulo seguinte abre quando as fases que a criança **pode jogar** do anterior estão feitas;
  1 livro offline por módulo ao concluir as fases grátis (na prática Palavras e Ditado). **Com assinatura:** 1º livro
  com 3 fases de Sílabas, depois 1 a cada 2 fases (16 na trilha).
- **Nunca tirar o que a criança ganhou** (fases feitas e livros ficam se a assinatura acabar).
- Fase paga tocada pela criança: "Chame um adulto" (sem preço/link); a assinatura só depois do `ParentalGateDialog`.
- Livros "Eu leio" não contam na cota grátis (`getStoryCount` só conta aventuras) nem nas estatísticas das aventuras.
- Voz: letras pelo **nome** ("B" → "bê", como em "Toque na letra B"); sílabas com vogal marcada ("BA" → "bá").
  A mesma regra está no `gerar_audios.py` (um teste confere as duas).
- Telas de alfabetização usam fonte **sem serifa** (`LetterFont`); o tema do app usa serifa nas aventuras.
- Botão diz "Li sozinha!" para meninas. Leitor "Eu leio" sem narração automática e **sem música ambiente**.
- Erro de ilustração nunca aparece para a criança (mostra a figura/cartão da palavra).
- IA: tenta 2 vezes com o validador; erro de rede/cota vai direto ao livro offline. Regras de segurança infantil
  compartilhadas em `StoryPrompts.SAFETY_RULES` (o prompt das aventuras não mudou).
- Painel dos pais: números de **uso**, não de aprendizagem; nenhum texto promete resultado (`MELHORIAS_UX.md`).

---

## 5. Testes, emulador e APK

```bash
# JDK: C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot
./gradlew testDebugUnitTest      # 175 testes unitários
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
```

- **Emulador:** nunca instalar, tocar ou rodar testes no emulador do usuário (`Medium_Phone_API_36.1`, porta 5554,
  com janela). Use o AVD **`LivroVivo_Teste`** (criado com aprovação dele, porta 5582, roda junto):
  ```bash
  emulator -avd LivroVivo_Teste -no-window -no-audio -no-snapshot -no-boot-anim -port 5582
  ANDROID_SERIAL=emulator-5582 ./gradlew connectedDebugAndroidTest   # 9 testes; desinstala o app no fim
  ```
  Ele não tem som: sons só foram conferidos pelo log (tag `LivroVivoSom`) — **ninguém ouviu ainda**.
- `uiautomator dump` leva ~4 s nesse emulador (as animações nunca param). Para fluxos longos (ex.: 21 fases até o
  1º livro), grave progresso direto no banco do app de teste: `adb exec-out run-as com.livrovivo.app cat
  databases/livro_vivo.db` (e `-wal`, `-shm`), inserir em `literacy_progress` com Python/sqlite3, `PRAGMA
  wal_checkpoint(TRUNCATE)`, `adb push` para `/data/local/tmp` e `run-as ... cp` de volta (apagando `-wal`/`-shm`).
- Neste ambiente, comandos Bash com **crases** ou **emojis** dentro de heredoc falham: grave scripts em arquivo
  (ferramenta de escrita) e só execute no terminal.
- **APK entregue em 23/09:** debug, 29 MB, versão 1.1.0, commit `2958223`, sem chaves dentro. Instala por cima de
  versões antigas mantendo os dados (banco migra até a v6); voltar para versão antiga exige desinstalar.

---

## 6. O que falta depois da trilha

1. Testar os livros com IA com uma **chave real** (Área dos Pais → "IA, vozes e ilustrações") e **ouvir** a voz das
   sílabas num aparelho com som.
2. Gravar os 83 áudios: chave no `local.properties`, e em `ferramentas/`: `python gerar_audios.py --teste`, depois
   `python gerar_audios.py`; ouvir os arquivos em `app/src/main/res/raw/` antes do commit.
3. Criar as 55 figuras (`ferramentas/lista_imagens.txt`, estilo guache do `DIRECAO_EDITORIAL.md`, em
   `res/drawable-nodpi/` como JPEG). Até lá aparecem cartões com a palavra.
4. Juntar a branch `claude/projeto-conforme-md-8263f2` (Billing real etc.). No `main`, a assinatura é **simulada**
   (DataStore) e o paywall tem preços fixos no código; na outra branch os preços vêm do Google Play.
5. Provedor de IA para produção: os termos da API do Gemini vetam apps para menores de 18 (vale para aventuras e livros).
6. Seção 10 do `ALFABETIZACAO.md` ainda aberta: nome definitivo do modo e nome do app na loja.

---

## 7. Texto pronto para colar no novo chat

> Estou continuando o app Livro Vivo (Android/Kotlin/Compose) em `%USERPROFILE%\Desktop\Creates\Livro Vivo`.
> A Trilha da Leitura está pronta na branch `feature/trilha-da-leitura` (sem push). Antes de tudo, leia
> `git show feature/trilha-da-leitura:NOVO_CHAT.md`, depois `git show feature/trilha-da-leitura:HANDOFF.md`
> e o `ALFABETIZACAO.md`. Trabalhe em cima dessa branch.
> Quero fazer algumas alterações: [descreva aqui o que quer mudar].
