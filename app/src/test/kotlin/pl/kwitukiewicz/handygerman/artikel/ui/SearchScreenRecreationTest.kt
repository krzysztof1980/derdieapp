package pl.kwitukiewicz.handygerman.artikel.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Assert.assertEquals
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
import pl.kwitukiewicz.handygerman.artikel.core.SavedWord
import pl.kwitukiewicz.handygerman.artikel.core.SavedWordsRepository
import pl.kwitukiewicz.handygerman.artikel.core.WordIdentity
import pl.kwitukiewicz.handygerman.artikel.core.WordRepository
import kotlinx.coroutines.runBlocking

/**
 * Pins the `rememberSaveable` query: type `virus`, recreate the Activity (the rotation and
 * process-death pathway), and assert the text field still carries the query while the
 * answer nodes settle on Virus again — restored query plus a fresh engine lookup through
 * the fake loader, not a resurrected [androidx.compose.runtime.remember]ed result.
 *
 * [SearchScreenRecreationTestHost] is a test-source Activity declared in
 * `app/src/debug/AndroidManifest.xml` (debug variant only) so Robolectric can launch it;
 * the unit-test classpath carries the class, so no shipped code references it.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class SearchScreenRecreationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<SearchScreenRecreationTestHost>

    /** The host is launched per test (not @Before): tests configure the host's
     * provider companion first, and `launch` is what reads it. */
    private fun launchHost() {
        scenario = ActivityScenario.launch(SearchScreenRecreationTestHost::class.java)
    }

    @After
    fun closeHost() {
        scenario.close()
        SearchScreenRecreationTestHost.SEARCH_REPOSITORY_PROVIDER = { FakeWordRepository() }
        SearchScreenRecreationTestHost.SEARCH_SAVED_PROVIDER = { FakeSavedWordsRepository() }
        SearchScreenRecreationTestHost.SEARCH_LEARNING_PROVIDER = { FakeLearningRepository() }
    }

    private fun fieldText(): String =
        compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun typedQuerySurvivesActivityRecreation() {
        launchHost()
        compose.typeQuery("virus")
        compose.awaitSemanticsText(UiTags.WORD, "Virus")
        assertEquals("virus", fieldText())

        scenario.recreate()

        assertEquals("query must come back via rememberSaveable", "virus", fieldText())
        compose.awaitSemanticsText(UiTags.WORD, "Virus")
    }

    /**
     * The rotation defect this pins: the homonym choice was keyed on the answer object
     * instance, which recreation recomputes — `boot` + `der` tap came back as `das`.
     * The selection is now `rememberSaveable(query)`: after recreation the wash must
     * still be the der-wash hex (the wash follows the selection — this is the selection
     * assertion), the toggle must still list both articles, and the der candidate's
     * detail must still be on screen.
     */
    @Test
    fun homonymChoiceSurvivesActivityRecreation() {
        launchHost()
        compose.typeQuery("boot")
        compose.awaitTag(UiTags.TOGGLE)
        assertEquals(listOf("das", "der"), compose.semanticsTexts(UiTags.TOGGLE))
        compose.onAllNodesWithTag(UiTags.TOGGLE)[1].performClick()
        compose.onNodeWithTag(UiTags.WASH_COLOR)
            .assertTextEquals(washHex(WashPalette.wash(Gender.MASKULINUM, dark = false)))

        scenario.recreate()

        compose.awaitTag(UiTags.TOGGLE)
        compose.onNodeWithTag(UiTags.WASH_COLOR)
            .assertTextEquals(washHex(WashPalette.wash(Gender.MASKULINUM, dark = false)))
        compose.assertTextContainsViaSemantics(UiTags.MEANING_LINE, "Familienname")
    }

    /**
     * Recreation between the row tap and the answer's first publish: the pending
     * (article, sense) target must come back through `rememberSaveable` and still
     * select the saved variant — without it, the restored query would land on the
     * first `die Bank` while the save points at the second.
     */
    @Test
    fun pendingOpenSurvivesActivityRecreation() {
        val store = FakeSavedWordsRepository()
        store.save(SavedWord("Bank", WordIdentity("bank", "FEMININUM", 2), "Geldinstitut", 100L))
        val gating = GatingRepository(
            FakeWordRepository(
                entries = listOf(
                    Entry(1, "Bank", Kind.WORD, listOf(Gender.FEMININUM), "Bänke", listOf("Sitzgelegenheit")),
                    Entry(2, "Bank", Kind.WORD, listOf(Gender.FEMININUM), "Banken", listOf("Geldinstitut")),
                ),
                rows = listOf(
                    FakeWordRepository.Row("bank", 1, "Singular", "Nominativ", isLemma = true),
                    FakeWordRepository.Row("bank", 2, "Singular", "Nominativ", isLemma = true),
                ),
            ),
        )
        SearchScreenRecreationTestHost.SEARCH_REPOSITORY_PROVIDER = { gating }
        SearchScreenRecreationTestHost.SEARCH_SAVED_PROVIDER = { store }

        launchHost()
        compose.mainClock.autoAdvance = false
        compose.awaitTag(UiTags.SAVED_ROW)
        compose.onNodeWithTag(UiTags.SAVED_ROW).performClick() // tap: query set, target pending
        gating.blockNext() // the query's lookup is next off the line…
        compose.mainClock.advanceTimeBy(200) // …debounce fires and it parks at the gate
        runBlocking { gating.inFlight.await() }

        scenario.recreate() // …recreation lands while the answer never published

        gating.releaseNext() // after recreation the query's new lookup is ungated
        compose.awaitSemanticsText(UiTags.WORD, "Bank")
        compose.assertTextContainsViaSemantics(UiTags.MEANING_LINE, "Geldinstitut")
    }
}

/** Composition host for [SearchScreenRecreationTest]: the real screen, the cheap fake. */
class SearchScreenRecreationTestHost : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SearchScreen(
                engineLoader = { LookupEngine(SEARCH_REPOSITORY_PROVIDER()) },
                savedLoader = { SEARCH_SAVED_PROVIDER() },
                learningLoader = { SEARCH_LEARNING_PROVIDER() },
            )
        }
    }

    companion object {
        // Survive scenario.recreate() like real singletons would: tests point these at
        // their own corpus/store before launch.
        var SEARCH_REPOSITORY_PROVIDER: () -> WordRepository = { FakeWordRepository() }
        var SEARCH_SAVED_PROVIDER: () -> SavedWordsRepository = { FakeSavedWordsRepository() }
        var SEARCH_LEARNING_PROVIDER: () -> LearningRepository = { FakeLearningRepository() }
    }
}
