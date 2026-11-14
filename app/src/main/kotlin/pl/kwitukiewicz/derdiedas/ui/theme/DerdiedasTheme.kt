package pl.kwitukiewicz.derdiedas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** App-wide theme placeholder; gender-wash tokens live in WashPalette. */
@Composable
fun DerdiedasTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
