package pl.kwitukiewicz.derdiedas.exporter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import wiktiopeggynary.model.markup.KTemplate
import wiktiopeggynary.model.markup.PlainText
import wiktiopeggynary.model.markup.RichText

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
