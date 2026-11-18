package pl.kwitukiewicz.handygerman.artikel.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import pl.kwitukiewicz.handygerman.artikel.core.SavedCursor
import pl.kwitukiewicz.handygerman.artikel.core.SavedWord
import pl.kwitukiewicz.handygerman.artikel.core.WordIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The writable `saved_words.db` store: INSERT-OR-IGNORE identity semantics,
 * PK-exact unsave, and the `(saved_at, rowid)` keyset walk that paging leans on.
 * Each test owns a fresh database file — no helper is shared across tests.
 */
@RunWith(RobolectricTestRunner::class)
class SqliteSavedWordsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var dbName: String? = null
    private var helper: SavedWordsDb? = null

    /** Production opens the fixed `saved_words.db`; tests pass a unique name. */
    private fun openStore(): SqliteSavedWordsRepository {
        val name = "saved_test_${System.nanoTime()}.db"
        dbName = name
        return SavedWordsDb(context, dbName = name).also { helper = it }.repository()
    }

    @After
    fun closeStore() {
        helper?.close()
        dbName?.let { context.deleteDatabase(it) }
    }

    private fun word(
        lemma: String,
        senseNo: Int = 1,
        articleKey: String = "FEMININUM",
        savedAt: Long,
    ) = SavedWord(
        lemma = lemma,
        identity = WordIdentity(lemma.lowercase(), articleKey, senseNo),
        firstMeaning = "$lemma meaning $senseNo",
        savedAtMillis = savedAt,
    )

    @Test
    fun saveRoundTripsThroughIsSavedAndPage() {
        val store = openStore()
        val bank = word("Bank", savedAt = 100L)
        val zeit = word("Zeit", savedAt = 200L)

        store.save(bank)
        store.save(zeit)

        assertTrue(store.isSaved(bank.identity))
        assertTrue(store.isSaved(zeit.identity))
        val page = store.page(null, 10)
        assertEquals(2, page.size)
        assertEquals("newest first", "Zeit", page[0].lemma)
        assertTrue("rowids assigned", page.all { it.rowid > 0L })
        assertEquals(100L, page.first { it.lemma == "Bank" }.savedAtMillis)
    }

    @Test
    fun duplicateIdentitySaveIsIgnored() {
        val store = openStore()
        store.save(word("Bank", savedAt = 100L))
        store.save(word("Bank", savedAt = 999L))

        val page = store.page(null, 10)
        assertEquals(1, page.size)
        assertEquals(100L, page.single().savedAtMillis)
    }

    @Test
    fun unsaveTargetsExactIdentity() {
        val store = openStore()
        val sense1 = word("Bank", senseNo = 1, savedAt = 100L)
        val sense2 = word("Bank", senseNo = 2, savedAt = 200L)
        store.save(sense1)
        store.save(sense2)

        store.unsave(sense2.identity)

        assertTrue(store.isSaved(sense1.identity))
        assertFalse(store.isSaved(sense2.identity))
    }

    @Test
    fun keysetPageWalksStableUnderInserts() {
        val store = openStore()
        val originals = (1L..60L).map { word("Wort$it", savedAt = it) }
        originals.forEach(store::save)

        val firstPage = store.page(null, 50)
        assertEquals(50, firstPage.size)

        store.save(word("Neuer", savedAt = 61L)) // newer than every paged row

        val last = firstPage.last()
        val secondPage = store.page(SavedCursor(last.savedAtMillis, last.rowid), 50)
        assertEquals(10, secondPage.size)

        val combined = firstPage.map { it.identity } + secondPage.map { it.identity }
        assertEquals("no overlap", combined.size, combined.distinct().size)
        assertEquals(
            "no gap: exactly the 60 originals — the newer row belongs above the cursor",
            originals.map { it.identity }.toSet(), combined.toSet(),
        )
    }
}
