package pl.kwitukiewicz.derdiedas.exporter

import com.fasterxml.jackson.databind.ObjectMapper
import pl.kwitukiewicz.derdiedas.core.Kind
import pl.kwitukiewicz.derdiedas.core.foldForm
import wiktiopeggynary.model.Kasus
import wiktiopeggynary.model.Numerus
import wiktiopeggynary.model.WiktionaryEntry
import wiktiopeggynary.model.substantiv.Substantiv
import wiktiopeggynary.parser.ConcurrentParserTaskExecutorFactory
import wiktiopeggynary.parser.ParserService

data class EntryRow(
    val lemma: String,
    val pageOrder: Int,
    val kind: Kind,
    val genders: String,
    val pluralForms: String?,
    val meaningLinesJson: String,
)

data class FormRow(
    val form: String,
    val formFolded: String,
    val entryId: Long,
    val numerus: String?,
    val kasus: String?,
    val isLemma: Boolean,
)

/** Parses one wiki page into its entries; empty when the parser yields nothing. */
fun parsePage(text: String): List<WiktionaryEntry> =
    ParserService(ConcurrentParserTaskExecutorFactory(1))
        .parseWiktionaryEntryPage(text)
        .map { it.wiktionaryEntries }
        .orElse(emptyList())

private val PERSON_NAME_ATTRIBUTES = setOf("Nachname", "Vorname", "Göttername")
private val PLACE_ATTRIBUTES = setOf("Toponym", "Bauwerksname", "Straßenname")

fun kindOf(attributes: Collection<String>): Kind = when {
    attributes.any { it in PERSON_NAME_ATTRIBUTES } -> Kind.PERSON_NAME
    attributes.any { it in PLACE_ATTRIBUTES } -> Kind.PLACE
    else -> Kind.WORD
}

/** Null for entries without any gender — nothing the app could answer with them. */
fun entryRow(e: Substantiv, pageOrder: Int): EntryRow? {
    val genders = e.gender?.genders?.takeIf { it.isNotEmpty() } ?: return null
    return EntryRow(
        lemma = e.lemma,
        pageOrder = pageOrder,
        kind = kindOf(e.attributes),
        genders = genders.joinToString(",") { it.name },
        pluralForms = pluralForms(e),
        meaningLinesJson = MEANINGS_JSON.writeValueAsString(e.meanings.map { flatten(it.text) }),
    )
}

/**
 * One row per distinct (variant, numerus, kasus) of the inflection tables, skipping
 * placeholders like the `—` of plural-only nouns. The lemma shares the row of its
 * nominative singular form (falling back to the nominative plural for plural tantum).
 */
fun formRows(e: Substantiv, entryId: Long): List<FormRow> {
    val cells = flexionCells(e)
    val lemmaCell = cells.singleOrNull { it.isNominativeOf(e.lemma, Numerus.Singular) }
        ?: cells.firstOrNull { it.isNominativeOf(e.lemma, Numerus.Plural) }
    return cells.map { cell ->
        FormRow(
            form = cell.form,
            formFolded = foldForm(cell.form),
            entryId = entryId,
            numerus = cell.numerus.name,
            kasus = cell.kasus.name,
            isLemma = cell == lemmaCell,
        )
    }
}

private data class FlexionCell(val form: String, val numerus: Numerus, val kasus: Kasus) {
    fun isNominativeOf(lemma: String, numerus: Numerus) =
        kasus == Kasus.Nominativ && this.numerus == numerus && form == lemma
}

private fun flexionCells(e: Substantiv): List<FlexionCell> =
    listOf(Numerus.Singular, Numerus.Plural)
        .flatMap { numerus ->
            e.getFlexionForms(numerus).flatMap { form ->
                form.flexions.flatMap { flexion ->
                    flexion.variants.filter(::isWordForm).map { FlexionCell(it, numerus, flexion.kasus) }
                }
            }
        }
        .distinct()

private fun pluralForms(e: Substantiv): String? =
    e.getFlexionForms(Numerus.Plural)
        .flatMap { it.flexions }
        .filter { it.kasus == Kasus.Nominativ }
        .flatMap { it.variants }
        .filter(::isWordForm)
        .distinct()
        .joinToString(" / ")
        .ifEmpty { null }

/** True for strings that carry at least one letter or digit; placeholder rows like `—` do not. */
private fun isWordForm(variant: String): Boolean = variant.any { it.isLetterOrDigit() }

private val MEANINGS_JSON = ObjectMapper()
