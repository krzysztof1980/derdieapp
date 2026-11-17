package pl.kwitukiewicz.handygerman.artikel.exporter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import wiktiopeggynary.model.markup.KTemplate
import wiktiopeggynary.model.markup.PlainText
import wiktiopeggynary.model.markup.RichText
import wiktiopeggynary.model.markup.WikipediaLink

internal fun readFixture(name: String): String =
    object {}.javaClass.getResourceAsStream("/articles-de/$name.txt")!!
        .reader(Charsets.UTF_8)
        .readText()

class RichTextFlattenerTest {

    @Test
    fun `boot first meaning flattens to plain text`() {
        val boot = parsePage(readFixture("Boot"))
        assertEquals("kleines Wasserfahrzeug", flatten(boot[0].meanings[0].text))
    }

    // The plan text expected "[Militär]: seegehende…" here, but the design spec brackets
    // only KTemplate/Kontext components; this meaning is a CursiveBlock, which flattens
    // through to its body (controller ruling 2026-11-14, see task-2-report Plan-deviation).
    @Test
    fun `boot cursive context flattens through to its body`() {
        val boot = parsePage(readFixture("Boot"))
        assertEquals(
            "Militär: seegehende Einheiten einer bestimmten Größenordnung bei der Marine",
            flatten(boot[0].meanings[1].text),
        )
    }

    @Test
    fun `gynoeceum cursive context flattens through to its body`() {
        val gynoeceum = parsePage(readFixture("Gynoeceum"))
        assertEquals(
            "Botanik: die Gesamtheit der weiblichen Teile (Fruchtblätter) einer Blüte",
            flatten(gynoeceum[0].meanings[0].text),
        )
    }

    @Test
    fun `flatten of missing text is empty`() {
        assertEquals("", flatten(null))
    }

    // The three fixtures contain no {{K}} template, so the spec's bracketed-context
    // rule (bracketed KTemplate, KontextShortcutMapper lookup, plain suffix) needs
    // a synthetic tree; written against the rule as pinned by the plan + ruling.
    @Test
    fun `KTemplate renders as bracketed shortcut with plain suffix`() {
        val text = RichText(
            KTemplate(listOf(KTemplate.Part(RichText("ugs."), null)), RichText(":")),
            PlainText(" dumme Idee"),
        )
        assertEquals("[umgangssprachlich]: dumme Idee", flatten(text))
    }

    @Test
    fun `KTemplate without shortcut keeps its raw first part bracketed`() {
        val text = RichText(
            KTemplate(
                listOf(KTemplate.Part(RichText("''[[Militär]]''"), null)),
                RichText(":"),
            ),
            PlainText(" Einheiten"),
        )
        assertEquals("[Militär]: Einheiten", flatten(text))
    }

    // Two-part Kontext per the model's own example ({{K|trans.|t1=;|besonders|…}}):
    // the first part's separator must render between the bracket and the rest.
    @Test
    fun `KTemplate renders the first part separator between bracket and remaining parts`() {
        val text = RichText(
            KTemplate(
                listOf(
                    KTemplate.Part(RichText("trans."), RichText("; ")),
                    KTemplate.Part(RichText("besonders"), null),
                ),
                RichText(":"),
            ),
            PlainText(" Rede"),
        )
        assertEquals("[transitiv]; besonders: Rede", flatten(text))
    }

    // Device finding 2026-11-15: {{K|Biologie|Medizin}} stores the pipe separator as a
    // bare "," — rendering it verbatim glued the fields ("[Biologie],Medizin"). The
    // flattener must normalize `,`/`;`/`:` separators inside its own KTemplate output
    // (never elsewhere on the meaning line), while an already-spaced separator ("; ")
    // stays single-spaced — pinned by the test above.
    @Test
    fun `KTemplate multi-field separator gains the missing space`() {
        val text = RichText(
            KTemplate(
                listOf(
                    KTemplate.Part(RichText("Biologie"), RichText(",")),
                    KTemplate.Part(RichText("Medizin"), null),
                ),
                RichText(":"),
            ),
            PlainText(" kleinster Krankheitserreger"),
        )
        assertEquals("[Biologie], Medizin: kleinster Krankheitserreger", flatten(text))
    }

    // Same defect at the OTHER boundary: Wiktionary often writes {{K|Zoologie}}:süd… —
    // the suffix colon closes the template and the glued text is a sibling component,
    // so renderKTemplate's own normalization can never reach it. The junction between a
    // context tag and the sibling right after it gets the same treatment: a redundant
    // separator run at the sibling's start collapses into the template's own separator,
    // and one space always follows it.
    @Test
    fun `context suffix never glues to the following sibling`() {
        val text = RichText(
            KTemplate(listOf(KTemplate.Part(RichText("Zoologie"), null)), RichText(":")),
            PlainText("südamerikanischer Affe"),
        )
        assertEquals("[Zoologie]: südamerikanischer Affe", flatten(text))
    }

    @Test
    fun `repeated separator at the context boundary collapses to one`() {
        val text = RichText(
            KTemplate(listOf(KTemplate.Part(RichText("umgangssprachlich"), null)), RichText(":")),
            PlainText(": Führungsperson"),
        )
        assertEquals("[umgangssprachlich]: Führungsperson", flatten(text))
    }

    @Test
    fun `separator remnant after the context suffix is dropped`() {
        val text = RichText(
            KTemplate(listOf(KTemplate.Part(RichText("Sport"), null)), RichText(":")),
            PlainText(", nur Plural: Sportgerät"),
        )
        assertEquals("[Sport]: nur Plural: Sportgerät", flatten(text))
    }

    // The separator-only sibling (": " between the template and the gloss) is the most
    // common real composition (Vorstand in the live index): collapsing it must still leave
    // the one space — collapsing it to nothing glued the gloss ("[x]:Führungsperson").
    @Test
    fun `separator-only sibling after the context tag still leaves one space`() {
        val text = RichText(
            KTemplate(listOf(KTemplate.Part(RichText("ugs."), null)), RichText(":")),
            PlainText(": "),
            PlainText("Führungsperson"),
            PlainText(" in einem "),
            PlainText("Verein"),
        )
        assertEquals("[umgangssprachlich]: Führungsperson in einem Verein", flatten(text))
    }

    @Test
    fun `whitespace-only sibling after the context tag still leaves one space`() {
        val text = RichText(
            KTemplate(listOf(KTemplate.Part(RichText("kein Plural"), null)), RichText(":")),
            PlainText(" "),
            PlainText("inniges Gefühl"),
        )
        assertEquals("[kein Plural]: inniges Gefühl", flatten(text))
    }

    // Final review round 1: a suffixless tag (`{{K|X}}word` renders parts only) leaves
    // the `]` closing the template, and the punctuation opens the SIBLING — the
    // separator-end rule above never sees it. The leading punctuation must stay (it IS
    // the separator) but gains its space.
    @Test
    fun `suffixless context tag never glues to a punctuated sibling`() {
        val text = RichText(
            KTemplate(listOf(KTemplate.Part(RichText("Zoologie"), null)), null),
            PlainText(":südamerikanischer Affe"),
        )
        assertEquals("[Zoologie]: südamerikanischer Affe", flatten(text))
    }

    @Test
    fun `punctuation-only sibling chain after a suffixless tag keeps the space`() {
        val text = RichText(
            KTemplate(listOf(KTemplate.Part(RichText("regional"), null)), null),
            PlainText(":"),
            PlainText("Knüppel"),
        )
        assertEquals("[regional]: Knüppel", flatten(text))
    }

    // Spec rule "links → their text": WikipediaLink behaves like InternalLink.
    @Test
    fun `wikipedia link renders its text falling back to the page title`() {
        assertEquals(
            "über die letzte Kaltzeit",
            flatten(RichText(PlainText("über die "), WikipediaLink("Letzte_Kaltzeit", "letzte Kaltzeit"))),
        )
        assertEquals("Letzte_Kaltzeit", flatten(RichText(WikipediaLink("Letzte_Kaltzeit", null))))
    }

    @Test
    fun `no wiki markup survives flattening in any fixture meaning`() {
        for (name in listOf("Boot", "Eltern", "Gynoeceum")) {
            for (entry in parsePage(readFixture(name))) {
                for (meaning in entry.meanings) {
                    val flat = flatten(meaning.text)
                    for (marker in listOf("{{", "[[", "''", "<ref")) {
                        assertFalse(flat.contains(marker), "$name: '$flat' still contains $marker")
                    }
                }
            }
        }
    }
}
