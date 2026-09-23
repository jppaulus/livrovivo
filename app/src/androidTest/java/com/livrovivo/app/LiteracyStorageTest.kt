package com.livrovivo.app

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.livrovivo.app.core.database.LivroVivoDatabase
import com.livrovivo.app.core.literacy.TrailParser
import com.livrovivo.app.data.model.toDomain
import com.livrovivo.app.data.model.toEntity
import com.livrovivo.app.data.repository.LiteracyRepositoryImpl
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.Story
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LiteracyStorageTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun migrationPreservesV4StoriesSessionsAndTrash() = runBlocking {
        val name = "migration-v4-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            // Esquema exato da versão 4 (o da versão 3 + MIGRATION_3_4).
            old.execSQL("CREATE TABLE child_profiles (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, ageGroup TEXT NOT NULL, interestsJson TEXT NOT NULL, companionId TEXT NOT NULL, createdAt INTEGER NOT NULL, gender TEXT NOT NULL, skinTone TEXT, hairColor TEXT, hairStyle TEXT, wearsGlasses INTEGER NOT NULL, isActive INTEGER NOT NULL DEFAULT 0)")
            old.execSQL("CREATE TABLE stories (id TEXT NOT NULL PRIMARY KEY, childId TEXT NOT NULL, title TEXT NOT NULL, theme TEXT NOT NULL, objectiveType TEXT NOT NULL, coverImageUrl TEXT, createdAt INTEGER NOT NULL, themeId TEXT, companionId TEXT NOT NULL, characterSheet TEXT, plannedChapters INTEGER NOT NULL, isCompleted INTEGER NOT NULL, lastReadChapter INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isOffline INTEGER NOT NULL, childSnapshotJson TEXT, deletedAt INTEGER, originId TEXT)")
            old.execSQL("CREATE TABLE story_chapters (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, storyId TEXT NOT NULL, chapterIndex INTEGER NOT NULL, content TEXT NOT NULL, choicesJson TEXT NOT NULL, isEnding INTEGER NOT NULL, audioUrl TEXT, sceneImagePrompt TEXT, imagePath TEXT, narrationScript TEXT, selectedChoiceText TEXT, newWordsJson TEXT NOT NULL, mood TEXT, openedAt INTEGER, FOREIGN KEY(storyId) REFERENCES stories(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE UNIQUE INDEX index_story_chapters_storyId_chapterIndex ON story_chapters(storyId, chapterIndex)")
            old.execSQL("CREATE TABLE reading_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, storyId TEXT NOT NULL, childId TEXT NOT NULL, startedAt INTEGER NOT NULL, durationMs INTEGER NOT NULL)")
            old.execSQL("CREATE INDEX index_reading_sessions_storyId ON reading_sessions(storyId)")
            old.execSQL("INSERT INTO child_profiles VALUES ('a','Lia','3-5','[]','luna',1,'menina',NULL,NULL,NULL,0,1)")
            old.execSQL("INSERT INTO stories VALUES ('s','a','O Caracol','Floresta','aventura',NULL,1,'floresta_encantada','luna',NULL,4,1,4,5,1,'{\"id\":\"a\",\"name\":\"Lia\",\"ageGroup\":\"3-5\",\"interestsJson\":\"[]\"}',NULL,NULL)")
            old.execSQL("INSERT INTO stories VALUES ('copia','a','O Caracol','Floresta','aventura',NULL,2,'floresta_encantada','luna',NULL,4,0,2,6,1,NULL,NULL,'s')")
            old.execSQL("INSERT INTO stories VALUES ('lixo','a','Na lixeira','Espaço','aventura',NULL,3,NULL,'luna',NULL,4,0,1,7,1,NULL,99,NULL)")
            old.execSQL("INSERT INTO story_chapters (storyId,chapterIndex,content,choicesJson,isEnding,newWordsJson,openedAt) VALUES ('s',1,'Lia ouviu um som.','[{\"text\":\"Olhar\",\"targetChapterIndex\":2,\"virtue\":\"curiosidade\"}]',0,'[]',10)")
            old.execSQL("INSERT INTO story_chapters (storyId,chapterIndex,content,choicesJson,isEnding,newWordsJson,selectedChoiceText) VALUES ('s',2,'Fim da aventura.','[]',1,'[\"caracol\"]',NULL)")
            old.execSQL("INSERT INTO story_chapters (storyId,chapterIndex,content,choicesJson,isEnding,newWordsJson) VALUES ('copia',1,'Lia ouviu um som.','[]',0,'[]')")
            old.execSQL("INSERT INTO reading_sessions (storyId,childId,startedAt,durationMs) VALUES ('s','a',1,90000)")
            old.version = 4
        }
        val db = Room.databaseBuilder(context, LivroVivoDatabase::class.java, name)
            .addMigrations(LivroVivoDatabase.MIGRATION_4_5).build()
        try {
            val stories = db.storyDao().getAllStoriesWithChapters().map { it.toDomain() }
            assertEquals(setOf("s", "copia"), stories.map { it.id }.toSet())
            assertTrue(stories.all { it.kind == Story.KIND_ADVENTURE })
            val story = stories.single { it.id == "s" }
            assertEquals(listOf("Lia ouviu um som.", "Fim da aventura."), story.sortedChapters.map { it.content })
            assertEquals("curiosidade", story.chapters.first().choices.single().virtue!!.code)
            assertEquals(10L, story.chapters.first().openedAt)
            assertEquals("Lia", story.childSnapshot!!.name)
            assertTrue(story.isCompleted)
            assertEquals("s", stories.single { it.id == "copia" }.originId)
            assertEquals("lixo", db.storyDao().observeTrash().first().single().story.id)
            assertEquals(90000L, db.storyDao().childReadingMs("a"))
            assertEquals("a", db.childProfileDao().getActiveProfile()!!.id)

            // A tabela nova funciona logo depois da migração.
            assertTrue(db.literacyDao().getProgress("a").isEmpty())
            db.literacyDao().recordAttempt("a", "vogais_a", stars = 2, mistakes = 1, now = 50)
            assertEquals(2, db.literacyDao().getPhase("a", "vogais_a")!!.stars)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun euLeioBooksAndPhaseProgressAreSavedPerChild() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LivroVivoDatabase::class.java).build()
        try {
            val book = Story(id = "livro", childId = "a", title = "A Bola", theme = "Trilha", objectiveType = "cognitivo",
                plannedChapters = 4, isOffline = true, kind = Story.KIND_EU_LEIO,
                chapters = listOf(Chapter(1, "A BOLA É DE LIA.", emptyList(), isEnding = true)))
            db.storyDao().insertStoryWithChapters(book.toEntity(), book.chapters.map { it.toEntity(book.id) })
            val saved = db.storyDao().getStoryWithChapters("livro")!!.toDomain()
            assertTrue(saved.isEuLeio)

            var now = 100L
            val repo = LiteracyRepositoryImpl(db.literacyDao(), clock = { now }) {
                context.assets.open(TrailParser.ASSET_PATH).bufferedReader().use { it.readText() }
            }
            assertEquals(51, repo.getTrail().phases.size)
            repo.recordAttempt("a", "silabas_b", stars = 0, mistakes = 5)
            assertNull(repo.getProgress("a").single().completedAt)
            now = 200L
            repo.recordAttempt("a", "silabas_b", stars = 3, mistakes = 0)
            now = 300L
            val progress = repo.recordAttempt("a", "silabas_b", stars = 1, mistakes = 4)
            assertEquals(3, progress.stars)
            assertEquals(3, progress.attempts)
            assertEquals(9, progress.mistakes)
            assertEquals(200L, progress.completedAt)
            assertTrue(repo.observeProgress("b").first().isEmpty())
        } finally {
            db.close()
        }
    }
}
