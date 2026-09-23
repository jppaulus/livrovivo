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
    val originId: String? = null,
    /** "aventura" (história interativa) ou "eu_leio" (livro da Trilha da Leitura). */
    @androidx.room.ColumnInfo(defaultValue = "aventura") val kind: String = "aventura"
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

/** Melhor resultado de cada fase da Trilha da Leitura, por criança. */
@Entity(tableName = "literacy_progress", primaryKeys = ["childId", "phaseId"])
data class LiteracyProgressEntity(
    val childId: String,
    val phaseId: String,
    val stars: Int,
    val attempts: Int,
    val mistakes: Int,
    val completedAt: Long?
) {
    /**
     * Soma uma nova tentativa: guarda a melhor nota, conta tentativas e erros e marca a data da
     * primeira conclusão com pelo menos 1 estrela.
     */
    fun withAttempt(stars: Int, mistakes: Int, now: Long): LiteracyProgressEntity {
        val safeStars = stars.coerceIn(0, 3)
        return copy(
            stars = maxOf(this.stars, safeStars),
            attempts = attempts + 1,
            mistakes = this.mistakes + mistakes.coerceAtLeast(0),
            completedAt = completedAt ?: now.takeIf { safeStars >= 1 }
        )
    }

    companion object {
        fun empty(childId: String, phaseId: String) = LiteracyProgressEntity(childId, phaseId, 0, 0, 0, null)
    }
}

/** Página de um livro "Eu leio" que a criança marcou como "Li sozinho!" (registro de uso, não avaliação). */
@Entity(tableName = "literacy_page_reads", primaryKeys = ["storyId", "chapterIndex"])
data class LiteracyPageReadEntity(
    val storyId: String,
    val chapterIndex: Int,
    val childId: String,
    val firstReadAt: Long,
    val timesRead: Int
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
