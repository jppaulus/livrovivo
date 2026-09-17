package com.livrovivo.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.livrovivo.app.core.database.dao.ChildProfileDao
import com.livrovivo.app.core.database.dao.StoryDao
import com.livrovivo.app.data.model.ChapterEntity
import com.livrovivo.app.data.model.ChildProfileEntity
import com.livrovivo.app.data.model.ReadingSessionEntity
import com.livrovivo.app.data.model.StoryEntity

@Database(
    entities = [
        ChildProfileEntity::class,
        StoryEntity::class,
        ChapterEntity::class,
        ReadingSessionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LivroVivoDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun childProfileDao(): ChildProfileDao

    companion object {
        @Volatile
        private var INSTANCE: LivroVivoDatabase? = null

        /** Preserva perfis e histórias existentes ao adicionar ilustrações, narração e progresso. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE child_profiles ADD COLUMN gender TEXT NOT NULL DEFAULT 'neutro'")
                db.execSQL("ALTER TABLE child_profiles ADD COLUMN skinTone TEXT")
                db.execSQL("ALTER TABLE child_profiles ADD COLUMN hairColor TEXT")
                db.execSQL("ALTER TABLE child_profiles ADD COLUMN hairStyle TEXT")
                db.execSQL("ALTER TABLE child_profiles ADD COLUMN wearsGlasses INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE stories ADD COLUMN themeId TEXT")
                db.execSQL("ALTER TABLE stories ADD COLUMN companionId TEXT NOT NULL DEFAULT 'bento'")
                db.execSQL("ALTER TABLE stories ADD COLUMN characterSheet TEXT")
                db.execSQL("ALTER TABLE stories ADD COLUMN plannedChapters INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE stories ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stories ADD COLUMN lastReadChapter INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE stories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stories ADD COLUMN isOffline INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE story_chapters ADD COLUMN imagePath TEXT")
                db.execSQL("ALTER TABLE story_chapters ADD COLUMN narrationScript TEXT")
                db.execSQL("ALTER TABLE story_chapters ADD COLUMN selectedChoiceText TEXT")
                db.execSQL("ALTER TABLE story_chapters ADD COLUMN newWordsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE story_chapters ADD COLUMN mood TEXT")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS reading_sessions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "storyId TEXT NOT NULL, " +
                        "childId TEXT NOT NULL, " +
                        "startedAt INTEGER NOT NULL, " +
                        "durationMs INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_sessions_storyId ON reading_sessions (storyId)")

                // Histórias antigas: ordenação, conclusão, tamanho e companheiro coerentes com o novo leitor.
                db.execSQL("UPDATE stories SET updatedAt = createdAt")
                db.execSQL(
                    "UPDATE stories SET isCompleted = 1 WHERE id IN " +
                        "(SELECT storyId FROM story_chapters WHERE isEnding = 1)"
                )
                db.execSQL(
                    "UPDATE stories SET plannedChapters = " +
                        "(SELECT MAX(chapterIndex) FROM story_chapters WHERE story_chapters.storyId = stories.id) " +
                        "WHERE isCompleted = 1"
                )
                db.execSQL(
                    "UPDATE stories SET companionId = " +
                        "(SELECT companionId FROM child_profiles WHERE child_profiles.id = stories.childId) " +
                        "WHERE EXISTS (SELECT 1 FROM child_profiles WHERE child_profiles.id = stories.childId)"
                )
            }
        }

        fun getInstance(context: Context): LivroVivoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LivroVivoDatabase::class.java,
                    "livro_vivo.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
