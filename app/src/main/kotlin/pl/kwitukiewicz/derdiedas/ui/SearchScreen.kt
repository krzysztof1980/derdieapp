package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
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
 * answer's gender color. The field is an outlined box (WashPalette's chrome outline colors,
 * always a visible border) and grabs focus plus the soft keyboard once per process entry;
 * the content column sits below the status-bar inset and the Box's content above the
 * navigation-bar inset — the wash paints edge to edge under both. [engineLoader] (which
 * copies and opens the bundled database) runs
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
 * hoisted here (keyed on the query, so the choice survives rotation and a new query
 * restarts it) so the wash follows the segmented article row's choice, and the About
 * link sits under the answer. Person-name homonyms are never shown: the engine demotes
 * them to [LookupResult.footerNames], an internal lane the UI dropped on user decision
 * 2026-11-15.
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
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // Autofocus + keyboard on launch, once per process entry: opening the app goes
        // straight to typing, and an Activity recreation inside the same process does
        // not fight the user for the IME. runCatching because unit tests drive input
        // through semantics and the Robolectric JVM has no real IME to show — a
        // keyboard-less launch must still survive.
        if (keyboardShownOnLaunch.compareAndSet(false, true)) {
            runCatching {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }
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
    // candidate on screen. Keyed on the query, not the answer instance — recreation
    // recomputes a fresh answer object, and the choice must ride through rotation:
    // the same query yields the same deterministically ordered candidates, so the
    // index stays valid (the getOrNull guard above stays as the backstop). A new
    // query restarts the selection at zero, exactly as the fresh-answer object did.
    var homonymSelection by rememberSaveable(query) { mutableIntStateOf(0) }

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
        // The wash paints edge to edge under the transparent bars; navigationBarsPadding
        // (after background) keeps the content — landscape navigation bars and cutouts
        // included — off the unusable strips, and statusBarsPadding on the Column below
        // is what finally puts the field under, not behind, the status icons.
        modifier = Modifier
            .testTag(UiTags.WASH)
            .fillMaxSize()
            .background(wash)
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            // Unmistakably an input box on every wash: an outlined field with the
            // material3 border transparented and a hand-drawn 1.5 dp outline in its
            // place — resting gray, focused darker, both pinned in WashPalette and
            // floored at >= 1.5:1 by WashPaletteTest — over a transparent container so
            // the wash shows through. Cursor and placeholder inherit the pinned light
            // scheme (always on-color in this single-appearance app).
            val fieldShape = RoundedCornerShape(12.dp)
            val fieldInteraction = remember { MutableInteractionSource() }
            val fieldFocused by fieldInteraction.collectIsFocusedAsState()
            var menuOpen by remember { mutableStateOf(false) }
            var showAbout by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 4.dp) // 2026-11-15 device fix: never edge-flush
                    .border(
                        width = 1.5.dp,
                        color = if (fieldFocused) {
                            WashPalette.fieldOutlineFocused()
                        } else {
                            WashPalette.fieldOutline()
                        },
                        shape = fieldShape,
                    )
                    .testTag(UiTags.QUERY_FIELD)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.query_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null, // decoration; the field's own semantics carry the input
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    // Chrome-style clear (user request 2026-11-15): only while there is
                    // text; tap empties the field and keeps focus — the blank-query path
                    // (null result, transparent wash) runs, never a NoResult for "".
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                focusRequester.requestFocus()
                            },
                            modifier = Modifier.testTag(UiTags.CLEAR_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_query),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = fieldShape,
                interactionSource = fieldInteraction,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
                // Amendment #4 (2026-11-15): the About attribution moved from a footer
                // link into this overflow — one entry today, room for settings tomorrow.
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier
                            .testTag(UiTags.MENU)
                            .padding(end = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.menu_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_about)) },
                            onClick = {
                                menuOpen = false
                                showAbout = true
                            },
                            modifier = Modifier.testTag(UiTags.MENU_ITEM),
                        )
                    }
                }
            }
            if (showAbout) AboutDialog(meta = meta, onDismiss = { showAbout = false })
            when (answer) {
                // no answer: either the dictionary is still opening / the query is blank,
                // or it refused to open — then show the plain error screen, no stack trace
                null -> if (dictionary is DictionaryLoad.Failed) DictionaryLoadError()
                is Single -> CandidateDetails(answer.candidate, dark)
                is Homonyms -> HomonymDetails(answer, homonymSelection, dark) { homonymSelection = it }
                is Suggestions -> SuggestionRows(answer.entries, dark) { query = it }
                NoResult -> NoResultLine(resultQuery)
            }
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

/** Autofocus gate: the launch keyboard shows once per process, never on recreations. */
private val keyboardShownOnLaunch = AtomicBoolean(false)

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
