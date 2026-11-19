package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.kwitukiewicz.handygerman.artikel.R
import pl.kwitukiewicz.handygerman.artikel.core.Candidate
import pl.kwitukiewicz.handygerman.artikel.core.Entry
import pl.kwitukiewicz.handygerman.artikel.core.articlesText
import pl.kwitukiewicz.handygerman.artikel.core.isPluralOnly
import pl.kwitukiewicz.handygerman.artikel.core.washGenderKey

/**
 * The semantics-tag vocabulary pinned by the plan: Tasks 7-8 tests match nodes by these
 * exact values, so renaming one is a contract change. `toggle` and `about`/`aboutBody`
 * render from the Task 8 subviews.
 */
internal object UiTags {
    const val WASH = "wash"
    const val WASH_COLOR = "washColor"
    const val QUERY_FIELD = "queryField" // chrome: the search box itself, not an answer node
    const val CLEAR_BUTTON = "clearButton" // chrome: in-field clear affordance (chrome decision 2026-11-15)
    const val ARTICLE = "article"
    const val WORD = "word"
    const val PLURAL_LINE = "pluralLine"
    const val PLURAL_NOTE = "pluralNote"
    const val MEANING_LINE = "meaningLine"
    const val FORM_NOTE = "formNote"
    const val SUGGEST_ROW = "suggestRow"
    const val NO_RESULT = "noResult"
    const val DICTIONARY_ERROR = "dictionaryError"
    const val TOGGLE = "toggle"
    const val SAVE_TOGGLE = "saveToggle" // star on the detail: membership of (lemma, article, sense)
    const val SAVED_ROW = "savedRow" // chrome: one saved word in the blank-query list (Task 4)
    const val SAVED_LIST = "savedList" // chrome: the blank-query saved list container (Task 4)
    const val SAVED_EMPTY = "savedEmpty" // chrome: hint line of the empty saved list (Task 4)
    const val MENU = "menu" // chrome: overflow button on the search row (2026-11-15)
    const val MENU_ITEM = "menuItem" // chrome: an entry in that overflow menu
    const val ABOUT_BODY = "aboutBody"
    const val QUIZ_HEADER = "quizHeader" // chrome: quiz run header replacing the search row (quiz)
    const val QUIZ_EXIT = "quizExit" // chrome: ✕ ending the run in place
    const val QUIZ_COUNTER = "quizCounter" // chrome: "n of m" run progress
    const val QUIZ_WORD = "quizWord" // quiz card: the drilled noun
    const val QUIZ_MEANING = "quizMeaning" // quiz card: disambiguating meaning line
    const val QUIZ_ANSWER = "quizAnswer" // quiz card: one of the four fixed article buttons
    const val QUIZ_END = "quizEnd" // chrome: end-of-run tally card
    const val QUIZ_EMPTY = "quizEmpty" // chrome: empty-queue hint card
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

/**
 * One candidate in full: accented articles, 40sp lemma beside its save star, plural,
 * meaning, case/plural-only notes. The star is membership of this candidate's
 * *(lemma, article, sense)* — [isSaved] renders it filled, [onToggleSaved] flips it.
 */
@Composable
fun CandidateDetails(
    candidate: Candidate,
    dark: Boolean,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    onToggleSaved: () -> Unit = {},
) {
    val entry = candidate.entry
    val washGender = washGenderKey(entry.genders)
    val ink = WashPalette.onWash(washGender, dark)
    val accent = WashPalette.accent(washGender, dark)
    Column(modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = articlesText(entry.genders),
            modifier = Modifier.testTag(UiTags.ARTICLE),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.lemma,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTags.WORD),
                fontSize = 40.sp,
                color = ink,
            )
            IconButton(
                onClick = onToggleSaved,
                modifier = Modifier.testTag(UiTags.SAVE_TOGGLE),
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(
                        if (isSaved) R.string.unsave_word else R.string.save_word,
                    ),
                    tint = if (isSaved) accent else WashPalette.onWashMuted(dark),
                )
            }
        }
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

/** Prefix completions, one row per distinct lemma; a tap replaces the query with that exact lemma. */
@Composable
fun SuggestionRows(
    entries: List<Entry>,
    dark: Boolean,
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
                    color = WashPalette.accent(washGenderKey(entry.genders), dark),
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
