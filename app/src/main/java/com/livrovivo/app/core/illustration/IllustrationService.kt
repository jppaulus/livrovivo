package com.livrovivo.app.core.illustration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.livrovivo.app.core.ai.AiException
import com.livrovivo.app.core.ai.FluxImageService
import com.livrovivo.app.core.ai.GeminiService
import com.livrovivo.app.core.ai.StoryPrompts
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.IllustrationStyle
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.Story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Gera ilustrações de livro infantil para cada página: com o FLUX da Microsoft (só o texto, que descreve os personagens
 * sempre do mesmo jeito) ou, nos testes, com o Gemini (que recebe a página anterior como referência).
 */
class IllustrationService(
    private val context: Context,
    private val gemini: GeminiService,
    private val flux: FluxImageService,
    private val settings: SettingsManager
) {
    suspend fun isEnabled(): Boolean = settings.current().illustrationsEnabled && (flux.isAvailable || gemini.isAvailable())

    suspend fun illustrate(story: Story, chapter: Chapter, child: ChildProfile?): File {
        val current = settings.current()
        val previousImage = story.sortedChapters
            .lastOrNull { it.index < chapter.index && it.imagePath != null }
            ?.imagePath?.let(::File)?.takeIf { it.exists() }

        // FLUX (Microsoft) primeiro: é o que pode ir para a loja; o Gemini fica para testes (HANDOFF §4, item 12).
        // No FLUX vai só o texto (a referência ainda é prévia): a descrição dos personagens mantém todos iguais.
        val image = if (flux.isAvailable) {
            try {
                flux.generate(buildPrompt(story, chapter, child, current.illustrationStyle, hasReference = false))
            } catch (e: AiException) {
                if (e.kind != AiException.Kind.BLOCKED) throw e
                // O filtro da Microsoft às vezes barra a soma de palavras inocentes: uma tentativa com o pedido mínimo.
                flux.generate(leanPrompt(story, chapter, child, current.illustrationStyle))
            }
        } else {
            val prompt = buildPrompt(story, chapter, child, current.illustrationStyle, hasReference = previousImage != null)
            val references = previousImage?.let { listOfNotNull(downscaleJpeg(it, maxSide = 768, quality = 80)) }.orEmpty()
            gemini.generateImage(prompt, references, aspectRatio = "4:3")
        }
        return withContext(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                ?: throw AiException(AiException.Kind.PARSE, "Imagem gerada em formato inválido")
            val dir = File(context.filesDir, "stories/${story.id}").apply { mkdirs() }
            val output = File(dir, "page_${chapter.index}_${System.currentTimeMillis()}.jpg")
            val scaled = scaleDown(bitmap, 1280)
            FileOutputStream(output).use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            // Remove versões antigas da mesma página.
            dir.listFiles()?.filter { it.name.startsWith("page_${chapter.index}_") && it != output }?.forEach { it.delete() }
            output
        }
    }

    companion object {
        private val EXACT_AGE = Regex("""\b\d{1,2}[- ]?(?:years?|yrs?)[- ]old\b""", RegexOption.IGNORE_CASE)

        /**
         * "a 4-year-old girl" vira "a young girl". A idade exata somada a uma cena em português fazia o filtro da
         * Microsoft barrar páginas inocentes (teste de 26/09/2026); o estilo de livro infantil já define a criança.
         */
        fun withoutExactAges(text: String): String = text.replace(EXACT_AGE, "young")
    }

    /** Ilustração de teste para a tela de configurações. */
    suspend fun sample(style: IllustrationStyle): File {
        val prompt = """
Create a single illustration for a children's picture book.
ART STYLE: ${style.prompt}.
SCENE: a curious child and ${MagicalCompanion.ALL.first().visualDescription} reading a glowing magic book under a starry sky on a cozy hill.
COMPOSITION: wide horizontal 4:3 frame, expressive friendly faces, gentle and safe for young children.
IMPORTANT: no text, no letters, no words, no watermarks.
""".trim()
        val image = if (flux.isAvailable) flux.generate(prompt) else gemini.generateImage(prompt, emptyList(), aspectRatio = "4:3")
        return withContext(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                ?: throw AiException(AiException.Kind.PARSE, "Imagem gerada em formato inválido")
            val output = File(context.cacheDir, "illustration_sample_${style.id}.jpg")
            FileOutputStream(output).use { scaleDown(bitmap, 1024).compress(Bitmap.CompressFormat.JPEG, 85, it) }
            output
        }
    }

    fun deleteStoryAssets(storyId: String) {
        File(context.filesDir, "stories/$storyId").deleteRecursively()
    }

    suspend fun copyStoryAssets(story: Story, newId: String): Story = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "stories/$newId").apply { mkdirs() }
        val chapters = story.chapters.map { chapter ->
            val image = chapter.imagePath?.let(::File)?.takeIf { it.isFile }
            val copy = image?.copyTo(File(dir, "page_${chapter.index}_saved.jpg"))
            chapter.copy(imagePath = copy?.path)
        }
        story.copy(id = newId, chapters = chapters, coverImageUrl = chapters.firstNotNullOfOrNull { it.imagePath })
    }

    fun deleteAllStoryAssets() {
        File(context.filesDir, "stories").deleteRecursively()
    }

    private fun characters(story: Story, child: ChildProfile?): String {
        val companion = MagicalCompanion.findById(story.companionId)
        val sheet = story.characterSheet?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(
                child?.let { "${it.name}: ${StoryPrompts.appearanceDescription(it)}" },
                "${companion.name}: ${companion.visualDescription}"
            ).joinToString(". ")
        return withoutExactAges(sheet)
    }

    private fun scene(chapter: Chapter): String = chapter.sceneImagePrompt?.takeIf { it.isNotBlank() }
        ?: "A key moment of this page of the story (in Brazilian Portuguese): ${chapter.content.take(600)}"

    /** Só o essencial (estilo, personagens e cena): usado quando o filtro de conteúdo barra o pedido completo. */
    private fun leanPrompt(story: Story, chapter: Chapter, child: ChildProfile?, style: IllustrationStyle): String =
        buildString {
            appendLine("Picture book illustration. ART STYLE: ${style.prompt}.")
            appendLine("CHARACTERS: ${characters(story, child)}")
            appendLine("SCENE: ${scene(chapter)}")
            append("No text, no letters, no watermarks.")
        }

    private fun buildPrompt(
        story: Story,
        chapter: Chapter,
        child: ChildProfile?,
        style: IllustrationStyle,
        hasReference: Boolean
    ): String {
        val characters = characters(story, child)
        val scene = scene(chapter)
        val mood = when (chapter.mood) {
            "sonolento" -> "sleepy, cozy and calm, soft moonlight"
            "aconchegante" -> "warm, cozy and loving"
            "misterioso" -> "gently mysterious and curious, magical glow, never scary"
            "aventura", "emocionante" -> "exciting and joyful adventure, dynamic but safe"
            else -> "cheerful and bright"
        }
        return buildString {
            appendLine("Create a single illustration for page ${chapter.index} of a children's picture book titled \"${story.title}\".")
            appendLine("ART STYLE: ${style.prompt}. Keep exactly the same art style on every page.")
            appendLine("ART DIRECTION: professional published picture-book quality; deliberate composition, convincing anatomy and perspective, layered depth, controlled color palette, tactile materials and expressive acting. Avoid generic clipart, emoji faces, primitive shape drawings and plastic stock-art gloss.")
            appendLine("VISUAL STORYTELLING: show one specific moment from this page. Vary camera distance and framing between pages; use foreground details and purposeful lighting. Leave room for the reader to discover a detail not stated in the text.")
            appendLine("CHARACTERS (must look identical on every page): $characters")
            appendLine("SCENE: $scene")
            appendLine("MOOD: $mood.")
            // Sem "young children"/"age-appropriate"/"portrait": somados a "sono", "cobertor" e "mão", o filtro da Microsoft
            // bloqueava páginas inocentes (26/09/2026). O estilo de livro infantil já diz o público.
            appendLine("COMPOSITION: wide horizontal 4:3 frame, main characters clearly visible with expressive friendly faces, uncluttered background, gentle and wholesome.")
            if (hasReference) {
                appendLine("REFERENCE: the attached image is the previous page of this same book. Keep the same characters, faces, outfits, colors and art style, but draw the new scene.")
            }
            append("IMPORTANT: no text, no letters, no words, no captions, no speech bubbles, no watermarks, no logos.")
        }
    }

    private fun downscaleJpeg(file: File, maxSide: Int, quality: Int): ByteArray? = try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            null
        } else {
            val scaled = scaleDown(bitmap, maxSide)
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }.also {
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }
}
