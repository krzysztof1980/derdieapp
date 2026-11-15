package pl.kwitukiewicz.derdiedas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.kwitukiewicz.derdiedas.core.FakeWordRepository
import pl.kwitukiewicz.derdiedas.core.LookupEngine

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

    @Before
    fun launchHost() {
        scenario = ActivityScenario.launch(SearchScreenRecreationTestHost::class.java)
    }

    @After
    fun closeHost() {
        scenario.close()
    }

    private fun fieldText(): String =
        compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun typedQuerySurvivesActivityRecreation() {
        compose.typeQuery("virus")
        compose.awaitSemanticsText(UiTags.WORD, "Virus")
        assertEquals("virus", fieldText())

        scenario.recreate()

        assertEquals("query must come back via rememberSaveable", "virus", fieldText())
        compose.awaitSemanticsText(UiTags.WORD, "Virus")
    }
}

/** Composition host for [SearchScreenRecreationTest]: the real screen, the cheap fake. */
class SearchScreenRecreationTestHost : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SearchScreen(engineLoader = { LookupEngine(FakeWordRepository()) })
        }
    }
}
