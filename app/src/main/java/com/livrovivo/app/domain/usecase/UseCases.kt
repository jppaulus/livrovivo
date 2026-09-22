package com.livrovivo.app.domain.usecase

import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.ParentInsights
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.repository.BillingRepository
import com.livrovivo.app.domain.repository.ChildProfileRepository
import com.livrovivo.app.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow

class QuotaExceededException : IllegalStateException(
    "Limite de histórias gratuitas atingido. Assine o Livro Vivo Premium para histórias ilimitadas!"
)

class GenerateStoryUseCase(
    private val storyRepository: StoryRepository,
    private val billingRepository: BillingRepository,
    private val childProfileRepository: ChildProfileRepository
) {
    suspend operator fun invoke(
        theme: String,
        objectiveType: String,
        themeId: String? = null,
        forceOffline: Boolean = false
    ): Result<Story> {
        if (!billingRepository.canGenerateNewStory()) {
            return Result.failure(QuotaExceededException())
        }

        val child = childProfileRepository.getActiveProfile()
            ?: return Result.failure(IllegalStateException("Nenhum perfil de criança ativo encontrado."))

        return storyRepository.createStory(
            child = child,
            theme = theme,
            themeId = themeId,
            objective = ObjectiveType.fromCode(objectiveType),
            forceOffline = forceOffline
        )
    }
}

class GetStoriesUseCase(
    private val storyRepository: StoryRepository
) {
    operator fun invoke(): Flow<List<Story>> = storyRepository.getStoriesFlow()
}

class GetStoryByIdUseCase(
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(storyId: String): Story? = storyRepository.getStoryById(storyId)
    fun observe(storyId: String): Flow<Story?> = storyRepository.observeStory(storyId)
}

class ContinueStoryUseCase(
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(storyId: String, fromChapterIndex: Int, choice: Choice): Result<Chapter> =
        storyRepository.continueStory(storyId, fromChapterIndex, choice)
}

class RewindStoryUseCase(
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(storyId: String, chapterIndex: Int) = storyRepository.rewindTo(storyId, chapterIndex)
}

class IllustrateChapterUseCase(
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(storyId: String, chapterIndex: Int): Result<String> =
        storyRepository.illustrateChapter(storyId, chapterIndex)
}

class DeleteStoryUseCase(
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(storyId: String) = storyRepository.deleteStory(storyId)
}

class DeleteAllStoriesUseCase(
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke() = storyRepository.deleteAllStories()
}

class SaveChildProfileUseCase(
    private val childProfileRepository: ChildProfileRepository
) {
    suspend operator fun invoke(profile: ChildProfile) {
        childProfileRepository.saveProfile(profile)
    }
}

class GetActiveChildUseCase(
    private val childProfileRepository: ChildProfileRepository
) {
    operator fun invoke(): Flow<ChildProfile?> = childProfileRepository.getActiveProfileFlow()
    suspend fun getDirect(): ChildProfile? = childProfileRepository.getActiveProfile()
    fun profiles(): Flow<List<ChildProfile>> = childProfileRepository.observeProfiles()
    suspend fun activate(id: String) = childProfileRepository.activateProfile(id)
}

class CheckStoryQuotaUseCase(
    private val billingRepository: BillingRepository,
    private val storyRepository: StoryRepository
) {
    suspend operator fun invoke(): QuotaStatus {
        if (billingRepository.isUserPremium()) return QuotaStatus.Unlimited
        val count = storyRepository.countGeneratedStories()
        val max = BillingRepository.FREE_STORIES
        return QuotaStatus.Limited(remaining = (max - count).coerceAtLeast(0), max = max)
    }
}

class GetParentInsightsUseCase(
    private val storyRepository: StoryRepository,
    private val childProfileRepository: ChildProfileRepository
) {
    suspend operator fun invoke(): ParentInsights =
        storyRepository.buildInsights(childProfileRepository.getActiveProfile())
}

sealed class QuotaStatus {
    data object Unlimited : QuotaStatus()
    data class Limited(val remaining: Int, val max: Int) : QuotaStatus()
}
