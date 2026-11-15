package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.kwitukiewicz.derdiedas.R
import pl.kwitukiewicz.derdiedas.core.Entry
import pl.kwitukiewicz.derdiedas.core.articlesText

/**
 * Person-name homonyms the engine demoted from the answer to a footnote lane: a muted
 * [UiTags.SURNAME_FOOTER] header that expands the names on tap, each as a muted
 * [UiTags.SURNAME_ENTRY] row of article plus lemma (`die Zeit`). An empty [names] list
 * renders nothing — the lane cannot structurally appear without demoted person names.
 */
@Composable
fun SurnameFooter(names: List<Entry>, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    var expanded by remember(names) { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Column(modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.surname_lane),
            modifier = Modifier
                .testTag(UiTags.SURNAME_FOOTER)
                .clickable { expanded = !expanded },
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        if (expanded) {
            names.forEach { entry ->
                Text(
                    text = "${articlesText(entry.genders)} ${entry.lemma}",
                    modifier = Modifier
                        .testTag(UiTags.SURNAME_ENTRY)
                        .padding(start = 12.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }
    }
}
