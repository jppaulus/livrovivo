package com.livrovivo.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.livrovivo.app.data.model.ChapterEntity
import com.livrovivo.app.data.model.ChildProfileEntity
import com.livrovivo.app.data.model.LiteracyPageReadEntity
import com.livrovivo.app.data.model.LiteracyProgressEntity
import com.livrovivo.app.data.model.ReadingSessionEntity
import com.livrovivo.app.data.model.StoryEntity
import com.livrovivo.app.data.model.StoryWithChapters
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Transaction
    @Query("SELECT * FROM stories WHERE deletedAt IS NULL ORDER BY updatedAt DESC, createdAt DESC")
    fun observeStoriesWithChapters(): Flow<List<StoryWithChapters>>

    @Transaction
    @Query("SELECT * FROM stories WHERE id = :storyId AND deletedAt IS NULL LIMIT 1")
    fun observeStoryWithChapters(storyId: String): Flow<StoryWithChapters?>

    @Transaction
    @Query("SELECT * FROM stories WHERE id = :storyId AND deletedAt IS NULL LIMIT 1")
    suspend fun getStoryWithChapters(storyId: String): StoryWithChapters?

    @Transaction
    @Query("SELECT * FROM stories WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAllStoriesWithChapters(): List<StoryWithChapters>

    @Query("SELECT * FROM stories WHERE id = :storyId LIMIT 1")
    suspend fun getStoryById(storyId: String): StoryEntity?

    @Query("SELECT * FROM story_chapters WHERE storyId = :storyId ORDER BY chapterIndex ASC")
    suspend fun getChaptersForStory(storyId: String): List<ChapterEntity>

    /** Aventuras criadas (conta para o limite grátis). Livros "Eu leio" não entram. */
    @Query("SELECT COUNT(*) FROM stories WHERE originId IS NULL AND kind = 'aventura'")
    suspend fun getStoryCount(): Int

    /** Livros de um tipo já criados para a criança, inclusive os que estão na lixeira. */
    @Query("SELECT COUNT(*) FROM stories WHERE childId = :childId AND kind = :kind")
    suspend fun countBooks(childId: String, kind: String): Int

    @Transaction
    @Query("SELECT * FROM stories WHERE childId = :childId AND kind = :kind AND deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeBooks(childId: String, kind: String): Flow<List<StoryWithChapters>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Transaction
    suspend fun insertStoryWithChapters(story: StoryEntity, chapters: List<ChapterEntity>) {
        insertStory(story)
        insertChapters(chapters)
    }

    @Transaction
    @Query("SELECT * FROM stories WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<StoryWithChapters>>

    @Query("UPDATE stories SET deletedAt = :deletedAt WHERE id = :storyId")
    suspend fun setDeletedAt(storyId: String, deletedAt: Long?)

    @Query("UPDATE stories SET deletedAt = :deletedAt WHERE childId = :childId AND deletedAt IS NULL")
    suspend fun moveChildStoriesToTrash(childId: String, deletedAt: Long)

    @Query("UPDATE stories SET childSnapshotJson = :snapshot WHERE id = :storyId AND childSnapshotJson IS NULL")
    suspend fun saveSnapshot(storyId: String, snapshot: String)

    @Query("UPDATE story_chapters SET openedAt = COALESCE(openedAt, :openedAt) WHERE storyId = :storyId AND chapterIndex = :chapterIndex")
    suspend fun markOpened(storyId: String, chapterIndex: Int, openedAt: Long)

    @Transaction
    suspend fun preserveAndRewind(copy: StoryEntity, chapters: List<ChapterEntity>, storyId: String, chapterIndex: Int, now: Long) {
        insertStoryWithChapters(copy, chapters)
        rewindTo(storyId, chapterIndex, now)
    }

    @Query("DELETE FROM stories WHERE id = :storyId")
    suspend fun deleteStory(storyId: String)

    @Query("DELETE FROM stories")
    suspend fun deleteAllStories()

    @Query("DELETE FROM reading_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM story_chapters WHERE storyId = :storyId AND chapterIndex > :chapterIndex")
    suspend fun deleteChaptersAfter(storyId: String, chapterIndex: Int)

    @Query("UPDATE story_chapters SET selectedChoiceText = :choiceText WHERE storyId = :storyId AND chapterIndex = :chapterIndex")
    suspend fun updateSelectedChoice(storyId: String, chapterIndex: Int, choiceText: String?)

    @Query("UPDATE story_chapters SET imagePath = :imagePath WHERE storyId = :storyId AND chapterIndex = :chapterIndex")
    suspend fun updateChapterImage(storyId: String, chapterIndex: Int, imagePath: String?)

    @Query("UPDATE stories SET coverImageUrl = :coverPath WHERE id = :storyId")
    suspend fun updateCover(storyId: String, coverPath: String?)

    @Query("UPDATE stories SET lastReadChapter = :chapterIndex, updatedAt = :updatedAt WHERE id = :storyId")
    suspend fun updateLastRead(storyId: String, chapterIndex: Int, updatedAt: Long)

    @Query("UPDATE stories SET isCompleted = :isCompleted, updatedAt = :updatedAt WHERE id = :storyId")
    suspend fun updateCompleted(storyId: String, isCompleted: Boolean, updatedAt: Long)

    @Query("UPDATE stories SET plannedChapters = :plannedChapters WHERE id = :storyId")
    suspend fun updatePlannedChapters(storyId: String, plannedChapters: Int)

    @Transaction
    suspend fun rewindTo(storyId: String, chapterIndex: Int, updatedAt: Long) {
        deleteChaptersAfter(storyId, chapterIndex)
        updateSelectedChoice(storyId, chapterIndex, null)
        updateCompleted(storyId, false, updatedAt)
        updateLastRead(storyId, chapterIndex, updatedAt)
    }

    @Insert
    suspend fun insertReadingSession(session: ReadingSessionEntity)

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reading_sessions")
    suspend fun totalReadingMs(): Long

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reading_sessions WHERE childId = :childId")
    suspend fun childReadingMs(childId: String): Long

    @Query("DELETE FROM reading_sessions WHERE storyId = :storyId")
    suspend fun deleteSessionsForStory(storyId: String)
}

@Dao
interface ChildProfileDao {
    @Query("SELECT * FROM child_profiles ORDER BY isActive DESC, createdAt DESC LIMIT 1")
    fun getActiveProfileFlow(): Flow<ChildProfileEntity?>

    @Query("SELECT * FROM child_profiles ORDER BY isActive DESC, createdAt DESC LIMIT 1")
    suspend fun getActiveProfile(): ChildProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ChildProfileEntity)

    @Query("SELECT * FROM child_profiles ORDER BY createdAt ASC")
    fun observeProfiles(): Flow<List<ChildProfileEntity>>

    @Query("SELECT * FROM child_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChildProfileEntity?

    @Query("UPDATE child_profiles SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END WHERE EXISTS (SELECT 1 FROM child_profiles WHERE id = :id)")
    suspend fun activate(id: String)

    @Transaction
    suspend fun saveAndActivate(profile: ChildProfileEntity) {
        insertProfile(profile)
        activate(profile.id)
    }
}

@Dao
interface LiteracyDao {
    @Query("SELECT * FROM literacy_progress WHERE childId = :childId")
    fun observeProgress(childId: String): Flow<List<LiteracyProgressEntity>>

    @Query("SELECT * FROM literacy_progress WHERE childId = :childId")
    suspend fun getProgress(childId: String): List<LiteracyProgressEntity>

    @Query("SELECT * FROM literacy_progress WHERE childId = :childId AND phaseId = :phaseId LIMIT 1")
    suspend fun getPhase(childId: String, phaseId: String): LiteracyProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: LiteracyProgressEntity)

    @Query("SELECT * FROM literacy_page_reads WHERE storyId = :storyId AND chapterIndex = :chapterIndex LIMIT 1")
    suspend fun getPageRead(storyId: String, chapterIndex: Int): LiteracyPageReadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPageRead(read: LiteracyPageReadEntity)

    @Transaction
    suspend fun recordPageRead(storyId: String, chapterIndex: Int, childId: String, now: Long) {
        val current = getPageRead(storyId, chapterIndex)
        upsertPageRead(
            current?.copy(timesRead = current.timesRead + 1)
                ?: LiteracyPageReadEntity(storyId, chapterIndex, childId, firstReadAt = now, timesRead = 1)
        )
    }

    @Query("SELECT chapterIndex FROM literacy_page_reads WHERE storyId = :storyId")
    suspend fun pagesReadAlone(storyId: String): List<Int>

    @Query("SELECT COUNT(*) FROM literacy_page_reads WHERE childId = :childId")
    suspend fun countPagesReadAlone(childId: String): Int

    /** Lê e grava na mesma transação, para duas tentativas seguidas não se sobrescreverem. */
    @Transaction
    suspend fun recordAttempt(childId: String, phaseId: String, stars: Int, mistakes: Int, now: Long): LiteracyProgressEntity {
        val updated = (getPhase(childId, phaseId) ?: LiteracyProgressEntity.empty(childId, phaseId))
            .withAttempt(stars, mistakes, now)
        upsert(updated)
        return updated
    }
}
