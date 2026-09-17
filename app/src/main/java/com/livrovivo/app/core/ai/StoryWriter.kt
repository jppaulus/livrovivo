package com.livrovivo.app.core.ai

import com.livrovivo.app.core.ai.JsonUtils.string
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.Story
import java.util.UUID

/**
 * Escreve histórias capítulo a capítulo: com IA quando disponível, ou com o motor offline.
 */
class StoryWriter(
    private val gemini: GeminiService
) {
    data class Draft(val story: Story, val usedAi: Boolean)

    /** Cria o capítulo 1. Sem IA configurada, usa o motor offline. Com IA e erro, falha (para a UI oferecer opções). */
    suspend fun startStory(
        brief: StoryBrief,
        themeId: String?,
        objectiveCode: String,
        forceOffline: Boolean = false
    ): Result<Draft> {
        val storyId = UUID.randomUUID().toString()
        if (forceOffline || !gemini.isAvailable()) {
            return Result.success(Draft(offlineStory(storyId, brief, themeId, objectiveCode), usedAi = false))
        }
        return try {
            val json = gemini.generateJson(
                systemPrompt = StoryPrompts.SYSTEM_PROMPT,
                userPrompt = StoryPrompts.openingPrompt(brief),
                schema = StoryPrompts.schema(includeOpeningFields = true)
            )
            val chapter = ChapterSanitizer.toChapter(json, index = 1, isFinal = false) {
                OfflineStoryEngine.fallbackChoices(brief.companion, 2)
            }
            val title = json.string("title")?.trim()?.trim('"', '*')?.take(80)
                ?.takeIf { it.isNotBlank() } ?: "${brief.child.name} e a Grande Aventura"
            val story = Story(
                id = storyId,
                childId = brief.child.id,
                title = title,
                theme = brief.theme,
                objectiveType = objectiveCode,
                chapters = listOf(chapter),
                themeId = themeId,
                companionId = brief.companion.id,
                characterSheet = json.string("characterSheet")?.trim()?.take(1200)
                    ?: defaultCharacterSheet(brief),
                plannedChapters = brief.plannedChapters,
                isOffline = false
            )
            Result.success(Draft(story, usedAi = true))
        } catch (e: AiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AiException(AiException.Kind.PARSE, e.message.orEmpty(), cause = e))
        }
    }

    /** Escreve o próximo capítulo a partir da escolha feita no último capítulo. */
    suspend fun continueStory(brief: StoryBrief, story: Story, choice: Choice): Result<Chapter> {
        val nextIndex = (story.lastChapter?.index ?: 0) + 1
        if (story.isOffline) {
            return Result.success(OfflineStoryEngine.continuation(brief, story.themeId, story, choice))
        }
        if (!gemini.isAvailable()) {
            return Result.failure(AiException(AiException.Kind.NOT_CONFIGURED))
        }
        return try {
            val isFinal = nextIndex >= story.plannedChapters
            val json = gemini.generateJson(
                systemPrompt = StoryPrompts.SYSTEM_PROMPT,
                userPrompt = StoryPrompts.continuationPrompt(brief, story, choice),
                schema = StoryPrompts.schema(includeOpeningFields = false)
            )
            Result.success(
                ChapterSanitizer.toChapter(json, index = nextIndex, isFinal = isFinal) {
                    OfflineStoryEngine.fallbackChoices(brief.companion, nextIndex + 1)
                }
            )
        } catch (e: AiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AiException(AiException.Kind.PARSE, e.message.orEmpty(), cause = e))
        }
    }

    private fun offlineStory(storyId: String, brief: StoryBrief, themeId: String?, objectiveCode: String): Story {
        val opening = OfflineStoryEngine.opening(brief, themeId)
        return Story(
            id = storyId,
            childId = brief.child.id,
            title = opening.title,
            theme = brief.theme,
            objectiveType = objectiveCode,
            chapters = listOf(opening.chapter),
            themeId = themeId,
            companionId = brief.companion.id,
            characterSheet = defaultCharacterSheet(brief),
            plannedChapters = opening.plannedChapters,
            isOffline = true
        )
    }

    private fun defaultCharacterSheet(brief: StoryBrief): String =
        "${brief.child.name}: ${StoryPrompts.appearanceDescription(brief.child)}, wearing a cozy yellow sweater. " +
            "${brief.companion.name}: ${brief.companion.visualDescription}."
}
