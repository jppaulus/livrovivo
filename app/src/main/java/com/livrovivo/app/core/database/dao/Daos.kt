package com.livrovivo.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.livrovivo.app.data.model.ChapterEntity
import com.livrovivo.app.data.model.ChildProfileEntity
import com.livrovivo.app.data.model.ReadingSessionEntity
import com.livrovivo.app.data.model.StoryEntity
import com.livrovivo.app.data.model.StoryWithChapters
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Transaction
    @Query("SELECT * FROM stories ORDER BY updatedAt DESC, createdAt DESC")
    fun observeStoriesWithChapters(): Flow<List<StoryWithChapters>>

    @Transaction
    @Query("SELECT * FROM stories WHERE id = :storyId LIMIT 1")
    fun observeStoryWithChapters(storyId: String): Flow<StoryWithChapters?>

    @Transaction
    @Query("SELECT * FROM stories WHERE id = :storyId LIMIT 1")
    suspend fun getStoryWithChapters(storyId: String): StoryWithChapters?

    @Transaction
    @Query("SELECT * FROM stories ORDER BY createdAt DESC")
    suspend fun getAllStoriesWithChapters(): List<StoryWithChapters>

    @Query("SELECT * FROM stories WHERE id = :storyId LIMIT 1")
    suspend fun getStoryById(storyId: String): StoryEntity?

    @Query("SELECT * FROM story_chapters WHERE storyId = :storyId ORDER BY chapterIndex ASC")
    suspend fun getChaptersForStory(storyId: String): List<ChapterEntity>

    @Query("SELECT COUNT(*) FROM stories")
    suspend fun getStoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Transaction
    suspend fun insertStoryWithChapters(story: StoryEntity, chapters: List<ChapterEntity>) {
        insertStory(story)
        insertChapters(chapters)
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

    @Query("DELETE FROM reading_sessions WHERE storyId = :storyId")
    suspend fun deleteSessionsForStory(storyId: String)
}

@Dao
interface ChildProfileDao {
    @Query("SELECT * FROM child_profiles ORDER BY createdAt DESC LIMIT 1")
    fun getActiveProfileFlow(): Flow<ChildProfileEntity?>

    @Query("SELECT * FROM child_profiles ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveProfile(): ChildProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ChildProfileEntity)
}
