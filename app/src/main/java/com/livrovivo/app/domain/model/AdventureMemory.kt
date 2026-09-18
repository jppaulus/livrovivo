package com.livrovivo.app.domain.model

/**
 * Uma aventura que a criança já viveu, resumida para o companheiro poder lembrar dela
 * ("Lembra quando você escolheu acender a lanterna mágica?").
 *
 * É montada a partir do que já fica salvo em cada história — título, tema e escolhas —,
 * então não precisa de nenhuma chamada extra à IA nem de coluna nova no banco.
 */
data class AdventureMemory(
    val storyId: String,
    val title: String,
    val theme: String,
    val companionId: String,
    /** Escolhas feitas pela criança, na ordem da história. */
    val choices: List<String>,
    val virtues: List<Virtue>,
    val isFinished: Boolean,
    val updatedAt: Long,
    /** A escolha mais marcante: a última feita, que costuma ser a do clímax. */
    val highlightChoice: String?,
    val highlightVirtue: Virtue?
) {

    /**
     * A escolha pronta para entrar no meio de uma frase: "você escolheu acender a lanterna".
     * As escolhas começam com verbo no infinitivo; se alguma fugir disso, ela vai entre aspas
     * para a frase continuar correta.
     */
    fun highlightAsClause(maxLength: Int = 60): String? {
        val text = highlightChoice?.trim()?.trimEnd('.', '!', '?')?.takeIf { it.isNotBlank() } ?: return null
        val short = shorten(text, maxLength)
        return if (startsWithInfinitive(short)) short.replaceFirstChar { it.lowercase() } else "‘$short’"
    }

    companion object {
        const val DEFAULT_LIMIT = 3

        /**
         * As aventuras mais recentes de uma criança, sem a história que está sendo escrita agora.
         * Só entram histórias em que ela já fez alguma escolha — é o que há para lembrar.
         * [stories] deve conter só histórias da mesma criança.
         */
        fun recent(
            stories: List<Story>,
            excludeStoryId: String? = null,
            limit: Int = DEFAULT_LIMIT
        ): List<AdventureMemory> =
            stories.asSequence()
                .filter { it.id != excludeStoryId }
                .filter { story -> story.chapters.any { it.selectedChoiceText != null } }
                .sortedByDescending { it.updatedAt }
                .take(limit)
                .map { from(it) }
                .toList()

        fun from(story: Story): AdventureMemory {
            val chosen = story.sortedChapters.filter { it.selectedChoiceText != null }
            val last = chosen.lastOrNull()
            return AdventureMemory(
                storyId = story.id,
                title = story.title,
                theme = ThemeOption.findById(story.themeId)?.title ?: story.theme,
                companionId = story.companionId,
                choices = chosen.mapNotNull { it.selectedChoiceText },
                virtues = story.chosenVirtues.distinct(),
                isFinished = story.isCompleted,
                updatedAt = story.updatedAt,
                highlightChoice = last?.selectedChoiceText,
                highlightVirtue = last?.selectedChoice?.virtue
            )
        }

        /** Verbos no infinitivo terminam em -ar, -er, -ir ou -or (inclui "ir" e "pôr"). */
        private val INFINITIVE = Regex("^\\p{L}*(ar|er|ir|or|ôr)$")

        private fun startsWithInfinitive(text: String): Boolean =
            INFINITIVE.matches(text.substringBefore(' ').lowercase())

        private fun shorten(text: String, maxLength: Int): String {
            if (text.length <= maxLength) return text
            val cut = text.take(maxLength).substringBeforeLast(' ').trimEnd(',', ';', ':')
            return "$cut…"
        }
    }
}
