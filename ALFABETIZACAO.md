# Livro Vivo — Trilha da Leitura (modo "Aprender a ler")

> Especificação de integração para o Claude Code. Criada em 22/09/2026.
> Leia junto com o `HANDOFF.md`, que tem a arquitetura e as decisões que **não** devem ser refeitas.

---

## 0. Antes de começar (obrigatório)

1. **Fazer o commit das mudanças pendentes.** Hoje há cerca de 37 arquivos modificados e 3 novos que ainda não foram commitados (seção 5 do `HANDOFF.md`). Commite tudo antes da primeira linha desta integração, para ter um ponto seguro de volta.
2. Rodar `./gradlew testDebugUnitTest`: todos os testes atuais precisam continuar passando.
3. Criar a branch `feature/trilha-da-leitura`.

**Sobre o desenvolvedor:** está começando em programação. Entregue sempre **arquivos completos** (nunca "adicione isto em algum lugar"), explique em português o que mudou e como testar, e faça uma etapa por vez: cada etapa precisa compilar, passar nos testes e rodar no emulador antes da próxima.

---

## 1. O que muda no produto

Hoje o Livro Vivo **lê histórias para a criança**. Com a Trilha da Leitura, ele passa também a **ensinar a criança a ler as próprias histórias**.

| Parte | O que faz |
| --- | --- |
| **Trilha da Leitura** | 5 módulos com 51 fases e 255 perguntas: vogais, consoantes, sílabas, primeiras palavras e ditado. O conteúdo já está pronto em `trilha.json` |
| **Livros "Eu leio"** (o diferencial) | Livros curtos gerados **só com as sílabas que a criança já aprendeu**, com ela como personagem. Ela lê sozinha, e o narrador ajuda palavra por palavra |
| **Painel dos pais** | Nova seção de leitura: letras e sílabas aprendidas, livros lidos e fases com mais dificuldade |

O ciclo que prende a criança:

```
Fase da trilha → aprende sílabas novas → desbloqueia um livro "Eu leio" → lê sozinha → volta à trilha
```

**Público:** a trilha aparece para as faixas `3-5` e `6-8`. Para `9+`, ela fica escondida (dá para ativar nas configurações dos pais).

**O que NÃO muda:** as histórias interativas atuais, os narradores, as escolhas com virtudes, as ilustrações, o modo offline e todas as decisões da seção 4 do `HANDOFF.md`.

---

## 2. Conteúdo da trilha

- **Arquivo:** `app/src/main/assets/alfabetizacao/trilha.json`, gerado por `ferramentas/gerar_conteudo.py` (Python 3, sem dependências). O script valida tudo antes de salvar.
- **Regra:** o app só **lê** esse JSON. Não coloque conteúdo da trilha fixo no código Kotlin.

### Estrutura

```json
{
  "versao": 1,
  "palavras_de_apoio": ["O", "A", "E", "É", "UM", "UMA", "DE", "NO", "NA", "TEM", "ESTÁ", "..."],
  "vocabulario": [
    { "palavra": "BOLA", "silabas": ["BO", "LA"], "imagem": "img_bola", "artigo": "A" }
  ],
  "modulos": [
    {
      "id": "silabas", "titulo": "Sílabas", "ordem": 3,
      "fases": [
        {
          "id": "silabas_b", "titulo": "Sílabas com B", "gratis": true,
          "ensina": ["BA", "BE", "BI", "BO", "BU"],
          "perguntas": [
            { "tipo": "juntar", "fala": "Junte as letras e forme BA", "resposta": "BA",
              "opcoes": [], "pecas": ["B", "A"], "imagem": null }
          ]
        }
      ]
    }
  ]
}
```

| Campo | Significado |
| --- | --- |
| `palavras_de_apoio` | Palavrinhas que a criança aprende "de vista". Podem aparecer nos livros "Eu leio" desde o início |
| `vocabulario` | As 50 palavras de 2 sílabas, com sílabas, figura e artigo (O/A) |
| `fase.ensina` | O que a criança aprende ao concluir a fase (uma letra, 5 sílabas ou 5 palavras). É a base dos livros "Eu leio" |
| `fase.gratis` | Se a fase é liberada sem assinatura |
| `pergunta.tipo` | `ouvir_tocar`, `juntar`, `montar_palavra`, `figura_palavra` ou `ditado` |
| `pergunta.fala` | Frase narrada |
| `pergunta.resposta` | Resposta certa |
| `pergunta.opcoes` | Botões para tocar (já embaralhados, incluem a resposta) |
| `pergunta.pecas` | Blocos para arrastar. No ditado, há 2 peças extras erradas |
| `pergunta.imagem` | Nome do drawable, ou `null` |

**Figuras:** são 55, com nomes em `ferramentas/lista_imagens.txt`. Enquanto elas não existirem, mostre um cartão com a palavra escrita, sem deixar o app quebrar. Se as figuras forem geradas, siga o mesmo estilo guache dos cenários (seção "Arte gerada" do `DIRECAO_EDITORIAL.md`) e salve em `res/drawable-nodpi/` como JPEG.

---

## 3. Dados (Room 4 → 5)

### Nova tabela `literacy_progress`

| Coluna | Tipo | Observação |
| --- | --- | --- |
| `childId` | String | Chave primária composta com `phaseId` |
| `phaseId` | String | Ex.: `silabas_b` |
| `stars` | Int | 0 a 3; guardar sempre a **melhor** nota |
| `attempts` | Int | Quantas vezes a fase foi jogada |
| `mistakes` | Int | Total de erros (para o painel dos pais) |
| `completedAt` | Long? | Data da primeira conclusão com pelo menos 1 estrela |

### Nova coluna em `stories`

- `kind: String`, com default `"aventura"`. Os livros da trilha usam `"eu_leio"`.

### Migração

- Criar `MIGRATION_4_5` no mesmo padrão de `MIGRATION_3_4`: criar a tabela e fazer `ALTER TABLE stories ADD COLUMN kind TEXT NOT NULL DEFAULT 'aventura'`.
- **Preservar todas as histórias e sessões existentes.**
- Criar um teste instrumentado no padrão do `FamilyStorageTest`, verificando que as histórias da versão 4 continuam lá depois da migração.

### O que a criança sabe

Não crie uma tabela para isso: calcule a partir das fases concluídas.

```kotlin
data class LiteracyKnowledge(
    val letters: Set<String>,     // vogais + consoantes concluídas
    val syllables: Set<String>,   // união do "ensina" das fases de sílabas concluídas
    val words: Set<String>        // união do "ensina" das fases de palavras concluídas
)
```

**Regra de desbloqueio:** um módulo só libera quando todas as fases do módulo anterior tiverem pelo menos 1 estrela. Dentro do módulo, as fases liberam em ordem.

**Estrelas:** 5 acertos = 3 estrelas; 3 ou 4 acertos = 2 estrelas; 1 ou 2 acertos = 1 estrela; 0 acertos = 0 estrelas.

---

## 4. Livros "Eu leio" (o coração da integração)

### 4.1. Regra do texto decodificável

Uma palavra pode aparecer num livro "Eu leio" se for **uma** destas opções:

1. estar em `palavras_de_apoio`;
2. ser o **nome da criança** (o narrador lê o nome, e ele aparece destacado como "palavra especial");
3. ser decodificável: ter tamanho par e, dividida em pedaços de 2 letras, **cada pedaço estar em `knowledge.syllables`**. Exemplo: BOLA → BO + LA.

Regras de formato do texto:

- Tudo em **letra maiúscula** (letra de forma).
- Pontuação permitida: `. , ! ?`
- Frases de 3 a 7 palavras.
- Páginas com 1 ou 2 frases.
- 4 páginas por livro.

Implemente em `core/literacy/DecodableValidator.kt`:

```kotlin
object DecodableValidator {
    /** Palavras do texto que a criança ainda não consegue ler (lista vazia = texto válido). */
    fun invalidWords(text: String, knowledge: LiteracyKnowledge, supportWords: Set<String>, childName: String): List<String>
    fun isDecodable(word: String, syllables: Set<String>): Boolean
}
```

O validador precisa de testes unitários completos, cobrindo pelo menos:

- palavras com acento nas palavras de apoio (`É`, `NÃO`, `ESTÁ`);
- nomes com acento (`JOÃO`);
- palavra com número ímpar de letras;
- sílaba que a criança ainda não aprendeu;
- pontuação colada na palavra.

### 4.2. Quando um livro é desbloqueado

- O primeiro livro libera quando a criança concluir **3 fases do módulo Sílabas**. Com B, C e D já dá para montar palavras como BOCA, DADO e CABO.
- Depois disso, libera um novo livro a cada 2 fases concluídas (de sílabas, palavras ou ditado).
- A tela mostra um aviso: "Você ganhou um livro novo para ler sozinho!".

### 4.3. Geração do livro

O `LiteracyBookWriter` segue a mesma ideia do `StoryWriter` (IA quando possível, offline como garantia):

1. **Com IA:** use um prompt novo em `StoryPrompts` (`decodablePrompt`), reaproveitando o `SYSTEM_PROMPT` de segurança infantil. O prompt manda:
    - usar **somente** as palavras de uma lista fechada: palavras de apoio + palavras decodificáveis do `vocabulario` + o nome da criança + o companheiro, só se o nome dele for decodificável;
    - escrever 4 páginas e o título;
    - incluir o `illustrationPrompt` em inglês, como já é feito hoje.

    Pedir a lista fechada é mais confiável do que pedir para a IA "usar só estas sílabas".
2. **Validar** cada página com o `DecodableValidator`. Se houver palavras inválidas, tentar **uma vez** de novo, informando à IA quais palavras não podem ser usadas.
3. **Se falhar de novo, ou se estiver sem IA:** usar o `OfflineDecodableEngine`, que monta frases com modelos fixos e as palavras do `vocabulario` que a criança já consegue ler (usando o campo `artigo`). Exemplos:
    - `{ART} {P1} É DE {NOME}.` → "A BOLA É DE LIA."
    - `{ART} {P1} ESTÁ {NO/NA} {P2}.` → "O GATO ESTÁ NA CAMA."
    - `{NOME} TEM {UM/UMA} {P1}.` → "LIA TEM UMA PIPA."
    - `{ART} {P1} E {ART2} {P2}.` → "O PATO E A VACA."

    O motor offline **nunca** pode gerar texto inválido: passe a saída pelo validador nos testes.
4. **Salvar** como `Story` com `kind = "eu_leio"`, `isOffline` conforme a origem e `plannedChapters = 4`, sem escolhas (`choices` vazio) e com a última página como `isEnding = true`.
5. **Ilustração:** reaproveitar o `IllustrationService` quando a IA estiver ativa. No modo offline, usar a figura do `vocabulario` da palavra principal da página.

Os livros "Eu leio" **não contam na cota de histórias grátis** (`storiesCreated`): eles fazem parte da trilha.

### 4.4. Leitor no modo "Eu leio"

Reaproveite o `ReaderScreen` com um parâmetro de modo, ou crie um `EasyReaderScreen` que use os mesmos componentes.

- **Sem narração automática.** A criança tenta ler sozinha.
- **Tocar numa palavra:** o narrador lê a palavra e a destaca, usando o destaque em estilo karaokê que já existe.
- **Tocar e segurar uma palavra:** o app separa a palavra em sílabas (BO · LA), lendo cada sílaba e depois a palavra inteira.
- **Botão "Ouvir a página":** narra a página inteira, como no modo normal, para a criança conferir.
- **Botão "Li sozinho!" no fim de cada página:** conta para as conquistas. Não é uma avaliação, só um registro.
- **No final:** comemoração (confete, igual ao das histórias) e a pergunta "Quer ler de novo?".

---

## 5. Telas novas

Crie o pacote `presentation/literacy/`:

| Tela / arquivo | O que mostra |
| --- | --- |
| `TrailScreen` | Mapa com os 5 módulos em caminho ilustrado, cadeado nos bloqueados e estrelas por módulo |
| `PhaseListScreen` | Fases do módulo com estrelas, selo "Assinantes" nas fases pagas e os livros "Eu leio" desbloqueados |
| `ActivityScreen` | As 5 perguntas da fase, com um Composable por tipo em `presentation/literacy/activity/` |
| `PhaseResultScreen` | Estrelas, comemoração e "Próxima fase"; mostra o aviso de livro novo quando for o caso |
| `LiteracyViewModel` | Carrega o `trilha.json`, o progresso e o `LiteracyKnowledge` |

### Navegação

Adicione estas rotas em `Screen`:

- `literacy_trail`
- `literacy_phases/{moduleId}`
- `literacy_activity/{phaseId}`
- `literacy_result/{phaseId}/{stars}`

### Home

Ao lado da estante, entra um cartão grande "**Aprender a ler**" com o progresso ("12 de 51 fases"). Os livros "Eu leio" aparecem na estante com o selo "Eu li!".

### Regras das atividades

- Toda instrução é narrada.
- Botões com no mínimo 64dp.
- Nas atividades de arrastar, tocar numa peça também leva ela para o próximo espaço vazio.
- O erro não pune: toca um som suave e diz "Tente de novo!".
- Nenhuma cobrança ou link aparece durante a atividade. A assinatura só aparece atrás do `ParentalGateDialog` que já existe.

---

## 6. Voz das letras e sílabas

Os motores de voz (`NarrationEngines.kt`) funcionam bem com frases, mas **letras e sílabas soltas saem erradas**: "B" vira "bê", "BA" vira "b-a".

Solução em duas camadas:

1. **Áudios prontos no app:** 18 letras + 65 sílabas = 83 arquivos em `res/raw/` (`som_a`, `som_ba` …). Eles são gerados **uma vez, no desenvolvimento**, com o ElevenLabs ou o Gemini TTS, usando o narrador Capitão Aventura. O ideal é um script em `ferramentas/` que chama a API e salva os arquivos. As chaves ficam no `local.properties` e nunca vão para o repositório.
2. **Fallback:** se o arquivo não existir, usar o motor de voz atual com a palavra ou a frase completa (por exemplo, "BA, de BALA").

Crie o `core/literacy/SoundPlayer.kt`, que toca `som_xx` quando o arquivo existe e usa a voz do app quando não existe. Assim a trilha funciona desde o primeiro dia, mesmo antes de existirem os áudios.

---

## 7. Assinatura (premium)

O modelo continua o atual, sem anúncios, e usa o `BillingRepository` que já existe.

| Recurso | Grátis | Assinante |
| --- | --- | --- |
| Módulos 1 e 2 (vogais e consoantes) | Tudo | Tudo |
| Módulos 3 a 5 | Fases com `"gratis": true` (2 por módulo) | Tudo |
| Livros "Eu leio" offline | 1 por módulo | Ilimitados |
| Livros "Eu leio" com IA e ilustração | — | Ilimitados |

Na `PaywallScreen`, acrescente o argumento de venda: "Seu filho aprende a ler com histórias em que ele é o personagem".

---

## 8. Painel dos pais

Nova seção **"Leitura"** no `ParentDashboardScreen`:

- letras e sílabas aprendidas (grade com as 65 sílabas, destacando as que já foram concluídas);
- livros "Eu leio" lidos e páginas marcadas como "Li sozinho";
- as 3 fases com mais erros ("Vale praticar juntos: sílabas com R");
- sugestão de atividade fora da tela, por exemplo: "Procure na cozinha objetos que começam com P".

Use o mesmo cuidado do `MELHORIAS_UX.md`: os números mostram **atividade de uso**, não uma medida de aprendizagem. O texto do painel não deve prometer resultado.

---

## 9. Ordem de implementação

| Etapa | Entrega | Pronto quando |
| --- | --- | --- |
| 1. Conteúdo e dados | `trilha.json` em assets, modelos + parser, `LiteracyRepository`, migração 4→5 | Testes do parser e da migração passam; as histórias antigas continuam lá |
| 2. Trilha jogável | `TrailScreen`, `PhaseListScreen`, `ActivityScreen` com os 5 tipos, `PhaseResultScreen`, cartão na Home | Uma fase inteira joga do início ao fim no emulador |
| 3. Progresso | Estrelas, desbloqueio, `LiteracyKnowledge` | O progresso continua depois de fechar o app; os módulos liberam em ordem |
| 4. Validador + livro offline | `DecodableValidator`, `OfflineDecodableEngine` + testes | Testes provam que o motor offline só gera texto válido |
| 5. Leitor "Eu leio" | Leitor com toque na palavra, sílabas, "Li sozinho!" | Um livro offline é lido do início ao fim |
| 6. Livro com IA | `decodablePrompt`, `LiteracyBookWriter`, tentar de novo + fallback | Com a chave configurada, gera um livro válido; sem a chave, cai no offline |
| 7. Voz das sílabas | `SoundPlayer` + script que gera os áudios | Letras e sílabas soam certas |
| 8. Pais e assinatura | Seção "Leitura" no painel, regras premium, textos do paywall | Fases pagas bloqueadas para quem não assina |

Ao final de cada etapa: rode `./gradlew testDebugUnitTest assembleDebug`, atualize o `HANDOFF.md` e sugira a mensagem de commit.

---

## 10. Decisões em aberto (perguntar ao usuário)

- Nome do modo: "Aprender a ler", "Trilha da Leitura" ou outro.
- Mudar o nome do app na loja para algo como "Livro Vivo: histórias e alfabetização".
- Se o companheiro mágico aparece nos livros "Eu leio". Luna (LU-NA) e Pipoca (PI-PO-CA) são decodificáveis; Bento e Aurora, não.
- Estilo e origem das 55 figuras do vocabulário.
- Se a trilha também aparece para a faixa 9+.
