package com.livrovivo.app.core.ai

import com.livrovivo.app.domain.model.AdventureMemory
import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildGender
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.SceneKind
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.ThemeOption
import com.livrovivo.app.domain.model.Virtue

/**
 * Histórias escritas à mão para quando não há IA configurada (ou sem internet).
 * Cada tema tem começo, desafios, clímax e final; o texto reage à escolha e às virtudes da criança.
 */
object OfflineStoryEngine {

    data class Opening(val title: String, val chapter: Chapter, val plannedChapters: Int)

    fun plannedChapters(ageGroup: AgeGroup): Int = if (ageGroup == AgeGroup.TODDLER) 4 else 5

    fun opening(brief: StoryBrief, themeId: String?): Opening {
        val ctx = Ctx(brief)
        val world = worldFor(themeId, brief.theme)
        val planned = plannedChapters(brief.ageGroup)
        val remembrance = brief.memories.firstOrNull()?.let { memoryLine(ctx, it) }
        val chapter = Chapter(
            index = 1,
            content = listOfNotNull(world.opening(ctx), remembrance).joinToString("\n\n"),
            choices = world.openingChoices(ctx).map { (text, virtue) -> Choice(text, 2, virtue) },
            isEnding = false,
            mood = world.mood
        )
        return Opening(world.title(ctx), chapter, planned)
    }

    /**
     * O companheiro lembra de uma escolha de outra aventura. Fica no fim do capítulo 1, logo
     * antes das escolhas, porque em todos os temas é o momento de decidir — e lembrar de uma
     * boa escolha anterior encoraja a próxima.
     */
    private fun memoryLine(x: Ctx, memory: AdventureMemory): String? {
        val choice = memory.highlightAsClause() ?: return null
        return if (memory.companionId == x.companion.id) {
            "— Lembra quando você escolheu $choice? — perguntou ${x.c}, ${x.cg("todo orgulhoso", "toda orgulhosa")}. " +
                "— Tenho certeza de que hoje você vai saber o que fazer de novo!"
        } else {
            val other = MagicalCompanion.findById(memory.companionId).name
            "— Sabia que $other me contou de quando você escolheu $choice? — disse ${x.c}, " +
                "${x.cg("todo animado", "toda animada")}. — Hoje eu quero ver isso de pertinho!"
        }
    }

    fun continuation(brief: StoryBrief, themeId: String?, story: Story, choice: Choice): Chapter {
        val ctx = Ctx(brief)
        val world = worldFor(themeId, brief.theme)
        val nextIndex = (story.lastChapter?.index ?: 1) + 1
        val planned = minOf(story.plannedChapters, 5).coerceAtLeast(3)
        val intro = reaction(ctx, choice)

        if (nextIndex >= planned) {
            val virtues = (story.chosenVirtues + listOfNotNull(choice.virtue)).distinct()
            return Chapter(
                index = nextIndex,
                content = "$intro\n\n${world.ending(ctx, virtueSummary(virtues))}",
                isEnding = true,
                mood = "aconchegante"
            )
        }

        val scene = if (nextIndex == planned - 1) {
            world.scenes.last()
        } else {
            world.scenes[(nextIndex - 2).coerceIn(0, world.scenes.size - 2)]
        }
        return Chapter(
            index = nextIndex,
            content = "$intro\n\n${scene.text(ctx)}",
            choices = scene.choices(ctx).map { (text, virtue) -> Choice(text, nextIndex + 1, virtue) },
            isEnding = false,
            mood = world.mood
        )
    }

    /** Escolhas genéricas usadas se a IA devolver menos de duas opções. */
    fun fallbackChoices(companion: MagicalCompanion, nextIndex: Int): List<Choice> = listOf(
        Choice("Seguir em frente com coragem", nextIndex, Virtue.CORAGEM),
        Choice("Pedir ajuda a ${companion.name} e agir juntos", nextIndex, Virtue.COOPERACAO)
    )

    fun virtueSummary(virtues: List<Virtue>): String {
        if (virtues.isEmpty()) return "Suas escolhas deixaram tudo mais bonito."
        val phrases = virtues.distinct().map {
            when (it) {
                Virtue.CORAGEM -> "muita coragem"
                Virtue.EMPATIA -> "um coração enorme"
                Virtue.CRIATIVIDADE -> "ideias criativas"
                Virtue.CURIOSIDADE -> "uma curiosidade brilhante"
                Virtue.CALMA -> "a calma de quem é sábio"
                Virtue.COOPERACAO -> "que juntos somos mais fortes"
            }
        }
        val joined = if (phrases.size == 1) phrases.first() else phrases.dropLast(1).joinToString(", ") + " e " + phrases.last()
        return "Você mostrou $joined."
    }

    // ---------------------------------------------------------------------------------------------

    private class Ctx(brief: StoryBrief) {
        val name: String = StoryPrompts.sanitizeInput(brief.child.name, 40).ifBlank { "Pequeno Leitor" }
        val companion: MagicalCompanion = brief.companion
        val gender: ChildGender = brief.child.gender
        val theme: String = StoryPrompts.sanitizeInput(brief.theme, 80)
        val fav: String = interestPhrase(brief.child.interests.firstOrNull())
        val c: String get() = companion.name
        fun g(masculine: String, feminine: String, neutral: String = masculine) = gender.pick(masculine, feminine, neutral)
        fun cg(masculine: String, feminine: String) = companion.pick(masculine, feminine)
    }

    private class Scene(
        val text: (Ctx) -> String,
        val choices: (Ctx) -> List<Pair<String, Virtue>>
    )

    private class World(
        val mood: String,
        val title: (Ctx) -> String,
        val opening: (Ctx) -> String,
        val openingChoices: (Ctx) -> List<Pair<String, Virtue>>,
        val scenes: List<Scene>,
        val ending: (Ctx, String) -> String
    )

    private fun interestPhrase(key: String?): String = when (key?.lowercase()) {
        "dinossauros" -> "dinossauros"
        "espaço", "espaco" -> "o espaço"
        "animais" -> "os animais"
        "fadas" -> "fadas e magia"
        "carros" -> "carros e trens"
        "natureza" -> "a natureza"
        "super-heróis", "super-herois" -> "super-heróis"
        "música", "musica" -> "música"
        "mar" -> "o fundo do mar"
        "robôs", "robos" -> "robôs"
        "princesas e castelos" -> "castelos encantados"
        "esportes" -> "esportes"
        null, "" -> "aventuras"
        else -> key
    }

    private fun reaction(ctx: Ctx, choice: Choice): String {
        val action = choice.text.trim().trimEnd('.', '!').replaceFirstChar { it.lowercase() }
        val c = ctx.c
        val line = when (choice.virtue) {
            Virtue.CORAGEM -> "— Que coragem bonita! — comemorou $c, dando um pulinho no ar."
            Virtue.EMPATIA -> "— Você tem um coração do tamanho do mundo — disse $c, ${ctx.cg("emocionado", "emocionada")}."
            Virtue.CRIATIVIDADE -> "— Que ideia genial! Eu nunca teria pensado nisso! — riu $c."
            Virtue.CURIOSIDADE -> "— Adoro descobrir coisas novas com você! — disse $c, ${ctx.cg("curioso", "curiosa")}."
            Virtue.CALMA -> "— Respirar fundo deixa tudo mais claro, não é? — disse $c baixinho."
            Virtue.COOPERACAO -> "— Juntos a gente vai muito mais longe! — disse $c, ${ctx.cg("animado", "animada")}."
            null -> "— Vamos nessa! — disse $c, ${ctx.cg("animado", "animada")}."
        }
        return "${ctx.name} decidiu $action.\n\n$line"
    }

    private fun worldFor(themeId: String?, theme: String): World = when (ThemeOption.findById(themeId)?.scene ?: ThemeOption.sceneFor(themeId, theme)) {
        SceneKind.NIGHT -> nightWorld
        SceneKind.SCHOOL -> schoolWorld
        SceneKind.SPACE -> spaceWorld
        SceneKind.PARTY -> toysWorld
        SceneKind.HOME -> babyWorld
        SceneKind.OCEAN -> genericWorld(ocean)
        SceneKind.DINOSAURS -> genericWorld(dinosaurs)
        SceneKind.FOREST -> if (themeId == null || themeId == ThemeOption.CUSTOM_ID) genericWorld(custom) else genericWorld(forest)
    }

    // --- Hora de dormir ----------------------------------------------------------------------------

    private val nightWorld = World(
        mood = "sonolento",
        title = { "${it.name} e a Lanterna das Estrelas" },
        opening = { x ->
            """
A lua espiava pela janela e o quarto de ${x.name} estava quietinho, quietinho. Só o relógio fazia tic-tac, tic-tac.

De repente, uma sombra comprida apareceu na parede. ${x.name} puxou o cobertor até o nariz. Mas então uma luzinha dourada piscou: era ${x.c}, ${x.companion.title.replaceFirstChar { it.lowercase() }}!

— Psiu, ${x.name}! — sussurrou ${x.c}. — Aquela sombra não é monstro nenhum. Olha só: ela tem orelhinhas de coelho e está bocejando!

A sombra se espreguiçou e falou com uma voz macia de travesseiro:
— Oi! Eu sou Lumi, a sombra guardiã dos sonhos. Hoje as estrelas se apagaram, e eu preciso de ajuda para acendê-las de novo.
""".trim()
        },
        openingChoices = {
            listOf(
                "Acender a lanterna mágica e procurar as estrelas" to Virtue.CORAGEM,
                "Cantar uma canção de ninar para acalmar o céu" to Virtue.CALMA
            )
        },
        scenes = listOf(
            Scene(
                text = { x ->
                    """
Um caminho de poeira prateada surgiu no chão, atravessou a janela e levou todos até o Jardim do Luar. Lá, as flores eram feitas de luz e os vaga-lumes tocavam sininhos: plim, plim, plim.

No meio do jardim, uma estrelinha pequena chorava escondida atrás de uma folha.
— Eu me perdi e fiquei com medo do escuro — disse ela, tremendo.

Lumi olhou para ${x.name}, esperando uma boa ideia.
""".trim()
                },
                choices = {
                    listOf(
                        "Abraçar a estrelinha e dizer que está tudo bem" to Virtue.EMPATIA,
                        "Inventar um jogo de luz para ela sorrir" to Virtue.CRIATIVIDADE
                    )
                }
            ),
            Scene(
                text = { _ ->
                    """
A estrelinha brilhou um pouquinho mais forte e mostrou o caminho até a Ponte das Nuvens, macia como algodão-doce. Lá de cima dava para ver a cidade inteira dormindo.

Mas a ponte balançava devagar, fazendo fuuu, fuuu com o vento. Do outro lado, a Coruja Guardiã segurava a sacola onde as estrelas apagadas descansavam.
— Só atravessa quem vai com o coração tranquilo — piou ela.
""".trim()
                },
                choices = { x ->
                    listOf(
                        "Respirar fundo e contar até cinco" to Virtue.CALMA,
                        "Dar a mão para ${x.c} e atravessar juntinhos" to Virtue.COOPERACAO
                    )
                }
            ),
            Scene(
                text = { x ->
                    """
Do outro lado da ponte, o céu estava todo escuro. A Coruja Guardiã abriu a sacola, e dezenas de estrelas sonolentas rolaram para fora, bocejando.

— Elas só acendem quando alguém lembra de uma coisa boa — explicou Lumi. — Uma lembrança feliz vira luz!

${x.name} fechou os olhos e sentiu o coração quentinho. Era a hora de acender o céu inteiro.
""".trim()
                },
                choices = {
                    listOf(
                        "Lembrar do abraço mais gostoso da família" to Virtue.EMPATIA,
                        "Soprar as estrelas como velas de aniversário" to Virtue.CRIATIVIDADE
                    )
                }
            )
        ),
        ending = { x, virtues ->
            """
Uma a uma, as estrelas se acenderam: plim, plim, PLIM! O céu ficou tão bonito que até a lua deu uma risadinha.

— Você conseguiu, ${x.name}! — comemorou ${x.c}. — $virtues

Lumi voltou para a parede do quarto, agora uma sombra amiga que cuidaria dos sonhos todas as noites. O escuro não parecia mais assustador: era só o cobertor do céu, cheio de luzinhas guardadas.

${x.name} bocejou, abraçou o travesseiro e adormeceu sorrindo. Lá fora, uma estrelinha piscou bem forte, como quem diz: boa noite.
""".trim()
        }
    )

    // --- Escola ----------------------------------------------------------------------------------

    private val schoolWorld = World(
        mood = "alegre",
        title = { "${it.name} e o Mistério da Mochila Amarela" },
        opening = { x ->
            """
Era o primeiro dia de aula, e o portão da escola estava enfeitado com bandeirinhas coloridas. ${x.name} segurava a alça da mochila com força, sentindo um friozinho na barriga que parecia uma borboleta dando cambalhotas.

— Borboleta na barriga é sinal de coisa nova chegando — cochichou ${x.c}, ${x.cg("escondido", "escondida")} no bolso da mochila. — E coisa nova pode ser muito divertida!

No pátio, perto da grande árvore, uma mochila amarela estava sozinha no banco. De dentro dela saía um barulhinho: tlim, tlim. Um pouco mais longe, um colega olhava tudo de cabeça baixa, sem saber com quem brincar.
""".trim()
        },
        openingChoices = {
            listOf(
                "Chamar o colega para brincar junto" to Virtue.EMPATIA,
                "Descobrir o que faz tlim na mochila" to Virtue.CURIOSIDADE
            )
        },
        scenes = listOf(
            Scene(
                text = { _ ->
                    """
O colega se chamava Tito e tinha um sorriso banguela que apareceu na mesma hora. Juntos, descobriram que o tlim-tlim vinha de um chaveiro de sininho preso na mochila amarela, que era dele!

— Eu trouxe para dar sorte, porque estava com vergonha — contou Tito.

Na hora do recreio, a professora Dora anunciou uma gincana: cada equipe precisava montar alguma coisa com caixas, fitas e muita imaginação.
""".trim()
                },
                choices = {
                    listOf(
                        "Construir um foguete de caixas com Tito" to Virtue.CRIATIVIDADE,
                        "Convidar mais colegas para a equipe" to Virtue.COOPERACAO
                    )
                }
            ),
            Scene(
                text = { x ->
                    """
A construção ficou incrível: tinha janelas redondas, botões desenhados e até uma porta que abria de verdade. Mas, quando foram buscar as tintas, o pote azul virou: ploft! Uma poça azul se espalhou pelo chão da sala de artes.

Uma menina chamada Bia começou a chorar, porque a tinta respingou no desenho dela.

— Ai, ai, ai — fez ${x.c}. — E agora, o que a gente faz?
""".trim()
                },
                choices = {
                    listOf(
                        "Ajudar a Bia a salvar o desenho" to Virtue.EMPATIA,
                        "Transformar a poça em um lago pintado" to Virtue.CRIATIVIDADE
                    )
                }
            ),
            Scene(
                text = { x ->
                    """
No fim, o desenho da Bia ganhou um céu azul ainda mais bonito, e a turma inteira quis participar. Chegou então a hora mais esperada: mostrar a construção para toda a escola.

Só que, diante de tanta gente, a voz de Tito sumiu e as pernas dele ficaram bambas. Ele olhou para ${x.name}, pedindo ajuda com os olhos.
""".trim()
                },
                choices = {
                    listOf(
                        "Segurar a mão de Tito e falar juntos" to Virtue.COOPERACAO,
                        "Começar a apresentação com uma piada" to Virtue.CORAGEM
                    )
                }
            )
        ),
        ending = { x, virtues ->
            """
A apresentação foi um sucesso! A turma bateu palmas, a professora Dora deu uma estrela dourada para cada um, e Tito pulava de alegria.

— Hoje eu ganhei um amigo — disse ele, abraçando ${x.name}.

— $virtues — completou ${x.c}, ${x.cg("orgulhoso", "orgulhosa")}.

Na saída, a borboleta da barriga de ${x.name} continuava lá, mas agora dava cambalhotas de felicidade. A escola, que parecia tão grande de manhã, tinha virado um lugar cheio de amigos. E amanhã tinha mais!
""".trim()
        }
    )

    // --- Espaço ----------------------------------------------------------------------------------

    private val spaceWorld = World(
        mood = "aventura",
        title = { "${it.name} e o Foguete de Papel Dourado" },
        opening = { x ->
            """
Numa noite estrelada, ${x.name} dobrou uma folha dourada e fez um foguete de papel. Assim que ficou pronto, o foguete começou a brilhar e a crescer, crescer, crescer, até virar uma nave de verdade no meio do quintal!

— Contagem regressiva! — gritou ${x.c}, pulando de animação. — Dez, nove, oito...

Três, dois, um... VRUUUM! A nave subiu entre as nuvens e, em poucos segundos, a Terra virou uma bolinha azul lá embaixo. Pela janela, um cometa passou acenando com a cauda brilhante.

No painel da nave, uma luz piscava: era um pedido de ajuda vindo do Planeta Algodão.
""".trim()
        },
        openingChoices = {
            listOf(
                "Seguir o pedido de ajuda até o planeta" to Virtue.CORAGEM,
                "Estudar o mapa das estrelas primeiro" to Virtue.CURIOSIDADE
            )
        },
        scenes = listOf(
            Scene(
                text = { x ->
                    """
O Planeta Algodão era fofinho como um travesseiro gigante, e cada passo fazia fofe, fofe. Lá moravam os Pufes, bichinhos redondos que flutuavam feito bolhas de sabão.

— Nosso Cristal de Luz parou de brilhar — explicou o Pufe mais velhinho. — Sem ele, as plantinhas não crescem e ninguém consegue flutuar direito.

${x.c} apontou para uma trilha de pegadas brilhantes que seguia até uma cratera.
""".trim()
                },
                choices = {
                    listOf(
                        "Descer na cratera com a lanterna espacial" to Virtue.CORAGEM,
                        "Perguntar aos Pufes como o cristal funciona" to Virtue.CURIOSIDADE
                    )
                }
            ),
            Scene(
                text = { x ->
                    """
Dentro da cratera estava o Cristal de Luz, cercado por três botões: um vermelho, um azul e um amarelo. Em cima deles, uma mensagem antiga dizia que o cristal só acordava com a cor do céu da Terra num dia de sol.

— Hmm, que enigma! — disse ${x.c}, coçando a cabeça. — Qual será a cor certa?

Um Pufe pequenininho chamado Nino olhava tudo atrás de uma pedra, com vontade de ajudar, mas com vergonha de chegar perto.
""".trim()
                },
                choices = {
                    listOf(
                        "Apertar o botão azul com cuidado" to Virtue.CURIOSIDADE,
                        "Chamar o Nino para resolver o enigma junto" to Virtue.COOPERACAO
                    )
                }
            ),
            Scene(
                text = { _ ->
                    """
O cristal acordou com um brilho azul lindo, e o planeta inteiro fez: ooooh! Mas, bem nessa hora, começou a cair do céu uma chuva de bolinhas coloridas que quicavam para todo lado, deixando os Pufes menores atordoados.

— Elas não machucam, mas estão fazendo a maior bagunça! — disse Nino.
""".trim()
                },
                choices = {
                    listOf(
                        "Organizar os Pufes para pegar as bolinhas" to Virtue.COOPERACAO,
                        "Criar uma rede gigante de algodão" to Virtue.CRIATIVIDADE
                    )
                }
            )
        ),
        ending = { x, virtues ->
            """
No fim, as bolinhas viraram enfeites para as casas dos Pufes, e o Cristal de Luz brilhou mais forte do que nunca. As plantinhas cresceram na mesma hora, e todo mundo flutuou de alegria.

— $virtues — disse ${x.c}. — O universo inteiro vai ouvir falar dessa aventura!

Os Pufes deram de presente uma estrelinha de bolso que brilha sempre que alguém sente saudade. A nave voltou para casa devagarinho, e o foguete encolheu até virar de novo uma folha dourada.

Da janela do quarto, ${x.name} olhou para o céu e teve certeza de que uma estrela piscou, dizendo obrigado.
""".trim()
        }
    )

    // --- Brinquedos ------------------------------------------------------------------------------

    private val toysWorld = World(
        mood = "alegre",
        title = { "${it.name} e a Festa dos Brinquedos Encantados" },
        opening = { x ->
            """
Quando o relógio bateu meia-noite, os brinquedos do quarto de ${x.name} abriram os olhos. O ursinho se espreguiçou, os blocos se empilharam sozinhos e o trenzinho apitou: piuí, piuí!

— Hoje é a Grande Festa dos Brinquedos — anunciou ${x.c}, com um chapéu de festa na cabeça. — E você é ${x.g("o convidado especial", "a convidada especial", "nossa visita especial")}!

Mas a festa mal tinha começado quando surgiu uma confusão: a boneca Lili e o robô Zeca puxavam a mesma bola colorida, cada um de um lado.
— É minha! — dizia um.
— Não, é minha! — dizia o outro.
""".trim()
        },
        openingChoices = {
            listOf(
                "Perguntar como cada um está se sentindo" to Virtue.EMPATIA,
                "Inventar uma brincadeira para os dois" to Virtue.CRIATIVIDADE
            )
        },
        scenes = listOf(
            Scene(
                text = { _ ->
                    """
Lili contou que a bola lembrava o dia em que ganhou seu primeiro abraço. Zeca contou que só queria brincar, porque se sentia sozinho na prateleira. Quando os dois se ouviram, a bola deixou de ser motivo de briga.

— Que tal um jogo de passar a bola? — sugeriu Zeca, meio sem jeito.

Só que, do alto da estante, o dinossauro Dudu observava tudo com cara emburrada. Ele guardava um balde cheio de peças de montar e não deixava ninguém chegar perto.
""".trim()
                },
                choices = {
                    listOf(
                        "Convidar o Dudu para construir juntos" to Virtue.COOPERACAO,
                        "Perguntar ao Dudu por que ele está triste" to Virtue.EMPATIA
                    )
                }
            ),
            Scene(
                text = { x ->
                    """
Dudu desceu da estante devagar e confessou baixinho:
— Eu tenho medo de dividir e as peças acabarem.

${x.name} teve uma ideia: cada brinquedo colocaria uma peça por vez, e o castelo seria de todo mundo. Clic, clic, clic! Em pouco tempo surgiu uma torre altíssima, com ponte, bandeira e até escorregador.

De repente, a luz da lua começou a sumir: a festa precisava acabar antes de amanhecer, e ainda faltava o bolo!
""".trim()
                },
                choices = {
                    listOf(
                        "Organizar uma corrente para terminar o bolo" to Virtue.COOPERACAO,
                        "Fazer um bolo de blocos bem colorido" to Virtue.CRIATIVIDADE
                    )
                }
            ),
            Scene(
                text = { x ->
                    """
O bolo ficou pronto a tempo, cheio de velinhas de massinha. Mas, na hora de cortar, apareceu um probleminha: eram sete pedaços e oito brinquedos esperando.

Todo mundo olhou para o prato e depois para ${x.name}. O trenzinho até segurou o apito, esperando para ver o que ia acontecer.
""".trim()
                },
                choices = {
                    listOf(
                        "Dividir os pedaços em partes menores" to Virtue.EMPATIA,
                        "Fazer mais um pedaço de massinha" to Virtue.CRIATIVIDADE
                    )
                }
            )
        ),
        ending = { x, virtues ->
            """
No fim, todo mundo ganhou um pedacinho, e ninguém ficou de fora. A boneca Lili, o robô Zeca e o dinossauro Dudu dançaram juntos, rindo tanto que o trenzinho apitou sem parar: piuí, piuí, piuí!

— $virtues — disse ${x.c}, dando um abraço apertado em ${x.name}. — Essa foi a festa mais bonita de todas!

Quando o sol começou a nascer, os brinquedos voltaram para seus lugares, mas agora dividiam a mesma prateleira, lado a lado.

E ${x.name} descobriu uma coisa importante: dividir não faz nada acabar, faz a alegria ficar maior.
""".trim()
        }
    )

    // --- Novo irmãozinho -------------------------------------------------------------------------

    private val babyWorld = World(
        mood = "aconchegante",
        title = { "${it.name} e a Missão de Cuidar do Bebê" },
        opening = { x ->
            """
A casa estava diferente. Tinha um berço novo no quarto ao lado, fraldas empilhadas e um cheirinho de talco no ar. O bebê tinha chegado!

Todo mundo falava baixinho e olhava para o bebê com carinho. ${x.name} sentiu um aperto no peito, como se tivesse ficado um pouquinho invisível.

— Sabe de uma coisa? — cochichou ${x.c}. — Sentir ciúme é normal. E eu tenho um segredo: todo bebê precisa de alguém especial para cuidar dele e ensinar as coisas do mundo.

Nesse instante, o bebê abriu os olhinhos, olhou direto para ${x.name} e começou a chorar: uém, uém, uém!
""".trim()
        },
        openingChoices = {
            listOf(
                "Cantar uma música engraçada para o bebê" to Virtue.CRIATIVIDADE,
                "Contar para a família o que está sentindo" to Virtue.CORAGEM
            )
        },
        scenes = listOf(
            Scene(
                text = { x ->
                    """
O bebê parou de chorar, arregalou os olhos e deu uma risadinha que parecia soluço de passarinho. A família veio ver e deu um abraço apertado em ${x.name}.

— Você tem um jeito especial com o bebê — disse a mamãe, com um beijo na testa. — E continua sendo muito especial para nós.

À noite, ${x.c} trouxe um mapa brilhante: era o Mapa das Missões do Coração, com três missões secretas para cuidar do bebê.
""".trim()
                },
                choices = {
                    listOf(
                        "Começar pela missão do sono tranquilo" to Virtue.CALMA,
                        "Começar pela missão do sorriso" to Virtue.CRIATIVIDADE
                    )
                }
            ),
            Scene(
                text = { x ->
                    """
A primeira missão foi um sucesso: o bebê ficou tranquilo ouvindo a voz de ${x.name}. A missão seguinte levou todos até o jardim, onde o vento balançava as folhas e fazia sombras dançantes.

— O bebê está aprendendo a olhar o mundo — explicou ${x.c}. — E aprende muito observando quem já sabe mais coisas.

Foi então que o chocalho favorito do bebê desapareceu. Sem ele, o bebê ficou inquieto e começou a resmungar.
""".trim()
                },
                choices = {
                    listOf(
                        "Procurar pistas do chocalho pela casa" to Virtue.CURIOSIDADE,
                        "Emprestar um brinquedo seu para o bebê" to Virtue.EMPATIA
                    )
                }
            ),
            Scene(
                text = { x ->
                    """
O bebê se acalmou e segurou o dedo de ${x.name} com a mãozinha pequenininha, bem forte, como quem diz: fica comigo.

Mas então chegou a missão mais difícil de todas: preparar a primeira festinha do bebê. A família estava cansada, e ninguém sabia por onde começar.
""".trim()
                },
                choices = {
                    listOf(
                        "Montar um time com a família toda" to Virtue.COOPERACAO,
                        "Desenhar um cartão de boas-vindas" to Virtue.CRIATIVIDADE
                    )
                }
            )
        ),
        ending = { x, virtues ->
            """
A festinha ficou linda: tinha bandeirinhas, bolo de frutas e um cartão colorido pendurado na parede. Quando a família cantou, o bebê bateu palminhas pela primeira vez, olhando para ${x.name}.

— $virtues — disse ${x.c}. — Esse bebê tem muita sorte de ter você por perto!

Naquela noite, ${x.name} descobriu que o amor da família não diminui quando alguém novo chega. Ele cresce, cresce e cresce, igual a um balão que nunca estoura.

E, do quarto ao lado, veio um barulhinho diferente: não era choro, era uma risadinha de bebê.
""".trim()
        }
    )

    // --- Mundos genéricos (mar, dinossauros, floresta e tema livre) --------------------------------

    private class Scenery(
        val place: (Ctx) -> String,
        val placeDetail: String,
        val guide: String,
        val guideName: String,
        val treasure: String,
        val sound: String,
        val obstacle: String,
        val creature: String,
        val entrance: String,
        val title: (Ctx) -> String,
        val mood: String
    )

    private val forest = Scenery(
        place = { "a Floresta Encantada" },
        placeDetail = "as árvores eram tão altas que faziam cócegas nas nuvens, e os cogumelos brilhavam como lampiões",
        guide = "uma coruja de óculos chamada Dona Sábia",
        guideName = "Dona Sábia",
        treasure = "a Semente Arco-Íris",
        sound = "fru-fru, fru-fru",
        obstacle = "um riacho de águas agitadas",
        creature = "um filhote de raposa com a patinha enroscada num cipó",
        entrance = "uma portinha escondida entre as raízes da velha árvore do quintal",
        title = { "${it.name} e a Semente Arco-Íris" },
        mood = "misterioso"
    )

    private val ocean = Scenery(
        place = { "o Reino do Fundo do Mar" },
        placeDetail = "os corais pareciam jardins coloridos e os peixinhos brilhavam como confete",
        guide = "uma tartaruga sábia chamada Dona Maré",
        guideName = "Dona Maré",
        treasure = "a Pérola das Marés",
        sound = "blup, blup",
        obstacle = "uma correnteza que girava como um redemoinho",
        creature = "um filhote de polvo enroscado numa alga comprida",
        entrance = "uma concha gigante que apareceu na beira da praia",
        title = { "${it.name} e a Pérola das Marés" },
        mood = "aventura"
    )

    private val dinosaurs = Scenery(
        place = { "o Vale dos Dinossauros" },
        placeDetail = "havia samambaias gigantes, vulcões sonolentos e pegadas do tamanho de piscinas",
        guide = "uma dinossaura pescoçuda e gentil chamada Dona Tetê",
        guideName = "Dona Tetê",
        treasure = "o Ovo Dourado da Amizade",
        sound = "tum, tum, tum",
        obstacle = "um rio de lama borbulhante",
        creature = "um filhote de tricerátops que tinha se perdido da mamãe",
        entrance = "uma pegada brilhante que apareceu no tapete do quarto",
        title = { "${it.name} e o Ovo Dourado da Amizade" },
        mood = "aventura"
    )

    private val custom = Scenery(
        place = { "o mundo mágico de ${it.theme.lowercase()}" },
        placeDetail = "tudo brilhava com cores que ninguém nunca tinha visto",
        guide = "um vaga-lume sábio chamado Seu Lampejo",
        guideName = "Seu Lampejo",
        treasure = "o Tesouro da Imaginação",
        sound = "tlim, tlim",
        obstacle = "um labirinto de espelhos risonhos",
        creature = "um pequeno bichinho mágico todo atrapalhado",
        entrance = "um livro que se abriu sozinho em cima da cama",
        title = { "${it.name} e o Tesouro da Imaginação" },
        mood = "alegre"
    )

    private fun genericWorld(s: Scenery) = World(
        mood = s.mood,
        title = s.title,
        opening = { x ->
            """
Era uma tarde gostosa quando ${x.name} encontrou ${s.entrance}. Lá de dentro vinha um som diferente: ${s.sound}.

— Você ouviu isso? — perguntou ${x.c}, com os olhos brilhando. — Parece um convite para uma aventura!

Num piscar de olhos, os dois estavam em ${s.place(x)}. Ali, ${s.placeDetail}. Quem veio recebê-los foi ${s.guide}.

— Que bom que vocês chegaram! — disse ${s.guideName}. — ${s.treasure.replaceFirstChar { it.uppercase() }} sumiu, e precisamos de alguém que ama ${x.fav} para trazer a magia de volta.
""".trim()
        },
        openingChoices = {
            listOf(
                "Seguir o som misterioso" to Virtue.CURIOSIDADE,
                "Ouvir com atenção tudo o que ${s.guideName} sabe" to Virtue.CALMA
            )
        },
        scenes = listOf(
            Scene(
                text = { x ->
                    """
O caminho levou ${x.name} e ${x.c} até ${s.obstacle}. Do outro lado, alguma coisa brilhava: parecia uma pista de ${s.treasure}!

— Precisamos atravessar com cuidado — disse ${x.c}. — Mas como?

Ali perto havia folhas enormes, cipós compridos e pedrinhas que formavam um desenho curioso no chão, como setas apontando para algum lugar.
""".trim()
                },
                choices = {
                    listOf(
                        "Construir uma ponte com folhas e cipós" to Virtue.CRIATIVIDADE,
                        "Seguir as setas desenhadas nas pedrinhas" to Virtue.CURIOSIDADE
                    )
                }
            ),
            Scene(
                text = { x ->
                    """
Do outro lado, ${x.name} ouviu um choramingo baixinho. Era ${s.creature}.

— Eu estava procurando ${s.treasure} para ajudar todo mundo, mas acabei me atrapalhando — disse o bichinho, com a voz tremida.

${x.c} olhou para ${x.name}. A pista brilhante estava logo ali, mas alguém precisava de ajuda.
""".trim()
                },
                choices = {
                    listOf(
                        "Ajudar o bichinho com muito cuidado" to Virtue.EMPATIA,
                        "Chamar os amigos para ajudar juntos" to Virtue.COOPERACAO
                    )
                }
            ),
            Scene(
                text = { x ->
                    """
Com o bichinho a salvo e cheio de gratidão, todos seguiram juntos até o coração de ${s.place(x)}. Lá estava ${s.treasure}, dentro de uma bolha de luz que girava devagar.

— A bolha só se abre para quem mostrar o que aprendeu na aventura — explicou ${s.guideName}.

${x.name} respirou fundo. Era o momento mais importante de toda a jornada.
""".trim()
                },
                choices = {
                    listOf(
                        "Tocar a bolha com coragem e carinho" to Virtue.CORAGEM,
                        "Criar uma canção sobre a aventura" to Virtue.CRIATIVIDADE
                    )
                }
            )
        ),
        ending = { x, virtues ->
            """
A bolha se abriu devagarinho, como uma flor, e ${s.treasure} espalhou uma luz dourada por todo lado. ${s.guideName} bateu palmas, e o bichinho deu pulinhos de alegria: ${s.sound}!

— $virtues — disse ${x.c}, ${x.cg("radiante", "radiante")}. — Essa aventura nunca vai ser esquecida!

De presente, ${x.name} ganhou um pedacinho de luz que cabia na palma da mão, para lembrar que a magia mora em quem cuida dos outros.

Quando voltaram para casa, tudo parecia igual. Mas ${x.name} sabia: bastava fechar os olhos para visitar ${s.place(x)} outra vez.
""".trim()
        }
    )
}
