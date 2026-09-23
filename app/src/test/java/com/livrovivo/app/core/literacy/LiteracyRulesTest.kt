package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.PhaseProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiteracyRulesTest {

    private val trail = TrailParser.parse(File("src/main/assets/${TrailParser.ASSET_PATH}").readText(Charsets.UTF_8))
    private fun module(id: String) = trail.module(id)!!.phases.map { it.id }
    private fun progress(phaseId: String, stars: Int) = PhaseProgress("lia", phaseId, stars, 1, 0, if (stars > 0) 1L else null)

    @Test
    fun `a new child can play only the first phase`() {
        assertEquals(setOf("vogais_a"), LiteracyRules.unlockedPhaseIds(trail, emptySet()))
    }

    @Test
    fun `phases inside a module unlock one after the other`() {
        val unlocked = LiteracyRules.unlockedPhaseIds(trail, setOf("vogais_a", "vogais_e"))

        assertEquals(setOf("vogais_a", "vogais_e", "vogais_i"), unlocked)
    }

    @Test
    fun `next module opens only when every phase of the previous one has a star`() {
        val vowels = module("vogais")
        val almost = LiteracyRules.unlockedPhaseIds(trail, vowels.dropLast(1).toSet())
        assertFalse(module("consoantes").first() in almost)

        val all = LiteracyRules.unlockedPhaseIds(trail, vowels.toSet())
        assertTrue(module("consoantes").first() in all)
        assertFalse(module("consoantes")[1] in all)
        assertFalse(module("silabas").first() in all)
    }

    @Test
    fun `zero stars does not count as completed`() {
        val completed = LiteracyRules.completedPhaseIds(listOf(progress("vogais_a", 0), progress("vogais_e", 1)))

        assertEquals(setOf("vogais_e"), completed)
    }

    @Test
    fun `completed phases stay open to play again`() {
        // Um progresso "fora de ordem" (ex.: conteúdo reordenado numa versão nova) não tranca o que já foi feito.
        val unlocked = LiteracyRules.unlockedPhaseIds(trail, setOf("silabas_b"))

        assertTrue("silabas_b" in unlocked)
        assertTrue("vogais_a" in unlocked)
    }

    @Test
    fun `the whole trail opens when everything is completed`() {
        val everything = trail.phases.map { it.id }.toSet()

        assertEquals(everything, LiteracyRules.unlockedPhaseIds(trail, everything))
    }

    @Test
    fun `knowledge comes from what each completed phase teaches`() {
        val completed = setOf("vogais_a", "vogais_e", "consoantes_b", "silabas_b", "silabas_c", "palavras_01")

        val knowledge = LiteracyRules.knowledge(trail, completed)

        assertEquals(setOf("A", "E", "B"), knowledge.letters)
        assertEquals(setOf("BA", "BE", "BI", "BO", "BU", "CA", "CE", "CI", "CO", "CU"), knowledge.syllables)
        assertEquals(trail.phase("palavras_01")!!.teaches.toSet(), knowledge.words)
    }

    @Test
    fun `a new child knows nothing yet`() {
        val knowledge = LiteracyRules.knowledge(trail, emptySet())

        assertTrue(knowledge.letters.isEmpty() && knowledge.syllables.isEmpty() && knowledge.words.isEmpty())
    }
}
