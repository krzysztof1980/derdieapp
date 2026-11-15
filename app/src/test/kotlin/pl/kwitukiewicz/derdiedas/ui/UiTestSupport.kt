@file:OptIn(ExperimentalTestApi::class)

package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertTrue

/**
 * Shared screen-test plumbing: [SearchScreenTest] and [AmbiguityTest] drive the same
 * semantics-tag vocabulary and the same frozen-clock idiom, so the helpers live once here.
 * The framework's `assertTextContains` verifies substrings through the text layout, which
 * Robolectric's native graphics leaves empty — reading [SemanticsProperties.Text] asserts
 * the same contract, hence [semanticsTexts] and everything built on it.
 */

internal const val CLOCK_SLICE_MS = 100L
internal const val CLOCK_SLICES = 50

/** Replaces the query field's content — one keystroke burst, no per-char events. */
internal fun ComposeContentTestRule.typeQuery(term: String) {
    onNode(hasSetTextAction()).performTextReplacement(term)
}

/** Semantics texts of every node tagged [tag], in tree order. */
internal fun ComposeContentTestRule.semanticsTexts(tag: String): List<String> =
    onAllNodesWithTag(tag).fetchSemanticsNodes().map { node ->
        node.config[SemanticsProperties.Text].joinToString(" ") { it.text }
    }

/** Substring check on the single node's semantics text tagged [tag]. */
internal fun ComposeContentTestRule.assertTextContainsViaSemantics(tag: String, needle: String) {
    val text = semanticsTexts(tag).single()
    assertTrue("expected '$needle' in node '$tag' with text '$text'", text.contains(needle))
}

/** Advances the frozen clock in slices until a node tagged [tag] appears. */
internal fun ComposeContentTestRule.awaitTag(tag: String) = awaitWhile {
    onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
}

/** Advances the frozen clock in slices until [tag]'s semantics text contains [needle]. */
internal fun ComposeContentTestRule.awaitSemanticsText(tag: String, needle: String) = awaitWhile {
    semanticsTexts(tag).none { needle in it }
}

/** Runs [CLOCK_SLICES] idle frames — enough rounds for a freed worker thread to race back. */
internal fun ComposeContentTestRule.pumpFrames() = awaitWhile { false }

private inline fun ComposeContentTestRule.awaitWhile(absent: () -> Boolean) {
    var slices = 0
    while (absent() && ++slices <= CLOCK_SLICES) {
        mainClock.advanceTimeBy(CLOCK_SLICE_MS)
        waitForIdle()
    }
}
