package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.MagicalCompanion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class VoiceClipsTest {

    private val trail = TrailParser.parse(File("src/main/assets/${TrailParser.ASSET_PATH}").readText(Charsets.UTF_8))
    private val script = File("../ferramentas/gerar_audios.py").readText(Charsets.UTF_8)

    @Test
    fun `keys follow the recording script`() {
        assertEquals("silaba:BA", VoiceClips.key(VoiceClips.Kind.SYLLABLE, "ba"))
        assertEquals("palavra:É", VoiceClips.key(VoiceClips.Kind.WORD, "é"))
        assertEquals("fala:Toque na sílaba BE", VoiceClips.key(VoiceClips.Kind.PROMPT, "  Toque na  sílaba BE "))
        // "é" escrito com o acento separado (e + ´) acha o mesmo áudio.
        assertEquals("palavra:É", VoiceClips.key(VoiceClips.Kind.WORD, "É"))
        assertEquals(VoiceClips.Kind.LETTER, VoiceClips.unitKind("B"))
        assertEquals(VoiceClips.Kind.SYLLABLE, VoiceClips.unitKind("BA"))
    }

    @Test
    fun `index maps each key to its file`() {
        val index = VoiceClips.parseIndex(
            """{"voz": "Puck", "clips": {"silaba:BA": "silabas/ba.ogg", "frase:Muito bem!": "frases/muito_bem.ogg", "palavra:E": ""}}"""
        )
        assertEquals(mapOf("silaba:BA" to "silabas/ba.ogg", "frase:Muito bem!" to "frases/muito_bem.ogg"), index)
        assertEquals(emptyMap<String, String>(), VoiceClips.parseIndex("""{"voz": "Puck"}"""))
    }

    @Test
    fun `the recording script records the same fixed phrases the screens speak`() {
        val block = Regex("FRASES_FIXAS = \\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(script)!!.groupValues[1]
        val python = Regex("\"([^\"]+)\"").findAll(block).map { it.groupValues[1] }.toList()
        assertEquals(TrailPhrases.FIXED.toSet(), python.toSet())

        val template = Regex("FRASE_MODULO_TRANCADO = \"([^\"]+)\"").find(script)!!.groupValues[1]
        trail.modules.forEach { module ->
            assertEquals(TrailPhrases.moduleLocked(module.title), template.replace("{}", module.title))
        }
    }

    @Test
    fun `the script records every companion the child can read`() {
        val syllables = LiteracyRules.allSyllables(trail)
        val readable = MagicalCompanion.ALL.map { it.name.uppercase() }.filter { DecodableValidator.isDecodable(it, syllables) }
        val block = Regex("COMPANHEIROS_LEGIVEIS = \\[(.*?)\\]").find(script)!!.groupValues[1]
        assertEquals(readable.toSet(), Regex("\"([^\"]+)\"").findAll(block).map { it.groupValues[1] }.toSet())
    }

    /** Depois de gravar (`gerar_audios.py`), toda fala da trilha precisa ter o seu áudio. */
    @Test
    fun `every trail phrase has its recording once the audios exist`() {
        val folder = File("src/main/assets/${VoiceClips.FOLDER}")
        val indexFile = File("src/main/assets/${VoiceClips.INDEX_PATH}")
        assumeTrue("áudios da trilha ainda não gravados", indexFile.exists())
        val index = VoiceClips.parseIndex(indexFile.readText(Charsets.UTF_8))

        val letters = listOf(LiteracyRules.MODULE_VOWELS, LiteracyRules.MODULE_CONSONANTS)
            .flatMap { id -> trail.module(id)!!.phases.flatMap { it.teaches } }
        val syllables = LiteracyRules.allSyllables(trail)
        val companions = Regex("COMPANHEIROS_LEGIVEIS = \\[(.*?)\\]").find(script)!!.groupValues[1]
            .let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
        val expected = TrailPhrases.FIXED.map { VoiceClips.key(VoiceClips.Kind.PHRASE, it) } +
            trail.modules.map { VoiceClips.key(VoiceClips.Kind.PHRASE, TrailPhrases.moduleLocked(it.title)) } +
            letters.map { VoiceClips.key(VoiceClips.Kind.LETTER, it) } +
            syllables.map { VoiceClips.key(VoiceClips.Kind.SYLLABLE, it) } +
            (trail.vocabulary.map { it.word } + trail.supportWords + companions).map { VoiceClips.key(VoiceClips.Kind.WORD, it) } +
            trail.phases.flatMap { it.questions }.map { VoiceClips.key(VoiceClips.Kind.PROMPT, it.prompt) }

        val missing = expected.distinct().filter { it !in index }
        assertTrue("Falas sem áudio (rode gerar_audios.py): ${missing.take(10)}", missing.isEmpty())
        val lostFiles = index.values.filter { !File(folder, it).exists() }
        assertTrue("Arquivos do índice que não existem: ${lostFiles.take(10)}", lostFiles.isEmpty())
    }
}
