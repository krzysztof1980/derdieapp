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

private const val SEPARATOR_CHARS = ",;:"

/**
 * Junction after a context tag (device finding 2026-11-15): Wiktionary writes
 * `{{K|Zoologie}}:süd…` — the suffix colon closes the template and the glued text is a
 * sibling component, so [renderKTemplate]'s own normalization can never reach it. When
 * the previous component was a KTemplate and its rendering ends on a separator, the
 * sibling's leading separator run (redundant leftovers like `:`/`,` from the wikitext)
 * collapses into that separator and exactly one space follows.
 */
private fun renderComponents(components: List<RichTextComponent>): String = buildString {
    var previousWasContextTag = false
    for (component in components) {
        var rendered = render(component)
        if (previousWasContextTag && length > 0 && this[length - 1] in SEPARATOR_CHARS) {
            // The space is unconditional: separator-only siblings (": ", " ") collapse
            // but the context tag still keeps its one space before the following text —
            // dropping them to "" glued 8387 exported meanings (caught by re-export scan).
            rendered = " " + rendered.dropWhile { it == ' ' || it in SEPARATOR_CHARS }
        }
        append(rendered)
        previousWasContextTag = component is KTemplate
    }
}

private fun render(component: RichTextComponent): String = when (component) {
    is PlainText -> component.text
    is InternalLink -> component.linkText ?: component.pageTitle
    is WikipediaLink -> component.linkText ?: component.pageTitle
    is CursiveBlock -> renderComponents(component.body.components)
    is KTemplate -> renderKTemplate(component)
    is CompoundRichTextComponent -> renderComponents(component.components)
    else -> ""
}

/**
 * The spec's bracket rule: only KTemplate/Kontext renders as a bracketed context tag
 * (the first part resolves through [KontextShortcutMapper]); every part's separator,
 * the trailing parts and the suffix render as plain text after it.
 */
private fun renderKTemplate(template: KTemplate): String {
    val parts = template.parts()
    val head = parts.firstOrNull()?.let {
        bracketContext(render(it.text())) + it.separator()?.let(::render).orEmpty()
    } ?: ""
    val rest = parts.drop(1)
        .joinToString("") { render(it.text()) + it.separator()?.let(::render).orEmpty() }
    return normalizeSeparators(head + rest + template.suffix()?.let(::render).orEmpty())
}

/**
 * Wiktionary stores the `{{K}}` field separator verbatim, so `{{K|Biologie|Medizin}}`
 * arrives as "Biologie,Medizin" and glued the two fields in the rendered meaning
 * ("[Biologie],Medizin:"). Device finding 2026-11-15: any `,`/`;`/`:` inside the
 * rendered template gains one following space; a separator that already ends in a
 * space is untouched — exactly one space between bracket fields.
 *
 * Scoped here (KTemplate output only), never to whole meaning lines: arbitrary text
 * can legitimately pack a comma ("…,dass"-style quotes).
 */
private fun normalizeSeparators(rendered: String): String =
    rendered.replace(Regex("([,;:])(?=\\S)"), "$1 ")

private fun bracketContext(rawContext: String): String {
    val context = stripWikiMarkup(rawContext)
    val mapped = KontextShortcutMapper.getShortcuts()[context] ?: context
    return "[" + stripWikiMarkup(mapped) + "]"
}

private fun stripWikiMarkup(text: String): String =
    text.replace("[[", "").replace("]]", "").replace("''", "").trim()

private fun normalizeSpaces(text: String): String =
    text.replace(Regex("\\s+"), " ").trim()
