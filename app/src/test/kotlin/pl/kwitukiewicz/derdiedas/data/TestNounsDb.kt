package pl.kwitukiewicz.derdiedas.data

import pl.kwitukiewicz.derdiedas.core.NounsSchema
import pl.kwitukiewicz.derdiedas.core.foldForm
import java.nio.file.Path
import java.sql.DriverManager

/**
 * Builds the Task 5 fixture `nouns.db`: executes [NounsSchema.DDL] verbatim over JDBC,
 * then the pinned fixture rows and the `meta` stamps. The real 93 MiB export is never
 * touched by unit tests — everything here is a few kilobytes.
 *
 * JDBC (test-scope org.xerial:sqlite-jdbc) is only the *writer*; production reads stay
 * on android.database.sqlite, which opens these files like any other SQLite database.
 * Rows mirror the exporter's physical layout: `meta` is the first table in [DDL], so its
 * pages (and the `built_at` record [WordsDb] scans for) sit at the head of the file.
 */
object TestNounsDb {

    data class EntryRow(
        val entryId: Long,
        val lemma: String,
        val pageOrder: Int,
        val kind: String,
        val genders: String,
        val pluralForms: String?,
        val meaningLines: List<String>,
    )

    data class FormRow(
        val form: String,
        val entryId: Long,
        val numerus: String?,
        val kasus: String?,
        val isLemma: Boolean,
    )

    // The folded column always goes through the app's own foldForm (see create) —
    // the fixture is clean by construction, only `poison` plants drift.

    val ENTRIES: List<EntryRow> = listOf(
        EntryRow(1, "Boot", 1, "WORD", "NEUTRUM", "Boote",
            listOf("(das) Boot, ein Wasserfahrzeug")),
        EntryRow(2, "Boot", 2, "WORD", "MASKULINUM", "Boote",
            listOf("(der) Boot, Familienname")),
        EntryRow(3, "Virus", 3, "WORD", "NEUTRUM,MASKULINUM", "Viren",
            listOf("(das|der) Virus, mikrobiologischer Erreger")),
        EntryRow(4, "Zeit", 4, "WORD", "FEMININUM", "Zeiten",
            listOf("(die) Zeit, Verlauf")),
        EntryRow(5, "Zeit", 5, "PERSON_NAME", "FEMININUM", null,
            listOf("(die) Zeit, weiblicher Vorname")),
        EntryRow(6, "Eltern", 6, "WORD", "PLURAL", null,
            listOf("(die) Eltern, Mutter und Vater")),
        EntryRow(7, "Müller", 7, "PERSON_NAME", "MASKULINUM", null,
            listOf("(der) Müller, Familienname; Beruf: Müller")),
        EntryRow(8, "Kevin", 8, "PERSON_NAME", "MASKULINUM", null,
            listOf("(der) Kevin, männlicher Vorname")),
    )

    val FORMS: List<FormRow> = listOf(
        // das Boot: lemma, NomPl, (böte), DatPl
        FormRow("boot", 1, "Singular", "Nominativ", isLemma = true),
        FormRow("boote", 1, "Plural", "Nominativ", isLemma = false),
        FormRow("böte", 1, "Singular", "Nominativ", isLemma = false),
        FormRow("booten", 1, "Plural", "Dativ", isLemma = false),
        // der Boot: lemma, NomPl, GenSg
        FormRow("boot", 2, "Singular", "Nominativ", isLemma = true),
        FormRow("boote", 2, "Plural", "Nominativ", isLemma = false),
        FormRow("booten", 2, "Singular", "Genitiv", isLemma = false),
        FormRow("virus", 3, "Singular", "Nominativ", isLemma = true),
        FormRow("zeit", 4, "Singular", "Nominativ", isLemma = true),
        FormRow("zeit", 5, "Singular", "Nominativ", isLemma = true),
        FormRow("eltern", 6, "Plural", "Nominativ", isLemma = true),
        // Müller: the name itself and its <ae>/<oe>/<ue>/<ss> spelling variant
        FormRow("müller", 7, "Singular", "Nominativ", isLemma = true),
        FormRow("mueller", 7, "Singular", "Nominativ", isLemma = false),
        FormRow("kevin", 8, "Singular", "Nominativ", isLemma = true),
    )

    /**
     * Writes the fixture into [dir] and returns the database path.
     *
     * [schemaVersion]/[builtAt] vary the meta stamps; [poison] adds a `Straße`→`straße`
     * row (fold drift the self-check must name); [unknownGender] corrupts Kevin's genders
     * CSV so `Gender.valueOf` must fail fast.
     */
    fun create(
        dir: Path,
        builtAt: String = "t1",
        schemaVersion: String = NounsSchema.VERSION,
        poison: Boolean = false,
        unknownGender: Boolean = false,
        name: String = "nouns.db",
    ): Path {
        val file = dir.resolve(name)
        DriverManager.getConnection("jdbc:sqlite:$file").use { db ->
            db.autoCommit = false
            db.createStatement().use { statement -> NounsSchema.DDL.forEach { statement.execute(it) } }
            db.prepareStatement("INSERT INTO meta(key, value) VALUES (?,?)").use { insert ->
                listOf(
                    NounsSchema.META_SCHEMA_VERSION to schemaVersion,
                    NounsSchema.META_BUILT_FROM to "test",
                    NounsSchema.META_BUILT_AT to builtAt,
                ).forEach { (key, value) ->
                    insert.setString(1, key)
                    insert.setString(2, value)
                    insert.addBatch()
                }
                insert.executeBatch()
            }
            db.prepareStatement(
                "INSERT INTO entries(entry_id, lemma, page_order, kind, genders, plural_forms, meaning_lines) VALUES (?,?,?,?,?,?,?)",
            ).use { insert ->
                ENTRIES.forEach { entry ->
                    val genders = if (unknownGender && entry.entryId == 8L) "MASKULINUM,GENUS" else entry.genders
                    insert.setLong(1, entry.entryId)
                    insert.setString(2, entry.lemma)
                    insert.setInt(3, entry.pageOrder)
                    insert.setString(4, entry.kind)
                    insert.setString(5, genders)
                    insert.setString(6, entry.pluralForms)
                    insert.setString(7, meaningsJson(entry.meaningLines))
                    insert.addBatch()
                }
                insert.executeBatch()
            }
            db.prepareStatement(
                "INSERT INTO forms(form, form_folded, entry_id, numerus, kasus, is_lemma) VALUES (?,?,?,?,?,?)",
            ).use { insert ->
                val poisonRow = FormRow("Straße", 8, null, null, isLemma = false)
                (FORMS + if (poison) listOf(poisonRow) else emptyList()).forEach { form ->
                    val folded = if (form === poisonRow) "straße" else foldForm(form.form)
                    insert.setString(1, form.form)
                    insert.setString(2, folded)
                    insert.setLong(3, form.entryId)
                    insert.setString(4, form.numerus)
                    insert.setString(5, form.kasus)
                    insert.setLong(6, if (form.isLemma) 1L else 0L)
                    insert.addBatch()
                }
                insert.executeBatch()
            }
            db.commit()
        }
        return file
    }

    /** Same JSON list of strings the exporter's Jackson writes into `meaning_lines`. */
    fun meaningsJson(lines: List<String>): String =
        lines.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
}
