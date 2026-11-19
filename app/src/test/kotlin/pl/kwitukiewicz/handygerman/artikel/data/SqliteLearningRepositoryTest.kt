package pl.kwitukiewicz.handygerman.artikel.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import pl.kwitukiewicz.handygerman.artikel.core.LearningState
import pl.kwitukiewicz.handygerman.artikel.core.SavedWord
import pl.kwitukiewicz.handygerman.artikel.core.WordIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The `learning` table of `saved_words.db` v2: single-row upserts over the shared
 * identity PK, the graduation stamp's lifecycle, and the v1→v2 migration that must
 * neither lose saves nor pretend the learning table exists before it was made.
 * Each test owns a fresh database file — no helper is shared across tests.
 */
@RunWith(RobolectricTestRunner::class)
class SqliteLearningRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var dbName: String? = null
    private var helper: SavedWordsDb? = null

    private val bank = WordIdentity("bank", "FEMININUM", 1)

    /** Opens a fresh unique file; [version] simulates an older build's schema. */
    private fun open(version: Int = 2): SavedWordsDb {
        val name = "learning_test_${System.nanoTime()}.db"
        dbName = name
        return reopen(version)
    }

    private fun reopen(version: Int = 2): SavedWordsDb =
        SavedWordsDb(context, dbName = dbName!!, version = version).also { helper = it }

    private fun word(lemma: String, savedAt: Long) = SavedWord(
        lemma = lemma,
        identity = WordIdentity(lemma.lowercase(), "FEMININUM", 1),
        firstMeaning = "Sitz- oder Ablagegelegenheit",
        savedAtMillis = savedAt,
    )

    @After
    fun closeStore() {
        helper?.close()
        dbName?.let { context.deleteDatabase(it) }
    }

    @Test
    fun learningStateRoundTrips() {
        val learning = open().learningRepository()

        learning.putLevel(bank, 1)
        assertEquals(LearningState(1, graduatedAt = null), learning.states()[bank])

        learning.graduate(bank, atMillis = 500L)
        assertEquals(LearningState(3, graduatedAt = 500L), learning.states()[bank])
        assertEquals(1, learning.learnedCount())

        learning.requeue(bank)
        assertEquals(LearningState(0, graduatedAt = null), learning.states()[bank])
        assertEquals(0, learning.learnedCount())

        learning.delete(bank)
        assertNull(learning.states()[bank])
    }

    @Test
    fun putLevelAfterGraduateClearsTheGraduationStamp() {
        val learning = open().learningRepository()
        learning.graduate(bank, atMillis = 500L)

        learning.putLevel(bank, 1)

        assertEquals(LearningState(1, graduatedAt = null), learning.states()[bank])
        assertEquals(0, learning.learnedCount())
    }

    @Test
    fun v1FileUpgradesInPlaceKeepingSaves() {
        // A real v1 file (learning table absent) reopened by the v2 build: saves
        // intact via the migration, and the new table usable — onUpgrade really
        // ran, an insert into a missing table would throw.
        open(version = 1).repository().save(word("Bank", savedAt = 100L))
        helper?.close()

        val v2 = reopen(version = 2)
        val rows = v2.repository().page(null, 10)
        assertEquals(1, rows.size)
        assertEquals("Bank", rows.single().lemma)

        val learning = v2.learningRepository()
        assertEquals(emptyMap<WordIdentity, LearningState>(), learning.states())
        learning.putLevel(bank, 1)
        assertEquals(LearningState(1, graduatedAt = null), learning.states()[bank])
    }

    @Test
    fun v2FileOnAV1BuildRefusesInsteadOfDeletingSaves() {
        // The rollback shape from SqliteSavedWordsRepositoryTest, aimed at v2: a
        // file stamped 2 handed to a build that knows only 1 must be refused
        // loudly, with the rows of both tables untouched.
        open(version = 2).repository().save(word("Bank", savedAt = 100L))
        helper?.close()

        val path = context.getDatabasePath(dbName!!).absolutePath
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("PRAGMA user_version = 2")
        }

        val tooOld = SavedWordsDb(context, dbName = dbName!!, version = 1)
        try {
            val thrown = runCatching { tooOld.repository() }.exceptionOrNull()
            assertTrue(
                "expected the v2 file to be refused by a v1 build, got: $thrown",
                thrown is IllegalStateException,
            )
        } finally {
            tooOld.close()
        }

        // The refusal lost nothing: the save survives for the next (fixed) build.
        val revived = reopen(version = 2)
        helper = revived
        val rows = revived.repository().page(null, 10)
        assertEquals(1, rows.size)
        assertEquals("Bank", rows.single().lemma)
    }

    @Test
    fun crashBetweenGraduateAndUnsaveKeepsBothRows() {
        // Graduation writes graduate-then-unsave; a crash between them leaves a
        // graduated-but-saved row — repairable by the re-queue rule, so the store
        // must keep BOTH rows across a reopen, not lose either side.
        val db = open()
        db.repository().save(word("Bank", savedAt = 100L))
        db.learningRepository().graduate(bank, atMillis = 700L)
        helper?.close()

        val reopened = reopen()
        assertEquals(1, reopened.repository().page(null, 10).size)
        assertEquals(LearningState(3, graduatedAt = 700L), reopened.learningRepository().states()[bank])
        assertEquals(1, reopened.learningRepository().learnedCount())
    }
}
