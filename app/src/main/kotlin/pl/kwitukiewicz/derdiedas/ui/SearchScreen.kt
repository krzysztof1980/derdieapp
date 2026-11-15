package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * blank. A loader that refuses the dictionary (foreign schema, missing asset, fold drift)
 * lands on a plain [DictionaryLoad.Failed] screen instead of crashing the launch.
 * Every keystroke (re)starts a [DEBOUNCE_MS] grace period and takes the next
 * generation in a version counter. Typing cancels the keyed effect, so only settled
 * queries ever dispatch, and each dispatch runs the lookup on the composition scope —
 * supersession never cancels it: a result that lands late is dropped by the version
 * counter alone, [Dispatchers.Default] to main, the newest generation the only one that
 * may publish. That equality check is the sole staleness mechanism.
 *
 * Published results are paired with the query they were computed for ([resultQuery],
 * written in the same main-confined publish step), so the NoResult row quotes the search,
 * never the field text it may no longer match.
 *
 * [metaProvider] supplies the exporter's meta stamps for the attribution dialog, read on
 * [Dispatchers.IO] the same way — never on the composition path. Homonym selection is
 * hoisted here (keyed on the answer object) so the wash follows the segmented article
 * row's choice, and the person-name lane plus the About link sit under the answer.
 */
@Composable
fun SearchScreen(
    engineLoader: suspend () -> LookupEngine,
    metaProvider: suspend () -> Map<String, String> = { emptyMap() },
) {
    // Single-appearance app: the pin, never isSystemInDarkTheme() — the same decision
    // DerdiedasTheme takes for the Material 3 scheme, so washes and chrome agree in
    // every system mode.
    val dark = WashPalette.appLooksDark
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<LookupResult?>(null) }
    // The query `result` was computed for — quoted by NoResult instead of the live field,
    // so the line never names a search whose answer is not on screen yet.
    var resultQuery by remember { mutableStateOf("") }
    val generation = remember { mutableIntStateOf(0) }
    // Lookups launch on the composition scope, NOT on the keyed effect below: replacing
    // the query cancels the effect but may not cancel the dispatched lookup — cooperative
    // cancellation would drop it for the wrong reason and hide the version counter.
    val lookupScope = rememberCoroutineScope()

    val dictionary by produceState<DictionaryLoad>(initialValue = DictionaryLoad.Loading) {
        value = withContext(Dispatchers.IO) {
            try {
                DictionaryLoad.Opened(engineLoader())
            } catch (refused: Exception) {
                // Any dictionary rejection — foreign schema version, missing asset, fold
                // drift — is a plain failure here; the stack trace belongs in logcat only.
                Log.w("SearchScreen", "dictionary load failed", refused)
                DictionaryLoad.Failed
            }
        }
    }
    val engine = (dictionary as? DictionaryLoad.Opened)?.engine

    val meta by produceState(initialValue = emptyMap(), metaProvider) {
        // The stamps ride the same dictionary the engine loader just opened (or refused);
        // without it the About dialog simply shows nothing.
        value = try {
            withContext(Dispatchers.IO) { metaProvider() }
        } catch (refused: Exception) {
            emptyMap()
        }
    }

    LaunchedEffect(query, engine) {
        val revision = generation.intValue + 1
        generation.intValue = revision
        delay(DEBOUNCE_MS)
        val engine = engine ?: return@LaunchedEffect // blank wash until the dictionary is open
        // Latest-wins at dispatch: this effect is the gate, so a keystroke burst never
        // queues more than the one lookup for the settled query. Once dispatched the
        // lookup outlives supersession; the version equality below is what drops it.
        lookupScope.launch(Dispatchers.Default) {
            val searched = query
            val outcome = engine.lookup(searched)
            withContext(Dispatchers.Main) {
                if (generation.intValue == revision) {
                    result = outcome
                    resultQuery = searched
                }
            }
        }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTags.QUERY_FIELD),
                placeholder = { Text(stringResource(R.string.query_hint)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    // Filled variant on the palette's chrome container: opaque white on
                    // both states, so the field reads as a raised box on every pastel
                    // wash. Placeholder/cursor/label inherit the pinned light scheme —
                    // visible on the container in every system mode. Resting state is
                    // container-only (no baseline); the focused indicator uses the
                    // scheme's own ink.
                    focusedContainerColor = WashPalette.fieldContainer(),
                    unfocusedContainerColor = WashPalette.fieldContainer(),
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
            when (answer) {
                // no answer: either the dictionary is still opening / the query is blank,
                // or it refused to open — then show the plain error screen, no stack trace
                null -> if (dictionary is DictionaryLoad.Failed) DictionaryLoadError()
                is Single -> CandidateDetails(answer.candidate, dark)
                is Homonyms -> HomonymDetails(answer, homonymSelection, dark) { homonymSelection = it }
                is Suggestions -> SuggestionRows(answer.entries, dark) { query = it }
                NoResult -> NoResultLine(resultQuery)
            }
            result?.footerNames
                ?.takeIf { it.isNotEmpty() } // no lane at all without demoted person names
                ?.let { names -> SurnameFooter(names) }
            AboutLink(meta = meta, modifier = Modifier.padding(start = 12.dp))
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

/** The three shapes of the off-main dictionary open: pending, usable, refused for good. */
private sealed interface DictionaryLoad {
    data object Loading : DictionaryLoad
    data object Failed : DictionaryLoad
    data class Opened(val engine: LookupEngine) : DictionaryLoad
}

/** Centered plain text — the launch must survive an unusable dictionary. */
@Composable
private fun DictionaryLoadError() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.dictionary_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(UiTags.DICTIONARY_ERROR),
        )
    }
}
