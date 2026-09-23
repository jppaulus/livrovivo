package com.livrovivo.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.livrovivo.app.core.ai.BackendConfig
import com.livrovivo.app.core.ai.GeminiService
import com.livrovivo.app.core.database.LivroVivoDatabase
import com.livrovivo.app.core.literacy.DecodableValidator
import com.livrovivo.app.core.literacy.GeminiBookAi
import com.livrovivo.app.core.literacy.LiteracyBookWriter
import com.livrovivo.app.core.literacy.LiteracyRules
import com.livrovivo.app.core.literacy.TrailParser
import com.livrovivo.app.data.model.toDomain
import com.livrovivo.app.data.model.toEntity
import com.livrovivo.app.data.repository.EuLeioRepositoryImpl
import com.livrovivo.app.data.repository.LiteracyRepositoryImpl
import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.core.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LiteracyStorageTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun trailJson(): String = context.assets.open(TrailParser.ASSET_PATH).bufferedReader().use { it.readText() }

    /** Esquema exato da versão 4 (o da versão 3 + MIGRATION_3_4), com duas histórias, lixeira e uma sessão. */
    private fun createV4(old: SQLiteDatabase) {
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
    }

    @Test fun migrationPreservesV4StoriesSessionsAndTrash() = runBlocking {
        val name = "migration-v4-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            createV4(old)
            old.version = 4
        }
        val db = Room.databaseBuilder(context, LivroVivoDatabase::class.java, name)
            .addMigrations(LivroVivoDatabase.MIGRATION_4_5, LivroVivoDatabase.MIGRATION_5_6).build()
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
            // As aventuras antigas continuam contando para o limite grátis.
            assertEquals(2, db.storyDao().getStoryCount())

            // As tabelas novas funcionam logo depois da migração.
            assertTrue(db.literacyDao().getProgress("a").isEmpty())
            db.literacyDao().recordAttempt("a", "vogais_a", stars = 2, mistakes = 1, now = 50)
            assertEquals(2, db.literacyDao().getPhase("a", "vogais_a")!!.stars)
            db.literacyDao().recordPageRead("s", 1, "a", now = 60)
            assertEquals(listOf(1), db.literacyDao().pagesReadAlone("s"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun migrationFromV5KeepsProgressAndEuLeioBooks() = runBlocking {
        val name = "migration-v5-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            createV4(old)
            // O que a MIGRATION_4_5 faz, para chegar ao esquema exato da versão 5.
            old.execSQL("CREATE TABLE IF NOT EXISTS literacy_progress (childId TEXT NOT NULL, phaseId TEXT NOT NULL, stars INTEGER NOT NULL, attempts INTEGER NOT NULL, mistakes INTEGER NOT NULL, completedAt INTEGER, PRIMARY KEY(childId, phaseId))")
            old.execSQL("ALTER TABLE stories ADD COLUMN kind TEXT NOT NULL DEFAULT 'aventura'")
            old.execSQL("INSERT INTO literacy_progress VALUES ('a','vogais_a',3,2,1,40)")
            old.execSQL("INSERT INTO stories VALUES ('livro','a','O DOCE DE LIA','Eu leio','cognitivo',NULL,8,'silabas','luna',NULL,4,0,1,8,1,NULL,NULL,NULL,'eu_leio')")
            old.execSQL("INSERT INTO story_chapters (storyId,chapterIndex,content,choicesJson,isEnding,newWordsJson) VALUES ('livro',1,'LIA TEM UM DOCE.','[]',0,'[\"DOCE\"]')")
            old.version = 5
        }
        val db = Room.databaseBuilder(context, LivroVivoDatabase::class.java, name)
            .addMigrations(LivroVivoDatabase.MIGRATION_5_6).build()
        try {
            assertEquals(3, db.literacyDao().getPhase("a", "vogais_a")!!.stars)
            val book = db.storyDao().getStoryWithChapters("livro")!!.toDomain()
            assertTrue(book.isEuLeio)
            assertEquals("DOCE", book.chapters.single().newWords.single())
            assertEquals(1, db.storyDao().countBooks("a", Story.KIND_EU_LEIO))
            assertEquals("o livro Eu leio não conta no limite grátis", 2, db.storyDao().getStoryCount())
            assertEquals(3, db.storyDao().getAllStoriesWithChapters().size)
            db.literacyDao().recordPageRead("livro", 1, "a", now = 70)
            assertEquals(1, db.literacyDao().countPagesReadAlone("a"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun earnedBooksAreWrittenOnceAndStayOutOfTheFreeQuota() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LivroVivoDatabase::class.java).build()
        try {
            val literacy = LiteracyRepositoryImpl(db.literacyDao()) { trailJson() }
            val books = EuLeioRepositoryImpl(literacy, db.literacyDao(), db.storyDao(), LiteracyBookWriter(ai = null))
            val child = ChildProfile("a", "Lia", AgeGroup.TODDLER.code, companionId = "luna")
            db.childProfileDao().saveAndActivate(child.toEntity())
            val trail = literacy.getTrail()
            val letters = trail.module("vogais")!!.phases + trail.module("consoantes")!!.phases
            val syllables = trail.module("silabas")!!.phases

            (letters + syllables.take(2)).forEach { literacy.recordAttempt(child.id, it.id, stars = 3, mistakes = 0) }
            assertTrue("2 fases de sílabas ainda não dão livro", books.ensureEarnedBooks(child).isEmpty())

            literacy.recordAttempt(child.id, syllables[2].id, stars = 1, mistakes = 4)
            val first = books.ensureEarnedBooks(child).single()
            assertTrue(first.isEuLeio)
            assertEquals(4, first.chapters.size)
            assertTrue(first.chapters.all { it.choices.isEmpty() })
            assertTrue(first.chapters.last().isEnding)
            assertEquals("silabas", first.themeId)

            // O texto salvo é o que a criança consegue ler com B, C e D.
            val saved = db.storyDao().getStoryWithChapters(first.id)!!.toDomain()
            val knowledge = LiteracyRules.knowledge(trail, (letters + syllables.take(3)).map { it.id }.toSet())
            (listOf(saved.title) + saved.chapters.map { it.content }).forEach { text ->
                assertEquals(text, emptyList<String>(), DecodableValidator.invalidWords(text, knowledge, trail.supportWords.toSet(), "Lia"))
            }

            assertTrue("pedir de novo não duplica", books.ensureEarnedBooks(child).isEmpty())
            assertEquals("livro Eu leio não conta no limite grátis", 0, db.storyDao().getStoryCount())

            syllables.drop(3).take(2).forEach { literacy.recordAttempt(child.id, it.id, stars = 3, mistakes = 0) }
            assertEquals(1, books.ensureEarnedBooks(child).size)
            assertEquals(2, books.observeBooks(child.id).first().size)

            books.recordPageReadAlone(first.id, 1, child.id)
            books.recordPageReadAlone(first.id, 1, child.id)
            books.recordPageReadAlone(first.id, 2, child.id)
            assertEquals(setOf(1, 2), books.pagesReadAlone(first.id))
            assertEquals(2, db.literacyDao().getPageRead(first.id, 1)!!.timesRead)

            books.markBookFinished(first.id)
            assertTrue(db.storyDao().getStoryWithChapters(first.id)!!.story.isCompleted)

            db.storyDao().setDeletedAt(first.id, 1L)
            assertTrue("livro na lixeira não faz outro aparecer", books.ensureEarnedBooks(child).isEmpty())
            assertEquals(1, books.observeBooks(child.id).first().size)
        } finally {
            db.close()
        }
    }

    /** Resposta no formato do Gemini com um livro "Eu leio" dentro. */
    private fun geminiAnswer(request: okhttp3.Request, title: String, vararg pages: String): okhttp3.Response {
        val book = org.json.JSONObject().put("title", title).put("pages", org.json.JSONArray().apply {
            pages.forEachIndexed { index, text ->
                put(org.json.JSONObject().put("text", text).put("illustrationPrompt", "Page ${index + 1}: a girl in a cozy room."))
            }
        })
        val body = org.json.JSONObject().put("candidates", org.json.JSONArray().put(
            org.json.JSONObject().put("content", org.json.JSONObject().put("parts", org.json.JSONArray().put(
                org.json.JSONObject().put("text", book.toString()))))))
        return okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toString().toResponseBody("application/json".toMediaType())).build()
    }

    @Test fun aiBooksAreCheckedRetriedAndSavedWithTheirIllustrationPrompts() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LivroVivoDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settingsFile = File(context.cacheDir, "eu-leio-ai-${UUID.randomUUID()}.preferences_pb")
        val settings = SettingsManager(context, PreferenceDataStoreFactory.create(scope = scope, produceFile = { settingsFile }))
        val bodies = mutableListOf<String>()
        // Lia: 1ª resposta com GATO (ela ainda não lê), 2ª certa. Caio: duas respostas com GATO.
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val body = okio.Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            bodies += body
            // GATO só aparece no pedido da 2ª tentativa (na lista do que foi recusado).
            val readable = body.contains("Nome no livro: LIA") && body.contains("GATO")
            if (readable) {
                geminiAnswer(chain.request(), "O DOCE DE LIA", "LIA TEM UM DOCE.", "O DOCE É DE LIA.", "O DOCE ESTÁ NA BOCA.", "LIA TEM UM DADO E UM DOCE.")
            } else {
                geminiAnswer(chain.request(), "O GATO", "O GATO TEM UM DADO.", "O GATO É DE LIA.", "O GATO ESTÁ AQUI.", "LIA TEM UM GATO.")
            }
        }.build()
        try {
            settings.saveGeminiApiKey("test-only-never-sent-to-network")
            val gemini = GeminiService(context, http, settings, BackendConfig("", ""))
            val literacy = LiteracyRepositoryImpl(db.literacyDao()) { trailJson() }
            val books = EuLeioRepositoryImpl(literacy, db.literacyDao(), db.storyDao(), LiteracyBookWriter(GeminiBookAi(gemini)))
            val trail = literacy.getTrail()
            val firstBookPhases = trail.module("vogais")!!.phases + trail.module("consoantes")!!.phases +
                trail.module("silabas")!!.phases.take(3)

            val lia = ChildProfile("a", "Lia", AgeGroup.TODDLER.code, companionId = "luna")
            db.childProfileDao().saveAndActivate(lia.toEntity())
            firstBookPhases.forEach { literacy.recordAttempt(lia.id, it.id, stars = 3, mistakes = 0) }
            val aiBook = books.ensureEarnedBooks(lia).single()
            assertEquals("a 1ª resposta foi recusada e a 2ª aceita", 2, bodies.size)
            assertTrue(!aiBook.isOffline)
            val saved = db.storyDao().getStoryWithChapters(aiBook.id)!!.toDomain()
            assertEquals("LIA TEM UM DOCE.", saved.sortedChapters.first().content)
            assertTrue(saved.sortedChapters.all { it.sceneImagePrompt?.startsWith("Page ") == true })
            assertTrue(saved.characterSheet!!.startsWith("LIA: "))

            val caio = ChildProfile("b", "Caio", AgeGroup.TODDLER.code)
            db.childProfileDao().saveAndActivate(caio.toEntity())
            firstBookPhases.forEach { literacy.recordAttempt(caio.id, it.id, stars = 3, mistakes = 0) }
            val fallback = books.ensureEarnedBooks(caio).single()
            assertEquals("tenta só duas vezes", 4, bodies.size)
            assertTrue("duas recusas: livro offline", fallback.isOffline)
            val knowledge = LiteracyRules.knowledge(trail, firstBookPhases.map { it.id }.toSet())
            fallback.chapters.forEach { page ->
                assertEquals(emptyList<String>(), DecodableValidator.invalidWords(page.content, knowledge, trail.supportWords.toSet(), "Caio"))
            }
        } finally {
            db.close()
            scope.cancel()
            settingsFile.delete()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
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
            val repo = LiteracyRepositoryImpl(db.literacyDao(), clock = { now }) { trailJson() }
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
