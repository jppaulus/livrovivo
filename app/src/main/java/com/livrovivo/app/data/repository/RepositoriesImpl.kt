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
import com.livrovivo.app.data.model.appJson
import kotlinx.serialization.encodeToString
import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.ParentInsights
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.StoryActivity
import com.livrovivo.app.domain.model.ThemeOption
import com.livrovivo.app.domain.model.Virtue
import com.livrovivo.app.domain.repository.BillingRepository
import com.livrovivo.app.domain.repository.ChildProfileRepository
import com.livrovivo.app.domain.repository.StoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
        storyDao.observeStoryWithChapters(storyId).map { it?.toDomain()?.let { story -> withSnapshot(story) } }

    override suspend fun getStoryById(storyId: String): Story? =
        storyDao.getStoryWithChapters(storyId)?.toDomain()?.let { withSnapshot(it) }

    private suspend fun withSnapshot(story: Story): Story {
        if (story.childSnapshot != null) return story
        val child = childProfileDao.getById(story.childId)?.toDomain() ?: fallbackChild(story)
        storyDao.saveSnapshot(story.id, appJson.encodeToString(child.toEntity()))
        return story.copy(childSnapshot = child)
    }

    override suspend fun createStory(
        child: ChildProfile,
        theme: String,
        themeId: String?,
        objective: ObjectiveType,
        forceOffline: Boolean
    ): Result<Story> {
        val ageGroup = AgeGroup.fromCode(child.ageGroup)
        val recent = storyDao.getAllStoriesWithChapters().map { it.toDomain() }
            .filter { it.childId == child.id && it.themeId == themeId }.maxByOrNull { it.createdAt }
        val previousVariant = recent?.chapters?.firstOrNull()?.sceneImagePrompt
            ?.substringBefore(".")?.substringAfterLast(":")?.toIntOrNull()
        val seed = if (previousVariant != null) listOf("c", "a", "b")[(previousVariant + 1) % 3] else UUID.randomUUID().toString()
        val brief = StoryBrief(
            child = child,
            companion = MagicalCompanion.findById(child.companionId),
            theme = theme,
            objective = objective,
            plannedChapters = ageGroup.plannedChapters,
            editionSeed = seed
        )
        return storyWriter.startStory(brief, themeId, objective.code, forceOffline).map { draft ->
            val now = System.currentTimeMillis()
            val story = draft.story.copy(createdAt = now, updatedAt = now, childSnapshot = child)
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

        val child = story.childSnapshot ?: fallbackChild(story)
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

        return try { storyWriter.continueStory(brief, storyWithChoice, choice)
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
        } catch (cancelled: CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable) {
                if (storyDao.getChaptersForStory(storyId).none { it.chapterIndex > last.index }) {
                    storyDao.updateSelectedChoice(storyId, last.index, null)
                }
            }
            throw cancelled
        }
    }

    override suspend fun prepareContinuations(storyId: String) = kotlinx.coroutines.supervisorScope {
        val story = getStoryById(storyId) ?: return@supervisorScope
        val last = story.lastChapter ?: return@supervisorScope
        if (story.isOffline || last.isEnding || story.deletedAt != null) return@supervisorScope
        val child = story.childSnapshot ?: fallbackChild(story)
        val brief = StoryBrief(child, MagicalCompanion.findById(story.companionId), story.theme,
            ObjectiveType.fromCode(story.objectiveType), story.plannedChapters)
        // Only the two immediate paths; no images, no recursive tree, no database mutations.
        last.choices.take(2).map { choice ->
            async { storyWriter.continueStory(brief, story, choice) }
        }.forEach { it.await() }
    }

    override suspend fun rewindTo(storyId: String, chapterIndex: Int) {
        val story = getStoryById(storyId) ?: return
        require(story.chapters.any { it.index == chapterIndex }) { "Página não encontrada." }
        if (story.chapters.none { it.index > chapterIndex }) return
        val copyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        try {
            val preserved = illustrationService.copyStoryAssets(story, copyId).copy(
                title = story.title.removeSuffix(" · caminho salvo") + " · caminho salvo",
                originId = story.originId ?: story.id,
                createdAt = now,
                updatedAt = now
            )
            storyDao.preserveAndRewind(preserved.toEntity(), preserved.chapters.map { it.toEntity(copyId) },
                storyId, chapterIndex, now)
        } catch (error: Exception) {
            // Database transaction failed: the original path is still intact.
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                if (storyDao.getStoryById(copyId) == null) illustrationService.deleteStoryAssets(copyId)
            }
            throw error
        }
    }

    override suspend fun markRead(storyId: String, chapterIndex: Int) {
        storyDao.updateLastRead(storyId, chapterIndex, System.currentTimeMillis())
        storyDao.markOpened(storyId, chapterIndex, System.currentTimeMillis())
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
            val child = story.childSnapshot
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
        storyDao.setDeletedAt(storyId, System.currentTimeMillis())
    }

    override fun observeTrash(): Flow<List<Story>> = storyDao.observeTrash().map { rows -> rows.map { it.toDomain() } }

    override suspend fun restoreStory(storyId: String) = storyDao.setDeletedAt(storyId, null)

    override suspend fun permanentlyDeleteStory(storyId: String) {
        val story = storyDao.getStoryById(storyId) ?: return
        require(story.deletedAt != null) { "Mova a história para a lixeira primeiro." }
        storyDao.deleteStory(storyId)
        storyDao.deleteSessionsForStory(storyId)
        withContext(Dispatchers.IO) { illustrationService.deleteStoryAssets(storyId) }
    }

    override suspend fun deleteAllStories() {
        val child = childProfileDao.getActiveProfile() ?: return
        storyDao.moveChildStoriesToTrash(child.id, System.currentTimeMillis())
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
        // Livros "Eu leio" ficam de fora: são da trilha e terão uma seção própria no painel.
        val stories = storyDao.getAllStoriesWithChapters().map { it.toDomain() }
            .filter { it.childId == child?.id && !it.isEuLeio }
        val activity = StoryActivity.from(stories)
        val name = child?.name ?: "a criança"
        val choices = activity.choices
        val virtues = choices.mapNotNull { it.virtue }
        val vocabulary = activity.vocabulary
        val themes = stories.distinctBy { it.originId ?: it.id }
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
            storiesStarted = stories.map { it.originId ?: it.id }.distinct().size,
            storiesCompleted = stories.filter { it.isCompleted }.map { it.originId ?: it.id }.distinct().size,
            pagesRead = activity.pagesOpened,
            minutesReading = (storyDao.childReadingMs(child?.id.orEmpty()) / 60_000L).toInt(),
            choicesMade = choices.size,
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
        childProfileDao.saveAndActivate(profile.toEntity())
    }

    override fun observeProfiles(): Flow<List<ChildProfile>> = childProfileDao.observeProfiles().map { rows -> rows.map { it.toDomain() } }
    override suspend fun activateProfile(id: String) = childProfileDao.activate(id)
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
