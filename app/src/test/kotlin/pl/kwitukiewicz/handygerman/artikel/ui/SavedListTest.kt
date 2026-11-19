package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.kwitukiewicz.handygerman.artikel.core.Entry
import pl.kwitukiewicz.handygerman.artikel.core.FakeLearningRepository
import pl.kwitukiewicz.handygerman.artikel.core.FakeSavedWordsRepository
import pl.kwitukiewicz.handygerman.artikel.core.Gender
import pl.kwitukiewicz.handygerman.artikel.core.Kind
import pl.kwitukiewicz.handygerman.artikel.core.FakeWordRepository
import pl.kwitukiewicz.handygerman.artikel.core.LookupEngine
import pl.kwitukiewicz.handygerman.artikel.core.SavedWord
import pl.kwitukiewicz.handygerman.artikel.core.WordIdentity

/**
 * The blank-query home state: saved rows (newest first, live-resolved, snapshot-
 * rendered when a sense vanished) instead of the empty wash; paging appends below
 * the visible end; stars — on rows and on the detail — remove rows from a mounted
 * list through the epoch. The list mounts from the saved store right away — rows show
 * their snapshot while the dictionary is still opening and re-hydrate once it is open.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class SavedListTest {

    @get:Rule
    val compose = createComposeRule()

    private val fakeSaved = FakeSavedWordsRepository()
    private val fakeLearning = FakeLearningRepository()

    /** Rounds the paging scroll retries before the test declares the page lost. */
    private val scrollAttempts = 20

    private fun show(
        store: FakeSavedWordsRepository = fakeSaved,
        engineLoader: suspend () -> LookupEngine = { LookupEngine(FakeWordRepository()) },
    ) = compose.setContent {
        SearchScreen(engineLoader = engineLoader, savedLoader = { store }, learningLoader = { fakeLearning })
    }

    /** Row texts of the saved list, in display order. */
    private fun ComposeTestRule.rowTexts(): List<String> = semanticsTexts(UiTags.SAVED_ROW)

    private fun savedWord(lemma: String, lemmaFolded: String, articleKey: String, sense: Int, meaning: String, at: Long) =
        SavedWord(lemma, WordIdentity(lemmaFolded, articleKey, sense), meaning, at)

    @Test
    fun blankQueryShowsSavedRowsNewestFirst() {
        fakeSaved.save(savedWord("Boot", "boot", "NEUTRUM", 1, "Wasserfahrzeug", 100L))
        fakeSaved.save(savedWord("Zeit", "zeit", "FEMININUM", 1, "Verlauf", 200L))
        fakeSaved.save(savedWord("Virus", "virus", "MASKULINUM,NEUTRUM", 1, "Erreger", 300L))
        show()

        compose.awaitRowCount(UiTags.SAVED_ROW, 3)
        val texts = compose.rowTexts()
        assertEquals(3, texts.size)
        assertTrue(texts[0].contains("Virus"))
        assertTrue(texts[1].contains("Zeit"))
        assertTrue(texts[2].contains("Boot"))

        compose.typeQuery("Z") // any query hides the list
        compose.onNodeWithTag(UiTags.SAVED_LIST).assertDoesNotExist()

        compose.onNodeWithTag(UiTags.CLEAR_BUTTON).performClick() // X brings it back
        compose.awaitRowCount(UiTags.SAVED_ROW, 3)
        assertEquals(3, compose.rowTexts().size)
    }

    @Test
    fun savedListShowsSnapshotsWhileDictionaryStillOpens() {
        // The saved store is a small independent database: the home list mounts from
        // it while the seconds-long dictionary open is still in flight, rendering the
        // stored snapshot (dictionaryReady = false pass-through hydrator).
        val gate = CompletableDeferred<Unit>()
        fakeSaved.save(savedWord("Wegweiser", "wegweiser", "MASKULINUM", 1, "historischer Hinweis", 100L))
        compose.setContent {
            SearchScreen(
                engineLoader = { gate.await(); LookupEngine(FakeWordRepository()) },
                savedLoader = { fakeSaved },
                learningLoader = { FakeLearningRepository() },
            )
        }
        // Text-wait, not node-wait (see rehydrates test): the stored meaning must
        // actually render before the lemma assertion reads it.
        compose.awaitSemanticsText(UiTags.SAVED_ROW, "historischer Hinweis")
        assertTrue("stored lemma kept", compose.rowTexts().single().contains("Wegweiser"))
        gate.complete(Unit)
    }

    @Test
    fun savedListRehydratesWithLiveEntriesWhenDictionaryOpens() {
        val gate = CompletableDeferred<Unit>()
        fakeSaved.save(savedWord("Bank", "bank", "FEMININUM", 1, "SNAPSHOT-MEANING", 100L))
        compose.setContent {
            SearchScreen(
                engineLoader = { gate.await(); LookupEngine(bankRepository()) },
                savedLoader = { fakeSaved },
                learningLoader = { FakeLearningRepository() },
            )
        }
        // Wait for the snapshot TEXT, not just the row node: a tagged row can exist
        // one merged-semantics frame before its text, and asserting against that
        // intermediate frame is exactly the cold-machine flake CI caught once.
        compose.awaitSemanticsText(UiTags.SAVED_ROW, "SNAPSHOT-MEANING")

        gate.complete(Unit)
        // The dictionary becoming ready re-keys the list: the row now shows the live
        // dictionary's sense instead of the stored snapshot.
        compose.awaitSemanticsText(UiTags.SAVED_ROW, "Sitzgelegenheit")
    }

    @Test
    fun tappingRowWhileDictionaryStillOpensLandsOnVariantOnceItLands() {
        // A tap in the loading window sets the query and the pending open; the first
        // publish after the dictionary lands must steer to the saved variant.
        val gate = CompletableDeferred<Unit>()
        fakeSaved.save(savedWord("Bank", "bank", "FEMININUM", 2, "Geldinstitut", 100L))
        compose.setContent {
            SearchScreen(
                engineLoader = { gate.await(); LookupEngine(bankRepository()) },
                savedLoader = { fakeSaved },
                learningLoader = { FakeLearningRepository() },
            )
        }
        compose.awaitTag(UiTags.SAVED_ROW)
        compose.onNodeWithTag(UiTags.SAVED_ROW).performClick()

        gate.complete(Unit)
        compose.awaitSemanticsText(UiTags.WORD, "Bank")
        assertEquals(listOf("die"), compose.semanticsTexts(UiTags.ARTICLE))
        compose.assertTextContainsViaSemantics(UiTags.MEANING_LINE, "Geldinstitut")
    }

    @Test
    fun emptyStoreShowsHintLine() {
        show()
        compose.awaitTag(UiTags.SAVED_EMPTY)
        compose.onNodeWithTag(UiTags.SAVED_EMPTY).assertExists()
    }

    @Test
    fun scrollToBottomAppendsNextPage() {
        repeat(60) { index -> // rowid order follows insertion; savedAt ascends with it
            fakeSaved.save(savedWord("Wort${index + 1}", "wort${index + 1}", "FEMININUM", 1, "Bedeutung ${index + 1}", index + 1L))
        }
        show()
        compose.awaitTag(UiTags.SAVED_ROW)

        // The store holds 60; the first page is 50 — scrolling to the loaded end must
        // append the rest (snapshot rows: none of these lemmas resolve live).
        compose.onNodeWithTag(UiTags.SAVED_LIST).performScrollToIndex(49)
        compose.pumpFrames()
        // scrollToIndex clamps while page 2 is still on its way in, so re-assert the
        // scroll each round until the oldest row composes — no wall-clock bet on the
        // page's IO landing between the two calls.
        var reached = false
        var attempts = 0
        while (!reached && ++attempts <= scrollAttempts) {
            compose.onNodeWithTag(UiTags.SAVED_LIST).performScrollToIndex(59)
            compose.pumpFrames(5)
            reached = compose.rowTexts().any { it.contains("Wort1 ") || it.endsWith("Wort1") }
        }
        assertTrue("oldest page reached", reached)
        // No seam assert: LazyColumn composes only its viewport, so a "Wort10 and
        // Wort11 adjacent" check is not decidable through scroll semantics —
        // gapless, duplicate-free page walks are pinned at the store (Task 2).
    }

    @Test
    fun rowStarUnsavesAndRowVanishes() {
        fakeSaved.save(savedWord("Zeit", "zeit", "FEMININUM", 1, "Verlauf", 100L))
        show()
        compose.awaitTag(UiTags.SAVED_ROW)

        compose.onAllNodesWithTag(UiTags.SAVE_TOGGLE)[0].performClick()

        compose.pumpFrames()
        compose.onAllNodesWithTag(UiTags.SAVED_ROW).assertCountEquals(0)
        assertTrue(fakeSaved.saved.isEmpty())
    }

    /**
     * The empty-list hint must not hide at the bottom of the screen in fine
     * print: it sits at the centre of the area the list would occupy — the pin
     * asserts coincidence of centres, not pixels (size/style is the screenshot's
     * business; presence was pinned before, placement never).
     */
    @Test
    fun emptyHintIsCenteredInTheListArea() {
        show()
        compose.awaitTag(UiTags.SAVED_EMPTY)

        val hint = compose.onNodeWithTag(UiTags.SAVED_EMPTY).fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag(UiTags.SAVED_LIST).fetchSemanticsNode().boundsInRoot

        val hintCx = hint.center.x
        val hintCy = hint.center.y
        val listCx = list.center.x
        val listCy = list.center.y
        assertEquals("horizontally centred in the list area", listCx, hintCx, 8f)
        assertEquals("vertically centred in the list area", listCy, hintCy, 8f)
    }

    /**
     * The row star is the detail star's inverse — including its second half:
     * "the star is the learning", so un-saving here deletes the in-progress
     * learning row in the same IO hop (no orphaned level behind a gone save).
     */
    @Test
    fun rowStarUnsaveDeletesLearningProgress() {
        fakeSaved.save(savedWord("Zeit", "zeit", "FEMININUM", 1, "Verlauf", 100L))
        fakeLearning.putLevel(WordIdentity("zeit", "FEMININUM", 1), 1)
        show()
        compose.awaitTag(UiTags.SAVED_ROW)

        compose.onAllNodesWithTag(UiTags.SAVE_TOGGLE)[0].performClick()

        compose.pumpFrames()
        compose.onAllNodesWithTag(UiTags.SAVED_ROW).assertCountEquals(0)
        assertTrue(fakeLearning.statesMap.isEmpty())
    }

    // Name says what the mechanism delivers: the row is gone when the list comes
    // back. This is NOT the epoch path (the detail and the list are never mounted
    // at once) — clearing the query re-mounts and re-reads the store; the in-place
    // epoch remount is pinned by rowStarUnsavesAndRowVanishes.
    @Test
    fun unstarInDetailGoneFromListOnReturn() {
        fakeSaved.save(savedWord("Zeit", "zeit", "FEMININUM", 1, "Verlauf", 100L))
        show()
        compose.awaitTag(UiTags.SAVED_ROW)

        compose.onNodeWithTag(UiTags.SAVED_ROW).performClick() // open the detail
        compose.awaitTag(UiTags.SAVE_TOGGLE)
        compose.onNodeWithTag(UiTags.SAVE_TOGGLE).performClick() // un-star there
        compose.awaitStarDescription("Save word")

        compose.onNodeWithTag(UiTags.CLEAR_BUTTON).performClick() // back to the list
        compose.awaitTag(UiTags.SAVED_EMPTY)
        compose.onNodeWithTag(UiTags.SAVED_EMPTY).assertExists()
        assertTrue(fakeSaved.saved.isEmpty())
    }

    @Test
    fun vanishedSenseRendersSnapshot() {
        // No "Wegweiser" in the dictionary fake — the row must fall back to the snapshot.
        fakeSaved.save(savedWord("Wegweiser", "wegweiser", "MASKULINUM", 1, "historischer Hinweis", 100L))
        show()

        compose.awaitTag(UiTags.SAVED_ROW)
        val text = compose.rowTexts().single()
        assertTrue("stored lemma kept", text.contains("Wegweiser"))
        assertTrue("stored meaning kept", text.contains("historischer Hinweis"))
    }

    @Test
    fun tappingRowOpensSavedHomographVariant() {
        // das Teil comes first in page order — without the row's target, the tap
        // would land on selection 0 (das) no matter which variant was saved.
        fakeSaved.save(savedWord("Teil", "teil", "MASKULINUM", 1, "Element eines Ganzen", 100L))
        show(engineLoader = { LookupEngine(teilRepository()) })

        compose.awaitTag(UiTags.SAVED_ROW)
        compose.onNodeWithTag(UiTags.SAVED_ROW).performClick()

        compose.awaitSemanticsText(UiTags.WORD, "Teil")
        assertEquals(2, compose.onAllNodesWithTag(UiTags.TOGGLE).fetchSemanticsNodes().size)
        assertEquals(listOf("der"), compose.semanticsTexts(UiTags.ARTICLE))
        compose.assertTextContainsViaSemantics(UiTags.MEANING_LINE, "Element eines Ganzen")
    }

    @Test
    fun tappingRowOpensSavedSenseWithinSameArticle() {
        fakeSaved.save(savedWord("Bank", "bank", "FEMININUM", 2, "Geldinstitut", 100L))
        show(engineLoader = { LookupEngine(bankRepository()) })

        compose.awaitTag(UiTags.SAVED_ROW)
        compose.onNodeWithTag(UiTags.SAVED_ROW).performClick()

        compose.awaitSemanticsText(UiTags.WORD, "Bank")
        assertEquals(listOf("die"), compose.semanticsTexts(UiTags.ARTICLE))
        compose.assertTextContainsViaSemantics(UiTags.MEANING_LINE, "Geldinstitut")
    }

    private fun teilRepository() = FakeWordRepository(
        entries = listOf(
            Entry(1, "Teil", Kind.WORD, listOf(Gender.NEUTRUM), "Teil", listOf("Stück; Teilmenge")),
            Entry(2, "Teil", Kind.WORD, listOf(Gender.MASKULINUM), "Teile", listOf("Element eines Ganzen")),
        ),
        rows = listOf(
            FakeWordRepository.Row("teil", 1, "Singular", "Nominativ", isLemma = true),
            FakeWordRepository.Row("teil", 2, "Singular", "Nominativ", isLemma = true),
        ),
    )

    private fun bankRepository() = FakeWordRepository(
        entries = listOf(
            Entry(1, "Bank", Kind.WORD, listOf(Gender.FEMININUM), "Bänke", listOf("Sitzgelegenheit")),
            Entry(2, "Bank", Kind.WORD, listOf(Gender.FEMININUM), "Banken", listOf("Geldinstitut")),
        ),
        rows = listOf(
            FakeWordRepository.Row("bank", 1, "Singular", "Nominativ", isLemma = true),
            FakeWordRepository.Row("bank", 2, "Singular", "Nominativ", isLemma = true),
        ),
    )
}
