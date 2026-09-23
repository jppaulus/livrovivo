package com.livrovivo.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.livrovivo.app.core.database.dao.ChildProfileDao
import com.livrovivo.app.core.database.dao.LiteracyDao
import com.livrovivo.app.core.database.dao.StoryDao
import com.livrovivo.app.data.model.ChapterEntity
import com.livrovivo.app.data.model.ChildProfileEntity
import com.livrovivo.app.data.model.LiteracyPageReadEntity
import com.livrovivo.app.data.model.LiteracyProgressEntity
import com.livrovivo.app.data.model.ReadingSessionEntity
import com.livrovivo.app.data.model.StoryEntity
import com.livrovivo.app.data.model.appJson
import kotlinx.serialization.encodeToString

@Database(
    entities = [
        ChildProfileEntity::class,
        StoryEntity::class,
        ChapterEntity::class,
        ReadingSessionEntity::class,
        LiteracyProgressEntity::class,
        LiteracyPageReadEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class LivroVivoDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao
    abstract fun childProfileDao(): ChildProfileDao
    abstract fun literacyDao(): LiteracyDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE child_profiles ADD COLUMN isActive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE child_profiles SET isActive = 1 WHERE id = (SELECT id FROM child_profiles ORDER BY createdAt DESC LIMIT 1)")
                db.execSQL("ALTER TABLE stories ADD COLUMN childSnapshotJson TEXT")
                db.execSQL("ALTER TABLE stories ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE stories ADD COLUMN originId TEXT")
                db.execSQL("ALTER TABLE story_chapters ADD COLUMN openedAt INTEGER")
                // Freeze the known profile at upgrade time, before the family can edit it.
                db.query("SELECT p.*, s.id AS story_id FROM stories s JOIN child_profiles p ON s.childId = p.id").use { cursor ->
                    fun text(name: String): String? = cursor.getColumnIndexOrThrow(name).let { index ->
                        if (cursor.isNull(index)) null else cursor.getString(index)
                    }
                    while (cursor.moveToNext()) {
                        val profile = ChildProfileEntity(
                            id = text("id")!!, name = text("name")!!, ageGroup = text("ageGroup")!!,
                            interestsJson = text("interestsJson")!!, companionId = text("companionId")!!,
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                            gender = text("gender")!!, skinTone = text("skinTone"), hairColor = text("hairColor"),
                            hairStyle = text("hairStyle"), wearsGlasses = cursor.getInt(cursor.getColumnIndexOrThrow("wearsGlasses")) != 0
                        )
                        db.execSQL("UPDATE stories SET childSnapshotJson = ? WHERE id = ?",
                            arrayOf(appJson.encodeToString(profile), text("story_id")))
                    }
                }
            }
        }

        /** Trilha da Leitura: progresso por fase e tipo de livro. Histórias e sessões ficam intactas. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS literacy_progress (" +
                        "childId TEXT NOT NULL, " +
                        "phaseId TEXT NOT NULL, " +
                        "stars INTEGER NOT NULL, " +
                        "attempts INTEGER NOT NULL, " +
                        "mistakes INTEGER NOT NULL, " +
                        "completedAt INTEGER, " +
                        "PRIMARY KEY(childId, phaseId))"
                )
                db.execSQL("ALTER TABLE stories ADD COLUMN kind TEXT NOT NULL DEFAULT 'aventura'")
            }
        }

        /** Páginas dos livros "Eu leio" marcadas como "Li sozinho!". Nada existente muda. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS literacy_page_reads (" +
                        "storyId TEXT NOT NULL, " +
                        "chapterIndex INTEGER NOT NULL, " +
                        "childId TEXT NOT NULL, " +
                        "firstReadAt INTEGER NOT NULL, " +
                        "timesRead INTEGER NOT NULL, " +
                        "PRIMARY KEY(storyId, chapterIndex))"
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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
