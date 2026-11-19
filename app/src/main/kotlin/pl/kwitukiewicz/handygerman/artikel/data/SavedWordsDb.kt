package pl.kwitukiewicz.handygerman.artikel.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import pl.kwitukiewicz.handygerman.artikel.core.LearningRepository
import pl.kwitukiewicz.handygerman.artikel.core.LearningState
import pl.kwitukiewicz.handygerman.artikel.core.QUIZ_GRADUATION_LEVEL
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
    private val version: Int = VERSION,
) : SQLiteOpenHelper(context.applicationContext, dbName, null, version) {

    /**
     * A build opened at [version] 1 creates exactly the released v1 schema — the
     * [version] parameter exists for migration tests, and only stays honest if it
     * produces the file that version of the app would have written.
     */
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SAVED_DDL)
        if (version >= 2) db.execSQL(LEARNING_DDL)
    }

    /**
     * The one migration: 1 → 2 adds the `learning` table, saving every row.
     * Any other pair still fails loudly — a future bump must come with a real
     * migration here, never with a recreate, which would destroy user saves.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2) {
            "no saved_words.db migration defined for $oldVersion -> $newVersion"
        }
        db.execSQL(LEARNING_DDL)
    }

    /**
     * A rollback hands this build a schema newer than it knows. The stock hook's
     * base body delegates to the public `onDowngrade(SQLiteDatabase)`, which
     * deletes and recreates the file — the same destroy-user-saves loss [onUpgrade]
     * refuses to buy with a recreate — so refuse loudly here: the file stays as it
     * is, and the next (fixed) release opens it again.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("saved_words.db version $oldVersion is newer than this build ($newVersion) — refusing to drop user saves")
    }

    /** Opens (creating on first touch) and hands out the repository. */
    fun repository(): SqliteSavedWordsRepository = SqliteSavedWordsRepository(writableDatabase)

    /** The v2 learning store over the same file and lifecycle. */
    fun learningRepository(): SqliteLearningRepository = SqliteLearningRepository(writableDatabase)

    companion object {
        const val DB_NAME = "saved_words.db"
        private const val VERSION = 2
        private const val SAVED_DDL =
            "CREATE TABLE saved(lemma TEXT NOT NULL, lemma_folded TEXT NOT NULL, " +
                "article_key TEXT NOT NULL, sense_no INTEGER NOT NULL, " +
                "first_meaning TEXT NULL, saved_at INTEGER NOT NULL, " +
                "PRIMARY KEY(lemma_folded, article_key, sense_no))"
        private const val LEARNING_DDL =
            "CREATE TABLE IF NOT EXISTS learning(lemma_folded TEXT NOT NULL, " +
                "article_key TEXT NOT NULL, sense_no INTEGER NOT NULL, " +
                "level INTEGER NOT NULL DEFAULT 0, graduated_at INTEGER, " +
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

/**
 * Synchronous [LearningRepository] over the `learning` table, same identity PK
 * as `saved`: every mutation is a single-row REPLACE upsert (threading is the
 * screen's job, exactly as in [SqliteSavedWordsRepository]); reads are one query.
 */
class SqliteLearningRepository(private val db: SQLiteDatabase) : LearningRepository {

    override fun states(): Map<WordIdentity, LearningState> =
        db.rawQuery("SELECT lemma_folded, article_key, sense_no, level, graduated_at FROM learning", null)
            .use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        val (identity, state) = cursor.toLearningState()
                        put(identity, state)
                    }
                }
            }

    override fun putLevel(identity: WordIdentity, level: Int) {
        // REPLACE with a NULL stamp: answering again clears any graduation flag.
        val values = identityValues(identity).apply {
            put("level", level)
            putNull("graduated_at")
        }
        db.insertWithOnConflict("learning", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun graduate(identity: WordIdentity, atMillis: Long) {
        val values = identityValues(identity).apply {
            put("level", QUIZ_GRADUATION_LEVEL)
            put("graduated_at", atMillis)
        }
        db.insertWithOnConflict("learning", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun requeue(identity: WordIdentity) = putLevel(identity, level = 0)

    override fun delete(identity: WordIdentity) {
        db.delete("learning", "lemma_folded = ? AND article_key = ? AND sense_no = ?",
            arrayOf(identity.lemmaFolded, identity.articleKey, identity.senseNo.toString()))
    }

    override fun learnedCount(): Int =
        db.rawQuery("SELECT COUNT(*) FROM learning WHERE graduated_at IS NOT NULL", null)
            .use { it.moveToFirst(); it.getInt(0) }

    private fun identityValues(identity: WordIdentity) = ContentValues().apply {
        put("lemma_folded", identity.lemmaFolded)
        put("article_key", identity.articleKey)
        put("sense_no", identity.senseNo)
    }
}

private fun Cursor.toLearningState(): Pair<WordIdentity, LearningState> {
    val identity = WordIdentity(
        lemmaFolded = getString(getColumnIndexOrThrow("lemma_folded")),
        articleKey = getString(getColumnIndexOrThrow("article_key")),
        senseNo = getInt(getColumnIndexOrThrow("sense_no")),
    )
    return identity to LearningState(
        level = getInt(getColumnIndexOrThrow("level")),
        graduatedAt = nullableLong("graduated_at"),
    )
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

private fun Cursor.nullableLong(column: String): Long? =
    if (isNull(getColumnIndexOrThrow(column))) null else getLong(getColumnIndexOrThrow(column))
