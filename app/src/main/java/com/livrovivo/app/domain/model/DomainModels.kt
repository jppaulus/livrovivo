package com.livrovivo.app.domain.model

/**
 * Virtude estimulada por uma escolha. Alimenta as conquistas da criança e o painel dos pais.
 */
enum class Virtue(
    val code: String,
    val title: String,
    val badgeTitle: String,
    val emoji: String
) {
    CORAGEM("coragem", "Coragem", "Coração Valente", "🦁"),
    EMPATIA("empatia", "Empatia", "Alma Bondosa", "💖"),
    CRIATIVIDADE("criatividade", "Criatividade", "Mente Criativa", "🎨"),
    CURIOSIDADE("curiosidade", "Curiosidade", "Olhos Curiosos", "🔍"),
    CALMA("calma", "Calma", "Mestre da Calma", "🌙"),
    COOPERACAO("cooperacao", "Cooperação", "Amizade em Equipe", "🤝");

    companion object {
        fun fromCode(code: String?): Virtue? {
            if (code.isNullOrBlank()) return null
            val normalized = code.trim().lowercase()
                .replace("ç", "c").replace("ã", "a").replace("õ", "o")
            return entries.find { it.code == normalized }
        }
    }
}

data class Choice(
    val text: String,
    val targetChapterIndex: Int,
    val virtue: Virtue? = null
)

data class Chapter(
    val index: Int,
    val content: String,
    val choices: List<Choice> = emptyList(),
    val isEnding: Boolean = false,
    val audioUrl: String? = null,
    /** Descrição da cena (em inglês) usada para gerar a ilustração. */
    val sceneImagePrompt: String? = null,
    /** Caminho local da ilustração gerada para esta página. */
    val imagePath: String? = null,
    /** Mesmo texto do capítulo com marcações de emoção ([whispers], [giggles]...) para a narração. */
    val narrationScript: String? = null,
    /** Escolha feita pela criança ao final desta página (null enquanto não escolheu). */
    val selectedChoiceText: String? = null,
    val newWords: List<String> = emptyList(),
    val mood: String? = null,
    val openedAt: Long? = null
) {
    val selectedChoice: Choice?
        get() = selectedChoiceText?.let { selected -> choices.find { it.text == selected } }
}

data class Story(
    val id: String,
    val childId: String,
    val title: String,
    val theme: String,
    val objectiveType: String,
    val coverImageUrl: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val themeId: String? = null,
    val companionId: String = MagicalCompanion.ALL.first().id,
    /** Ficha visual (em inglês) dos personagens para manter as ilustrações consistentes. */
    val characterSheet: String? = null,
    val plannedChapters: Int = 5,
    val isCompleted: Boolean = false,
    val lastReadChapter: Int = 1,
    val updatedAt: Long = createdAt,
    /** true quando a história foi escrita pelo motor offline (sem IA). */
    val isOffline: Boolean = false,
    val childSnapshot: ChildProfile? = null,
    val deletedAt: Long? = null,
    /** Original adventure when this book preserves a previous path. */
    val originId: String? = null,
    /** [KIND_ADVENTURE] (história interativa) ou [KIND_EU_LEIO] (livro da Trilha da Leitura). */
    val kind: String = KIND_ADVENTURE
) {
    val sortedChapters: List<Chapter> get() = chapters.sortedBy { it.index }
    val lastChapter: Chapter? get() = chapters.maxByOrNull { it.index }
    val coverPath: String? get() = coverImageUrl ?: sortedChapters.firstNotNullOfOrNull { it.imagePath }
    val chosenVirtues: List<Virtue> get() = sortedChapters.mapNotNull { it.selectedChoice?.virtue }
    val isEuLeio: Boolean get() = kind == KIND_EU_LEIO

    companion object {
        const val KIND_ADVENTURE = "aventura"
        const val KIND_EU_LEIO = "eu_leio"
    }
}

enum class ChildGender(val code: String, val label: String) {
    GIRL("menina", "Menina"),
    BOY("menino", "Menino"),
    NEUTRAL("neutro", "Prefiro não dizer");

    fun pick(masculine: String, feminine: String, neutral: String = masculine): String = when (this) {
        BOY -> masculine
        GIRL -> feminine
        NEUTRAL -> neutral
    }

    companion object {
        fun fromCode(code: String?): ChildGender = entries.find { it.code == code } ?: NEUTRAL
    }
}

/** Aparência opcional usada apenas para personalizar as ilustrações. */
data class ChildAppearance(
    val skinTone: String? = null,
    val hairColor: String? = null,
    val hairStyle: String? = null,
    val wearsGlasses: Boolean = false
) {
    val isEmpty: Boolean
        get() = skinTone == null && hairColor == null && hairStyle == null && !wearsGlasses

    companion object {
        /** Opções com descrição em inglês para o ilustrador e cor para o seletor visual. */
        val SKIN_TONES = listOf(
            AppearanceOption("clara", "Clara", "fair skin", 0xFFF7D9C4),
            AppearanceOption("media_clara", "Média clara", "light tan skin", 0xFFE8B98F),
            AppearanceOption("morena", "Morena", "warm medium-brown skin", 0xFFC68A5E),
            AppearanceOption("marrom", "Marrom", "brown skin", 0xFF8D5A3B),
            AppearanceOption("escura", "Escura", "deep dark-brown skin", 0xFF5A3825)
        )
        val HAIR_COLORS = listOf(
            AppearanceOption("preto", "Preto", "black hair", 0xFF1F1B1A),
            AppearanceOption("castanho_escuro", "Castanho escuro", "dark brown hair", 0xFF4A2E1F),
            AppearanceOption("castanho_claro", "Castanho claro", "light brown hair", 0xFF8B5E3C),
            AppearanceOption("loiro", "Loiro", "golden blond hair", 0xFFE3C16F),
            AppearanceOption("ruivo", "Ruivo", "copper red hair", 0xFFB5522B)
        )
        val HAIR_STYLES = listOf(
            AppearanceOption("liso", "Liso", "straight", 0),
            AppearanceOption("ondulado", "Ondulado", "wavy", 0),
            AppearanceOption("cacheado", "Cacheado", "curly", 0),
            AppearanceOption("crespo", "Crespo", "coily afro-textured", 0)
        )
    }
}

data class AppearanceOption(
    val code: String,
    val label: String,
    val englishDescription: String,
    val color: Long
)

data class ChildProfile(
    val id: String,
    val name: String,
    val ageGroup: String, // "3-5", "6-8", "9+"
    val interests: List<String> = emptyList(),
    val companionId: String = "bento",
    val createdAt: Long = System.currentTimeMillis(),
    val gender: ChildGender = ChildGender.NEUTRAL,
    val appearance: ChildAppearance = ChildAppearance()
)

data class MagicalCompanion(
    val id: String,
    val name: String,
    val title: String,
    val emoji: String,
    val personality: String,
    val isFeminine: Boolean = false,
    /** Descrição visual fixa (em inglês) para manter o companheiro igual em todas as ilustrações. */
    val visualDescription: String = ""
) {
    fun pick(masculine: String, feminine: String): String = if (isFeminine) feminine else masculine

    companion object {
        val ALL = listOf(
            MagicalCompanion(
                id = "bento",
                name = "Bento",
                title = "O Dragãozinho Corajoso",
                emoji = "🐲",
                personality = "Adora aventuras e ensina a ter coragem.",
                visualDescription = "a small chubby baby dragon with mint-green scales, a cream-colored belly, tiny rounded wings, two stubby horns and big sparkling amber eyes"
            ),
            MagicalCompanion(
                id = "luna",
                name = "Luna",
                title = "A Corujinha Sábia",
                emoji = "🦉",
                personality = "Calma e curiosa, ótima ouvinte para a hora de dormir.",
                isFeminine = true,
                visualDescription = "a fluffy little owl with silvery-lavender feathers, a small crescent-moon mark on her chest and huge gentle golden eyes"
            ),
            MagicalCompanion(
                id = "pipoca",
                name = "Pipoca",
                title = "O Robô Amigo",
                emoji = "🤖",
                personality = "Divertido e criativo, adora resolver quebra-cabeças.",
                visualDescription = "a small round friendly robot with a glossy white and sky-blue body, a screen face with happy pixel eyes, a little antenna with a glowing yellow tip and caterpillar wheels"
            ),
            MagicalCompanion(
                id = "aurora",
                name = "Aurora",
                title = "A Fadinha da Bondade",
                emoji = "🧚",
                personality = "Espalha empatia e ajuda a acolher emoções difíceis.",
                isFeminine = true,
                visualDescription = "a tiny fairy with warm brown skin, curly pink-violet hair tied with a small flower, translucent rainbow-shimmer wings and a sparkling star wand"
            )
        )

        fun findById(id: String?): MagicalCompanion = ALL.find { it.id == id } ?: ALL.first()
    }
}

enum class AgeGroup(
    val code: String,
    val label: String,
    val description: String,
    val emoji: String,
    val plannedChapters: Int,
    val wordRange: IntRange,
    val illustrationAge: String
) {
    TODDLER("3-5", "3 a 5 anos", "Frases curtinhas, sons divertidos e muito aconchego.", "🧸", 4, 45..75, "4-year-old"),
    KID("6-8", "6 a 8 anos", "Aventuras com empatia, cooperação e palavras novas.", "🎈", 5, 90..130, "7-year-old"),
    EXPLORER("9+", "9+ anos", "Mistérios, enigmas e dilemas para decidir.", "🧭", 6, 130..180, "10-year-old");

    companion object {
        fun fromCode(code: String?): AgeGroup = entries.find { it.code == code } ?: KID
    }
}

enum class ObjectiveType(
    val code: String,
    val title: String,
    val iconRes: String,
    val description: String,
    val shortTitle: String
) {
    EMOCIONAL("emocional", "Acolhimento Emocional", "❤️", "Superação de medos, segurança e autoconfiança", "Emoções"),
    COGNITIVO("cognitivo", "Desenvolvimento Cognitivo", "🧠", "Raciocínio lógico, curiosidade e vocabulário", "Raciocínio"),
    AVENTURA("aventura", "Aventura & Imaginação", "🚀", "Exploração, empatia e tomada de decisões", "Imaginação");

    companion object {
        fun fromCode(code: String): ObjectiveType = entries.find { it.code == code } ?: EMOCIONAL
    }
}

/** Cenário usado pelas ilustrações procedurais (offline) e pela trilha sonora. */
enum class SceneKind { NIGHT, SCHOOL, SPACE, PARTY, HOME, OCEAN, DINOSAURS, FOREST }

data class ThemeOption(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val defaultObjective: ObjectiveType,
    val scene: SceneKind = SceneKind.FOREST
) {
    companion object {
        const val CUSTOM_ID = "tema_livre"

        val PRESETS = listOf(
            ThemeOption("medo_do_escuro", "Hora de Dormir sem Medo do Escuro", "Sombras amigas, estrelas e uma canção de ninar.", "🌙", ObjectiveType.EMOCIONAL, SceneKind.NIGHT),
            ThemeOption("adaptacao_escolar", "O Primeiro Dia na Escola", "Novos amigos, timidez superada e alegria de compartilhar.", "🎒", ObjectiveType.EMOCIONAL, SceneKind.SCHOOL),
            ThemeOption("aventura_espacial", "Exploradores das Galáxias", "Foguetes, planetas curiosos e coragem cósmica.", "🚀", ObjectiveType.AVENTURA, SceneKind.SPACE),
            ThemeOption("dividir_brinquedos", "A Festa dos Brinquedos", "Empatia, resolver briguinhas e a alegria de dividir.", "🧸", ObjectiveType.EMOCIONAL, SceneKind.PARTY),
            ThemeOption("chegada_irmaozinho", "O Novo Irmãozinho", "Carinho, ciúmes acolhidos e o papel de irmão mais velho.", "🍼", ObjectiveType.EMOCIONAL, SceneKind.HOME),
            ThemeOption("fundo_do_mar", "Mistérios do Fundo do Mar", "Recifes coloridos, tesouros e amigos marinhos.", "🐠", ObjectiveType.COGNITIVO, SceneKind.OCEAN),
            ThemeOption("terra_dinossauros", "A Terra dos Dinossauros", "Pegadas gigantes, ovos misteriosos e amizade pré-histórica.", "🦕", ObjectiveType.AVENTURA, SceneKind.DINOSAURS),
            ThemeOption("floresta_encantada", "A Floresta Encantada", "Árvores falantes, enigmas e cuidado com a natureza.", "🌳", ObjectiveType.COGNITIVO, SceneKind.FOREST)
        )

        fun findById(id: String?): ThemeOption? = PRESETS.find { it.id == id }

        /** Descobre o cenário visual mais próximo de um tema livre digitado pelos pais. */
        fun sceneFor(themeId: String?, themeText: String): SceneKind {
            findById(themeId)?.let { return it.scene }
            val t = themeText.lowercase()
            return when {
                listOf("escuro", "noite", "dormir", "sono", "lua", "estrela").any { it in t } -> SceneKind.NIGHT
                listOf("escola", "aula", "professor", "colega").any { it in t } -> SceneKind.SCHOOL
                listOf("espaço", "espaco", "planeta", "foguete", "galáxia", "galaxia", "astronauta").any { it in t } -> SceneKind.SPACE
                listOf("mar", "oceano", "praia", "peixe", "sereia", "baleia").any { it in t } -> SceneKind.OCEAN
                listOf("dinossauro", "dino", "vulcão", "vulcao").any { it in t } -> SceneKind.DINOSAURS
                listOf("brinquedo", "festa", "aniversário", "aniversario", "dividir").any { it in t } -> SceneKind.PARTY
                listOf("irmão", "irmao", "irmã", "irma", "bebê", "bebe", "família", "familia", "casa").any { it in t } -> SceneKind.HOME
                else -> SceneKind.FOREST
            }
        }
    }
}

enum class IllustrationStyle(val id: String, val title: String, val emoji: String, val prompt: String) {
    AQUARELA(
        "aquarela", "Aquarela", "🎨",
        "soft watercolor and gouache children's picture book illustration, warm pastel palette, gentle textured paper, cozy magical lighting"
    ),
    LAPIS_DE_COR(
        "lapis", "Lápis de cor", "✏️",
        "colored pencil and crayon children's book illustration with visible hand-drawn strokes, bright cheerful colors, whimsical and warm"
    ),
    ANIMACAO_3D(
        "animacao3d", "Animação 3D", "🧸",
        "cute soft 3D animated movie style render, rounded shapes, big expressive eyes, warm cinematic lighting, gentle depth of field"
    ),
    RECORTE(
        "recorte", "Papel recortado", "✂️",
        "paper cutout collage illustration, layered textured craft paper shapes, bold simple forms, playful handmade look"
    ),
    DESENHO(
        "desenho", "Desenho animado", "🖍️",
        "flat vector cartoon illustration for a modern picture book, clean rounded outlines, vibrant friendly colors"
    );

    companion object {
        fun fromId(id: String?): IllustrationStyle = entries.find { it.id == id } ?: AQUARELA
    }
}

data class ParentInsights(
    val childName: String = "",
    val storiesStarted: Int = 0,
    val storiesCompleted: Int = 0,
    val pagesRead: Int = 0,
    val minutesReading: Int = 0,
    val choicesMade: Int = 0,
    val virtueCounts: Map<Virtue, Int> = emptyMap(),
    val vocabulary: List<String> = emptyList(),
    val favoriteThemes: List<Pair<String, Int>> = emptyList(),
    val conversationTip: String? = null
)
