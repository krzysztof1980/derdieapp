package pl.kwitukiewicz.handygerman.artikel.core

/**
 * In-memory [SavedWordsRepository] mirroring the SQLite store's contract: save
 * ignores an already-saved identity (INSERT OR IGNORE), rowids are assigned
 * deterministically from 1, and [page] walks `(savedAtMillis, rowid)` descending
 * with a strictly-less-than cursor. [clock] stamps `savedAtMillis` only for
 * words submitted without one (value 0), so tests keep full control of order.
 */
class FakeSavedWordsRepository(private val clock: () -> Long = System::currentTimeMillis) :
    SavedWordsRepository {

    private val rows = mutableListOf<SavedWord>()
    private var nextRowid = 1L

    /** Store (insertion) order, with assigned rowids. */
    val saved: List<SavedWord> get() = rows.toList()

    override fun save(word: SavedWord) {
        if (rows.any { it.identity == word.identity }) return
        rows += word.copy(
            savedAtMillis = if (word.savedAtMillis > 0L) word.savedAtMillis else clock(),
            rowid = if (word.rowid > 0L) word.rowid else nextRowid++,
        )
    }

    override fun unsave(identity: WordIdentity) {
        rows.removeAll { it.identity == identity }
    }

    override fun isSaved(identity: WordIdentity): Boolean = rows.any { it.identity == identity }

    override fun page(after: SavedCursor?, limit: Int): List<SavedWord> =
        rows.asSequence()
            .filter {
                after == null || it.savedAtMillis < after.savedAtMillis ||
                    (it.savedAtMillis == after.savedAtMillis && it.rowid < after.rowid)
            }
            .sortedWith(compareByDescending<SavedWord> { it.savedAtMillis }.thenByDescending { it.rowid })
            .take(limit)
            .toList()
}
