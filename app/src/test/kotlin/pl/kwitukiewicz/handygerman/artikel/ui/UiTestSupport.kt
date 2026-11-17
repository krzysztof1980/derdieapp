@file:OptIn(ExperimentalTestApi::class)

package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTextReplacement
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import pl.kwitukiewicz.handygerman.artikel.core.FakeWordRepository
import pl.kwitukiewicz.handygerman.artikel.core.FormHit
import pl.kwitukiewicz.handygerman.artikel.core.WordRepository

/**
 * Shared screen-test plumbing: [SearchScreenTest] and [AmbiguityTest] drive the same
 * semantics-tag vocabulary and the same frozen-clock idiom, so the helpers live once here.
 * The framework's `assertTextContains` verifies substrings through the text layout, which
 * Robolectric's native graphics leaves empty — reading [SemanticsProperties.Text] asserts
 * the same contract, hence [semanticsTexts] and everything built on it.
 */

internal const val CLOCK_SLICE_MS = 100L
internal const val CLOCK_SLICES = 50

// The slice pumps advance only Compose's frozen clock; the IO/Default hops these tests
// wait on (produceState, SQLite reads, hydrators) run on real threads that need real wall
// time to be scheduled. Each slice therefore also sleeps — without it the whole budget can
// burn in tens of milliseconds on a busy machine and the next assertion reads stale state.
private const val WALL_SLICE_MS = 5L

/** Replaces the query field's content — one keystroke burst, no per-char events. */
internal fun ComposeTestRule.typeQuery(term: String) {
    onNode(hasSetTextAction()).performTextReplacement(term)
}

/** Semantics texts of every node tagged [tag], in tree order. */
internal fun ComposeTestRule.semanticsTexts(tag: String): List<String> =
    onAllNodesWithTag(tag).fetchSemanticsNodes().map { node ->
        node.config[SemanticsProperties.Text].joinToString(" ") { it.text }
    }

/** The single star node's ([UiTags.SAVE_TOGGLE]) content description — filled vs. outlined state. */
internal fun ComposeTestRule.starDescription(): String =
    onAllNodesWithTag(UiTags.SAVE_TOGGLE).fetchSemanticsNodes().single()
        .config[SemanticsProperties.ContentDescription].single()

/** Advances the clock until the star reports [expected] — the flip is the save write's receipt. */
internal fun ComposeTestRule.awaitStarDescription(expected: String) {
    var slices = 0
    while (runCatching { starDescription() }.getOrNull() != expected && ++slices <= CLOCK_SLICES) {
        mainClock.advanceTimeBy(CLOCK_SLICE_MS)
        waitForIdle()
        Thread.sleep(WALL_SLICE_MS)
    }
    assertEquals(expected, starDescription())
}

/** Substring check on the single node's semantics text tagged [tag]. */
internal fun ComposeTestRule.assertTextContainsViaSemantics(tag: String, needle: String) {
    val text = semanticsTexts(tag).single()
    assertTrue("expected '$needle' in node '$tag' with text '$text'", text.contains(needle))
}

/** Advances the frozen clock in slices until a node tagged [tag] appears. */
internal fun ComposeTestRule.awaitTag(tag: String) = awaitWhile {
    onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
}

/** Advances the frozen clock in slices until [tag]'s semantics text contains [needle]. */
internal fun ComposeTestRule.awaitSemanticsText(tag: String, needle: String) = awaitWhile {
    semanticsTexts(tag).none { needle in it }
}

/** Runs [count] idle frames — enough rounds for a freed worker thread to race back. */
internal fun ComposeTestRule.pumpFrames(count: Int = CLOCK_SLICES) {
    repeat(count) {
        mainClock.advanceTimeBy(CLOCK_SLICE_MS)
        waitForIdle()
        Thread.sleep(WALL_SLICE_MS)
    }
}

private inline fun ComposeTestRule.awaitWhile(absent: () -> Boolean) {
    var slices = 0
    while (absent() && ++slices <= CLOCK_SLICES) {
        mainClock.advanceTimeBy(CLOCK_SLICE_MS)
        waitForIdle()
        Thread.sleep(WALL_SLICE_MS)
    }
    if (absent()) {
        throw AssertionError("state still absent after $CLOCK_SLICES slices — real-thread work never landed")
    }
}

/**
 * Delegating [WordRepository] fake that can park exactly one call off the main thread:
 * [blockNext] arms a one-shot gate; the next repository call signals [inFlight] and then
 * blocks on a [CompletableDeferred] until [releaseNext]. Simulates a slow query without
 * sleeping — the engine API is frozen, so the slowness wraps the fake from the outside.
 */
internal class GatingRepository(
    private val delegate: WordRepository = FakeWordRepository(),
) : WordRepository {

    val inFlight = CompletableDeferred<Unit>()
    private val released = CompletableDeferred<Unit>()
    private val armed = AtomicBoolean(false)

    fun blockNext() {
        armed.set(true)
    }

    fun releaseNext() {
        released.complete(Unit)
    }

    private fun tripGate() {
        if (armed.compareAndSet(true, false)) {
            runBlocking {
                inFlight.complete(Unit)
                released.await()
            }
        }
    }

    override fun exactForm(folded: String): List<FormHit> {
        tripGate()
        return delegate.exactForm(folded)
    }

    override fun prefixForms(prefix: String, limit: Int): List<FormHit> {
        tripGate()
        return delegate.prefixForms(prefix, limit)
    }

    override fun meta(): Map<String, String> = delegate.meta()
}
