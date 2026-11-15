package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.kwitukiewicz.derdiedas.core.FakeWordRepository
import pl.kwitukiewicz.derdiedas.core.Gender
import pl.kwitukiewicz.derdiedas.core.LookupEngine

/**
 * Task 8's surface: the segmented article row over homonymous entries (wash, meaning and
 * notes follow the selected candidate), the expandable person-name footer lane, and the
 * About dialog's attribution. Wash expectations are derived from [WashPalette] itself so
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
        compose.onNodeWithTag(UiTags.SURNAME_FOOTER).assertDoesNotExist()

        compose.onAllNodesWithTag(UiTags.TOGGLE)[1].performClick()
        compose.waitForIdle()
        // The wash follows the selection: now the der-wash, exactly WashPalette's value.
        compose.onNodeWithTag(UiTags.WASH_COLOR)
            .assertTextEquals(washHex(WashPalette.wash(Gender.MASKULINUM, dark = false)))
        // …and so does the detail: the der-Boot entry shows its own meaning line.
        compose.assertTextContainsViaSemantics(UiTags.MEANING_LINE, "Familienname")
    }

    @Test
    fun surnameFooterExpandsTheDemotedNamesOnTap() {
        showSearch()
        compose.typeQuery("zeit")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.SURNAME_FOOTER), SETTLE_TIMEOUT_MS)
        // Collapsed by default: the lane header only.
        compose.onAllNodesWithTag(UiTags.SURNAME_ENTRY).assertCountEquals(0)

        compose.onNodeWithTag(UiTags.SURNAME_FOOTER).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.SURNAME_ENTRY), SETTLE_TIMEOUT_MS)
        compose.assertTextContainsViaSemantics(UiTags.SURNAME_ENTRY, "die Zeit")
    }

    @Test
    fun lonePersonNameOpensNoSurnameLane() {
        showSearch()
        compose.typeQuery("müller")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.WORD), SETTLE_TIMEOUT_MS)
        // Zero person names in the footer: the lane must not render at all.
        compose.onNodeWithTag(UiTags.SURNAME_FOOTER).assertDoesNotExist()
    }

    @Test
    fun aboutDialogAttributesWiktionaryAndTheParser() {
        showSearch()
        compose.onNodeWithTag(UiTags.ABOUT).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.ABOUT_BODY), SETTLE_TIMEOUT_MS)
        compose.assertTextContainsViaSemantics(UiTags.ABOUT_BODY, "CC BY-SA 4.0")
        compose.assertTextContainsViaSemantics(UiTags.ABOUT_BODY, "GFDL")
        compose.assertTextContainsViaSemantics(UiTags.ABOUT_BODY, "de.wiktionary.org, dump test")
        compose.assertTextContainsViaSemantics(UiTags.ABOUT_BODY, "wiktiopeggynary")
    }

    private companion object {
        const val SETTLE_TIMEOUT_MS = 30_000L
    }
}
