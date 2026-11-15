package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import pl.kwitukiewicz.derdiedas.R
import pl.kwitukiewicz.derdiedas.core.NounsSchema

/**
 * Attribution entry point: a muted [UiTags.ABOUT] link opening a dialog that credits the
 * German Wiktionary under CC BY-SA 4.0 + GFDL, names the exact dump via the repository's
 * `built_from` meta stamp, and credits the wiktiopeggynary parser that produced the data.
 * [meta] arrives from the screen's off-main provider, so composing this stays a pure read.
 */
@Composable
fun AboutLink(meta: Map<String, String>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    TextButton(
        onClick = { open = true },
        modifier = modifier.testTag(UiTags.ABOUT),
    ) {
        Text(
            text = stringResource(R.string.about_link),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
    if (open) {
        val body = buildString {
            appendLine(stringResource(R.string.about_attribution))
            appendLine(
                stringResource(
                    R.string.about_source,
                    meta[NounsSchema.META_BUILT_FROM] ?: "unknown",
                ),
            )
            append(stringResource(R.string.about_parser))
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Text(
                    text = body,
                    modifier = Modifier.testTag(UiTags.ABOUT_BODY),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.about_close))
                }
            },
        )
    }
}
