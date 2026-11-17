package pl.kwitukiewicz.handygerman.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.kwitukiewicz.handygerman.core.Gender.MASKULINUM
import pl.kwitukiewicz.handygerman.core.Gender.NEUTRUM
import pl.kwitukiewicz.handygerman.core.Kind.PERSON_NAME

/**
 * The Task 6 rule chain over the Task 5 fixture (via [FakeWordRepository]):
 * fold → exact hits grouped by entry → person-name footer/promotion →
 * Homonyms vs Single → prefix suggestions → NoResult.
 */
class LookupEngineTest {

    private val engine = LookupEngine(FakeWordRepository())

    private fun entry(entryId: Long): Entry =
        FakeWordRepository.ENTRIES.first { it.entryId == entryId }

    @Test
    fun `two genders of one spelling surface as homonyms in page order`() {
        val result = engine.lookup("Boote")!!
        val answer = result.answer as Homonyms

        assertEquals(listOf(1L, 2L), answer.candidates.map { it.entry.entryId })
        assertEquals(listOf("das", "der"),
            answer.candidates.map { articlesText(it.entry.genders) })
        assertEquals(0, answer.selectedIndex)
        assertTrue(result.footerNames.isEmpty())
    }

    @Test
    fun `an inflected form resolves through the fold to its single entry`() {
        val candidate = engine.lookup("böte")!!.let { (it.answer as Single).candidate }

        assertEquals(1L, candidate.entry.entryId)
        assertEquals(listOf(NEUTRUM), candidate.entry.genders)
        assertEquals("böte", candidate.typedForm.form)
    }

    @Test
    fun `booten hits the dative plural of das Boot and the genitive singular of der Boot`() {
        val answer = engine.lookup("booten")!!.answer as Homonyms

        assertEquals(listOf(1L, 2L), answer.candidates.map { it.entry.entryId })
        assertEquals(listOf("Dativ", "Genitiv"), answer.candidates.map { it.typedForm.kasus })
    }

    @Test
    fun `a word hit puts person-name homonyms in the footer, keeping page order`() {
        val result = engine.lookup("zeit")!!

        assertEquals(4L, (result.answer as Single).candidate.entry.entryId)
        assertEquals(listOf(entry(5)), result.footerNames)
        assertEquals(PERSON_NAME, result.footerNames.single().kind)
    }

    @Test
    fun `a person name alone is promoted into the answer with an empty footer`() {
        val result = engine.lookup("müller")!!

        val candidate = (result.answer as Single).candidate
        assertEquals(7L, candidate.entry.entryId)
        assertEquals(PERSON_NAME, candidate.entry.kind)
        // First hit is the annotation source: the lemma spelling, not the variant row.
        assertEquals("müller", candidate.typedForm.form)
        assertTrue(candidate.typedForm.isLemma)
        assertTrue(result.footerNames.isEmpty())
    }

    @Test
    fun `a first name is an ordinary single answer`() {
        val candidate = engine.lookup("kevin")!!.let { (it.answer as Single).candidate }

        assertEquals(8L, candidate.entry.entryId)
        assertEquals(PERSON_NAME, candidate.entry.kind)
    }

    @Test
    fun `one entry with two genders stays Single - both accepted via articlesText`() {
        val candidate = engine.lookup("virus")!!.let { (it.answer as Single).candidate }

        assertEquals(3L, candidate.entry.entryId)
        assertEquals("das/der", articlesText(candidate.entry.genders))
        assertEquals(listOf(NEUTRUM, MASKULINUM), candidate.entry.genders)
    }

    @Test
    fun `a plural-only noun stays Single and stays plural-only`() {
        val candidate = engine.lookup("eltern")!!.let { (it.answer as Single).candidate }

        assertEquals(6L, candidate.entry.entryId)
        assertTrue(isPluralOnly(candidate.entry.genders))
    }

    @Test
    fun `a prefix with no exact hit suggests distinct lemmas`() {
        val suggestions = engine.lookup("ze")!!.answer as Suggestions

        // "Zeit" once, represented by its lowest-page-order entry (the word, not the name).
        assertEquals(listOf(entry(4)), suggestions.entries)
    }

    @Test
    fun `an unknown spelling with no prefix is no result`() {
        assertEquals(NoResult, engine.lookup("xyzq")!!.answer)
    }

    @Test
    fun `blank input is no lookup at all`() {
        assertNull(engine.lookup("  "))
        assertNull(engine.lookup(""))
    }

    @Test
    fun `strasse is absent from the fixture and stays no result`() {
        // No Straße row without the poison flag — folding cannot invent a corpus entry.
        assertEquals(NoResult, engine.lookup("strasse")!!.answer)
    }
}
