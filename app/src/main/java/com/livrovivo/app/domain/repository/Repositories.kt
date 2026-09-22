package com.livrovivo.app.domain.repository

import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.ParentInsights
import com.livrovivo.app.domain.model.Story
import kotlinx.coroutines.flow.Flow

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

interface BillingRepository {
    val isPremiumFlow: Flow<Boolean>
    suspend fun isUserPremium(): Boolean
    suspend fun canGenerateNewStory(): Boolean
    suspend fun purchaseSubscription(sku: String): Result<Boolean>

    companion object {
        const val FREE_STORIES = 3
    }
}
