package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilExactlyOneExists
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.kwitukiewicz.derdiedas.core.FakeWordRepository
import pl.kwitukiewicz.derdiedas.core.Gender
import pl.kwitukiewicz.derdiedas.core.LookupEngine
import pl.kwitukiewicz.derdiedas.ui.theme.DerdiedasTheme

/**
 * Single-appearance invariant on a system-dark phone: with Robolectric forced to the
 * night qualifier, the screen must render exactly what a day-mode phone shows —
 * the LIGHT wash hex for `virus` (identical to SearchScreenTest's day assertion), an
 * outlined query field, and a still-light Material 3 scheme (the [SchemeProbe]; the
 * original phone bug was dark washes under a silently light scheme). Flipping the
 * appearance signal — `WashPalette.appLooksDark` or a re-added isSystemInDarkTheme()
 * pass-through — turns this test red, which is the regression gate this gap once lacked.
 * The About dialog (pure M3 chrome) must also open under night qualifiers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "night")
@OptIn(ExperimentalTestApi::class)
class NightModeInvarianceTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showSearch() {
        compose.setContent {
            DerdiedasTheme {
                SearchScreen(engineLoader = { LookupEngine(FakeWordRepository()) })
                SchemeProbe()
            }
        }
    }

    @Test
    fun nightQualifiersKeepTheLightAppearancePins() {
        showSearch()
        compose.onNodeWithTag(UiTags.QUERY_FIELD).assertExists()
        compose.typeQuery("virus")
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.WORD), SETTLE_TIMEOUT_MS)
        // The LIGHT das-wash — the system's night mode must not have moved the wash.
        compose.onNodeWithTag(UiTags.WASH_COLOR)
            .assertTextEquals(washHex(WashPalette.wash(Gender.NEUTRUM, dark = false)))
        // …and the M3 chrome stayed on the light baseline too.
        compose.onNodeWithTag(SCHEME_PROBE)
            .assertTextEquals(washHex(lightColorScheme().primary))
    }

    @Test
    fun aboutDialogOpensUnderNightQualifiers() {
        showSearch()
        // Amendment #4: dialog now reached via the overflow menu.
        compose.onNodeWithTag(UiTags.MENU).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.MENU_ITEM), SETTLE_TIMEOUT_MS)
        compose.onNodeWithTag(UiTags.MENU_ITEM).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(UiTags.ABOUT_BODY), SETTLE_TIMEOUT_MS)
    }

    /**
     * Semantics-visible witness of the ambient Material 3 scheme: its primary hex.
     * compared against [lightColorScheme] in the assertion — baseline-relative, so a
     * material3 version bump cannot make it flaky — and the dark baseline differs in
     * primary, so a theme flip trips it.
     */
    @Composable
    private fun SchemeProbe() {
        Text(
            text = washHex(MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag(SCHEME_PROBE),
        )
    }

    private companion object {
        const val SCHEME_PROBE = "schemeProbe"
        const val SETTLE_TIMEOUT_MS = 30_000L
    }
}
