package com.livrovivo.app.domain.model

import java.time.Instant
import java.time.ZoneId

/** As três páginas do álbum. */
enum class StickerPage(val title: String, val subtitle: String) {
    MUNDOS("Mundos", "Termine uma história em cada mundo."),
    VIRTUDES("Virtudes", "Cada escolha faz um selo crescer: bronze, prata e ouro."),
    CONQUISTAS("Conquistas", "Marcos de quem ama ler.")
}

/** Níveis dos selos de virtude e quantas escolhas cada um pede. */
enum class MedalLevel(val label: String, val threshold: Int) {
    BRONZE("bronze", 1),
    PRATA("prata", 5),
    OURO("ouro", 12)
}

/** Como a figurinha é desenhada. Nada depende da IA: o álbum funciona até offline. */
sealed interface StickerArt {
    /** Um mundo: a cena que o app já desenha para o tema. */
    data class World(val scene: SceneKind, val emoji: String) : StickerArt

    /** Um selo de virtude, com a cor do nível. */
    data class Medal(val virtue: Virtue, val level: MedalLevel) : StickerArt

    /** Uma conquista de leitura. */
    data class Badge(val emoji: String) : StickerArt
}

data class Sticker(
    val id: String,
    /** Número do espaço no álbum, como num álbum de figurinhas de verdade. */
    val number: Int,
    val page: StickerPage,
    val title: String,
    /** Como ganhar, em palavras que a criança (ou quem lê com ela) entende. */
    val howToEarn: String,
    val art: StickerArt
) {
    /** Legenda para espaços pequenos: nos selos, o nível vai para a segunda linha. */
    val caption: String
        get() = when (art) {
            is StickerArt.Medal -> "${art.virtue.badgeTitle}\n${art.level.label}"
            else -> title
        }
}

/** Os números da leitura de uma criança que decidem o que ela já ganhou. */
data class ReadingTally(
    val finishedStories: Int = 0,
    /** Mundos com pelo menos uma história terminada (ids dos temas, ou [StickerAlbum.CUSTOM_WORLD]). */
    val finishedWorlds: Set<String> = emptySet(),
    val virtueCounts: Map<Virtue, Int> = emptyMap(),
    /** Dias diferentes em que a criança terminou histórias (nunca zera). */
    val readingDays: Int = 0
)

/** Um espaço do álbum: a figurinha, se já foi colada e quanto falta para ganhar. */
data class StickerSlot(
    val sticker: Sticker,
    val collected: Boolean,
    /** (quanto já tem, quanto precisa) para as figurinhas que dependem de contagem. */
    val progress: Pair<Int, Int>? = null
)

data class AlbumView(val slots: List<StickerSlot>) {
    val collectedCount: Int get() = slots.count { it.collected }
    val total: Int get() = slots.size
    fun page(page: StickerPage): List<StickerSlot> = slots.filter { it.sticker.page == page }
}

/** Depois de colar: o que fica no álbum e o que acabou de chegar. */
data class CollectResult(val collected: Set<String>, val newlyCollected: List<Sticker>)

/**
 * O álbum de figurinhas. As figurinhas se ganham lendo — nunca comprando nem por sorteio — e,
 * uma vez coladas, não saem mais: apagar ou reescrever uma história não tira nada do álbum.
 */
object StickerAlbum {

    const val CUSTOM_WORLD = "tema_livre"

    private val WORLD_NAMES = mapOf(
        "medo_do_escuro" to "Hora de Dormir",
        "adaptacao_escolar" to "Primeiro Dia na Escola",
        "aventura_espacial" to "Galáxias",
        "dividir_brinquedos" to "Festa dos Brinquedos",
        "chegada_irmaozinho" to "Novo Irmãozinho",
        "fundo_do_mar" to "Fundo do Mar",
        "terra_dinossauros" to "Terra dos Dinossauros",
        "floresta_encantada" to "Floresta Encantada"
    )

    private data class Milestone(val id: String, val emoji: String, val title: String, val howToEarn: String, val target: Int)

    private val MILESTONES = listOf(
        Milestone("primeira_aventura", "📖", "Primeira aventura", "Termine sua primeira história", 1),
        Milestone("cinco_aventuras", "📚", "Cinco aventuras", "Termine 5 histórias", 5),
        Milestone("dez_aventuras", "🏆", "Dez aventuras", "Termine 10 histórias", 10),
        Milestone("vinte_aventuras", "👑", "Vinte aventuras", "Termine 20 histórias", 20),
        Milestone("arco_iris", "🌈", "Arco-íris de virtudes", "Faça pelo menos uma escolha de cada virtude", Virtue.entries.size),
        Milestone("explorador", "🧭", "Explorador de mundos", "Termine histórias em 4 mundos diferentes", 4),
        Milestone("sete_dias", "📅", "Sete dias de histórias", "Leia histórias em 7 dias diferentes", 7)
    )

    val CATALOG: List<Sticker> = buildList {
        var number = 1
        ThemeOption.PRESETS.forEach { theme ->
            val name = WORLD_NAMES[theme.id] ?: theme.title
            add(
                Sticker(
                    id = worldId(theme.id), number = number++, page = StickerPage.MUNDOS, title = name,
                    howToEarn = "Termine uma história do mundo $name",
                    art = StickerArt.World(theme.scene, theme.emoji)
                )
            )
        }
        add(
            Sticker(
                id = worldId(CUSTOM_WORLD), number = number++, page = StickerPage.MUNDOS, title = "Mundo Inventado",
                howToEarn = "Termine uma história com um tema inventado por você",
                art = StickerArt.World(SceneKind.FOREST, "🪄")
            )
        )
        Virtue.entries.forEach { virtue ->
            MedalLevel.entries.forEach { level ->
                val choices = if (level.threshold == 1) "1 escolha" else "${level.threshold} escolhas"
                add(
                    Sticker(
                        id = medalId(virtue, level), number = number++, page = StickerPage.VIRTUDES,
                        title = "${virtue.badgeTitle} · ${level.label}",
                        howToEarn = "Faça $choices de ${virtue.title.lowercase()}",
                        art = StickerArt.Medal(virtue, level)
                    )
                )
            }
        }
        MILESTONES.forEach { milestone ->
            add(
                Sticker(
                    id = milestone.id, number = number++, page = StickerPage.CONQUISTAS, title = milestone.title,
                    howToEarn = milestone.howToEarn, art = StickerArt.Badge(milestone.emoji)
                )
            )
        }
    }

    private val BY_ID: Map<String, Sticker> = CATALOG.associateBy { it.id }

    fun worldId(themeId: String): String = "mundo_$themeId"
    fun medalId(virtue: Virtue, level: MedalLevel): String = "virtude_${virtue.code}_${level.name.lowercase()}"

    /** Uma história conta como terminada quando tem a página final, mesmo antes de o banco marcá-la. */
    private val Story.isFinished: Boolean get() = isCompleted || chapters.any { it.isEnding }

    private val Story.worldKey: String
        get() = themeId?.takeIf { ThemeOption.findById(it) != null } ?: CUSTOM_WORLD

    fun tally(stories: List<Story>, zone: ZoneId): ReadingTally {
        val finished = stories.filter { it.isFinished }
        return ReadingTally(
            finishedStories = finished.size,
            finishedWorlds = finished.map { it.worldKey }.toSet(),
            // Escolhas contam mesmo em histórias ainda sem final.
            virtueCounts = stories.flatMap { it.chosenVirtues }.groupingBy { it }.eachCount(),
            readingDays = finished.map { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }.toSet().size
        )
    }

    /** Figurinhas que a leitura atual já garante. */
    fun earned(tally: ReadingTally): Set<String> = CATALOG.filter { progressOf(it, tally).let { (have, need) -> have >= need } }
        .map { it.id }
        .toSet()

    /** (quanto já tem, quanto precisa) para cada figurinha. */
    private fun progressOf(sticker: Sticker, tally: ReadingTally): Pair<Int, Int> = when (val art = sticker.art) {
        is StickerArt.World -> {
            val key = sticker.id.removePrefix("mundo_")
            (if (key in tally.finishedWorlds) 1 else 0) to 1
        }
        is StickerArt.Medal -> (tally.virtueCounts[art.virtue] ?: 0) to art.level.threshold
        is StickerArt.Badge -> {
            val milestone = MILESTONES.first { it.id == sticker.id }
            val have = when (milestone.id) {
                "arco_iris" -> Virtue.entries.count { (tally.virtueCounts[it] ?: 0) > 0 }
                "explorador" -> tally.finishedWorlds.size
                "sete_dias" -> tally.readingDays
                else -> tally.finishedStories
            }
            have to milestone.target
        }
    }

    /**
     * Cola as figurinhas que a leitura garante. [collected] é o que já estava no álbum (null =
     * criança que ainda não tinha álbum). Nesse caso, o que ela já tinha antes da história [currentStoryId]
     * entra sem festa, e só o que essa história trouxe aparece como novidade.
     */
    fun collect(collected: Set<String>?, stories: List<Story>, currentStoryId: String?, zone: ZoneId): CollectResult {
        val earnedNow = earned(tally(stories, zone))
        val before = collected ?: earned(tally(stories.filter { it.id != currentStoryId }, zone))
        val fresh = CATALOG.filter { it.id in earnedNow && it.id !in before }
        return CollectResult(collected = before + earnedNow, newlyCollected = fresh)
    }

    /** O álbum como a criança vê: colado = já estava no álbum ou a leitura atual garante. */
    fun view(collected: Set<String>?, stories: List<Story>, zone: ZoneId): AlbumView {
        val tally = tally(stories, zone)
        val owned = collected.orEmpty() + earned(tally)
        return AlbumView(
            CATALOG.map { sticker ->
                val has = sticker.id in owned
                val progress = if (has || sticker.art is StickerArt.World) null else progressOf(sticker, tally).let { (have, need) -> have.coerceAtMost(need) to need }
                StickerSlot(sticker, collected = has, progress = progress)
            }
        )
    }

    fun find(id: String): Sticker? = BY_ID[id]
}
