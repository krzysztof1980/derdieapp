package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.kwitukiewicz.handygerman.artikel.core.FakeWordRepository
import pl.kwitukiewicz.handygerman.artikel.core.FormHit
import pl.kwitukiewicz.handygerman.artikel.core.LookupEngine
import pl.kwitukiewicz.handygerman.artikel.core.WordRepository

/**
 * Pins the search screen's contract: the semantics-tag vocabulary Tasks 7-8 test against,
 * the Single answer layout (accented articles, 40sp lemma, notes), the wash hex exposed
 * through the hidden `washColor` node (color asserted without screenshots), and the
 * debounce + generation-guard machinery (stale answers never settle on screen).
 *
 * Timing is deterministic: default-clock tests let `waitUntilExactlyOneExists` advance the
 * virtual clock through the 150 ms debounce; the frozen-clock stale test drops the
 * intermediate query before its lookup even starts, and [GatingRepository] parks a real
 * off-main lookup so a superseded result lands late and the version equality check —
 * the sole drop mechanism — has to be what drops it.
 * No Thread.sleep anywhere. Shared clock/semantics helpers live in `UiTestSupport.kt`.
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

    /**
     * The phone defect this pins: EditText was laid out at [0,0] — ~75% of the field
     * hid behind the status bar. The test environment reports a real status-bar strip
     * (Robolectric's `status_bar_height` dimen, 56 px at the default density), so the
     * statusBarsPadding on the screen's Column must keep the field strictly below it.
     */
    @Test
    fun queryFieldStartsBelowTheStatusBarStrip() {
        showSearch()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stripId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val stripDp = with(context.resources) {
            if (stripId > 0) getDimension(stripId) / displayMetrics.density else 0f
        }.dp
        assertTrue("status strip should be reported by the test environment", stripDp > 0.dp)
        val top = compose.onNodeWithTag(UiTags.QUERY_FIELD).getBoundsInRoot().top
        assertTrue("field top=$top must sit below the $stripDp status strip", top >= stripDp)
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
        compose.assertTextContainsViaSemantics(UiTags.NO_RESULT, "xyzq")
    }

    @Test
    fun noResultQuotesTheQueryItWasComputedForNotTheFieldText() {
        // Frozen clock: while the extended query sits in its debounce window, the no-result
        // row must keep quoting the search that produced it — never name ahead.
        compose.mainClock.autoAdvance = false
        showSearch()
        compose.mainClock.advanceTimeByFrame()
        val field = compose.onNode(hasSetTextAction())

        field.performTextReplacement("xyz")
        compose.awaitTag(UiTags.NO_RESULT)
        compose.assertTextContainsViaSemantics(UiTags.NO_RESULT, "\"xyz\"")

        field.performTextReplacement("xyzq") // one frame recomposes the field…
        compose.mainClock.advanceTimeByFrame()
        val quoted = compose.semanticsTexts(UiTags.NO_RESULT).single()
        assertTrue("expected the settled 'xyz' quote, got '$quoted'", "\"xyz\"" in quoted)
        assertFalse("'xyzq' must not be quoted before its lookup settles: $quoted", "xyzq" in quoted)

        // …the debounce then settles on the extension — slice-pumped like the stale tests
        compose.awaitSemanticsText(UiTags.NO_RESULT, "\"xyzq\"")
    }

    @Test
    fun dictionaryLoaderFailureLandsOnPlainErrorScreenNotCrash() {
        // A foreign schema version — the planned guard against shipping a db the app's
        // NounsSchema cannot read — must surface as text, not as a launch crash.
        compose.setContent {
            SearchScreen(engineLoader = { throw IllegalStateException("schema version 99 does not equal 1") })
        }
        compose.awaitTag(UiTags.DICTIONARY_ERROR)
        val error = compose.semanticsTexts(UiTags.DICTIONARY_ERROR).single()
        assertTrue("expected the dictionary mention in '$error'", "dictionary could not be loaded" in error)
        assertFalse("no stack trace on screen: $error", "IllegalStateException" in error)
        assertFalse("no internals on screen: $error", "schema version 99" in error)
        compose.onNodeWithTag(UiTags.WASH_COLOR).assertDoesNotExist() // no wash for a refused dictionary
        compose.onNodeWithTag(UiTags.WORD).assertDoesNotExist()
    }

    /**
     * The surname-footer withdrawal, pinned: `zeit` answers with the word's die entry,
     * and no footer/lane shows the demoted person names — asserted with the OLD literal
     * tags, since the [UiTags] constants are gone with the component, so a re-add under
     * the old vocabulary goes red here.
     */
    @Test
    fun wordHomonymAnswersTheWordAndShowsNoSurnameLane() {
        showSearch()
        type("zeit")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.MEANING_LINE), SETTLE_TIMEOUT_MS)
        compose.onNodeWithTag(UiTags.MEANING_LINE).assertTextEquals("(die) Zeit, Verlauf")
        compose.waitForIdle()
        compose.onAllNodesWithTag("surnameFooter").assertCountEquals(0)
        compose.onAllNodesWithTag("surnameEntry").assertCountEquals(0)
    }

    @Test
    fun declinedFormGetsEnglishCaseNote() {
        showSearch()
        type("booten")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.FORM_NOTE), SETTLE_TIMEOUT_MS)
        compose.assertTextContainsViaSemantics(UiTags.FORM_NOTE, "dative")
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
        compose.awaitTag(UiTags.NO_RESULT)
        compose.assertTextContainsViaSemantics(UiTags.NO_RESULT, "bootek")
        compose.onNodeWithTag(UiTags.WORD).assertDoesNotExist()
        compose.onNodeWithText("Boot").assertDoesNotExist()
    }

    @Test
    fun supersededLookupStaysDroppedEvenWhenItLandsLate() {
        // Manual foreground clock; [GatingRepository] parks one lookup on a real worker
        // thread, so the superseded result is still in flight when a newer query takes the
        // generation and only resumes afterwards.
        compose.mainClock.autoAdvance = false
        val repo = GatingRepository()
        compose.setContent { SearchScreen(engineLoader = { LookupEngine(repo) }) }
        compose.mainClock.advanceTimeByFrame()
        val field = compose.onNode(hasSetTextAction())

        field.performTextReplacement("zeit")
        compose.awaitTag(UiTags.MEANING_LINE) // the first result gets published

        repo.blockNext()
        field.performTextReplacement("boot")
        compose.mainClock.advanceTimeBy(200) // past the debounce: the lookup starts…
        runBlocking { repo.inFlight.await() } // …and is parked mid-flight off-main

        field.performTextReplacement("virus") // supersede it — the parked lookup survives,
        compose.awaitSemanticsText(UiTags.WORD, "Virus") // so the guard, not cancellation,
        compose.onNodeWithTag(UiTags.TOGGLE).assertDoesNotExist() // is what drops it

        repo.releaseNext() // the stale boot lookup finally completes — after its moment
        compose.pumpFrames()
        compose.onNodeWithTag(UiTags.TOGGLE).assertDoesNotExist() // guard dropped it
        compose.onNodeWithText("Boot").assertDoesNotExist()
        compose.onNodeWithTag(UiTags.WORD).assertTextEquals("Virus")
    }

    @Test
    fun aboutLivesInTheOverflowMenuNotOnTheBaseScreen() {
        showSearch()
        // Amendment #4: the footer link is gone — nothing labeled "About" until the
        // overflow opens, and the menu's single entry carries the exact label.
        compose.onNodeWithText("About").assertDoesNotExist()
        compose.onNodeWithTag(UiTags.MENU).performClick()
        compose.awaitTag(UiTags.MENU_ITEM)
        compose.onNodeWithTag(UiTags.MENU_ITEM).assertTextEquals("About")
    }

    private companion object {
        const val DAS_WASH_LIGHT_HEX = "#FFC3E8D3"
        const val SETTLE_TIMEOUT_MS = 30_000L
    }
}

/**
 * Delegating [WordRepository] fake that can park exactly one call off the main thread:
 * [blockNext] arms a one-shot gate; the next repository call signals [inFlight] and then
 * blocks on a [CompletableDeferred] until [releaseNext]. Simulates a slow query without
 * sleeping — the engine API is frozen, so the slowness wraps the fake from the outside.
 */
private class GatingRepository(
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
