package pl.kwitukiewicz.handygerman.core

import pl.kwitukiewicz.handygerman.core.Gender.FEMININUM
import pl.kwitukiewicz.handygerman.core.Gender.MASKULINUM
import pl.kwitukiewicz.handygerman.core.Gender.NEUTRUM
import pl.kwitukiewicz.handygerman.core.Gender.PLURAL
import pl.kwitukiewicz.handygerman.core.Kind.PERSON_NAME
import pl.kwitukiewicz.handygerman.core.Kind.WORD

/**
 * In-memory [WordRepository] mirroring the Task 5 fixture (`TestNounsDb`): same entries,
 * forms and meta stamps as plain data, no SQLite. Ordering replicates the SQL:
 * `exactForm` by page order, `prefixForms` by folded form then page order, capped at
 * `limit`. Page order is the [ENTRIES] position, matching the fixture's `page_order` 1..8.
 *
 * The `form` values are surface spellings; the fake folds them with [foldForm] exactly like
 * the exporter fills `forms.form_folded`, so the fixture is clean by construction.
 */
class FakeWordRepository(
    private val entries: List<Entry> = ENTRIES,
    private val rows: List<Row> = ROWS,
    private val meta: Map<String, String> = META,
) : WordRepository {

    /** One `forms` row: surface [form], owning [entryId] and its grammatical annotation. */
    data class Row(
        val form: String,
        val entryId: Long,
        val numerus: String?,
        val kasus: String?,
        val isLemma: Boolean,
    )

    private val entriesById: Map<Long, Entry> = entries.associateBy { it.entryId }
    private val pageOrder: Map<Long, Int> =
        entries.withIndex().associate { (index, entry) -> entry.entryId to index }

    override fun exactForm(folded: String): List<FormHit> =
        rows.withIndex()
            .filter { (_, row) -> foldForm(row.form) == folded }
            .sortedWith { a, b -> // mirrors `ORDER BY page_order`; ties keep the seeded row order
                val byPage = pageOrder.getValue(a.value.entryId).compareTo(pageOrder.getValue(b.value.entryId))
                if (byPage != 0) byPage else a.index.compareTo(b.index)
            }
            .map { (_, row) -> row.hit() }

    override fun prefixForms(prefix: String, limit: Int): List<FormHit> =
        rows.withIndex()
            .filter { (_, row) -> foldForm(row.form).startsWith(prefix) }
            .sortedWith(compareBy(
                { it.value.form.let(::foldForm) },
                { pageOrder.getValue(it.value.entryId) },
                { it.index },
            ))
            .take(limit)
            .map { (_, row) -> row.hit() }

    override fun meta(): Map<String, String> = meta

    private fun Row.hit() = FormHit(
        form = form,
        entry = requireNotNull(entriesById[entryId]) { "form '$form' references unknown entry $entryId" },
        numerus = numerus,
        kasus = kasus,
        isLemma = isLemma,
    )

    companion object {

        /** The Task 5 fixture entries, in page order 1..8 (ids equal it in the fixture). */
        val ENTRIES: List<Entry> = listOf(
            Entry(1, "Boot", WORD, listOf(NEUTRUM), "Boote",
                listOf("(das) Boot, ein Wasserfahrzeug")),
            Entry(2, "Boot", WORD, listOf(MASKULINUM), "Boote",
                listOf("(der) Boot, Familienname")),
            Entry(3, "Virus", WORD, listOf(NEUTRUM, MASKULINUM), "Viren",
                listOf("(das|der) Virus, mikrobiologischer Erreger")),
            Entry(4, "Zeit", WORD, listOf(FEMININUM), "Zeiten",
                listOf("(die) Zeit, Verlauf")),
            Entry(5, "Zeit", PERSON_NAME, listOf(FEMININUM), null,
                listOf("(die) Zeit, weiblicher Vorname")),
            Entry(6, "Eltern", WORD, listOf(PLURAL), null,
                listOf("(die) Eltern, Mutter und Vater")),
            Entry(7, "Müller", PERSON_NAME, listOf(MASKULINUM), null,
                listOf("(der) Müller, Familienname; Beruf: Müller")),
            Entry(8, "Kevin", PERSON_NAME, listOf(MASKULINUM), null,
                listOf("(der) Kevin, männlicher Vorname")),
        )

        /**
         * The Task 5 fixture forms. `booten` carries the plan's noun model on purpose:
         * Dativ plural on the das-Boot row, Genitiv singular on the der-Boot row — that is
         * what pins `booten → Homonyms (Dativ/Genitiv)`.
         */
        val ROWS: List<Row> = listOf(
            // das Boot: lemma, NomPl, (böte), DatPl
            Row("boot", 1, "Singular", "Nominativ", isLemma = true),
            Row("boote", 1, "Plural", "Nominativ", isLemma = false),
            Row("böte", 1, "Singular", "Nominativ", isLemma = false),
            Row("booten", 1, "Plural", "Dativ", isLemma = false),
            // der Boot: lemma, NomPl, GenSg
            Row("boot", 2, "Singular", "Nominativ", isLemma = true),
            Row("boote", 2, "Plural", "Nominativ", isLemma = false),
            Row("booten", 2, "Singular", "Genitiv", isLemma = false),
            Row("virus", 3, "Singular", "Nominativ", isLemma = true),
            Row("zeit", 4, "Singular", "Nominativ", isLemma = true),
            Row("zeit", 5, "Singular", "Nominativ", isLemma = true),
            Row("eltern", 6, "Plural", "Nominativ", isLemma = true),
            // Müller: the name itself and its <ae>/<oe>/<ue>/<ss> spelling variant
            Row("müller", 7, "Singular", "Nominativ", isLemma = true),
            Row("mueller", 7, "Singular", "Nominativ", isLemma = false),
            Row("kevin", 8, "Singular", "Nominativ", isLemma = true),
            // Escape-discriminator (Task 5 review round 1): a real form containing the
            // ESCAPE character, so a backslash prefix has a positive match to find.
            Row("ke\\vin", 8, null, null, isLemma = false),
        )

        /** Fixture stand-in for the exporter's real 64-hex stamp (row-for-row with TestNounsDb). */
        const val DUMMY_FOLD_VECTORS_SHA256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        val META: Map<String, String> = mapOf(
            NounsSchema.META_SCHEMA_VERSION to NounsSchema.VERSION,
            NounsSchema.META_BUILT_AT to "t1",
            NounsSchema.META_BUILT_FROM to "test",
            NounsSchema.META_FOLD_VECTORS_SHA256 to DUMMY_FOLD_VECTORS_SHA256,
        )
    }
}
