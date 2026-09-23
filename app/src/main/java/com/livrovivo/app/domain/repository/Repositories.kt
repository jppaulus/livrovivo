package com.livrovivo.app.domain.repository

import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.LiteracyTrail
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.ParentInsights
import com.livrovivo.app.domain.model.PhaseProgress
import com.livrovivo.app.domain.model.Story
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface StoryRepository {
    fun getStoriesFlow(): Flow<List<Story>>
    fun observeStory(storyId: String): Flow<Story?>
    suspend fun getStoryById(storyId: String): Story?

    /** Cria a história e o capítulo 1 (com IA ou, se [forceOffline], com o motor offline). */
    suspend fun createStory(
        child: ChildProfile,
        theme: String,
        themeId: String?,
        objective: ObjectiveType,
        forceOffline: Boolean = false
    ): Result<Story>

    /** Registra a escolha feita no capítulo [fromChapterIndex] e escreve o próximo. */
    suspend fun continueStory(storyId: String, fromChapterIndex: Int, choice: Choice): Result<Chapter>

    /** Volta para uma página, apagando o que veio depois, para a criança escolher outro caminho. */
    suspend fun rewindTo(storyId: String, chapterIndex: Int)

    suspend fun prepareContinuations(storyId: String) {}

    suspend fun markRead(storyId: String, chapterIndex: Int)
    suspend fun illustrateChapter(storyId: String, chapterIndex: Int): Result<String>
    suspend fun deleteStory(storyId: String)
    suspend fun deleteAllStories()
    fun observeTrash(): Flow<List<Story>>
    suspend fun restoreStory(storyId: String)
    suspend fun permanentlyDeleteStory(storyId: String)

    /** Histórias já criadas (inclusive apagadas), usado no limite do plano gratuito. */
    suspend fun countGeneratedStories(): Int
    suspend fun recordReadingSession(storyId: String, childId: String, startedAt: Long, durationMs: Long)
    suspend fun buildInsights(child: ChildProfile?): ParentInsights
}

interface ChildProfileRepository {
    fun getActiveProfileFlow(): Flow<ChildProfile?>
    suspend fun getActiveProfile(): ChildProfile?
    suspend fun saveProfile(profile: ChildProfile)
    fun observeProfiles(): Flow<List<ChildProfile>>
    suspend fun activateProfile(id: String)
}

interface LiteracyRepository {
    /** Conteúdo da trilha (lido uma vez e guardado). Falha com InvalidTrailException se o JSON estiver errado. */
    suspend fun getTrail(): LiteracyTrail

    fun observeProgress(childId: String): Flow<List<PhaseProgress>>
    suspend fun getProgress(childId: String): List<PhaseProgress>

    /** Registra uma tentativa da fase: guarda a melhor nota e soma tentativas e erros. */
    suspend fun recordAttempt(childId: String, phaseId: String, stars: Int, mistakes: Int): PhaseProgress
}

/** Livros "Eu leio": os que a criança ganhou na trilha e o que ela leu sozinha. */
interface EuLeioRepository {
    fun observeBooks(childId: String): Flow<List<Story>>

    /** Escreve e guarda os livros que a criança já ganhou e ainda não tem. Devolve só os novos. */
    suspend fun ensureEarnedBooks(child: ChildProfile): List<Story>

    /** Quantos livros a criança já ganhou e ainda não foram escritos. */
    suspend fun pendingBooks(child: ChildProfile): Int

    /**
     * Escreve os livros pendentes em segundo plano (com IA pode levar alguns segundos), sem prender a tela.
     * O livro aparece em [observeBooks] quando fica pronto.
     */
    fun requestEarnedBooks(child: ChildProfile)

    /** true enquanto algum livro está sendo escrito. */
    val isWriting: StateFlow<Boolean>

    /** "Li sozinho!" numa página: registro de uso para as conquistas e o painel, não uma avaliação. */
    suspend fun recordPageReadAlone(storyId: String, chapterIndex: Int, childId: String)
    suspend fun pagesReadAlone(storyId: String): Set<Int>

    /** A criança chegou ao fim do livro: ele ganha o selo "Eu li!". */
    suspend fun markBookFinished(storyId: String)
}

interface BillingRepository {
    val isPremiumFlow: Flow<Boolean>
    suspend fun isUserPremium(): Boolean
    suspend fun canGenerateNewStory(): Boolean
    suspend fun purchaseSubscription(sku: String): Result<Boolean>

    companion object {
        const val FREE_STORIES = 3
    }
}
