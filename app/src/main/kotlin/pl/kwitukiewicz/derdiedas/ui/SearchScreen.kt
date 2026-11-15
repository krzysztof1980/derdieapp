package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.kwitukiewicz.derdiedas.R
import pl.kwitukiewicz.derdiedas.core.Homonyms
import pl.kwitukiewicz.derdiedas.core.LookupEngine
import pl.kwitukiewicz.derdiedas.core.LookupResult
import pl.kwitukiewicz.derdiedas.core.NoResult
import pl.kwitukiewicz.derdiedas.core.Single
import pl.kwitukiewicz.derdiedas.core.Suggestions
import pl.kwitukiewicz.derdiedas.core.washGenderKey

/**
 * The app's main screen: a query field over an answer area, the whole [Box] washed in the
 * answer's gender color. [engineLoader] (which copies and opens the bundled database) runs
 * once, on [Dispatchers.IO], behind a [produceState] — until it resolves the wash stays
 * blank. Every keystroke (re)starts a [DEBOUNCE_MS] grace period and takes the next
 * generation in a version counter; when the lookup finally returns from
 * [Dispatchers.Default], only the newest generation may publish — stale answers are
 * dropped instead of flashing. Results are applied back on the main thread.
 */
@Composable
fun SearchScreen(engineLoader: suspend () -> LookupEngine) {
    val dark = isSystemInDarkTheme()
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<LookupResult?>(null) }
    val generation = remember { mutableIntStateOf(0) }

    val engine by produceState<LookupEngine?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { engineLoader() }
    }

    LaunchedEffect(query, engine) {
        val revision = generation.intValue + 1
        generation.intValue = revision
        delay(DEBOUNCE_MS)
        val engine = engine ?: return@LaunchedEffect // blank wash until the dictionary is open
        val outcome = withContext(Dispatchers.Default) { engine.lookup(query) }
        if (generation.intValue == revision) result = outcome
    }

    val answer = result?.answer
    // Homonym selection lives here, not inside the subview: the wash must follow the
    // candidate on screen. A fresh answer object restarts the selection at zero.
    var homonymSelection by remember(answer) { mutableIntStateOf(0) }

    val visibleWash = when (answer) {
        is Single -> answer.candidate.entry.genders
        is Homonyms -> answer.candidates.getOrNull(homonymSelection)?.entry?.genders
        else -> null
    }?.let { WashPalette.wash(washGenderKey(it), dark) }
    val wash by animateColorAsState(
        targetValue = visibleWash ?: Color.Transparent,
        animationSpec = tween(150),
        label = "searchWash",
    )

    Box(
        modifier = Modifier
            .testTag(UiTags.WASH)
            .fillMaxSize()
            .background(wash),
    ) {
        Column {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.query_hint)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            when (answer) {
                null -> Unit // dictionary not open yet, or blank query: wash only
                is Single -> CandidateDetails(answer.candidate, dark)
                is Homonyms -> HomonymDetails(answer, homonymSelection, dark) { homonymSelection = it }
                is Suggestions -> SuggestionRows(answer.entries) { query = it }
                NoResult -> NoResultLine(query)
            }
            result?.footerNames?.let { names -> SurnameFooters(names) }
            visibleWash?.let {
                // Zero-size but in the semantics tree: tests assert the active wash color
                // as its hex instead of screenshotting pixels.
                Text(
                    text = washHex(it),
                    modifier = Modifier
                        .testTag(UiTags.WASH_COLOR)
                        .size(0.dp),
                    color = Color.Transparent,
                )
            }
        }
    }
}

private const val DEBOUNCE_MS = 150L
