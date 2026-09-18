package com.livrovivo.app.domain.repository

import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.ParentInsights
import com.livrovivo.app.domain.model.Story
import kotlinx.coroutines.flow.Flow

interface StoryRepository {
    /** Estante de uma criança; com [childId] nulo, devolve as histórias de todas. */
    fun getStoriesFlow(childId: String?): Flow<List<Story>>
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

    suspend fun markRead(storyId: String, chapterIndex: Int)
    suspend fun illustrateChapter(storyId: String, chapterIndex: Int): Result<String>
    suspend fun deleteStory(storyId: String)
    suspend fun deleteAllStories()

    /** Apaga as histórias, ilustrações e sessões de leitura de uma criança. */
    suspend fun deleteStoriesOf(childId: String)

    /**
     * Histórias já criadas (inclusive apagadas), usado no limite do plano gratuito.
     * É somado no aparelho todo: cadastrar irmãos não multiplica as histórias grátis.
     */
    suspend fun countGeneratedStories(): Int
    suspend fun recordReadingSession(storyId: String, childId: String, startedAt: Long, durationMs: Long)

    /** Métricas só da [child] indicada (leitura, virtudes, vocabulário). */
    suspend fun buildInsights(child: ChildProfile?): ParentInsights
}

interface ChildProfileRepository {
    /** Todos os irmãos cadastrados, na ordem em que entraram. */
    fun getProfilesFlow(): Flow<List<ChildProfile>>
    suspend fun getProfiles(): List<ChildProfile>

    /** Um perfil específico, mesmo que outro irmão esteja ativo agora. */
    suspend fun getProfile(childId: String): ChildProfile?

    /** Criança cuja estante está aberta. */
    fun getActiveProfileFlow(): Flow<ChildProfile?>
    suspend fun getActiveProfile(): ChildProfile?

    /** Cria ou atualiza um perfil; um perfil novo já entra como ativo. */
    suspend fun saveProfile(profile: ChildProfile)

    suspend fun setActiveProfile(childId: String)

    /**
     * Apaga o perfil e tudo que é dele. Devolve false (sem apagar nada) quando é o
     * último perfil, porque o app não funciona sem nenhuma criança cadastrada.
     */
    suspend fun deleteProfile(childId: String): Boolean
}

interface BillingRepository {
    /**
     * Se a assinatura está valendo. Quem escreve este valor é o Google Play (via
     * `BillingManager`); o app só lê. Abrir a compra é papel da tela de assinatura,
     * porque precisa da Activity e o resultado chega de forma assíncrona.
     */
    val isPremiumFlow: Flow<Boolean>
    suspend fun isUserPremium(): Boolean
    suspend fun canGenerateNewStory(): Boolean

    /** Reconsulta as assinaturas ativas no Google Play. */
    suspend fun refreshSubscription()

    companion object {
        const val FREE_STORIES = 3
    }
}
