package com.livrovivo.app

import android.content.Context
import androidx.room.Room
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.livrovivo.app.core.ai.BackendConfig
import com.livrovivo.app.core.ai.GeminiService
import com.livrovivo.app.core.ai.StoryWriter
import com.livrovivo.app.core.database.LivroVivoDatabase
import com.livrovivo.app.core.illustration.IllustrationService
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.data.model.*
import com.livrovivo.app.data.repository.StoryRepositoryImpl
import com.livrovivo.app.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FamilyStorageTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun preparedBranchesDoNotChooseForTheChildAndAreReusedOnTap() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LivroVivoDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settingsFile = File(context.cacheDir, "prefetch-${UUID.randomUUID()}.preferences_pb")
        val settings = SettingsManager(context, PreferenceDataStoreFactory.create(scope = scope, produceFile = { settingsFile }))
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            val body = okio.Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            val action = if (body.contains("Levantar a folha")) "folha" else "pedra"
            val page = org.json.JSONObject()
                .put("content", "Lia levantou a $action e encontrou um caracol muito pequeno. Ele carregava uma mochila azul e procurava o caminho de volta para casa. Bento apontou uma trilha de flores amarelas perto do riacho.")
                .put("choices", org.json.JSONArray()
                    .put(org.json.JSONObject().put("text", "Seguir a trilha").put("virtue", "curiosidade"))
                    .put(org.json.JSONObject().put("text", "Chamar o caracol").put("virtue", "empatia")))
                .put("isEnding", false).put("mood", "alegre")
                .put("illustrationPrompt", "A snail beneath a leaf beside a stream.").put("newWords", org.json.JSONArray())
            val response = org.json.JSONObject().put("candidates", org.json.JSONArray().put(
                org.json.JSONObject().put("content", org.json.JSONObject().put("parts", org.json.JSONArray().put(
                    org.json.JSONObject().put("text", page.toString()))))))
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            settings.saveGeminiApiKey("test-only-never-sent-to-network")
            val child = ChildProfile("prefetch-child", "Lia", AgeGroup.TODDLER.code)
            db.childProfileDao().saveAndActivate(child.toEntity())
            val story = Story(id = "prefetch-book", childId = child.id, title = "O Caracol", theme = "Floresta",
                objectiveType = "aventura", childSnapshot = child, plannedChapters = 4, isOffline = false,
                chapters = listOf(Chapter(1, "Lia ouviu um som perto de uma folha e uma pedra.",
                    listOf(Choice("Levantar a folha", 2), Choice("Examinar a pedra", 2)))))
            db.storyDao().insertStoryWithChapters(story.toEntity(), story.chapters.map { it.toEntity(story.id) })
            val gemini = GeminiService(context, http, settings, BackendConfig("", ""))
            val repo = StoryRepositoryImpl(db.storyDao(), db.childProfileDao(), StoryWriter(gemini),
                IllustrationService(context, gemini, settings), settings)
            repo.prepareContinuations(story.id)
            assertEquals(2, calls.get())
            val untouched = repo.getStoryById(story.id)!!
            assertEquals(1, untouched.chapters.size)
            assertNull(untouched.lastChapter!!.selectedChoiceText)
            val next = repo.continueStory(story.id, 1, story.lastChapter!!.choices[1]).getOrThrow()
            assertTrue(next.content.contains("levantou a pedra"))
            assertEquals("tap must reuse the prepared page", 2, calls.get())
            assertEquals("Examinar a pedra", repo.getStoryById(story.id)!!.chapters.first().selectedChoiceText)
        } finally {
            db.close()
            scope.cancel()
            settingsFile.delete()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test fun migrationPreservesV3StoriesAndFreezesTheirOriginalProfile() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE child_profiles (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, ageGroup TEXT NOT NULL, interestsJson TEXT NOT NULL, companionId TEXT NOT NULL, createdAt INTEGER NOT NULL, gender TEXT NOT NULL, skinTone TEXT, hairColor TEXT, hairStyle TEXT, wearsGlasses INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE stories (id TEXT NOT NULL PRIMARY KEY, childId TEXT NOT NULL, title TEXT NOT NULL, theme TEXT NOT NULL, objectiveType TEXT NOT NULL, coverImageUrl TEXT, createdAt INTEGER NOT NULL, themeId TEXT, companionId TEXT NOT NULL, characterSheet TEXT, plannedChapters INTEGER NOT NULL, isCompleted INTEGER NOT NULL, lastReadChapter INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isOffline INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE story_chapters (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, storyId TEXT NOT NULL, chapterIndex INTEGER NOT NULL, content TEXT NOT NULL, choicesJson TEXT NOT NULL, isEnding INTEGER NOT NULL, audioUrl TEXT, sceneImagePrompt TEXT, imagePath TEXT, narrationScript TEXT, selectedChoiceText TEXT, newWordsJson TEXT NOT NULL, mood TEXT, FOREIGN KEY(storyId) REFERENCES stories(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE UNIQUE INDEX index_story_chapters_storyId_chapterIndex ON story_chapters(storyId, chapterIndex)")
            old.execSQL("CREATE TABLE reading_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, storyId TEXT NOT NULL, childId TEXT NOT NULL, startedAt INTEGER NOT NULL, durationMs INTEGER NOT NULL)")
            old.execSQL("CREATE INDEX index_reading_sessions_storyId ON reading_sessions(storyId)")
            old.execSQL("INSERT INTO child_profiles VALUES ('a','Lia','3-5','[]','bento',1,'menina',NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO child_profiles VALUES ('b','Caio','6-8','[]','bento',2,'menino',NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO stories VALUES ('s','a','Livro antigo','Floresta','aventura',NULL,1,NULL,'bento',NULL,4,0,1,1,1)")
            old.execSQL("INSERT INTO story_chapters (storyId,chapterIndex,content,choicesJson,isEnding,newWordsJson) VALUES ('s',1,'Lia encontrou uma luz.','[]',0,'[]')")
            old.execSQL("INSERT INTO reading_sessions (storyId,childId,startedAt,durationMs) VALUES ('s','a',1,60000)")
            old.version = 3
        }
        val db = Room.databaseBuilder(context, LivroVivoDatabase::class.java, name)
            .addMigrations(LivroVivoDatabase.MIGRATION_3_4).build()
        try {
            val story = db.storyDao().getStoryWithChapters("s")!!.toDomain()
            assertEquals("Lia encontrou uma luz.", story.chapters.single().content)
            assertEquals("Lia", story.childSnapshot!!.name)
            assertNull(story.chapters.single().openedAt)
            assertEquals(60000L, db.storyDao().childReadingMs("a"))
            assertEquals("b", db.childProfileDao().getActiveProfile()!!.id)
            db.childProfileDao().activate("a")
            assertEquals("a", db.childProfileDao().getActiveProfile()!!.id)
            assertEquals(2, db.childProfileDao().observeProfiles().first().size)
            db.childProfileDao().saveAndActivate(db.childProfileDao().getById("a")!!.copy(name = "Outro nome"))
            assertEquals("Lia", db.storyDao().getStoryWithChapters("s")!!.toDomain().childSnapshot!!.name)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun offlineStorySurvivesProfileSwitchRewindTrashRestoreAndOriginalDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LivroVivoDatabase::class.java).build()
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settingsFile = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        val settings = SettingsManager(context, PreferenceDataStoreFactory.create(scope = settingsScope, produceFile = { settingsFile }))
        val http = OkHttpClient.Builder().addInterceptor { throw AssertionError("Tests must not contact AI providers") }.build()
        val gemini = GeminiService(context, http, settings, BackendConfig("", ""))
        val illustrations = IllustrationService(context, gemini, settings)
        val repo = StoryRepositoryImpl(db.storyDao(), db.childProfileDao(), StoryWriter(gemini), illustrations, settings)
        val assetIds = mutableListOf<String>()
        try {
            val child = ChildProfile("a", "Lia", AgeGroup.TODDLER.code)
            db.childProfileDao().saveAndActivate(child.toEntity())
            val story = repo.createStory(child, "Floresta", "floresta_encantada", ObjectiveType.AVENTURA, true).getOrThrow()
            assetIds += story.id
            db.childProfileDao().saveAndActivate(ChildProfile("b", "Caio", AgeGroup.KID.code).toEntity())
            val next = repo.continueStory(story.id, 1, story.chapters.single().choices.first()).getOrThrow()
            assertTrue(next.content.contains("Lia"))
            assertFalse(next.content.contains("Caio"))
            repo.markRead(story.id, 1)
            assertEquals(1, repo.buildInsights(child).pagesRead)
            assertEquals(0, repo.buildInsights(ChildProfile("b", "Caio", AgeGroup.KID.code)).pagesRead)
            val image = File(context.filesDir, "stories/${story.id}/page_2_test.jpg")
            image.parentFile!!.mkdirs()
            image.writeText("image-fixture")
            db.storyDao().updateChapterImage(story.id, 2, image.path)
            repo.rewindTo(story.id, 1)
            val saved = repo.getStoriesFlow().first().single { it.originId == story.id }
            assetIds += saved.id
            assertEquals(2, saved.chapters.size)
            assertEquals(1, repo.getStoryById(story.id)!!.chapters.size)
            assertEquals(1, db.storyDao().getStoryCount())
            val copy = File(saved.chapters.last().imagePath!!)
            assertEquals("image-fixture", copy.readText())
            assertNotEquals(image.path, copy.path)
            repo.deleteStory(story.id)
            assertNull(repo.getStoryById(story.id))
            assertEquals(story.id, repo.observeTrash().first().single().id)
            repo.restoreStory(story.id)
            assertNotNull(repo.getStoryById(story.id))
            repo.deleteStory(story.id)
            repo.permanentlyDeleteStory(story.id)
            assertTrue(copy.exists())
            assertEquals("image-fixture", copy.readText())
        } finally {
            assetIds.forEach { illustrations.deleteStoryAssets(it) }
            db.close()
            settingsScope.cancel()
            settingsFile.delete()
        }
    }
}
