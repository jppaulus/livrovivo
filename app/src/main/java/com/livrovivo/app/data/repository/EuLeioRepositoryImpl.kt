package com.livrovivo.app.data.repository

import com.livrovivo.app.core.database.dao.LiteracyDao
import com.livrovivo.app.core.database.dao.StoryDao
import com.livrovivo.app.core.literacy.LiteracyBookWriter
import com.livrovivo.app.core.literacy.LiteracyRules
import com.livrovivo.app.data.model.toDomain
import com.livrovivo.app.data.model.toEntity
import com.livrovivo.app.data.model.toEuLeioStory
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.repository.EuLeioRepository
import com.livrovivo.app.domain.repository.LiteracyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class EuLeioRepositoryImpl(
    private val literacyRepository: LiteracyRepository,
    private val literacyDao: LiteracyDao,
    private val storyDao: StoryDao,
    private val bookWriter: LiteracyBookWriter,
    private val clock: () -> Long = System::currentTimeMillis
) : EuLeioRepository {

    /** O resultado de uma fase e a abertura da trilha podem pedir livros ao mesmo tempo: um de cada vez. */
    private val creating = Mutex()

    override fun observeBooks(childId: String): Flow<List<Story>> =
        storyDao.observeBooks(childId, Story.KIND_EU_LEIO).map { list -> list.map { it.toDomain() } }

    override suspend fun ensureEarnedBooks(child: ChildProfile): List<Story> = creating.withLock {
        val trail = literacyRepository.getTrail()
        val completed = LiteracyRules.completedPhaseIds(literacyRepository.getProgress(child.id))
        val triggers = LiteracyRules.bookTriggerPhases(trail, completed)
        // Conta também os da lixeira: jogar um livro fora não faz outro aparecer no lugar.
        val existing = storyDao.countBooks(child.id, Story.KIND_EU_LEIO)
        triggers.drop(existing).mapIndexedNotNull { offset, phase ->
            val number = existing + offset
            val written = bookWriter.write(
                trail = trail,
                knowledge = LiteracyRules.knowledgeUpTo(trail, completed, phase),
                child = child,
                seed = child.id.hashCode() + number,
                focusSyllables = LiteracyRules.phaseSyllables(trail, phase)
            ) ?: return@mapIndexedNotNull null
            val story = written.book.toEuLeioStory(
                id = UUID.randomUUID().toString(),
                child = child,
                moduleId = trail.moduleOf(phase.id)?.id.orEmpty(),
                isOffline = written.isOffline,
                createdAt = clock() + number
            )
            storyDao.insertStoryWithChapters(story.toEntity(), story.chapters.map { it.toEntity(story.id) })
            story
        }
    }

    override suspend fun recordPageReadAlone(storyId: String, chapterIndex: Int, childId: String) =
        literacyDao.recordPageRead(storyId, chapterIndex, childId, clock())

    override suspend fun pagesReadAlone(storyId: String): Set<Int> = literacyDao.pagesReadAlone(storyId).toSet()

    override suspend fun markBookFinished(storyId: String) = storyDao.updateCompleted(storyId, true, clock())
}
