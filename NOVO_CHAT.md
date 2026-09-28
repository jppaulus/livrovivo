# Livro Vivo — continuar em um novo chat

> Atualizado em 28/09/2026: vozes e ilustrações da Microsoft, plano grátis limitado e o **servidor do app no Supabase**
> (HANDOFF §4, itens 12 e 13). O chat de 23/09 implementou a **Trilha da Leitura** (`ALFABETIZACAO.md`, etapas 0 a 8).
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

O usuário escolheu, em ordem: (1) commit + GitHub — **feito em 24/09** (histórico reescrito em 25/09, sem o e-mail
pessoal); (2) **servidor do app** — **no ar desde 28/09** no Supabase (não no Firebase: o Google proíbe IA generativa
em apps infantis, §3): voz e ilustrações da Microsoft já passam por ele, com os limites dos planos e teto de US$ 5/dia.
**Texto das histórias: fornecedor NÃO decidido** — o usuário ainda não previu esse custo (28/09). O servidor está
pronto para o Claude, mas sem chave não gasta nada; não pedir conta nem créditos até ele decidir. Antes da loja: Play
Integrity e a compra conferida no servidor. (3) Termos de uso, privacidade (LGPD) e aviso de IA. (4) Juntar a outra linha. Depois: as 55 figuras da
trilha. Regra: toda conta nova de serviço pago precisa de limite de gasto, e o custo estimado vem antes de gravar.
⚠️ A avaliação gratuita da Azure termina em **25/10/2026**.

---

## 3. Vozes (o que mudou em 23–25/09) — leia o HANDOFF §4, itens 5, 9, 10, 11 e **12**

- **25/09: os termos do Google Cloud proíbem IA generativa em apps usados por menores de 18** (HANDOFF §4 item 12).
  Gemini (texto, imagens, vozes) só em testes. Na loja, a voz é da **Microsoft Azure** (conta de avaliação gratuita,
  recurso `livro-vivo-voz`, chave `azure.speechKey` no `local.properties`).
- O usuário **recusou** a voz do aparelho ("robótica, tipo Google Maps") e todas as vozes abertas testadas
  (Supertonic, Kokoro, Piper, Qwen3-TTS, Chatterbox). Pais **nunca** criam chave de API.
- **Narradores (Azure, escolhidos de ouvido em 25/09):** Capitão = `pt-BR-Macerio:DragonHDLatestNeural`, Ursinho =
  `pt-BR-ValerioNeural`, Vovó = `pt-BR-ThalitaMultilingualNeural`, Fada = `pt-BR-LeticiaNeural` (`VoicePersona.azureVoice`).
  Na versão de teste, as histórias já são narradas por eles em streaming (chave `azure.speechKey` do
  `local.properties` dentro do APK de teste: não compartilhe). Primeiro som em 0,35 a 1 s no emulador. A escolha do
  narrador na Área dos Pais toca a saudação gravada de cada um (`assets/voz/narradores`, `gravar_narradores.py`).
  A versão da loja ainda não tem a chave: precisa do servidor.
- **Ilustrações (26/09):** FLUX.2 pro pela Microsoft Foundry (HANDOFF §4 item 12), só com o texto. Plano grátis:
  ilustração de IA só na capa; assinante: todas as páginas (o usuário decidiu que o grátis tem gasto limitado e o
  pago pode ter custo). Gemini só nos testes.
- **Trilha:** as 370 falas fixas (instruções, letras, sílabas, palavras, frases) estão em `app/src/main/assets/voz`,
  com o índice `indice.json`. Em 25/09 foram **regravadas com a Azure**: frases com o Macerio HD, letras/sílabas/palavras
  com o `pt-BR-MacerioMultilingualNeural` (a HD inventa palavras em falas curtas). Script: `ferramentas/gerar_audios.py`.
- **Histórias:** a página inteira é narrada em **streaming** pela Microsoft, pelo servidor do app (primeiro som em
  0,6 a 0,9 s). O texto com IA ainda não tem fornecedor para a loja (custo não previsto): só a versão de teste escreve
  (Gemini); sem IA, o app usa a "Coleção pronta".
- **Área dos Pais:** virou "Narração e ilustrações", sem chaves; chaves e modelos só numa seção "Desenvolvedor" da
  versão de teste.
- **Google Cloud:** projeto "Livro Vivo" (ID no `local.properties`), APIs Cloud Text-to-Speech e Agent Platform
  ativadas; login no PC pelo Google Cloud CLI (`gcloud auth application-default login`). O usuário **liga o
  faturamento só quando precisa** e tem um alerta de orçamento.
- As vozes do Gemini no Google Cloud **não aceitam chave de API** (só login OAuth). Tanto o AI Studio quanto o Google
  Cloud proíbem apps usados por menores de 18: só para testes.

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
./gradlew testDebugUnitTest      # 201 testes (um deles confere que as 370 falas têm áudio)
npx -y deno@2 test supabase/functions/ai-gateway/regras_test.ts   # 10 testes do servidor
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

1. **Servidor do app:** no ar (voz e imagens). Faltam decidir o fornecedor (e o custo) do texto, Play Integrity e a
   compra da Google Play conferida no servidor (hoje o assinante é marcado à mão em `lv_contas`).
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
