package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.kwitukiewicz.derdiedas.R
import pl.kwitukiewicz.derdiedas.core.Candidate
import pl.kwitukiewicz.derdiedas.core.Entry
import pl.kwitukiewicz.derdiedas.core.Homonyms
import pl.kwitukiewicz.derdiedas.core.articlesText
import pl.kwitukiewicz.derdiedas.core.isPluralOnly
import pl.kwitukiewicz.derdiedas.core.washGenderKey

/**
 * The semantics-tag vocabulary pinned by the plan: Tasks 7-8 tests match nodes by these
 * exact values, so renaming one is a contract change. `about` belongs to Task 8's state
 * and has no rendering site yet; the constant keeps the vocabulary in one place.
 */
internal object UiTags {
    const val WASH = "wash"
    const val WASH_COLOR = "washColor"
    const val ARTICLE = "article"
    const val WORD = "word"
    const val PLURAL_LINE = "pluralLine"
    const val PLURAL_NOTE = "pluralNote"
    const val MEANING_LINE = "meaningLine"
    const val FORM_NOTE = "formNote"
    const val SUGGEST_ROW = "suggestRow"
    const val NO_RESULT = "noResult"
    const val TOGGLE = "toggle"
    const val SURNAME_FOOTER = "surnameFooter"
    const val ABOUT = "about"
}

/** `#AARRGGBB` uppercase of [color]; what the hidden [UiTags.WASH_COLOR] node exposes. */
internal fun washHex(color: Color): String = "#%08X".format(color.toArgb())

/**
 * Lowercase English case/number note for a declined lookup — `Dativ` + `Plural` become
 * "dative plural" — or `null` when the typed form *is* the lemma (the headword row, per
 * its `isLemma` flag) or carries no annotation at all.
 */
internal fun formNoteText(candidate: Candidate): String? {
    val form = candidate.typedForm
    if (form.isLemma) return null
    return listOfNotNull(form.kasus?.let(::germanCase), form.numerus?.let(::germanNumber))
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")
}

private fun germanCase(kasus: String): String = when (kasus) {
    "Nominativ" -> "nominative"
    "Genitiv" -> "genitive"
    "Dativ" -> "dative"
    "Akkusativ" -> "accusative"
    else -> kasus.lowercase() // unknown annotations surface verbatim, never invented
}

private fun germanNumber(numerus: String): String = when (numerus) {
    "Singular" -> "singular"
    "Plural" -> "plural"
    else -> numerus.lowercase()
}

/** One candidate in full: accented articles, 40sp lemma, plural, meaning, case/plural-only notes. */
@Composable
fun CandidateDetails(candidate: Candidate, dark: Boolean, modifier: Modifier = Modifier) {
    val entry = candidate.entry
    val washGender = washGenderKey(entry.genders)
    val ink = WashPalette.onWash(washGender, dark)
    val accent = WashPalette.accent(washGender)
    Column(modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = articlesText(entry.genders),
            modifier = Modifier.testTag(UiTags.ARTICLE),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            text = entry.lemma,
            modifier = Modifier.testTag(UiTags.WORD),
            fontSize = 40.sp,
            color = ink,
        )
        entry.pluralForms?.let { plural ->
            Text(
                text = stringResource(R.string.plural_forms, plural),
                modifier = Modifier.testTag(UiTags.PLURAL_LINE),
                style = MaterialTheme.typography.bodyLarge,
                color = ink,
            )
        }
        entry.meaningLines.firstOrNull()?.let { meaning ->
            Text(
                text = meaning,
                modifier = Modifier.testTag(UiTags.MEANING_LINE),
                style = MaterialTheme.typography.bodyLarge,
                color = ink,
            )
        }
        formNoteText(candidate)?.let { note ->
            Text(
                text = note,
                modifier = Modifier.testTag(UiTags.FORM_NOTE),
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
            )
        }
        if (isPluralOnly(entry.genders)) {
            Text(
                text = stringResource(R.string.plural_only),
                modifier = Modifier.testTag(UiTags.PLURAL_NOTE),
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
            )
        }
    }
}

/**
 * Several entries share the typed form: [selectedIndex]'s candidate renders in full and
 * the [UiTags.TOGGLE] button cycles to the next one. Selection is screen state passed in,
 * so the wash can follow whichever candidate is shown.
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
    val next = candidates[(selectedIndex + 1) % candidates.size]
    Column(modifier) {
        CandidateDetails(candidates[selectedIndex], dark)
        TextButton(
            onClick = { onSelect((selectedIndex + 1) % candidates.size) },
            modifier = Modifier
                .testTag(UiTags.TOGGLE)
                .padding(start = 12.dp),
        ) {
            Text(stringResource(R.string.homonym_toggle, articlesText(next.entry.genders), next.entry.lemma))
        }
    }
}

/** Prefix completions, one row per distinct lemma; a tap replaces the query with that exact lemma. */
@Composable
fun SuggestionRows(
    entries: List<Entry>,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    Column(modifier) {
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(entry.lemma) }
                    .testTag(UiTags.SUGGEST_ROW)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = entry.lemma,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = articlesText(entry.genders),
                    style = MaterialTheme.typography.titleMedium,
                    color = WashPalette.accent(washGenderKey(entry.genders)),
                )
            }
        }
    }
}

/** Dead end: the typed text quoted back, so the user sees what was searched. */
@Composable
fun NoResultLine(query: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.no_result, query),
        modifier = modifier
            .testTag(UiTags.NO_RESULT)
            .padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** Person-name homonyms the engine demoted from the answer to a footnote. */
@Composable
fun SurnameFooters(names: List<Entry>, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        names.forEach { entry ->
            Text(
                text = stringResource(R.string.surname_footer, entry.lemma),
                modifier = Modifier.testTag(UiTags.SURNAME_FOOTER),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
