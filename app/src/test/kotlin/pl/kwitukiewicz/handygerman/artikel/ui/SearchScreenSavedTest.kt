package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.kwitukiewicz.handygerman.artikel.core.Entry
import pl.kwitukiewicz.handygerman.artikel.core.FakeLearningRepository
import pl.kwitukiewicz.handygerman.artikel.core.FakeSavedWordsRepository
import pl.kwitukiewicz.handygerman.artikel.core.FakeWordRepository
import pl.kwitukiewicz.handygerman.artikel.core.LearningRepository
import pl.kwitukiewicz.handygerman.artikel.core.Gender
import pl.kwitukiewicz.handygerman.artikel.core.Kind
import pl.kwitukiewicz.handygerman.artikel.core.LookupEngine
import pl.kwitukiewicz.handygerman.artikel.core.SavedWordsRepository
import pl.kwitukiewicz.handygerman.artikel.core.WordIdentity

/**
 * The star contract on the answer detail: membership is per *(lemma, article,
 * sense)* — homographs with the same article star independently, a save made
 * from a declined-form subset carries the FULL-lemma sense number, star state
 * republishes with the answer it belongs to, and a saved store that refuses to
 * open lands on the same plain error screen as a refused dictionary.
 *
 * Toggle assertions never read the store right after the click: the write is
 * off-main and the star flips only when it lands, so every assertion first
 * [awaitStarDescription] — the flip is the write's receipt.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class SearchScreenSavedTest {

    @get:Rule
    val compose = createComposeRule()

    private val fakeSaved = FakeSavedWordsRepository()
    private val fakeLearning = FakeLearningRepository()

    // Two die-Bank variants (`banken` on the second only — Task 1's corpus shape)
    // plus a third unrelated word for the stale-publish choreography.
    private val bank1 = Entry(1, "Bank", Kind.WORD, listOf(Gender.FEMININUM), "Bänke", listOf("Sitzgelegenheit"))
    private val bank2 = Entry(2, "Bank", Kind.WORD, listOf(Gender.FEMININUM), "Banken", listOf("Geldinstitut"))
    private val zeit = Entry(3, "Zeit", Kind.WORD, listOf(Gender.FEMININUM), "Zeiten", listOf("(die) Zeit, Verlauf"))
    private val bankRows = listOf(
        FakeWordRepository.Row("bank", 1, "Singular", "Nominativ", isLemma = true),
        FakeWordRepository.Row("bank", 2, "Singular", "Nominativ", isLemma = true),
        FakeWordRepository.Row("banken", 2, "Plural", "Nominativ", isLemma = false),
        FakeWordRepository.Row("zeit", 3, "Singular", "Nominativ", isLemma = true),
    )
    private val bankEntries = listOf(bank1, bank2, zeit)

    private fun bankRepository(): GatingRepository =
        GatingRepository(FakeWordRepository(entries = bankEntries, rows = bankRows))

    private fun show(
        engineLoader: suspend () -> LookupEngine = { LookupEngine(FakeWordRepository()) },
        savedLoader: suspend () -> SavedWordsRepository = { fakeSaved },
        learningLoader: suspend () -> LearningRepository = { fakeLearning },
    ) = compose.setContent {
        SearchScreen(engineLoader = engineLoader, savedLoader = savedLoader, learningLoader = learningLoader)
    }

    @Test
    fun starSavesDisplayedWordAndPersistsIntoStore() {
        show()
        compose.typeQuery("Zeit")
        compose.awaitTag(UiTags.SAVE_TOGGLE)
        compose.onNodeWithTag(UiTags.SAVE_TOGGLE).performClick()
        compose.awaitStarDescription("Remove from saved")

        val stored = fakeSaved.saved.single()
        assertEquals(WordIdentity("zeit", "FEMININUM", 1), stored.identity)
        assertEquals("Zeit", stored.lemma)
        assertEquals("(die) Zeit, Verlauf", stored.firstMeaning)
        assertTrue(stored.savedAtMillis > 0L)
    }

    @Test
    fun starStateSurvivesQueryRoundTrip() {
        show()
        compose.typeQuery("Zeit")
        compose.awaitTag(UiTags.SAVE_TOGGLE)
        compose.onNodeWithTag(UiTags.SAVE_TOGGLE).performClick()
        compose.awaitStarDescription("Remove from saved")

        compose.typeQuery("")
        compose.typeQuery("Zeit")
        compose.awaitSemanticsText(UiTags.MEANING_LINE, "Zeit")
        compose.awaitStarDescription("Remove from saved")
    }

    @Test
    fun sameArticleHomonymsStarIndependently() {
        show(engineLoader = { LookupEngine(FakeWordRepository(entries = bankEntries, rows = bankRows)) })
        compose.typeQuery("Bank")
        compose.awaitTag(UiTags.TOGGLE)
        compose.onAllNodesWithTag(UiTags.TOGGLE)[1].performClick()
        compose.awaitTag(UiTags.SAVE_TOGGLE)

        compose.onNodeWithTag(UiTags.SAVE_TOGGLE).performClick()
        compose.awaitStarDescription("Remove from saved")
        assertEquals(WordIdentity("bank", "FEMININUM", 2), fakeSaved.saved.single().identity)

        compose.onAllNodesWithTag(UiTags.TOGGLE)[0].performClick()
        assertEquals("Save word", compose.starDescription())
    }

    @Test
    fun savingFromDeclinedSubsetHitsUsesFullLemmaRanking() {
        show(engineLoader = { LookupEngine(FakeWordRepository(entries = bankEntries, rows = bankRows)) })
        compose.typeQuery("banken") // hits carry only the second variant…
        compose.awaitTag(UiTags.SAVE_TOGGLE)
        compose.onNodeWithTag(UiTags.SAVE_TOGGLE).performClick()
        compose.awaitStarDescription("Remove from saved")
        assertEquals(WordIdentity("bank", "FEMININUM", 2), fakeSaved.saved.single().identity)

        compose.typeQuery("Bank") // …so opening the lemma must still light sense 2 only.
        compose.awaitTag(UiTags.TOGGLE)
        assertEquals("Save word", compose.starDescription()) // selection 0: sense 1 — un-saved
        compose.onAllNodesWithTag(UiTags.TOGGLE)[1].performClick()
        assertEquals("Remove from saved", compose.starDescription())
    }

    @Test
    fun togglingAgainUnsaves() {
        show()
        compose.typeQuery("Zeit")
        compose.awaitTag(UiTags.SAVE_TOGGLE)
        compose.onNodeWithTag(UiTags.SAVE_TOGGLE).performClick()
        compose.awaitStarDescription("Remove from saved")
        compose.onNodeWithTag(UiTags.SAVE_TOGGLE).performClick()
        compose.awaitStarDescription("Save word")

        assertTrue(fakeSaved.saved.isEmpty())
    }

    @Test
    fun stalePublishLeavesSavedStarsOfCurrentAnswerIntact() {
        // Parked `banken` lookup (whose variant IS saved) may not overwrite the star
        // state of the settled `Zeit` answer — saved identities are published together
        // with the answer under the one generation check.
        compose.mainClock.autoAdvance = false
        val gating = bankRepository()
        show(engineLoader = { LookupEngine(gating) })
        compose.mainClock.advanceTimeByFrame()
        val field = compose.onNode(hasSetTextAction())

        field.performTextReplacement("Bank")
        compose.awaitTag(UiTags.TOGGLE)
        compose.onAllNodesWithTag(UiTags.TOGGLE)[1].performClick()
        compose.awaitTag(UiTags.SAVE_TOGGLE)
        compose.onNodeWithTag(UiTags.SAVE_TOGGLE).performClick()
        compose.awaitStarDescription("Remove from saved") // bank sense 2 saved

        gating.blockNext()
        field.performTextReplacement("banken")
        compose.mainClock.advanceTimeBy(200)
        runBlocking { gating.inFlight.await() } // parked mid-flight — its answer is saved

        field.performTextReplacement("zeit") // supersede; zeit settles un-saved
        compose.awaitSemanticsText(UiTags.WORD, "Zeit")
        assertEquals("Save word", compose.starDescription())

        gating.releaseNext() // the stale banken publish attempt lands late…
        compose.pumpFrames()
        compose.onNodeWithText("Bank").assertDoesNotExist() // …guard dropped it
        assertEquals("Save word", compose.starDescription()) // …star follows Zeit, not banken
        assertEquals(1, fakeSaved.saved.size)
    }

    @Test
    fun savedToggleLandingWhileLookupFliesIsNotErasedByItsPublish() {
        // Review Focus line 3, real interleaving: the `banken` snapshot's isSaved statement
        // reads before the user's star write commits (modelled by [ParkedStaleReadStore],
        // faithful to a mid-statement SQLite read), write + flip land mid-park, then the
        // stale read returns. The publish must notice the store write (generation guard)
        // and re-derive — otherwise the fresh star goes dark while the store holds the save.
        val store = ParkedStaleReadStore(fakeSaved, staleOn = WordIdentity("bank", "FEMININUM", 2))
        show(
            engineLoader = { LookupEngine(FakeWordRepository(entries = bankEntries, rows = bankRows + pluralOnlyBaenke)) },
            savedLoader = { store },
        )
        compose.typeQuery("baenke") // Single: bank sense 2, star outlined
        compose.awaitTag(UiTags.SAVE_TOGGLE)

        store.arm()
        compose.typeQuery("banken") // lookup settles, its snapshot parks mid-read
        compose.mainClock.advanceTimeBy(200)
        runBlocking { withTimeout(5_000) { store.parked.await() } }

        compose.onNodeWithTag(UiTags.SAVE_TOGGLE).performClick() // write commits while parked
        compose.awaitStarDescription("Remove from saved")
        assertEquals(WordIdentity("bank", "FEMININUM", 2), fakeSaved.saved.single().identity)

        store.resume.complete(Unit) // the stale snapshot read finally returns
        compose.pumpFrames(5) // let the (stale) publish take its turn on Main

        // The store holds the save; the answer on screen is bank sense 2 — star stays lit.
        assertEquals("Remove from saved", compose.starDescription())
    }

    /**
     * Models a SQLite statement that read a value and then stalls: with [arm] set, the
     * next matching read takes the delegate's value, parks until [resume], and returns the
     * (possibly stale) value taken at entry — a write committing while parked is invisible.
     */
    private class ParkedStaleReadStore(
        private val delegate: SavedWordsRepository,
        private val staleOn: WordIdentity,
    ) : SavedWordsRepository by delegate {
        val parked = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()

        @Volatile
        private var armed = false

        fun arm() {
            armed = true
        }

        override fun isSaved(identity: WordIdentity): Boolean {
            if (armed && identity == staleOn) {
                armed = false
                val stale = delegate.isSaved(identity)
                runBlocking {
                    parked.complete(Unit)
                    resume.await()
                }
                return stale
            }
            return delegate.isSaved(identity)
        }
    }

    private companion object {
        /** `Bänke` folds to `baenke` and belongs to the SECOND Bank entry only. */
        val pluralOnlyBaenke = FakeWordRepository.Row("baenke", 2, "Plural", "Nominativ", isLemma = false)
    }

    @Test
    fun savedStoreLoadFailureLandsOnErrorScreen() {
        show(savedLoader = { throw IOException("disk full") })
        compose.awaitTag(UiTags.DICTIONARY_ERROR)
    }
}
