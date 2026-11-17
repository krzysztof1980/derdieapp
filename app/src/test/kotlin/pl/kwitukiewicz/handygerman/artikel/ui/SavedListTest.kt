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
import pl.kwitukiewicz.handygerman.artikel.core.FakeSavedWordsRepository
import pl.kwitukiewicz.handygerman.artikel.core.FakeWordRepository
import pl.kwitukiewicz.handygerman.artikel.core.LookupEngine
import pl.kwitukiewicz.handygerman.artikel.core.SavedWord
import pl.kwitukiewicz.handygerman.artikel.core.WordIdentity

/**
 * The blank-query home state: saved rows (newest first, live-resolved, snapshot-
 * rendered when a sense vanished) instead of the empty wash; paging appends below
 * the visible end; stars — on rows and on the detail — remove rows from a mounted
 * list through the epoch. The list only exists while the dictionary is open.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class SavedListTest {

    @get:Rule
    val compose = createComposeRule()

    private val fakeSaved = FakeSavedWordsRepository()

    private fun show(store: FakeSavedWordsRepository = fakeSaved) = compose.setContent {
        SearchScreen(
            engineLoader = { LookupEngine(FakeWordRepository()) },
            savedLoader = { store },
        )
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

        compose.awaitTag(UiTags.SAVED_ROW)
        val texts = compose.rowTexts()
        assertEquals(3, texts.size)
        assertTrue(texts[0].contains("Virus"))
        assertTrue(texts[1].contains("Zeit"))
        assertTrue(texts[2].contains("Boot"))

        compose.typeQuery("Z") // any query hides the list
        compose.onNodeWithTag(UiTags.SAVED_LIST).assertDoesNotExist()

        compose.onNodeWithTag(UiTags.CLEAR_BUTTON).performClick() // X brings it back
        compose.awaitTag(UiTags.SAVED_ROW)
        assertEquals(3, compose.rowTexts().size)
    }

    @Test
    fun listAppearsOnlyAfterDictionaryOpens() {
        val gate = CompletableDeferred<Unit>()
        compose.setContent {
            SearchScreen(
                engineLoader = { gate.await(); LookupEngine(FakeWordRepository()) },
                savedLoader = { fakeSaved },
            )
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag(UiTags.SAVED_LIST).assertDoesNotExist()

        gate.complete(Unit)
        compose.awaitTag(UiTags.SAVED_LIST)
        compose.onNodeWithTag(UiTags.SAVED_LIST).assertExists()
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
        compose.onNodeWithTag(UiTags.SAVED_LIST).performScrollToIndex(59)
        compose.pumpFrames()
        assertTrue(
            "oldest page reached",
            compose.rowTexts().any { it.contains("Wort1 ") || it.contains("Wort1\n") || it.endsWith("Wort1") },
        )
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

    @Test
    fun unstarFromDetailRemovesRowFromMountedList() {
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
}
