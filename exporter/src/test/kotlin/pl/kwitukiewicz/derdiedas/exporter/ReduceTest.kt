package pl.kwitukiewicz.derdiedas.exporter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.kwitukiewicz.derdiedas.core.Kind
import pl.kwitukiewicz.derdiedas.core.foldForm

/**
 * Fixtures under `/esdocs` are the `_source` payloads of the live index
 * (`000000000087-00` das Boot, `000000000087-01` der Boot, `000000000096-01`
 * Zeit/Nachname) — delivered here scrambled, exactly as `scrollAll` hands them over.
 */
class ReduceTest {

    private fun doc(name: String) =
        object {}.javaClass.getResourceAsStream("/esdocs/$name.json")!!
            .reader(Charsets.UTF_8).readText()

    @Test
    fun `reduce sorts by _id and keeps the person-name entry`() {
        val (entries, forms) = reduce(
            listOf(
                "000000000002-00" to doc("zeit-nachname"),
                "000000000001-00" to doc("boot-neutrum"),
                "000000000001-01" to doc("boot-masculinum"),
            ),
        )

        assertEquals(3, entries.size)
        assertEquals(listOf(0, 1, 2), entries.map { it.pageOrder })
        assertEquals(listOf("Boot", "Boot", "Zeit"), entries.map { it.lemma })
        assertEquals(listOf(Kind.WORD, Kind.WORD, Kind.PERSON_NAME), entries.map { it.kind })
        assertEquals("MASKULINUM,FEMININUM", entries[2].genders)
        assertTrue(entries[0].meaningLinesJson.contains("kleines Wasserfahrzeug"))

        // Entry ids are 1-based list positions — the writeDb identity contract.
        assertEquals(listOf(1L, 2L, 3L), forms.map { it.entryId }.distinct().sorted())
        for (position in entries.indices) {
            val entryForms = forms.filter { it.entryId == position + 1L }
            assertTrue(entryForms.isNotEmpty(), "entry ${position + 1} has no forms")
            assertEquals(1, entryForms.count { it.isLemma }, "entry ${position + 1} lemma rows")
            assertTrue(entryForms.all { it.formFolded == foldForm(it.form) })
        }
        assertTrue(forms.any { it.entryId == 3L && it.form == "Zeit" && it.formFolded == "zeit" })
    }

    @Test
    fun `a doc without genders is skipped and grants no entry_id`() {
        // MultiGender refuses empty lists at construction, so a genderless doc is one
        // where the serializer omitted the whole `gender` field.
        val genderless = Regex(",?\"gender\":\\{\"@c\":\"\\.MultiGender\",\"genders\":\\[\"NEUTRUM\"\\],\"empty\":true\\}")
            .replaceFirst(doc("boot-neutrum"), "")
        val (entries, forms) = reduce(
            listOf(
                "000000000001-00" to genderless,
                "000000000002-00" to doc("zeit-nachname"),
            ),
        )
        assertEquals(1, entries.size)
        assertEquals(1, entries.single().pageOrder) // rank of the surviving DOC, not of surviving entries
        assertEquals(Kind.PERSON_NAME, entries.single().kind)
        assertTrue(forms.all { it.entryId == 1L })
    }
}
