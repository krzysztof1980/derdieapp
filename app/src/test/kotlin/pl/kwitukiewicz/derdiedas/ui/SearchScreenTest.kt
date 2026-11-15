package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.kwitukiewicz.derdiedas.core.FakeWordRepository
import pl.kwitukiewicz.derdiedas.core.LookupEngine

/**
 * Pins the search screen's contract: the semantics-tag vocabulary Tasks 7-8 test against,
 * the Single answer layout (accented articles, 40sp lemma, notes), the wash hex exposed
 * through the hidden `washColor` node (color asserted without screenshots), and the
 * debounce + generation-guard machinery (stale answers never settle on screen).
 *
 * Timing is deterministic: default-clock tests let `waitUntilExactlyOneExists` advance the
 * virtual clock through the 150 ms debounce; the stale-guard test freezes the clock so the
 * intermediate query is never even looked up. No Thread.sleep anywhere.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class SearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showSearch() {
        compose.setContent { SearchScreen(engineLoader = { LookupEngine(FakeWordRepository()) }) }
    }

    private fun type(term: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(term)
    }

    @Test
    fun singleRendersAccentedArticleLemmaAndWashHex() {
        showSearch()
        type("virus")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.WORD), SETTLE_TIMEOUT_MS)
        compose.onNodeWithTag(UiTags.ARTICLE).assertTextEquals("das/der")
        compose.onNodeWithTag(UiTags.WORD).assertTextEquals("Virus")
        // virus' first gender is NEUTRUM, so the light das-wash must be the active one.
        compose.onNodeWithTag(UiTags.WASH_COLOR).assertTextEquals(DAS_WASH_LIGHT_HEX)
    }

    @Test
    fun pluralOnlyNounCarriesPluralNote() {
        showSearch()
        type("eltern")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.PLURAL_NOTE), SETTLE_TIMEOUT_MS)
    }

    @Test
    fun unknownTermSettlesOnNoResultQuotingTheTypedText() {
        showSearch()
        type("xyzq")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.NO_RESULT), SETTLE_TIMEOUT_MS)
        assertTextContainsViaSemantics(UiTags.NO_RESULT, "xyzq")
    }

    @Test
    fun wordHomonymDemotesNameEntryToSurnameFooter() {
        showSearch()
        type("zeit")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.MEANING_LINE), SETTLE_TIMEOUT_MS)
        compose.onNodeWithTag(UiTags.MEANING_LINE).assertTextEquals("(die) Zeit, Verlauf")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.SURNAME_FOOTER), SETTLE_TIMEOUT_MS)
    }

    @Test
    fun declinedFormGetsEnglishCaseNote() {
        showSearch()
        type("booten")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.FORM_NOTE), SETTLE_TIMEOUT_MS)
        assertTextContainsViaSemantics(UiTags.FORM_NOTE, "dative")
    }

    @Test
    fun staleLookupNeverOvertakesTheCurrentOne() {
        // Frozen clock: "boote" is typed away before its debounce elapses, so no Boot
        // answer may ever reach the screen, and the settled state must be noResult.
        compose.mainClock.autoAdvance = false
        showSearch()
        compose.mainClock.advanceTimeByFrame()
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("boote")
        field.performTextReplacement("bootek")
        awaitTag(UiTags.NO_RESULT)
        assertTextContainsViaSemantics(UiTags.NO_RESULT, "bootek")
        compose.onNodeWithTag(UiTags.WORD).assertDoesNotExist()
        compose.onNodeWithText("Boot").assertDoesNotExist()
    }

    /**
     * Substring check on the node's semantics text. The framework's `assertTextContains`
     * verifies substrings through the text layout, which Robolectric's native graphics
     * leaves empty — reading [SemanticsProperties.Text] asserts the same contract.
     */
    private fun assertTextContainsViaSemantics(tag: String, needle: String) {
        val text = compose.onAllNodesWithTag(tag)
            .fetchSemanticsNodes()
            .single()
            .config[SemanticsProperties.Text]
            .joinToString(" ") { it.text }
        assertTrue("expected '$needle' in node '$tag' with text '$text'", text.contains(needle))
    }

    /** Advances the frozen clock in slices until [tag] appears, bounded by [CLOCK_SLICES]. */
    private fun awaitTag(tag: String) {
        var slices = 0
        while (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() && ++slices <= CLOCK_SLICES) {
            compose.mainClock.advanceTimeBy(CLOCK_SLICE_MS)
            compose.waitForIdle()
        }
    }

    private companion object {
        const val DAS_WASH_LIGHT_HEX = "#FFE9F5EE"
        const val SETTLE_TIMEOUT_MS = 30_000L
        const val CLOCK_SLICE_MS = 100L
        const val CLOCK_SLICES = 50
    }
}
