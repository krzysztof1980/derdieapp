package pl.kwitukiewicz.handygerman.artikel.exporter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pl.kwitukiewicz.handygerman.artikel.core.Kind
import pl.kwitukiewicz.handygerman.artikel.core.NounsSchema
import pl.kwitukiewicz.handygerman.artikel.core.foldForm
import java.nio.file.Path
import java.sql.DriverManager

class AppDbWriterTest {

    @TempDir
    lateinit var dir: Path

    private val boot = EntryRow(
        lemma = "Boot",
        pageOrder = 0,
        kind = Kind.WORD,
        genders = "NEUTRUM",
        pluralForms = "Boote / Böte",
        meaningLinesJson = """["kleines Wasserfahrzeug"]""",
    )

    private val zeitSurname = EntryRow(
        lemma = "Zeit",
        pageOrder = 1,
        kind = Kind.PERSON_NAME,
        genders = "MASKULINUM,FEMININUM",
        pluralForms = null,
        meaningLinesJson = """["deutschsprachiger Nachname, Familienname"]""",
    )

    private fun forms() = listOf(
        FormRow("Boot", foldForm("Boot"), entryId = 1, "Singular", "Nominativ", isLemma = true),
        FormRow("Booten", foldForm("Booten"), entryId = 1, "Plural", "Dativ", isLemma = false),
        FormRow("Boote", foldForm("Boote"), entryId = 1, "Plural", "Nominativ", isLemma = false),
        FormRow("Böte", foldForm("Böte"), entryId = 1, "Plural", "Nominativ", isLemma = false),
        FormRow("Zeit", foldForm("Zeit"), entryId = 2, "Singular", "Nominativ", isLemma = true),
    )

    private fun rows(db: Path, sql: String): List<String> =
        DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).let { rs ->
                    val columns = rs.metaData.columnCount
                    buildList {
                        while (rs.next()) {
                            add((1..columns).joinToString("|") { rs.getString(it) ?: "null" })
                        }
                    }
                }
            }
        }

    @Test
    fun `writing two entries yields a queryable db stamped with meta`() {
        val db = dir.resolve("nouns.db")
        writeDb(listOf(boot, zeitSurname), forms(), db, "test-dump")

        assertEquals(
            listOf(NounsSchema.VERSION),
            rows(db, "SELECT value FROM meta WHERE key='${NounsSchema.META_SCHEMA_VERSION}'"),
        )
        assertEquals(
            listOf("test-dump"),
            rows(db, "SELECT value FROM meta WHERE key='${NounsSchema.META_BUILT_FROM}'"),
        )
        assertTrue(rows(db, "SELECT value FROM meta WHERE key='${NounsSchema.META_BUILT_AT}'").single().isNotBlank())
        assertEquals(
            listOf(foldVectorsSha256OfResource()),
            rows(db, "SELECT value FROM meta WHERE key='${NounsSchema.META_FOLD_VECTORS_SHA256}'"),
        )

        // Folded lookup: 'Böte' must be reachable under its folded key and carry the right entry.
        assertEquals(listOf("Böte|1"), rows(db, "SELECT form, entry_id FROM forms WHERE form_folded='boete'"))
        assertEquals(listOf("Boot|1"), rows(db, "SELECT form, entry_id FROM forms WHERE form_folded='boot' AND is_lemma=1"))
        assertEquals(listOf("Zeit|2"), rows(db, "SELECT form, entry_id FROM forms WHERE form_folded='zeit'"))
        assertEquals(listOf("integer", "integer", "integer", "integer", "integer"), rows(db, "SELECT typeof(is_lemma) FROM forms ORDER BY rowid"))

        assertEquals(
            listOf("1|Boot|0|WORD|NEUTRUM|Boote / Böte|[\"kleines Wasserfahrzeug\"]", "2|Zeit|1|PERSON_NAME|MASKULINUM,FEMININUM|null|[\"deutschsprachiger Nachname, Familienname\"]"),
            rows(db, "SELECT * FROM entries ORDER BY entry_id"),
        )
    }

    @Test
    fun `rebuilding the db is byte-identical except built_at`() {
        val first = dir.resolve("first.db")
        val second = dir.resolve("second.db")
        writeDb(listOf(boot, zeitSurname), forms(), first, "test-dump")
        writeDb(listOf(boot, zeitSurname), forms(), second, "test-dump")

        assertEquals(rows(first, "SELECT * FROM entries ORDER BY rowid"), rows(second, "SELECT * FROM entries ORDER BY rowid"))
        assertEquals(rows(first, "SELECT * FROM forms ORDER BY rowid"), rows(second, "SELECT * FROM forms ORDER BY rowid"))
        val metaWithoutBuiltAt = "SELECT key, value FROM meta WHERE key <> '${NounsSchema.META_BUILT_AT}' ORDER BY key"
        assertEquals(rows(first, metaWithoutBuiltAt), rows(second, metaWithoutBuiltAt))
    }
}

/** The ruling for `fold_vectors_sha256`: hash of the `:core` resource BYTES, not of a path string. */
internal fun foldVectorsSha256OfResource(): String {
    val bytes = object {}.javaClass.getResourceAsStream("/${NounsSchema.FOLD_VECTORS_RESOURCE}")!!.readBytes()
    return java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
