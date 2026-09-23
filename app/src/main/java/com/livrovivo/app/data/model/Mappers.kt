package com.livrovivo.app.data.model

import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildAppearance
import com.livrovivo.app.domain.model.ChildGender
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.DecodableBook
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.PhaseProgress
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.Virtue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val appJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

private fun decodeStringList(json: String?): List<String> = try {
    if (json.isNullOrBlank()) emptyList() else appJson.decodeFromString(json)
} catch (_: Exception) {
    emptyList()
}

fun ChildProfileEntity.toDomain(): ChildProfile {
    return ChildProfile(
        id = id,
        name = name,
        ageGroup = ageGroup,
        interests = decodeStringList(interestsJson),
        companionId = companionId,
        createdAt = createdAt,
        gender = ChildGender.fromCode(gender),
        appearance = ChildAppearance(
            skinTone = skinTone,
            hairColor = hairColor,
            hairStyle = hairStyle,
            wearsGlasses = wearsGlasses
        )
    )
}

fun ChildProfile.toEntity(): ChildProfileEntity {
    return ChildProfileEntity(
        id = id,
        name = name,
        ageGroup = ageGroup,
        interestsJson = appJson.encodeToString(interests),
        companionId = companionId,
        createdAt = createdAt,
        gender = gender.code,
        skinTone = appearance.skinTone,
        hairColor = appearance.hairColor,
        hairStyle = appearance.hairStyle,
        wearsGlasses = appearance.wearsGlasses
    )
}

fun StoryEntity.toDomain(chapters: List<ChapterEntity>): Story {
    return Story(
        id = id,
        childId = childId,
        title = title,
        theme = theme,
        objectiveType = objectiveType,
        coverImageUrl = coverImageUrl,
        createdAt = createdAt,
        chapters = chapters.sortedBy { it.chapterIndex }.map { it.toDomain() },
        themeId = themeId,
        companionId = companionId,
        characterSheet = characterSheet,
        plannedChapters = plannedChapters,
        isCompleted = isCompleted,
        lastReadChapter = lastReadChapter,
        updatedAt = if (updatedAt > 0) updatedAt else createdAt,
        isOffline = isOffline,
        childSnapshot = childSnapshotJson?.let { raw ->
            runCatching { appJson.decodeFromString<ChildProfileEntity>(raw).toDomain() }.getOrNull()
        },
        deletedAt = deletedAt,
        originId = originId,
        kind = kind
    )
}

fun StoryWithChapters.toDomain(): Story = story.toDomain(chapters)

fun ChapterEntity.toDomain(): Chapter {
    val choicesList: List<Choice> = try {
        appJson.decodeFromString<List<ChoiceDto>>(choicesJson).map {
            Choice(it.text, it.targetChapterIndex, Virtue.fromCode(it.virtue))
        }
    } catch (_: Exception) {
        emptyList()
    }
    return Chapter(
        index = chapterIndex,
        content = content,
        choices = choicesList,
        isEnding = isEnding,
        audioUrl = audioUrl,
        sceneImagePrompt = sceneImagePrompt,
        imagePath = imagePath,
        narrationScript = narrationScript,
        selectedChoiceText = selectedChoiceText,
        newWords = decodeStringList(newWordsJson),
        mood = mood,
        openedAt = openedAt
    )
}

fun Story.toEntity(): StoryEntity {
    return StoryEntity(
        id = id,
        childId = childId,
        title = title,
        theme = theme,
        objectiveType = objectiveType,
        coverImageUrl = coverImageUrl,
        createdAt = createdAt,
        themeId = themeId,
        companionId = companionId,
        characterSheet = characterSheet,
        plannedChapters = plannedChapters,
        isCompleted = isCompleted,
        lastReadChapter = lastReadChapter,
        updatedAt = updatedAt,
        isOffline = isOffline,
        childSnapshotJson = childSnapshot?.let { appJson.encodeToString(it.toEntity()) },
        deletedAt = deletedAt,
        originId = originId,
        kind = kind
    )
}

fun Chapter.toEntity(storyId: String): ChapterEntity {
    val choicesDto = choices.map { ChoiceDto(it.text, it.targetChapterIndex, it.virtue?.code) }
    return ChapterEntity(
        storyId = storyId,
        chapterIndex = index,
        content = content,
        choicesJson = appJson.encodeToString(choicesDto),
        isEnding = isEnding,
        audioUrl = audioUrl,
        sceneImagePrompt = sceneImagePrompt,
        imagePath = imagePath,
        narrationScript = narrationScript,
        selectedChoiceText = selectedChoiceText,
        newWordsJson = appJson.encodeToString(newWords),
        mood = mood,
        openedAt = openedAt
    )
}

fun LiteracyProgressEntity.toDomain(): PhaseProgress =
    PhaseProgress(childId, phaseId, stars, attempts, mistakes, completedAt)

/**
 * Livro "Eu leio" pronto para a estante: 4 páginas sem escolhas, e a última é o fim.
 * [moduleId] fica em `themeId` para saber em que módulo da trilha o livro foi ganho.
 * A palavra principal de cada página vai em `newWords` (a figura dela ilustra a página sem IA).
 */
fun DecodableBook.toEuLeioStory(
    id: String,
    child: ChildProfile,
    moduleId: String,
    isOffline: Boolean,
    createdAt: Long
): Story = Story(
    id = id,
    childId = child.id,
    title = title,
    theme = Story.EU_LEIO_THEME,
    objectiveType = ObjectiveType.COGNITIVO.code,
    createdAt = createdAt,
    themeId = moduleId,
    companionId = child.companionId,
    plannedChapters = pages.size,
    updatedAt = createdAt,
    isOffline = isOffline,
    childSnapshot = child,
    kind = Story.KIND_EU_LEIO,
    chapters = pages.mapIndexed { index, page ->
        Chapter(
            index = index + 1,
            content = page.text,
            choices = emptyList(),
            isEnding = index == pages.lastIndex,
            newWords = listOf(page.mainWord.word)
        )
    }
)
