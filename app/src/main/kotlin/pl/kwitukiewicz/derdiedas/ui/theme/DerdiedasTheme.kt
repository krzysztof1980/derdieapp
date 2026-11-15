package pl.kwitukiewicz.derdiedas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import pl.kwitukiewicz.derdiedas.ui.WashPalette

/**
 * App-wide theme: the Material 3 color scheme is selected by [WashPalette.appLooksDark]
 * — the app's single appearance decision — and nothing else. The app is single-appearance
 * by design (user decision 2026-11-15): system dark mode MUST NOT change the app's look,
 * so `isSystemInDarkTheme` is deliberately not consulted anywhere in the chrome. That
 * holds by construction, not by luck: every color the composition paints is explicit,
 * which also leaves Android's force-dark nothing to reinterpret. Washes and Material 3
 * chrome (query-field placeholder/cursor, dialog surfaces) switch on the same pin, so
 * they can never disagree — the phone-found bug this replaces had the washes go dark
 * while the no-arg [MaterialTheme] stayed light.
 */
@Composable
fun DerdiedasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (WashPalette.appLooksDark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
