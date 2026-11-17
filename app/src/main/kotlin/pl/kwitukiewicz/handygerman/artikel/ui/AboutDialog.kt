package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import pl.kwitukiewicz.handygerman.artikel.R
import pl.kwitukiewicz.handygerman.artikel.core.NounsSchema

/**
 * Attribution dialog (opened from the search row's overflow menu since Amendment #4):
 * credits the German Wiktionary under CC BY-SA 4.0 + GFDL, names the exact dump via the
 * repository's `built_from` meta stamp, and credits the wiktiopeggynary parser that
 * produced the data. [meta] arrives from the screen's off-main provider, so composing
 * this stays a pure read.
 */
@Composable
fun AboutDialog(meta: Map<String, String>, onDismiss: () -> Unit) {
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
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title, stringResource(R.string.app_long_name))) },
        text = {
            Text(
                text = body,
                modifier = Modifier.testTag(UiTags.ABOUT_BODY),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}
