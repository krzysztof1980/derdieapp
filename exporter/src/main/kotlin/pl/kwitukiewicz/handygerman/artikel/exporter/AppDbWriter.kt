package pl.kwitukiewicz.handygerman.artikel.exporter

import pl.kwitukiewicz.handygerman.artikel.core.NounsSchema
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Instant

/**
 * Writes `nouns.db` from scratch: [NounsSchema.DDL], then every row and the `meta`
 * stamps, all in one transaction so a half-written file can never be read.
 *
 * Identity contract with [reduce]: each entry gets an explicit `entry_id` equal to its
 * position in [entries] plus one, and a [FormRow.entryId] must already carry that number;
 * rows keep insertion order (`ORDER BY rowid` reproduces the lists).
 *
 * Everything except [Instant.now]-stamped `built_at` is a pure function of the inputs,
 * so two rebuilds over the same input compare equal.
 */
fun writeDb(entries: List<EntryRow>, forms: List<FormRow>, dbFile: Path, builtFrom: String) {
    val file = dbFile.toAbsolutePath()
    Files.createDirectories(file.parent)
    Files.deleteIfExists(file)
    DriverManager.getConnection("jdbc:sqlite:$file").use { db ->
        db.autoCommit = false
        try {
            db.createStatement().use { statement -> NounsSchema.DDL.forEach { statement.execute(it) } }
            db.prepareStatement(
                "INSERT INTO entries(entry_id, lemma, page_order, kind, genders, plural_forms, meaning_lines) VALUES (?,?,?,?,?,?,?)",
            ).use { insert ->
                entries.forEachIndexed { index, entry ->
                    insert.setLong(1, index + 1L)
                    insert.setString(2, entry.lemma)
                    insert.setInt(3, entry.pageOrder)
                    insert.setString(4, entry.kind.name)
                    insert.setString(5, entry.genders)
                    insert.setString(6, entry.pluralForms)
                    insert.setString(7, entry.meaningLinesJson)
                    insert.addBatch()
                }
                insert.executeBatch()
            }
            db.prepareStatement(
                "INSERT INTO forms(form, form_folded, entry_id, numerus, kasus, is_lemma) VALUES (?,?,?,?,?,?)",
            ).use { insert ->
                forms.forEach { form ->
                    insert.setString(1, form.form)
                    insert.setString(2, form.formFolded)
                    insert.setLong(3, form.entryId)
                    insert.setString(4, form.numerus)
                    insert.setString(5, form.kasus)
                    insert.setLong(6, if (form.isLemma) 1L else 0L)
                    insert.addBatch()
                }
                insert.executeBatch()
            }
            db.prepareStatement("INSERT INTO meta(key, value) VALUES (?,?)").use { insert ->
                meta(builtFrom).forEach { (key, value) ->
                    insert.setString(1, key)
                    insert.setString(2, value)
                    insert.addBatch()
                }
                insert.executeBatch()
            }
            db.commit()
        } catch (failure: Exception) {
            db.rollback()
            throw failure
        }
    }
}

private fun meta(builtFrom: String) = mapOf(
    NounsSchema.META_SCHEMA_VERSION to NounsSchema.VERSION,
    NounsSchema.META_BUILT_FROM to builtFrom,
    NounsSchema.META_BUILT_AT to Instant.now().toString(),
    NounsSchema.META_FOLD_VECTORS_SHA256 to foldVectorsSha256(),
)

/**
 * SHA-256 hex of the `:core` fold-vectors resource BYTES, stamped into `meta` for offline
 * forensics of asset-vs-jar vector drift. The app itself does not verify this stamp:
 * debug builds catch behavior-relevant drift via FoldSelfCheck's whole-forms fold scan.
 */
fun foldVectorsSha256(): String {
    val bytes = NounsSchema::class.java.classLoader
        .getResourceAsStream(NounsSchema.FOLD_VECTORS_RESOURCE)
        ?.readBytes()
        ?: error("missing classpath resource ${NounsSchema.FOLD_VECTORS_RESOURCE}")
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
