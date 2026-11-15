package pl.kwitukiewicz.derdiedas.core

/** One Wiktionary entry: what a page asserts about a headword, ready for display. */
data class Entry(
    val entryId: Long,
    val lemma: String,
    val kind: Kind,
    val genders: List<Gender>,
    val pluralForms: String?,
    val meaningLines: List<String>,
)

/** One row of the `forms` table joined with the [entry] it belongs to. */
data class FormHit(
    val form: String,
    val entry: Entry,
    val numerus: String?,
    val kasus: String?,
    val isLemma: Boolean,
)

/**
 * The read side of `nouns.db`, pure so that the lookup engine compiles against it without
 * Android: `:app` implements it over SQLite, engine tests with an in-memory fake.
 * Every argument is already canonical [foldForm] output — folding happens once, at input.
 */
interface WordRepository {

    /** Every form folding exactly to [folded], page order. */
    fun exactForm(folded: String): List<FormHit>

    /** Forms starting with the literal [prefix] (`LIKE` metacharacters escaped), grouped by form, page order, max [limit]. */
    fun prefixForms(prefix: String, limit: Int): List<FormHit>

    /** The exporter's `meta` stamps (`schema_version`, `built_from`, `built_at`, …). */
    fun meta(): Map<String, String>
}
