package com.livrovivo.app.data.repository

import com.livrovivo.app.core.database.dao.LiteracyDao
import com.livrovivo.app.core.literacy.TrailParser
import com.livrovivo.app.data.model.toDomain
import com.livrovivo.app.domain.model.LiteracyTrail
import com.livrovivo.app.domain.model.PhaseProgress
import com.livrovivo.app.domain.repository.LiteracyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Trilha da Leitura: o conteúdo vem do JSON em assets e o progresso fica no Room.
 * [loadTrailJson] lê o arquivo; nos testes ele pode ler direto do disco.
 */
class LiteracyRepositoryImpl(
    private val literacyDao: LiteracyDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val loadTrailJson: () -> String
) : LiteracyRepository {

    private val trailLock = Mutex()
    private var trail: LiteracyTrail? = null

    override suspend fun getTrail(): LiteracyTrail = trailLock.withLock {
        trail ?: withContext(Dispatchers.IO) { TrailParser.parse(loadTrailJson()) }.also { trail = it }
    }

    override fun observeProgress(childId: String): Flow<List<PhaseProgress>> =
        literacyDao.observeProgress(childId).map { list -> list.map { it.toDomain() } }

    override suspend fun getProgress(childId: String): List<PhaseProgress> =
        literacyDao.getProgress(childId).map { it.toDomain() }

    override suspend fun recordAttempt(childId: String, phaseId: String, stars: Int, mistakes: Int): PhaseProgress =
        literacyDao.recordAttempt(childId, phaseId, stars, mistakes, clock()).toDomain()
}
