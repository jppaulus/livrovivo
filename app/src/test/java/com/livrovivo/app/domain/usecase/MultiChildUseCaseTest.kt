package com.livrovivo.app.domain.usecase

import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.repository.BillingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comportamento com irmãos cadastrados: cada criança tem a própria estante e as
 * próprias métricas, mas o limite do plano gratuito continua sendo do aparelho.
 */
class MultiChildUseCaseTest {

    private val leo = ChildProfile("leo", "Leo", "3-5", listOf("dinossauros"))
    private val mia = ChildProfile("mia", "Mia", "6-8", listOf("espaço"))

    private fun storyFor(child: ChildProfile, id: String) = Story(
        id = id,
        childId = child.id,
        title = "Aventura de ${child.name}",
        theme = "floresta",
        objectiveType = "aventura",
        chapters = listOf(Chapter(1, "Era uma vez"))
    )

    private fun setup(): Pair<FakeStoryRepository, FakeChildProfileRepository> {
        val stories = FakeStoryRepository()
        val profiles = FakeChildProfileRepository(listOf(leo, mia), stories)
        stories.stories += storyFor(leo, "leo-1")
        stories.stories += storyFor(leo, "leo-2")
        stories.stories += storyFor(mia, "mia-1")
        return stories to profiles
    }

    @Test
    fun `shelf shows only the active child stories`() = runTest {
        val (stories, profiles) = setup()

        val shelf = GetStoriesUseCase(stories, profiles)().first()

        assertEquals(listOf("leo-1", "leo-2"), shelf.map { it.id })
    }

    @Test
    fun `switching child switches the shelf`() = runTest {
        val (stories, profiles) = setup()
        val getStories = GetStoriesUseCase(stories, profiles)

        SwitchChildUseCase(profiles)(mia.id)

        assertEquals(listOf("mia-1"), getStories().first().map { it.id })
        assertEquals("Mia", GetActiveChildUseCase(profiles).getDirect()?.name)
    }

    @Test
    fun `switching to an unknown child keeps the current one`() = runTest {
        val (_, profiles) = setup()

        SwitchChildUseCase(profiles)("nao-existe")

        assertEquals("Leo", GetActiveChildUseCase(profiles).getDirect()?.name)
    }

    @Test
    fun `a newly saved child becomes the active one`() = runTest {
        val (stories, profiles) = setup()
        val theo = ChildProfile("theo", "Theo", "9+")

        SaveChildProfileUseCase(profiles)(theo)

        assertEquals("Theo", GetActiveChildUseCase(profiles).getDirect()?.name)
        // Criança nova começa com a estante vazia, sem herdar histórias dos irmãos.
        assertTrue(GetStoriesUseCase(stories, profiles)().first().isEmpty())
    }

    @Test
    fun `editing an existing child does not change who is active`() = runTest {
        val (_, profiles) = setup()
        SwitchChildUseCase(profiles)(mia.id)

        SaveChildProfileUseCase(profiles)(leo.copy(name = "Leonardo"))

        assertEquals("Mia", GetActiveChildUseCase(profiles).getDirect()?.name)
        assertEquals("Leonardo", profiles.getProfiles().first { it.id == leo.id }.name)
    }

    @Test
    fun `deleting a child removes the stories of that child only`() = runTest {
        val (stories, profiles) = setup()

        val removed = DeleteChildProfileUseCase(profiles)(leo.id)

        assertTrue(removed)
        assertEquals(listOf("mia-1"), stories.stories.map { it.id })
        assertEquals(listOf("Mia"), profiles.getProfiles().map { it.name })
    }

    @Test
    fun `deleting the active child falls back to a sibling`() = runTest {
        val (_, profiles) = setup()

        DeleteChildProfileUseCase(profiles)(leo.id)

        assertEquals("Mia", GetActiveChildUseCase(profiles).getDirect()?.name)
    }

    @Test
    fun `the last child profile is never deleted`() = runTest {
        val stories = FakeStoryRepository()
        val profiles = FakeChildProfileRepository(listOf(leo), stories)
        stories.stories += storyFor(leo, "leo-1")

        val removed = DeleteChildProfileUseCase(profiles)(leo.id)

        assertFalse(removed)
        assertEquals(listOf("Leo"), profiles.getProfiles().map { it.name })
        assertEquals(listOf("leo-1"), stories.stories.map { it.id })
    }

    @Test
    fun `insights are scoped to the active child`() = runTest {
        val (stories, profiles) = setup()
        val insights = GetParentInsightsUseCase(stories, profiles)

        assertEquals(2, insights().storiesStarted)

        SwitchChildUseCase(profiles)(mia.id)
        assertEquals("Mia", insights().childName)
        assertEquals(1, insights().storiesStarted)
    }

    @Test
    fun `free quota counts the whole device, so siblings do not multiply free stories`() = runTest {
        val (stories, profiles) = setup()
        // 3 histórias no aparelho (2 do Leo + 1 da Mia) já esgotam o plano gratuito,
        // mesmo com nenhuma criança tendo criado 3 sozinha.
        stories.count = stories.stories.size
        val billing = FakeBillingRepository(canGenerate = false)

        assertEquals(BillingRepository.FREE_STORIES, stories.count)
        assertEquals(
            QuotaStatus.Limited(remaining = 0, max = BillingRepository.FREE_STORIES),
            CheckStoryQuotaUseCase(billing, stories)()
        )

        val result = GenerateStoryUseCase(stories, billing, profiles)(theme = "mar", objectiveType = "aventura")
        assertTrue(result.exceptionOrNull() is QuotaExceededException)
    }

    @Test
    fun `a story keeps its own child even when a sibling is active`() = runTest {
        val (stories, profiles) = setup()
        SwitchChildUseCase(profiles)(mia.id)

        // O leitor e a continuação da história resolvem a criança pelo dono da história.
        val owner = GetChildProfilesUseCase(profiles).byId(stories.stories.first { it.id == "leo-1" }.childId)

        assertEquals("Leo", owner?.name)
        assertEquals("Mia", GetActiveChildUseCase(profiles).getDirect()?.name)
    }

    @Test
    fun `with no child registered there is no active profile`() = runTest {
        val stories = FakeStoryRepository()
        val profiles = FakeChildProfileRepository(emptyList(), stories)

        assertNull(GetActiveChildUseCase(profiles).getDirect())
        assertTrue(GetChildProfilesUseCase(profiles)().first().isEmpty())
    }
}
