package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class TrailParserTest {

    private val realJson: String
        get() = File("src/main/assets/${TrailParser.ASSET_PATH}").readText(Charsets.UTF_8)

    @Test
    fun `real trail loads with all modules, phases and questions`() {
        val trail = TrailParser.parse(realJson)

        assertEquals(listOf("vogais", "consoantes", "silabas", "palavras", "ditado"), trail.modules.map { it.id })
        assertEquals(listOf(1, 2, 3, 4, 5), trail.modules.map { it.order })
        assertEquals(51, trail.phases.size)
        assertEquals(255, trail.phases.sumOf { it.questions.size })
        assertEquals(50, trail.vocabulary.size)
        assertTrue(trail.supportWords.containsAll(listOf("É", "NÃO", "ESTÁ")))
    }

    @Test
    fun `real trail keeps the free phases of the spec`() {
        val trail = TrailParser.parse(realJson)

        trail.modules.take(2).forEach { module -> assertTrue(module.phases.all { it.isFree }) }
        trail.modules.drop(2).forEach { module -> assertEquals(module.id, 2, module.phases.count { it.isFree }) }
    }

    @Test
    fun `real trail maps fields and lookups`() {
        val trail = TrailParser.parse(realJson)

        val phase = trail.phase("silabas_b")!!
        assertEquals(listOf("BA", "BE", "BI", "BO", "BU"), phase.teaches)
        assertEquals("silabas", trail.moduleOf("silabas_b")!!.id)
        val join = phase.questions.first()
        assertEquals(QuestionType.JOIN, join.type)
        assertEquals("BA", join.answer)
        assertTrue(join.options.isEmpty())

        val bola = trail.vocabulary.single { it.word == "BOLA" }
        assertEquals(listOf("BO", "LA"), bola.syllables)
        assertEquals("img_bola", bola.image)
        assertEquals("A", bola.article)
        assertEquals("UMA", bola.indefiniteArticle)
        assertEquals("NA", bola.inArticle)
        assertTrue(bola.isObject)
        assertTrue(trail.vocabulary.single { it.word == "CAMA" }.isPlace)
        assertFalse("não entra em \"LIA TEM UMA FACA\"", trail.vocabulary.single { it.word == "FACA" }.isObject)

        val pictureQuestions = trail.phases.flatMap { it.questions }.filter { it.type == QuestionType.PICTURE_WORD }
        assertTrue(pictureQuestions.all { it.image != null })
    }

    @Test
    fun `broken questions are all reported together`() {
        val broken = trail(
            """{"id":"f1","titulo":"F1","gratis":true,"ensina":["A"],"perguntas":[
                {"tipo":"ouvir_tocar","fala":"Toque no A","resposta":"A","opcoes":["E","I"],"pecas":[],"imagem":null},
                {"tipo":"juntar","fala":"Forme BA","resposta":"BA","opcoes":[],"pecas":["B","E"],"imagem":null},
                {"tipo":"figura_palavra","fala":"Que figura?","resposta":"BOLA","opcoes":["BOLA","CASA"],"pecas":[],"imagem":null},
                {"tipo":"cantar","fala":"Cante","resposta":"A","opcoes":[],"pecas":[],"imagem":null}
            ]}"""
        )

        val problems = problemsOf(broken)

        assertTrue(problems.any { "resposta não está nas opções" in it })
        assertTrue(problems.any { "não dá para formar" in it })
        assertTrue(problems.any { "precisa de imagem" in it })
        assertTrue(problems.any { "tipo desconhecido 'cantar'" in it })
    }

    @Test
    fun `repeated phase id is rejected`() {
        val phase = """{"id":"f1","titulo":"F1","gratis":true,"ensina":["A"],"perguntas":[
            {"tipo":"ouvir_tocar","fala":"Toque no A","resposta":"A","opcoes":["A","E"]}]}"""

        assertTrue(problemsOf(trail("$phase,$phase")).any { "fase repetida: f1" in it })
    }

    @Test
    fun `unreadable json is reported instead of crashing elsewhere`() {
        assertTrue(problemsOf("{ isto não é json").single().startsWith("JSON ilegível"))
    }

    @Test
    fun `answer is built only from available pieces`() {
        assertTrue(TrailParser.canBuild("BOLA", listOf("LA", "BO")))
        assertTrue(TrailParser.canBuild("DADO", listOf("DA", "NI", "MO", "DO")))
        assertTrue(TrailParser.canBuild("BA", listOf("B", "A")))
        assertFalse(TrailParser.canBuild("DADA", listOf("DA", "NI")))
        assertFalse(TrailParser.canBuild("BOLA", listOf("BO")))
    }

    private fun trail(phases: String) = """
        {"versao":1,"palavras_de_apoio":["O","A"],
         "vocabulario":[{"palavra":"BOLA","silabas":["BO","LA"],"imagem":"img_bola","artigo":"A"}],
         "modulos":[{"id":"m1","titulo":"M1","ordem":1,"fases":[$phases]}]}
    """.trimIndent()

    private fun problemsOf(raw: String): List<String> = try {
        TrailParser.parse(raw)
        fail("o conteúdo deveria ser recusado")
        emptyList()
    } catch (e: InvalidTrailException) {
        e.problems
    }
}
