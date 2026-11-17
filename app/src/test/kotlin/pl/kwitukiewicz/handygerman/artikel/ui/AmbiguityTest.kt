package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.waitUntilExactlyOneExists
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.kwitukiewicz.handygerman.artikel.core.FakeWordRepository
import pl.kwitukiewicz.handygerman.artikel.core.Gender
import pl.kwitukiewicz.handygerman.artikel.core.LookupEngine

/**
 * Task 8's surface: the segmented article row over homonymous entries (wash, meaning and
 * notes follow the selected candidate) and the About dialog's attribution. Wash
 * expectations are derived from [WashPalette] itself so
 * the pin can never drift from the palette.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class AmbiguityTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showSearch() {
        compose.setContent {
            SearchScreen(
                engineLoader = { LookupEngine(FakeWordRepository()) },
                metaProvider = { FakeWordRepository().meta() },
            )
        }
    }

    @Test
    fun segmentedToggleOffersBothArticlesAndCarriesTheWash() {
        showSearch()
        compose.typeQuery("boot")
        // Two candidates: the row carries one node per segment, so await any of them and
        // pin the exact pair via semanticsTexts below.
        compose.awaitTag(UiTags.TOGGLE)
        // das Boot and der Boot share the form: one segment per candidate, in page order.
        assertEquals(listOf("das", "der"), compose.semanticsTexts(UiTags.TOGGLE))
        compose.onNodeWithTag(UiTags.WASH_COLOR)
            .assertTextEquals(washHex(WashPalette.wash(Gender.NEUTRUM, dark = false)))

        compose.onAllNodesWithTag(UiTags.TOGGLE)[1].performClick()
        compose.waitForIdle()
        // The wash follows the selection: now the der-wash, exactly WashPalette's value.
        compose.onNodeWithTag(UiTags.WASH_COLOR)
            .assertTextEquals(washHex(WashPalette.wash(Gender.MASKULINUM, dark = false)))
        // …and so does the detail: the der-Boot entry shows its own meaning line.
        compose.assertTextContainsViaSemantics(UiTags.MEANING_LINE, "Familienname")
    }

    @Test
    fun aboutDialogAttributesWiktionaryAndTheParser() {
        showSearch()
        // 2026-11-15: About lives in the overflow menu now (Amendment #4), not a footer link.
        compose.onNodeWithTag(UiTags.MENU).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.MENU_ITEM), SETTLE_TIMEOUT_MS)
        compose.onNodeWithTag(UiTags.MENU_ITEM).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.ABOUT_BODY), SETTLE_TIMEOUT_MS)
        compose.assertTextContainsViaSemantics(UiTags.ABOUT_BODY, "CC BY-SA 4.0")
        compose.assertTextContainsViaSemantics(UiTags.ABOUT_BODY, "GFDL")
        // The dump stamp rides in on the async meta produceState — await it, don't race it.
        compose.awaitSemanticsText(UiTags.ABOUT_BODY, "de.wiktionary.org, dump test")
        compose.assertTextContainsViaSemantics(UiTags.ABOUT_BODY, "wiktiopeggynary")
    }

    @Test
    fun toggleRowSitsAboveTheWordSoSwappingNeverMovesIt() {
        showSearch()
        compose.typeQuery("boot")
        compose.awaitTag(UiTags.TOGGLE)
        // Spec UX-5: the segmented row is ABOVE the word. Asserted as geometry (not just
        // tree order) so a future layout pass cannot silently reintroduce the vertical
        // jump the author felt on-device: meaning length differs per candidate, so a
        // toggle below the word would move every time the selection changes.
        // One node per segment; they share a row, so the first segment's top is the row's.
        val toggleTop = compose.onAllNodesWithTag(UiTags.TOGGLE)[0]
            .fetchSemanticsNode().boundsInRoot.top
        val wordTop = compose.onNodeWithTag(UiTags.WORD).fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "toggle (y=$toggleTop) must render above the word (y=$wordTop)",
            toggleTop < wordTop,
        )
    }

    @Test
    fun clearButtonAppearsWithTextAndClearsTheQuery() {
        showSearch()
        // Blank field: no affordance to clear.
        compose.onNodeWithTag(UiTags.CLEAR_BUTTON).assertDoesNotExist()
        compose.typeQuery("virus")
        compose.awaitTag(UiTags.WORD)
        compose.onNodeWithTag(UiTags.CLEAR_BUTTON).assertIsDisplayed()
        compose.onNodeWithTag(UiTags.CLEAR_BUTTON).performClick()
        compose.pumpFrames() // clear→debounce→worker lookup→publish needs real frames, not one idle
        // Cleared: the field is empty again and the answer (and its wash) is gone —
        // clearing must reuse the blank path (null result), not flash a NoResult for "".
        compose.onNodeWithTag(UiTags.WORD).assertDoesNotExist()
        compose.onNodeWithTag(UiTags.CLEAR_BUTTON).assertDoesNotExist()
    }

    private companion object {
        const val SETTLE_TIMEOUT_MS = 30_000L
    }
}
