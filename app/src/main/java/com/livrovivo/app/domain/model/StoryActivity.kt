package com.livrovivo.app.domain.model

/** Counts observed activity without treating copied paths as new reading or learning. */
data class StoryActivity(val pagesOpened: Int, val choices: List<Choice>, val vocabulary: List<String>) {
    companion object {
        fun from(stories: List<Story>): StoryActivity {
            val pages = stories.filter { it.deletedAt == null }.flatMap { story ->
                story.chapters.map { chapter ->
                    listOf(story.originId ?: story.id, chapter.index.toString(), chapter.content) to chapter
                }
            }
            val opened = pages.filter { it.second.openedAt != null }.distinctBy { it.first }
            val choices = pages.filter { it.second.selectedChoice != null }
                .distinctBy { (key, chapter) -> key + chapter.selectedChoiceText.orEmpty() }
                .mapNotNull { it.second.selectedChoice }
            return StoryActivity(opened.size, choices,
                opened.flatMap { it.second.newWords }.distinctBy { it.lowercase() }.take(24))
        }
    }
}
