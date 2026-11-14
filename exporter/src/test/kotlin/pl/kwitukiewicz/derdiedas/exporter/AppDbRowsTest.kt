package pl.kwitukiewicz.derdiedas.exporter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.kwitukiewicz.derdiedas.core.Kind
import pl.kwitukiewicz.derdiedas.core.foldForm
import wiktiopeggynary.model.substantiv.Substantiv

class AppDbRowsTest {

    private fun substantivs(name: String) = parsePage(readFixture(name)).filterIsInstance<Substantiv>()

    @Test
    fun `boot page yields three noun entries with kind and genders`() {
        val boot = substantivs("Boot")
        assertEquals(3, boot.size)

        val water = entryRow(boot[0], 0)!!
        assertEquals("Boot", water.lemma)
        assertEquals(0, water.pageOrder)
        assertEquals(Kind.WORD, water.kind)
        assertEquals("NEUTRUM", water.genders)
        val pluralForms = water.pluralForms!!
        assertTrue(pluralForms.contains("Boote"), "plural_forms '$pluralForms' misses Boote")
        assertTrue(pluralForms.contains("Böte"), "plural_forms '$pluralForms' misses Böte")

        val familyName = entryRow(boot[2], 2)!!
        assertEquals(Kind.PERSON_NAME, familyName.kind)
        assertEquals("MASKULINUM,FEMININUM", familyName.genders)
    }

    @Test
    fun `meaning lines json holds the flattened meanings in document order`() {
        val row = entryRow(substantivs("Boot")[0], 0)!!
        assertEquals(
            """["kleines Wasserfahrzeug",""" +
                """"Militär: seegehende Einheiten einer bestimmten Größenordnung bei der Marine"]""",
            row.meaningLinesJson,
        )
    }

    @Test
    fun `form rows fold every variant and flag the nominative singular lemma once`() {
        val rows = formRows(substantivs("Boot")[0], 42)
        assertTrue(rows.any { it.form.equals("Boot", ignoreCase = true) && it.isLemma })
        assertEquals(1, rows.count { it.isLemma }, "exactly one lemma row expected")
        assertTrue(rows.any { it.form == "Böte" && it.numerus == "Plural" })
        assertTrue(rows.all { it.entryId == 42L })
        rows.forEach { assertEquals(foldForm(it.form), it.formFolded) }
    }

    @Test
    fun `plural tantum parents has plural rows only and a plural gender`() {
        val eltern = substantivs("Eltern")[0]
        assertEquals("PLURAL", entryRow(eltern, 7)!!.genders)

        val rows = formRows(eltern, 7)
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.numerus == "Plural" }, "singular rows: $rows")
        assertEquals(1, rows.count { it.isLemma }, "the nominative plural carries the lemma")
    }

    @Test
    fun `entry whose lemma appears in no nominative cell still yields its lemma row`() {
        val familyName = substantivs("Boot")[2]
        val rows = formRows(familyName, 3)
        assertEquals(1, rows.size)
        val lemmaOnly = rows[0]
        assertEquals("Boot", lemmaOnly.form)
        assertEquals(foldForm("Boot"), lemmaOnly.formFolded)
        assertEquals(3L, lemmaOnly.entryId)
        assertEquals("Singular", lemmaOnly.numerus)
        assertEquals("Nominativ", lemmaOnly.kasus)
        assertTrue(lemmaOnly.isLemma)
    }

    @Test
    fun `pages without nouns produce no entry rows`() {
        val adjectivePage = """
            == Wandern ({{Sprache|Deutsch}}) ==
            === {{Wortart|Adjektiv|Deutsch}} ===

            {{Bedeutungen}}
            :[1] dem [[Wandern]] [[gewidmet]]
        """.trimIndent()
        assertTrue(parsePage(adjectivePage).filterIsInstance<Substantiv>().isEmpty())
        assertNull(entryRow(Substantiv(), 0))
    }
}
