package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.kwitukiewicz.handygerman.artikel.R
import pl.kwitukiewicz.handygerman.artikel.core.NounsSchema

/**
 * Attribution dialog (opened from the search row's overflow menu since Amendment #4):
 * credits the German Wiktionary under CC BY-SA 4.0 + GFDL, names the exact dump via the
 * repository's `built_from` meta stamp, and credits the wiktiopeggynary parser that
 * produced the data. [meta] arrives from the screen's off-main provider, so composing
 * this stays a pure read. [learnedCount] — read by the screen when the dialog opens —
 * adds the one learning line, shown iff known (null while the store is still opening,
 * or when this build carries no learning store).
 */
@Composable
fun AboutDialog(meta: Map<String, String>, learnedCount: Int?, onDismiss: () -> Unit) {
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
            Column {
                Text(
                    text = body,
                    modifier = Modifier.testTag(UiTags.ABOUT_BODY),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // The learned count is the progress the user earned — a plain extra
                // line at the foot of the fine print hid it (2026-11-19 usability
                // note): it gets its own ruled block below the attribution, set in
                // titleMedium, led by the celebration emoji.
                if (learnedCount != null) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_learned_count, learnedCount),
                        modifier = Modifier.testTag(UiTags.ABOUT_LEARNED),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}
