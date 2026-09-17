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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBillingRepository(
    var isPremium: Boolean = false,
    var canGenerate: Boolean = true
) : BillingRepository {
    override val isPremiumFlow: Flow<Boolean> = flowOf(isPremium)
    override suspend fun isUserPremium(): Boolean = isPremium
    override suspend fun canGenerateNewStory(): Boolean = canGenerate
    override suspend fun purchaseSubscription(sku: String): Result<Boolean> = Result.success(true)
}

class FakeStoryRepository(
    var count: Int = 0
) : StoryRepository {
    val stories = mutableListOf<Story>()
    var lastForceOffline: Boolean? = null

    override fun getStoriesFlow(): Flow<List<Story>> = flowOf(stories)
    override fun observeStory(storyId: String): Flow<Story?> = flowOf(stories.find { it.id == storyId })
    override suspend fun getStoryById(storyId: String): Story? = stories.find { it.id == storyId }

    override suspend fun createStory(
        child: ChildProfile,
        theme: String,
        themeId: String?,
        objective: ObjectiveType,
        forceOffline: Boolean
    ): Result<Story> {
        lastForceOffline = forceOffline
        val story = Story(
            id = "test-id",
            childId = child.id,
            title = "${child.name} e $theme",
            theme = theme,
            objectiveType = objective.code,
            themeId = themeId,
            chapters = listOf(Chapter(1, "Era uma vez", listOf(Choice("Abraçar o amigo", 2))))
        )
        stories.add(story)
        count++
        return Result.success(story)
    }

    override suspend fun continueStory(storyId: String, fromChapterIndex: Int, choice: Choice): Result<Chapter> =
        Result.success(
            Chapter(
                index = fromChapterIndex + 1,
                content = "Continuação adaptada para a escolha: ${choice.text}",
                isEnding = true
            )
        )

    override suspend fun rewindTo(storyId: String, chapterIndex: Int) = Unit
    override suspend fun markRead(storyId: String, chapterIndex: Int) = Unit
    override suspend fun illustrateChapter(storyId: String, chapterIndex: Int): Result<String> = Result.success("/tmp/img.jpg")
    override suspend fun deleteStory(storyId: String) {
        stories.removeIf { it.id == storyId }
    }
    override suspend fun deleteAllStories() {
        stories.clear()
    }
    override suspend fun countGeneratedStories(): Int = count
    override suspend fun recordReadingSession(storyId: String, childId: String, startedAt: Long, durationMs: Long) = Unit
    override suspend fun buildInsights(child: ChildProfile?): ParentInsights = ParentInsights(childName = child?.name.orEmpty())
}

class FakeChildProfileRepository(
    var activeProfile: ChildProfile? = ChildProfile("child-1", "Leo", "3-5", listOf("espaço"))
) : ChildProfileRepository {
    override fun getActiveProfileFlow(): Flow<ChildProfile?> = flowOf(activeProfile)
    override suspend fun getActiveProfile(): ChildProfile? = activeProfile
    override suspend fun saveProfile(profile: ChildProfile) {
        activeProfile = profile
    }
}

class UseCaseTest {

    @Test
    fun `when user is free and within quota, generation succeeds`() = runTest {
        val generateUseCase = GenerateStoryUseCase(FakeStoryRepository(count = 1), FakeBillingRepository(), FakeChildProfileRepository())

        val result = generateUseCase(theme = "aventura", objectiveType = "emocional", themeId = "floresta_encantada")

        assertTrue(result.isSuccess)
        assertEquals("Leo e aventura", result.getOrNull()?.title)
        assertEquals("floresta_encantada", result.getOrNull()?.themeId)
    }

    @Test
    fun `when quota is reached, generation returns quota failure`() = runTest {
        val generateUseCase = GenerateStoryUseCase(
            FakeStoryRepository(count = 3),
            FakeBillingRepository(canGenerate = false),
            FakeChildProfileRepository()
        )

        val result = generateUseCase(theme = "aventura", objectiveType = "emocional")

        assertTrue(result.exceptionOrNull() is QuotaExceededException)
    }

    @Test
    fun `without child profile generation fails`() = runTest {
        val generateUseCase = GenerateStoryUseCase(FakeStoryRepository(), FakeBillingRepository(), FakeChildProfileRepository(null))
        assertTrue(generateUseCase(theme = "aventura", objectiveType = "emocional").isFailure)
    }

    @Test
    fun `offline flag is forwarded to the repository`() = runTest {
        val repository = FakeStoryRepository()
        GenerateStoryUseCase(repository, FakeBillingRepository(), FakeChildProfileRepository())(
            theme = "mar",
            objectiveType = "cognitivo",
            forceOffline = true
        )
        assertEquals(true, repository.lastForceOffline)
    }

    @Test
    fun `quota status calculation works correctly`() = runTest {
        val quota = CheckStoryQuotaUseCase(FakeBillingRepository(isPremium = false), FakeStoryRepository(count = 2))()

        assertTrue(quota is QuotaStatus.Limited)
        assertEquals(1, (quota as QuotaStatus.Limited).remaining)
    }

    @Test
    fun `premium users have unlimited quota`() = runTest {
        val quota = CheckStoryQuotaUseCase(FakeBillingRepository(isPremium = true), FakeStoryRepository(count = 50))()
        assertEquals(QuotaStatus.Unlimited, quota)
    }

    @Test
    fun `delete use cases remove one or all stories`() = runTest {
        val repository = FakeStoryRepository()
        val generate = GenerateStoryUseCase(repository, FakeBillingRepository(), FakeChildProfileRepository())
        generate(theme = "mar", objectiveType = "cognitivo")
        repository.stories.add(repository.stories.first().copy(id = "outra"))

        DeleteStoryUseCase(repository)("outra")
        assertEquals(listOf("test-id"), repository.stories.map { it.id })

        DeleteAllStoriesUseCase(repository)()
        assertTrue(repository.stories.isEmpty())
    }

    @Test
    fun `when child makes choice, continue story targets the next page`() = runTest {
        val continueUseCase = ContinueStoryUseCase(FakeStoryRepository())

        val result = continueUseCase(storyId = "s-1", fromChapterIndex = 1, choice = Choice("Abraçar o amigo", 2))

        assertEquals(2, result.getOrNull()?.index)
        assertTrue(result.getOrNull()?.content?.contains("Abraçar o amigo") == true)
    }
}
