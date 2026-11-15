package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilExactlyOneExists
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.kwitukiewicz.derdiedas.core.FakeWordRepository
import pl.kwitukiewicz.derdiedas.core.FormHit
import pl.kwitukiewicz.derdiedas.core.LookupEngine
import pl.kwitukiewicz.derdiedas.core.WordRepository

/**
 * Pins the search screen's contract: the semantics-tag vocabulary Tasks 7-8 test against,
 * the Single answer layout (accented articles, 40sp lemma, notes), the wash hex exposed
 * through the hidden `washColor` node (color asserted without screenshots), and the
 * debounce + generation-guard machinery (stale answers never settle on screen).
 *
 * Timing is deterministic: default-clock tests let `waitUntilExactlyOneExists` advance the
 * virtual clock through the 150 ms debounce; the frozen-clock stale test drops the
 * intermediate query before its lookup even starts, and [GatingRepository] parks a real
 * off-main lookup to exercise the drop of a result that lands after being superseded.
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

        field.performTextReplacement("virus") // supersede it
        compose.awaitSemanticsText(UiTags.WORD, "Virus") // while boot is parked, virus settles
        compose.onNodeWithTag(UiTags.TOGGLE).assertDoesNotExist() // boot's answer never showed

        repo.releaseNext() // the stale boot lookup finally completes — after its moment
        compose.pumpFrames()
        compose.onNodeWithTag(UiTags.TOGGLE).assertDoesNotExist()
        compose.onNodeWithText("Boot").assertDoesNotExist()
        compose.onNodeWithTag(UiTags.WORD).assertTextEquals("Virus")
    }

    private companion object {
        const val DAS_WASH_LIGHT_HEX = "#FFE9F5EE"
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
