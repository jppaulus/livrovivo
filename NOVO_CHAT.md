# Livro Vivo — continuar em um novo chat

> Atualizado em 24/09/2026, ao fim do chat que trocou as vozes (trilha gravada com o Gemini e narrador das histórias
> em streaming). O chat anterior (23/09) implementou a **Trilha da Leitura** (`ALFABETIZACAO.md`, etapas 0 a 8).
> Leia este arquivo inteiro antes de mexer em qualquer coisa. Responda sempre em **português do Brasil**.
> O usuário está começando em programação: explique o que mudou e como testar, uma etapa por vez.

---

## 1. Onde está o código (importante: não é o `main`)

| O quê | Onde |
|---|---|
| Pasta do projeto | a pasta principal do projeto no PC (branch `main`, commit `a226e2f`) |
| **Linha atual** | branch **`feature/trilha-da-leitura`**: a trilha (etapas 1–8) + as vozes de 24/09 |
| GitHub (**público**) | https://github.com/jppaulus/livrovivo — `main`, `feature/trilha-da-leitura` e `claude/projeto-conforme-md-8263f2` |

- **Repositório público:** nunca commitar chaves, o ID do projeto do Google Cloud, caminhos do PC ou anotações
  pessoais do usuário. Em 25/09 o histórico foi reescrito para tirar o e-mail pessoal e caminhos do PC: os commits
  mudaram de código (ver HANDOFF §5) e usam o e-mail privado do GitHub.

- Este arquivo e o `HANDOFF.md` atualizado estão na branch `feature/trilha-da-leitura`
  (`git show feature/trilha-da-leitura:NOVO_CHAT.md`). O `HANDOFF.md` da pasta principal (`main`) é antigo.
- Worktree novo não tem o `local.properties` (fica fora do git): copie o da pasta principal. Ele tem
  `gemini.apiKey` (a chave de desenvolvimento, que vai **dentro do APK de teste**: não compartilhe esses APKs) e
  `googlecloud.projeto` (o ID do projeto no Google Cloud).

### Pendências de git (decidir com o usuário)
1. **AGP:** `gradle/libs.versions.toml` alterado sem commit na pasta principal (`8.7.3` → `8.13.2`, feito pelo
   Android Studio em 22/09). Testado em 23/09 na linha da trilha: compila e os testes passam. Falta o usuário decidir.
2. **Outra linha nunca juntada:** `claude/projeto-conforme-md-8263f2` (até 19/09): Google Play Billing **real**, álbum
   de figurinhas, ritual da hora de dormir, sons da página, memória do companheiro. 35 arquivos em comum com esta linha.
3. Juntar `feature/trilha-da-leitura` no `main`: ainda não pedido.

---

## 2. Próximo passo combinado

O usuário escolheu, em ordem: (1) commit + GitHub — **feito em 24/09**; (2) **servidor do app no Firebase** (Cloud
Functions no mesmo projeto do Google Cloud, App Check com Play Integrity, login anônimo, limite de gasto diário no
código e o orçamento com teto de gasto do Google Cloud); (3) juntar a outra linha. Depois: as 55 figuras da trilha e
amostras gravadas dos 4 narradores. Ele comparou Supabase e Firebase e ficou com o **Firebase** (sem custo fixo, sem
chave secreta no servidor, mesma conta do Google).

---

## 3. Vozes (o que mudou em 23–24/09) — leia o HANDOFF §4, itens 5, 9, 10 e 11

- O usuário **recusou** a voz do aparelho ("robótica, tipo Google Maps") e todas as vozes abertas testadas
  (Supertonic, Kokoro, Piper, Qwen3-TTS, Chatterbox). Ele quer as vozes do **Gemini**: Capitão = **Puck**,
  Ursinho = **Algieba**. Pais **nunca** criam chave de API.
- **Trilha:** as 370 falas fixas (instruções, letras, sílabas, palavras, frases) foram **gravadas** com o Puck e estão
  em `app/src/main/assets/voz` (5,9 MB), com o índice `indice.json`. Gravadas pelo Google Cloud (Vertex AI / Agent
  Platform, `gemini-3.1-flash-tts-preview`), que é o caminho que pode ir para a loja. Script: `ferramentas/gerar_audios.py`.
- **Histórias:** com a chave do Gemini, a página inteira é narrada em **streaming** (som em ~1,3 s, antes 9 a 12 s ou
  voz robótica). Sem chave (versão da loja), ainda cai na voz do aparelho: é isso que o servidor vai resolver.
- **Área dos Pais:** virou "Narração e ilustrações", sem chaves; chaves e modelos só numa seção "Desenvolvedor" da
  versão de teste.
- **Google Cloud:** projeto "Livro Vivo" (ID no `local.properties`), APIs Cloud Text-to-Speech e Agent Platform
  ativadas; login no PC pelo Google Cloud CLI (`gcloud auth application-default login`). O usuário **liga o
  faturamento só quando precisa** e tem um alerta de orçamento.
- As vozes do Gemini no Google Cloud **não aceitam chave de API** (só login OAuth). A API do AI Studio (chave) proíbe
  apps usados por menores de 18: só para testes.

---

## 4. Decisões já tomadas (não refazer sem falar com o usuário)

- Nome do modo: **"Aprender a ler"**. Trilha **escondida para 9+**, com chave nos pais.
- Conteúdo da trilha **nunca** no Kotlin (`trilha.json`, gerado por `gerar_conteudo.py`).
- Companheiro só entra no livro quando a criança já lê o nome (LUNA, PIPOCA); Bento e Aurora nunca.
- **Sem assinatura:** módulo seguinte abre quando as fases que a criança **pode jogar** estão feitas; 1 livro offline
  por módulo. **Nunca tirar o que a criança ganhou.**
- Fase paga tocada pela criança: "Chame um adulto" (sem preço/link); a assinatura só depois do portão parental.
- Voz: letras pelo **nome** ("B" → "bê"); sílabas com vogal marcada ("BA" → "bá"). Na gravação, a letra vai como
  letra ("F"): escrito "éfe", a voz soletrava o acento.
- Erro de ilustração nunca aparece para a criança. Painel dos pais: números de **uso**, sem prometer resultado.
- Sons da trilha: cada tela para só o próprio som (antes, a instrução era cortada no "Toque..." ao entrar na fase).

---

## 5. Testes, emulador e APK

```bash
# JDK: C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot
./gradlew testDebugUnitTest      # 180 testes (um deles confere que as 370 falas têm áudio)
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk (~35 MB)
```

- **Emulador:** nunca instalar, tocar ou rodar testes no emulador do usuário (`Medium_Phone_API_36.1`, porta 5554).
  Use o AVD **`LivroVivo_Teste`** (porta 5582, sem som: sons só pelo log, tags `LivroVivoSom` e `LivroVivoPerf`).
  O usuário testa num **celular de verdade** e manda vídeos das telas.
- Neste ambiente, comandos Bash com **crases**, **emojis** ou barras invertidas dentro de heredoc se estragam: grave scripts em
  arquivo (ferramenta de escrita) e só execute no terminal.
- Ferramentas fora do repositório, na pasta temporária do Windows (`%TEMP%\lvtts2`)
  (ambiente `qwen` com PyTorch/CUDA na RTX 3060; `transcrever.py` e `conferir.py` usam o Whisper para conferir falas).

---

## 6. O que falta

1. **Servidor no Firebase** (próximo passo): narração em streaming e histórias/ilustrações com IA sem chave no app,
   limite de gasto no código e teto de gasto no Google Cloud.
2. Juntar a branch `claude/projeto-conforme-md-8263f2` (Billing real, álbum, ritual, sons).
3. Regras da loja para apps infantis (Política Famílias, LGPD, aviso de conteúdo gerado por IA).
4. As 55 figuras da trilha (`ferramentas/lista_imagens.txt`); amostras gravadas dos 4 narradores; comportamento sem
   internet (sugestão: tocar o que já foi narrado e avisar os pais para lerem junto).
5. Custo por história (~US$ 0,10–0,20 com texto, voz e ilustrações) x preço da assinatura; nome do modo e do app na loja.
6. A tela de criar história ainda diz "Precisa de IA configurada": muda junto com o servidor.

---

## 7. Texto pronto para colar no novo chat

> Estou continuando o app Livro Vivo (Android/Kotlin/Compose), na pasta principal do projeto.
> A linha atual é a branch `feature/trilha-da-leitura` (também no GitHub). Antes de tudo, leia
> `git show feature/trilha-da-leitura:NOVO_CHAT.md` e depois `git show feature/trilha-da-leitura:HANDOFF.md`.
> Quero seguir com: [ex.: o servidor no Firebase].
