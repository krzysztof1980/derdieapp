package pl.kwitukiewicz.handygerman.artikel.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import pl.kwitukiewicz.handygerman.artikel.core.Entry
import pl.kwitukiewicz.handygerman.artikel.core.FormHit
import pl.kwitukiewicz.handygerman.artikel.core.Gender
import pl.kwitukiewicz.handygerman.artikel.core.Kind
import pl.kwitukiewicz.handygerman.artikel.core.WordRepository

/**
 * Read-only [WordRepository] over the exporter's `nouns.db`, on android.database.sqlite.
 * `form_folded` is indexed ([pl.kwitukiewicz.handygerman.artikel.core.NounsSchema.DDL]); the exact
 * lookup rides it, and the prefix scan groups by `form_folded` so autocompletion sees one
 * group per distinct form, in `page_order` within a group.
 */
class SqliteWordRepository(private val db: SQLiteDatabase) : WordRepository {

    override fun exactForm(folded: String): List<FormHit> =
        query("WHERE form_folded = ? ORDER BY page_order", arrayOf(folded))

    override fun prefixForms(prefix: String, limit: Int): List<FormHit> =
        query(
            "WHERE form_folded LIKE ? || '%' ESCAPE '\\' ORDER BY form_folded, page_order LIMIT ?",
            arrayOf(escapeLike(prefix), limit.toString()),
        )

    override fun meta(): Map<String, String> =
        db.rawQuery("SELECT key, value FROM meta", null).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }

    private fun query(whereOrderByLimit: String, args: Array<String>): List<FormHit> =
        db.rawQuery("$SELECT_HIT $whereOrderByLimit", args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        FormHit(
                            form = cursor.string("form"),
                            entry = cursor.entry(),
                            numerus = cursor.nullable("numerus"),
                            kasus = cursor.nullable("kasus"),
                            isLemma = cursor.int("is_lemma") != 0,
                        ),
                    )
                }
            }
        }

    private fun Cursor.entry(): Entry =
        Entry(
            entryId = long("entry_id"),
            lemma = string("lemma"),
            kind = Kind.valueOf(string("kind")),
            // Fail-fast on a name neither side ever writes twice (Gender.valueOf throws).
            genders = string("genders").split(',').map { Gender.valueOf(it) },
            pluralForms = nullable("plural_forms"),
            meaningLines = meanings(string("meaning_lines")),
        )

    private fun meanings(json: String): List<String> = JSONArray(json).let { array ->
        (0 until array.length()).map { array.getString(it) }
    }

    companion object {
        private const val SELECT_HIT = """
            SELECT forms.form, numerus, kasus, is_lemma,
                   entry_id, lemma, kind, genders, plural_forms, meaning_lines
            FROM forms JOIN entries USING(entry_id)
        """

        /** The query declares `ESCAPE '\\'`: make LIKE input match itself. */
        private fun escapeLike(input: String): String =
            input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}

private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
private fun Cursor.nullable(column: String): String? = getString(getColumnIndexOrThrow(column))
private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
