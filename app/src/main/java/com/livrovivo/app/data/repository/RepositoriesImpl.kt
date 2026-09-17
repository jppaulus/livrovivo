package com.livrovivo.app.data.repository

import com.livrovivo.app.core.ai.AiException
import com.livrovivo.app.core.ai.StoryBrief
import com.livrovivo.app.core.ai.StoryWriter
import com.livrovivo.app.core.database.dao.ChildProfileDao
import com.livrovivo.app.core.database.dao.StoryDao
import com.livrovivo.app.core.illustration.IllustrationService
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.data.model.ReadingSessionEntity
import com.livrovivo.app.data.model.toDomain
import com.livrovivo.app.data.model.toEntity
import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.ParentInsights
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.ThemeOption
import com.livrovivo.app.domain.model.Virtue
import com.livrovivo.app.domain.repository.BillingRepository
import com.livrovivo.app.domain.repository.ChildProfileRepository
import com.livrovivo.app.domain.repository.StoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class StoryRepositoryImpl(
    private val storyDao: StoryDao,
    private val childProfileDao: ChildProfileDao,
    private val storyWriter: StoryWriter,
    private val illustrationService: IllustrationService,
    private val settingsManager: SettingsManager
) : StoryRepository {

    override fun getStoriesFlow(): Flow<List<Story>> =
        storyDao.observeStoriesWithChapters().map { list -> list.map { it.toDomain() } }

    override fun observeStory(storyId: String): Flow<Story?> =
        storyDao.observeStoryWithChapters(storyId).map { it?.toDomain() }

    override suspend fun getStoryById(storyId: String): Story? =
        storyDao.getStoryWithChapters(storyId)?.toDomain()

    override suspend fun createStory(
        child: ChildProfile,
        theme: String,
        themeId: String?,
        objective: ObjectiveType,
        forceOffline: Boolean
    ): Result<Story> {
        val ageGroup = AgeGroup.fromCode(child.ageGroup)
        val brief = StoryBrief(
            child = child,
            companion = MagicalCompanion.findById(child.companionId),
            theme = theme,
            objective = objective,
            plannedChapters = ageGroup.plannedChapters
        )
        return storyWriter.startStory(brief, themeId, objective.code, forceOffline).map { draft ->
            val now = System.currentTimeMillis()
            val story = draft.story.copy(createdAt = now, updatedAt = now)
            storyDao.insertStoryWithChapters(story.toEntity(), story.chapters.map { it.toEntity(story.id) })
            settingsManager.registerStoryCreated(existingStories = storyDao.getStoryCount())
            story
        }
    }

    override suspend fun continueStory(storyId: String, fromChapterIndex: Int, choice: Choice): Result<Chapter> {
        val story = getStoryById(storyId)
            ?: return Result.failure(IllegalStateException("História não encontrada."))
        val last = story.lastChapter
            ?: return Result.failure(IllegalStateException("História sem capítulos."))
        if (last.index != fromChapterIndex) {
            return Result.failure(IllegalStateException("Esta página já tem continuação."))
        }
        if (last.isEnding) {
            return Result.failure(IllegalStateException("A história já terminou."))
        }

        val child = childProfileDao.getActiveProfile()?.toDomain() ?: fallbackChild(story)
        val brief = StoryBrief(
            child = child,
            companion = MagicalCompanion.findById(story.companionId),
            theme = story.theme,
            objective = ObjectiveType.fromCode(story.objectiveType),
            plannedChapters = story.plannedChapters
        )

        storyDao.updateSelectedChoice(storyId, last.index, choice.text)
        val storyWithChoice = story.copy(
            chapters = story.chapters.map { if (it.index == last.index) it.copy(selectedChoiceText = choice.text) else it }
        )

        return storyWriter.continueStory(brief, storyWithChoice, choice)
            .onSuccess { chapter ->
                val now = System.currentTimeMillis()
                storyDao.insertChapters(listOf(chapter.toEntity(storyId)))
                storyDao.updateLastRead(storyId, chapter.index, now)
                if (chapter.isEnding) {
                    storyDao.updateCompleted(storyId, true, now)
                    if (chapter.index != story.plannedChapters) storyDao.updatePlannedChapters(storyId, chapter.index)
                }
            }
            .onFailure {
                // Libera as escolhas para a criança tentar de novo.
                storyDao.updateSelectedChoice(storyId, last.index, null)
            }
    }

    override suspend fun rewindTo(storyId: String, chapterIndex: Int) {
        val story = getStoryById(storyId) ?: return
        story.chapters.filter { it.index > chapterIndex }.forEach { chapter ->
            chapter.imagePath?.let { File(it).delete() }
        }
        storyDao.rewindTo(storyId, chapterIndex, System.currentTimeMillis())
    }

    override suspend fun markRead(storyId: String, chapterIndex: Int) {
        storyDao.updateLastRead(storyId, chapterIndex, System.currentTimeMillis())
    }

    override suspend fun illustrateChapter(storyId: String, chapterIndex: Int): Result<String> {
        val story = getStoryById(storyId)
            ?: return Result.failure(IllegalStateException("História não encontrada."))
        val chapter = story.chapters.find { it.index == chapterIndex }
            ?: return Result.failure(IllegalStateException("Página não encontrada."))
        chapter.imagePath?.takeIf { File(it).exists() }?.let { return Result.success(it) }
        if (!illustrationService.isEnabled()) {
            return Result.failure(AiException(AiException.Kind.NOT_CONFIGURED))
        }
        return try {
            val child = childProfileDao.getActiveProfile()?.toDomain()
            val file = illustrationService.illustrate(story, chapter, child)
            // A página pode ter sido apagada enquanto a ilustração era gerada (criança voltou atrás).
            val stillExists = storyDao.getChaptersForStory(storyId).any { it.chapterIndex == chapterIndex }
            if (!stillExists) {
                file.delete()
                return Result.failure(IllegalStateException("Página removida."))
            }
            storyDao.updateChapterImage(storyId, chapterIndex, file.path)
            if (chapterIndex == 1) storyDao.updateCover(storyId, file.path)
            Result.success(file.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteStory(storyId: String) {
        storyDao.deleteStory(storyId)
        storyDao.deleteSessionsForStory(storyId)
        withContext(Dispatchers.IO) { illustrationService.deleteStoryAssets(storyId) }
    }

    override suspend fun deleteAllStories() {
        storyDao.deleteAllStories()
        storyDao.deleteAllSessions()
        withContext(Dispatchers.IO) { illustrationService.deleteAllStoryAssets() }
    }

    override suspend fun countGeneratedStories(): Int =
        maxOf(storyDao.getStoryCount(), settingsManager.current().storiesCreated)

    override suspend fun recordReadingSession(storyId: String, childId: String, startedAt: Long, durationMs: Long) {
        if (durationMs < 5_000) return
        storyDao.insertReadingSession(
            ReadingSessionEntity(
                storyId = storyId,
                childId = childId,
                startedAt = startedAt,
                durationMs = durationMs.coerceAtMost(3 * 60 * 60 * 1000L)
            )
        )
    }

    override suspend fun buildInsights(child: ChildProfile?): ParentInsights {
        val stories = storyDao.getAllStoriesWithChapters().map { it.toDomain() }
        val name = child?.name ?: "a criança"
        val virtues = stories.flatMap { it.chosenVirtues }
        val vocabulary = stories.sortedByDescending { it.updatedAt }
            .flatMap { story -> story.sortedChapters.flatMap { it.newWords } }
            .distinctBy { it.lowercase() }
            .take(24)
        val themes = stories
            .groupingBy { ThemeOption.findById(it.themeId)?.title ?: it.theme }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(3)

        val latest = stories.maxByOrNull { it.updatedAt }
        val lastChoice = latest?.sortedChapters?.lastOrNull { it.selectedChoice != null }?.selectedChoice
        val tip = when {
            latest != null && lastChoice != null ->
                "Em \"${latest.title}\", $name escolheu \"${lastChoice.text}\". Pergunte: ${conversationQuestion(lastChoice.virtue)}"
            latest != null ->
                "Depois de ler \"${latest.title}\", pergunte: qual foi a parte mais legal? E o que você faria diferente?"
            else -> null
        }

        return ParentInsights(
            childName = child?.name.orEmpty(),
            storiesStarted = stories.size,
            storiesCompleted = stories.count { it.isCompleted },
            pagesRead = stories.sumOf { it.chapters.size },
            minutesReading = (storyDao.totalReadingMs() / 60_000L).toInt(),
            choicesMade = stories.sumOf { story -> story.chapters.count { it.selectedChoiceText != null } },
            virtueCounts = virtues.groupingBy { it }.eachCount(),
            vocabulary = vocabulary,
            favoriteThemes = themes,
            conversationTip = tip
        )
    }

    private fun conversationQuestion(virtue: Virtue?): String = when (virtue) {
        Virtue.CORAGEM -> "o que te deu coragem naquela hora? Quando foi que você sentiu coragem de verdade?"
        Virtue.EMPATIA -> "como você acha que o outro personagem se sentiu? O que faz você se sentir acolhido por alguém?"
        Virtue.CRIATIVIDADE -> "que outra ideia diferente poderia resolver aquele problema?"
        Virtue.CURIOSIDADE -> "o que mais você gostaria de descobrir naquele mundo?"
        Virtue.CALMA -> "o que ajuda você a se acalmar quando algo parece difícil?"
        Virtue.COOPERACAO -> "com quem você gosta de fazer coisas em equipe? Por quê?"
        null -> "qual foi a parte mais legal da história? E o que você faria diferente?"
    }

    private fun fallbackChild(story: Story) = ChildProfile(
        id = story.childId,
        name = "Pequeno Leitor",
        ageGroup = AgeGroup.KID.code
    )
}

class ChildProfileRepositoryImpl(
    private val childProfileDao: ChildProfileDao
) : ChildProfileRepository {

    override fun getActiveProfileFlow(): Flow<ChildProfile?> =
        childProfileDao.getActiveProfileFlow().map { it?.toDomain() }

    override suspend fun getActiveProfile(): ChildProfile? = childProfileDao.getActiveProfile()?.toDomain()

    override suspend fun saveProfile(profile: ChildProfile) {
        childProfileDao.insertProfile(profile.toEntity())
    }
}

/**
 * Assinatura simulada (persistida no aparelho). A integração real com o Google Play Billing
 * deve substituir [purchaseSubscription] antes da publicação.
 */
class BillingRepositoryImpl(
    private val storyDao: StoryDao,
    private val settingsManager: SettingsManager
) : BillingRepository {

    override val isPremiumFlow: Flow<Boolean> = settingsManager.settingsFlow.map { it.isPremium }

    override suspend fun isUserPremium(): Boolean = settingsManager.current().isPremium

    override suspend fun canGenerateNewStory(): Boolean {
        if (isUserPremium()) return true
        val created = maxOf(storyDao.getStoryCount(), settingsManager.current().storiesCreated)
        return created < BillingRepository.FREE_STORIES
    }

    override suspend fun purchaseSubscription(sku: String): Result<Boolean> {
        settingsManager.setPremium(true)
        return Result.success(true)
    }
}
