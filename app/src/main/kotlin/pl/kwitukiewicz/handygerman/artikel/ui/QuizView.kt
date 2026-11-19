package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.kwitukiewicz.handygerman.artikel.R
import pl.kwitukiewicz.handygerman.artikel.core.Gender
import pl.kwitukiewicz.handygerman.artikel.core.QUIZ_ANSWERS
import pl.kwitukiewicz.handygerman.artikel.core.QuizItem

/**
 * The quiz views: the header that replaces the search row mid-run, the drilled
 * card, and the empty/end cards that replace the answer area. All colors come
 * from [WashPalette]; the four article buttons are always `der / die / das / –`
 * in [QUIZ_ANSWERS] order — the button set must never hint the answer.
 */

/** What the card shows: live input, or the brief feedback frame after a tap. */
enum class QuizFeedback { NONE, CORRECT, MISSED }

/** The run header replacing the search row: ✕ exit, `n of m` counter, and the overflow slot. */
@Composable
fun QuizHeader(position: Int, total: Int, onExit: () -> Unit, overflow: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTags.QUIZ_HEADER)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onExit,
            modifier = Modifier.testTag(UiTags.QUIZ_EXIT),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.quiz_exit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.quiz_counter, position, total),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(UiTags.QUIZ_COUNTER),
        )
        Spacer(Modifier.weight(1f))
        overflow()
    }
}

/**
 * One drilled card: the noun (detail-size type) over its disambiguating meaning,
 * over the four fixed article buttons. While [feedback] is [QuizFeedback.NONE]
 * the card is plain live input — no color may leak the article. A CORRECT tap
 * washes the card in the true article's wash; a MISSED paints the tapped button
 * in [WashPalette.error] and reveals every correct one in its article's accent.
 */
@Composable
fun QuizCard(
    item: QuizItem,
    dark: Boolean,
    tapped: String?,
    feedback: QuizFeedback,
    onAnswer: (String) -> Unit,
) {
    val answersGender = QUIZ_ANSWERS.firstOrNull { it in item.answers }?.let(::genderFor)
    val washTarget =
        if (feedback == QuizFeedback.CORRECT && answersGender != null) {
            WashPalette.wash(answersGender, dark)
        } else {
            Color.Transparent
        }
    val wash by animateColorAsState(washTarget, tween(150), label = "quizCardWash")
    val ink = WashPalette.onWash(answersGender ?: Gender.UTRUM, dark)
    val muted = WashPalette.onWashMuted(dark)
    Column(
        Modifier
            .fillMaxWidth()
            .background(wash)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text = item.word.lemma,
            modifier = Modifier.testTag(UiTags.QUIZ_WORD),
            fontSize = 40.sp,
            color = ink,
        )
        item.meaning?.let { line ->
            Text(
                text = line,
                modifier = Modifier.testTag(UiTags.QUIZ_MEANING),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QUIZ_ANSWERS.forEachIndexed { index, label ->
                val correct = label in item.answers
                val wrongTap = feedback == QuizFeedback.MISSED && label == tapped
                val accent = if (feedback != QuizFeedback.NONE && correct) {
                    WashPalette.accent(genderFor(label), dark)
                } else {
                    null
                }
                val marker = when {
                    wrongTap -> WashPalette.error()
                    else -> accent
                }
                OutlinedButton(
                    onClick = { onAnswer(label) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(UiTags.QUIZ_ANSWER),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, marker ?: WashPalette.fieldOutline()),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = marker ?: ink,
                        containerColor = Color.Transparent,
                    ),
                ) {
                    Text(
                        text = stringResource(quizAnswerLabelRes(index)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = marker ?: ink,
                    )
                }
            }
        }
    }
}

/** The empty-queue card the run opens on when nothing is saved. */
@Composable
fun QuizEmpty() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.quiz_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(UiTags.QUIZ_EMPTY),
        )
    }
}

/** The end-of-run card: the tally, then back to the list. */
@Composable
fun QuizEnd(graduated: Int, missed: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.quiz_end, graduated, missed),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(UiTags.QUIZ_END),
        )
        TextButton(onClick = onDone) {
            Text(stringResource(R.string.quiz_end_back))
        }
    }
}

/** The article a button label stands for — the same four the core drills. */
private fun genderFor(label: String): Gender = when (label) {
    "der" -> Gender.MASKULINUM
    "die" -> Gender.FEMININUM
    "das" -> Gender.NEUTRUM
    else -> Gender.UTRUM // "–"
}

/** The string resource of the button label at [index] in [QUIZ_ANSWERS] order. */
private fun quizAnswerLabelRes(index: Int): Int = when (index) {
    0 -> R.string.quiz_answer_der
    1 -> R.string.quiz_answer_die
    2 -> R.string.quiz_answer_das
    else -> R.string.quiz_answer_dash
}
