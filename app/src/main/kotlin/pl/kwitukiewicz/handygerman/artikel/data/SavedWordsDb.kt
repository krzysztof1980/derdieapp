package pl.kwitukiewicz.handygerman.artikel.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import pl.kwitukiewicz.handygerman.artikel.core.SavedCursor
import pl.kwitukiewicz.handygerman.artikel.core.SavedWord
import pl.kwitukiewicz.handygerman.artikel.core.SavedWordsRepository
import pl.kwitukiewicz.handygerman.artikel.core.WordIdentity

/**
 * The app's second, writable database: user saves, kept strictly apart from the
 * read-only dictionary asset — which the app re-seeds wholesale on `built_at`
 * change and which would therefore destroy anything stored inside it.
 *
 * Identity is the spec's *(lemma_folded, article_key, sense_no)* triple as the
 * PRIMARY KEY — no `entry_id`: dictionary ids are global dump ranks and shift on
 * every rebuild, while this triple survives them by construction.
 */
class SavedWordsDb(
    context: Context,
    private val dbName: String = DB_NAME,
) : SQLiteOpenHelper(context.applicationContext, dbName, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(DDL)
    }

    /**
     * Version 1 defines no upgrade path: bumping [VERSION] must come with a real
     * migration here — never with a recreate, which would destroy user saves.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 1) {
            "no saved_words.db migration defined for $oldVersion -> $newVersion"
        }
    }

    /** Opens (creating on first touch) and hands out the repository. */
    fun repository(): SqliteSavedWordsRepository = SqliteSavedWordsRepository(writableDatabase)

    companion object {
        const val DB_NAME = "saved_words.db"
        private const val VERSION = 1
        private const val DDL =
            "CREATE TABLE saved(lemma TEXT NOT NULL, lemma_folded TEXT NOT NULL, " +
                "article_key TEXT NOT NULL, sense_no INTEGER NOT NULL, " +
                "first_meaning TEXT NULL, saved_at INTEGER NOT NULL, " +
                "PRIMARY KEY(lemma_folded, article_key, sense_no))"
    }
}

/**
 * Synchronous [SavedWordsRepository] over `saved_words.db`, mirroring
 * [SqliteWordRepository]'s style — threading is the screen's job. Saves are
 * INSERT-OR-IGNORE on the identity PK; paging walks `(saved_at, rowid)`
 * descending with a strictly-less-than keyset cursor (no OFFSET — stable under
 * inserts above the cursor).
 */
class SqliteSavedWordsRepository(private val db: SQLiteDatabase) : SavedWordsRepository {

    override fun save(word: SavedWord) {
        val values = ContentValues().apply {
            put("lemma", word.lemma)
            put("lemma_folded", word.identity.lemmaFolded)
            put("article_key", word.identity.articleKey)
            put("sense_no", word.identity.senseNo)
            put("first_meaning", word.firstMeaning)
            put("saved_at", word.savedAtMillis)
        }
        db.insertWithOnConflict("saved", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun unsave(identity: WordIdentity) {
        db.delete("saved", "lemma_folded = ? AND article_key = ? AND sense_no = ?",
            arrayOf(identity.lemmaFolded, identity.articleKey, identity.senseNo.toString()))
    }

    override fun isSaved(identity: WordIdentity): Boolean =
        db.rawQuery(
            "SELECT 1 FROM saved WHERE lemma_folded = ? AND article_key = ? AND sense_no = ? LIMIT 1",
            arrayOf(identity.lemmaFolded, identity.articleKey, identity.senseNo.toString()),
        ).use { it.moveToFirst() }

    override fun page(after: SavedCursor?, limit: Int): List<SavedWord> {
        val selection = after?.let { "WHERE saved_at < ? OR (saved_at = ? AND rowid < ?)" }.orEmpty()
        val args = when (after) {
            null -> arrayOf(limit.toString())
            else -> arrayOf(
                after.savedAtMillis.toString(),
                after.savedAtMillis.toString(),
                after.rowid.toString(),
                limit.toString(),
            )
        }
        return db.rawQuery(
            "SELECT rowid AS _id, * FROM saved $selection ORDER BY saved_at DESC, rowid DESC LIMIT ?",
            args,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toSavedWord()) } }
    }
}

private fun Cursor.toSavedWord() = SavedWord(
    lemma = getString(getColumnIndexOrThrow("lemma")),
    identity = WordIdentity(
        lemmaFolded = getString(getColumnIndexOrThrow("lemma_folded")),
        articleKey = getString(getColumnIndexOrThrow("article_key")),
        senseNo = getInt(getColumnIndexOrThrow("sense_no")),
    ),
    firstMeaning = nullableString("first_meaning"),
    savedAtMillis = getLong(getColumnIndexOrThrow("saved_at")),
    rowid = getLong(getColumnIndexOrThrow("_id")),
)

private fun Cursor.nullableString(column: String): String? =
    if (isNull(getColumnIndexOrThrow(column))) null else getString(getColumnIndexOrThrow(column))
