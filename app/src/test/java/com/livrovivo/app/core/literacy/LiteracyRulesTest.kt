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
    fun `the first book comes with 3 syllable phases`() {
        val letters = (module("vogais") + module("consoantes")).toSet()
        val syllables = module("silabas")

        assertEquals(0, LiteracyRules.booksEarned(trail, letters))
        assertEquals(0, LiteracyRules.booksEarned(trail, letters + syllables.take(2)))
        assertEquals(1, LiteracyRules.booksEarned(trail, letters + syllables.take(3)))
    }

    @Test
    fun `after the first, a new book every 2 phases of syllables, words or dictation`() {
        val letters = (module("vogais") + module("consoantes")).toSet()
        val later = module("silabas") + module("palavras") + module("ditado")

        assertEquals(1, LiteracyRules.booksEarned(trail, letters + later.take(4)))
        assertEquals(2, LiteracyRules.booksEarned(trail, letters + later.take(5)))
        assertEquals(6, LiteracyRules.booksEarned(trail, letters + module("silabas")))
        assertEquals(7, LiteracyRules.booksEarned(trail, letters + later.take(15)))
        assertEquals("a trilha inteira dá 16 livros", 16, LiteracyRules.booksEarned(trail, letters + later))
    }

    @Test
    fun `each book remembers the phase that unlocked it`() {
        val letters = (module("vogais") + module("consoantes")).toSet()
        val later = module("silabas") + module("palavras") + module("ditado")

        val triggers = LiteracyRules.bookTriggerPhases(trail, letters + later.take(15)).map { it.id }

        assertEquals(listOf("silabas_d", "silabas_g", "silabas_m", "silabas_p", "silabas_s", "silabas_v", "palavras_02"), triggers)
    }

    @Test
    fun `a book uses what the child knew when it was unlocked`() {
        val everything = trail.phases.map { it.id }.toSet()

        val knowledge = LiteracyRules.knowledgeUpTo(trail, everything, trail.phase("silabas_d")!!)

        assertEquals(trail.module("silabas")!!.phases.take(3).flatMap { it.teaches }.toSet(), knowledge.syllables)
        assertTrue(knowledge.words.isEmpty())
    }

    @Test
    fun `the book practices the syllables of the phase that unlocked it`() {
        assertEquals(setOf("BA", "BE", "BI", "BO", "BU"), LiteracyRules.phaseSyllables(trail, trail.phase("silabas_b")!!))
        // BOLA, CASA, GATO, PATO e MALA
        assertEquals(
            setOf("BO", "LA", "CA", "SA", "GA", "TO", "PA", "MA"),
            LiteracyRules.phaseSyllables(trail, trail.phase("palavras_01")!!)
        )
        // O ditado não "ensina" nada novo: vale o que ele pede para escrever.
        val dictation = trail.phase("ditado_01")!!
        val expected = dictation.questions.flatMap { question -> trail.vocabulary.single { it.word == question.answer }.syllables }
        assertEquals(expected.toSet(), LiteracyRules.phaseSyllables(trail, dictation))
    }

    @Test
    fun `all syllables of the trail`() {
        val syllables = LiteracyRules.allSyllables(trail)
        assertEquals(65, syllables.size)
        assertTrue(syllables.containsAll(listOf("BA", "LU", "NA", "VU")))
    }

    @Test
    fun `without a subscription paid phases stay closed but do not block the free ones`() {
        val letters = (module("vogais") + module("consoantes")).toSet()
        val freeSyllables = module("silabas").take(2)

        val unlocked = LiteracyRules.unlockedPhaseIds(trail, letters + freeSyllables, subscriber = false)

        assertFalse("Sílabas com D é paga", "silabas_d" in unlocked)
        assertTrue("Palavras abre depois das fases grátis de Sílabas", "palavras_01" in unlocked)
        assertFalse("palavras_02" in unlocked)
        assertFalse("com assinatura, Palavras espera todas as sílabas", "palavras_01" in LiteracyRules.unlockedPhaseIds(trail, letters + freeSyllables))
    }

    @Test
    fun `a phase done while subscribed stays open after the subscription ends`() {
        val letters = (module("vogais") + module("consoantes")).toSet()

        val unlocked = LiteracyRules.unlockedPhaseIds(trail, letters + module("silabas").take(4), subscriber = false)

        assertTrue("silabas_d" in unlocked)
        assertTrue("silabas_f" in unlocked)
        assertFalse("silabas_g" in unlocked)
    }

    @Test
    fun `without a subscription there is one book per module once its free phases are done`() {
        val letters = (module("vogais") + module("consoantes")).toSet()
        val free = { id: String -> trail.module(id)!!.phases.filter { it.isFree }.map { it.id } }

        val afterSyllables = letters + free("silabas")
        assertEquals("com só B e C ela lê uma palavra: ainda não dá livro", 0, LiteracyRules.booksEarned(trail, afterSyllables, subscriber = false))

        val afterWords = afterSyllables + free("palavras")
        assertEquals(listOf("palavras_02"), LiteracyRules.bookTriggerPhases(trail, afterWords, subscriber = false).map { it.id })

        val afterDictation = afterWords + free("ditado")
        assertEquals(listOf("palavras_02", "ditado_02"), LiteracyRules.bookTriggerPhases(trail, afterDictation, subscriber = false).map { it.id })
        assertEquals("a regra de assinante continua valendo para quem assina", 0, LiteracyRules.booksEarned(trail, afterDictation))
    }

    @Test
    fun `readable words count what the child can read`() {
        val knowledge = LiteracyRules.knowledge(trail, (module("vogais") + module("consoantes") + module("silabas").take(3)).toSet())

        assertEquals(4, LiteracyRules.readableWords(trail, knowledge))
    }

    @Test
    fun `a new child knows nothing yet`() {
        val knowledge = LiteracyRules.knowledge(trail, emptySet())

        assertTrue(knowledge.letters.isEmpty() && knowledge.syllables.isEmpty() && knowledge.words.isEmpty())
    }
}
