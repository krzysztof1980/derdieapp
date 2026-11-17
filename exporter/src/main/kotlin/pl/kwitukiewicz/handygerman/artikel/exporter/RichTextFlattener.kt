package pl.kwitukiewicz.handygerman.artikel.exporter

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
 * sibling component, so [renderKTemplate]'s own normalization can never reach it. While
 * the context tag's tail is open (a KTemplate, optionally followed by separator/blank-only
 * siblings), the sibling's leading separator run collapses into the tag's own separator
 * and exactly one space follows; when the tag is suffixless, the sibling's own leading
 * punctuation (the real separator) gains that space.
 */
private fun renderComponents(components: List<RichTextComponent>): String = buildString {
    var contextTailOpen = false
    for (component in components) {
        var rendered = render(component)
        if (contextTailOpen && rendered.isNotEmpty()) {
            if (length > 0 && this[length - 1] in SEPARATOR_CHARS) {
                // The space is unconditional: separator-only siblings (": ", " ") collapse
                // but the context tag still keeps its one space before the following text —
                // dropping them to "" glued 8387 exported meanings (caught by re-export scan).
                rendered = " " + rendered.dropWhile { it == ' ' || it in SEPARATOR_CHARS }
            } else if (rendered.first() in SEPARATOR_CHARS) {
                // Suffixless tag (`]` closes the template): the sibling's punctuation IS
                // the separator — it stays, but gains the space after it.
                val run = rendered.takeWhile { it in SEPARATOR_CHARS }
                val rest = rendered.drop(run.length)
                rendered = run + if (rest.isEmpty() || rest.first() == ' ') rest else " $rest"
            }
        }
        append(rendered)
        // A punctuation/whitespace-only sibling continues the tag's tail, so the chain
        // ([X] + ":" + word) still gets its one space at the real word.
        val tailContinues = rendered.isNotEmpty() && rendered.all { it == ' ' || it in SEPARATOR_CHARS }
        contextTailOpen = component is KTemplate || (contextTailOpen && tailContinues)
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
