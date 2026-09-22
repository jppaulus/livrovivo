package com.livrovivo.app.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

// --- Room Entities ---

@Entity(tableName = "child_profiles")
@Serializable
data class ChildProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ageGroup: String,
    val interestsJson: String,
    val companionId: String = "bento",
    val createdAt: Long = System.currentTimeMillis(),
    val gender: String = "neutro",
    val skinTone: String? = null,
    val hairColor: String? = null,
    val hairStyle: String? = null,
    val wearsGlasses: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val isActive: Boolean = false
)

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String,
    val childId: String,
    val title: String,
    val theme: String,
    val objectiveType: String,
    val coverImageUrl: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val themeId: String? = null,
    val companionId: String = "bento",
    val characterSheet: String? = null,
    val plannedChapters: Int = 5,
    val isCompleted: Boolean = false,
    val lastReadChapter: Int = 1,
    val updatedAt: Long = 0,
    val isOffline: Boolean = false,
    val childSnapshotJson: String? = null,
    val deletedAt: Long? = null,
    val originId: String? = null
)

@Entity(
    tableName = "story_chapters",
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["storyId", "chapterIndex"], unique = true)]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storyId: String,
    val chapterIndex: Int,
    val content: String,
    val choicesJson: String,
    val isEnding: Boolean,
    val audioUrl: String?,
    val sceneImagePrompt: String?,
    val imagePath: String? = null,
    val narrationScript: String? = null,
    val selectedChoiceText: String? = null,
    val newWordsJson: String = "[]",
    val mood: String? = null,
    val openedAt: Long? = null
)

@Entity(
    tableName = "reading_sessions",
    indices = [Index(value = ["storyId"])]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storyId: String,
    val childId: String,
    val startedAt: Long,
    val durationMs: Long
)

data class StoryWithChapters(
    @Embedded val story: StoryEntity,
    @Relation(parentColumn = "id", entityColumn = "storyId")
    val chapters: List<ChapterEntity>
)

// --- JSON persistido nas colunas ---

@Serializable
data class ChoiceDto(
    val text: String,
    val targetChapterIndex: Int,
    val virtue: String? = null
)
