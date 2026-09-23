package com.livrovivo.app.data.repository

import com.livrovivo.app.core.database.dao.LiteracyDao
import com.livrovivo.app.core.literacy.TrailParser
import com.livrovivo.app.data.model.LiteracyProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class LiteracyRepositoryImplTest {

    private val dao = FakeLiteracyDao()
    private var now = 1_000L
    private var loads = 0
    private val repo = LiteracyRepositoryImpl(dao, clock = { now }) {
        loads++
        File("src/main/assets/${TrailParser.ASSET_PATH}").readText(Charsets.UTF_8)
    }

    @Test
    fun `trail is read from the file only once`() = runTest {
        val first = repo.getTrail()
        val second = repo.getTrail()

        assertSame(first, second)
        assertEquals(1, loads)
    }

    @Test
    fun `best stars are kept and every attempt is counted`() = runTest {
        repo.recordAttempt("lia", "silabas_b", stars = 0, mistakes = 5)
        now = 2_000L
        repo.recordAttempt("lia", "silabas_b", stars = 3, mistakes = 0)
        now = 3_000L
        val last = repo.recordAttempt("lia", "silabas_b", stars = 1, mistakes = 3)

        assertEquals(3, last.stars)
        assertEquals(3, last.attempts)
        assertEquals(8, last.mistakes)
        assertEquals("first completion with a star", 2_000L, last.completedAt)
    }

    @Test
    fun `zero stars never marks the phase as completed`() = runTest {
        val progress = repo.recordAttempt("lia", "vogais_a", stars = 0, mistakes = 5)

        assertNull(progress.completedAt)
        assertFalse(progress.isCompleted)
    }

    @Test
    fun `invalid numbers are kept inside the allowed range`() = runTest {
        val progress = repo.recordAttempt("lia", "vogais_a", stars = 7, mistakes = -2)

        assertEquals(3, progress.stars)
        assertEquals(0, progress.mistakes)
    }

    @Test
    fun `progress is kept separately for each child`() = runTest {
        repo.recordAttempt("lia", "vogais_a", stars = 3, mistakes = 0)
        repo.recordAttempt("caio", "vogais_a", stars = 1, mistakes = 4)

        assertEquals(3, repo.getProgress("lia").single().stars)
        assertEquals(1, repo.observeProgress("caio").first().single().stars)
    }

    private class FakeLiteracyDao : LiteracyDao {
        private val rows = MutableStateFlow<Map<Pair<String, String>, LiteracyProgressEntity>>(emptyMap())

        override fun observeProgress(childId: String): Flow<List<LiteracyProgressEntity>> =
            rows.map { all -> all.values.filter { it.childId == childId } }

        override suspend fun getProgress(childId: String) = rows.value.values.filter { it.childId == childId }

        override suspend fun getPhase(childId: String, phaseId: String) = rows.value[childId to phaseId]

        override suspend fun upsert(progress: LiteracyProgressEntity) {
            rows.value = rows.value + ((progress.childId to progress.phaseId) to progress)
        }
    }
}
