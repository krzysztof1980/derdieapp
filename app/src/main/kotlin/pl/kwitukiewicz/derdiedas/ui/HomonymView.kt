package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.kwitukiewicz.derdiedas.core.Homonyms
import pl.kwitukiewicz.derdiedas.core.articlesText
import pl.kwitukiewicz.derdiedas.core.washGenderKey

/**
 * Several entries share the typed form: a segmented article row — one [UiTags.TOGGLE]
 * segment per candidate, in page order — sits above [selectedIndex]'s fully rendered
 * candidate (device fix 2026-11-15: swapping never moves the word), and a
 * segment tap reports that candidate's index through [onSelect]. Selection is screen state
 * passed in, so the wash can follow the candidate on screen; the detail (meaning, notes)
 * is [selectedIndex]'s by construction.
 */
@Composable
fun HomonymDetails(
    homonyms: Homonyms,
    selectedIndex: Int,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val candidates = homonyms.candidates
    Column(modifier) {
        // Spec UX-5 / 2026-11-15 device fix: the segmented row is ABOVE the word block —
        // meaning length differs per candidate, so a row below would jump on every swap.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            candidates.forEachIndexed { index, candidate ->
                val accent = WashPalette.accent(washGenderKey(candidate.entry.genders), dark)
                val selected = index == selectedIndex
                Text(
                    text = articlesText(candidate.entry.genders),
                    modifier = Modifier
                        .testTag(UiTags.TOGGLE)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) accent else WashPalette.onWashMuted(dark),
                )
            }
        }
        CandidateDetails(candidates[selectedIndex], dark)
    }
}
