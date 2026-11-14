package pl.kwitukiewicz.derdiedas.exporter

import wiktiopeggynary.model.markup.CompoundRichTextComponent
import wiktiopeggynary.model.markup.CursiveBlock
import wiktiopeggynary.model.markup.InternalLink
import wiktiopeggynary.model.markup.KTemplate
import wiktiopeggynary.model.markup.PlainText
import wiktiopeggynary.model.markup.RichText
import wiktiopeggynary.model.markup.RichTextComponent
import wiktiopeggynary.model.markup.WikipediaLink
import wiktiopeggynary.model.meaning.KontextShortcutMapper

/**
 * Renders a meaning's RichText tree as display text: links become their text, cursive blocks
 * flatten through to their body, [KTemplate]s become bracketed context tags. Spaces collapse.
 */
fun flatten(text: RichText?): String =
    text?.let { renderComponents(it.components) }?.let(::normalizeSpaces) ?: ""

private fun renderComponents(components: List<RichTextComponent>): String =
    components.joinToString("") { render(it) }

private fun render(component: RichTextComponent): String = when (component) {
    is PlainText -> component.text
    is InternalLink -> component.linkText ?: component.pageTitle
    is WikipediaLink -> component.pageTitle
    is CursiveBlock -> renderComponents(component.body.components)
    is KTemplate -> renderKTemplate(component)
    is CompoundRichTextComponent -> renderComponents(component.components)
    else -> ""
}

/**
 * The spec's bracket rule: only KTemplate/Kontext renders as a bracketed context tag
 * (the first part resolves through [KontextShortcutMapper]); trailing parts, their
 * separators and the suffix render as plain text.
 */
private fun renderKTemplate(template: KTemplate): String {
    val parts = template.parts()
    val contextTag = parts.firstOrNull()?.let { bracketContext(render(it.text())) } ?: ""
    val rest = parts.drop(1)
        .joinToString("") { render(it.text()) + it.separator()?.let(::render).orEmpty() }
    return contextTag + rest + template.suffix()?.let(::render).orEmpty()
}

private fun bracketContext(rawContext: String): String {
    val context = stripWikiMarkup(rawContext)
    val mapped = KontextShortcutMapper.getShortcuts()[context] ?: context
    return "[" + stripWikiMarkup(mapped) + "]"
}

private fun stripWikiMarkup(text: String): String =
    text.replace("[[", "").replace("]]", "").replace("''", "").trim()

private fun normalizeSpaces(text: String): String =
    text.replace(Regex("\\s+"), " ").trim()
