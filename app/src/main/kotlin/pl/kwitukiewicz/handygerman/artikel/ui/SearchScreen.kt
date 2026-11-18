package pl.kwitukiewicz.handygerman.artikel.ui

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
import pl.kwitukiewicz.handygerman.artikel.R
import pl.kwitukiewicz.handygerman.artikel.core.Entry
import pl.kwitukiewicz.handygerman.artikel.core.Homonyms
import pl.kwitukiewicz.handygerman.artikel.core.LookupEngine
import pl.kwitukiewicz.handygerman.artikel.core.LookupResult
import pl.kwitukiewicz.handygerman.artikel.core.NoResult
import pl.kwitukiewicz.handygerman.artikel.core.ResolvedSaved
import pl.kwitukiewicz.handygerman.artikel.core.SavedWord
import pl.kwitukiewicz.handygerman.artikel.core.SavedWordsRepository
import pl.kwitukiewicz.handygerman.artikel.core.Single
import pl.kwitukiewicz.handygerman.artikel.core.Suggestions
import pl.kwitukiewicz.handygerman.artikel.core.WordIdentity
import pl.kwitukiewicz.handygerman.artikel.core.foldForm
import pl.kwitukiewicz.handygerman.artikel.core.resolveSaved
import pl.kwitukiewicz.handygerman.artikel.core.identitiesOf
import pl.kwitukiewicz.handygerman.artikel.core.washGenderKey

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
 * entry rides the search row's overflow menu (Amendment #4). Person-name homonyms are
 * never shown: the engine demotes
 * them to [LookupResult.footerNames], an internal lane the UI dropped on user decision
 * 2026-11-15.
 */
@Composable
fun SearchScreen(
    engineLoader: suspend () -> LookupEngine,
    savedLoader: suspend () -> SavedWordsRepository,
    metaProvider: suspend () -> Map<String, String> = { emptyMap() },
) {
    // Single-appearance app: the pin, never isSystemInDarkTheme() — the same decision
    // HandyGermanTheme takes for the Material 3 scheme, so washes and chrome agree in
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
    // The identities of the published answer's candidates and which of them sit in
    // the saved store — one holder so stars and answer can only ever publish together
    // under the generation check (a star state belonging to a superseded answer is
    // the exact drift this pairing prevents).
    var published by remember { mutableStateOf(PublishedAnswers()) }
    // Bumped by every save/unsave; Task 4's list keys its reload on it.
    val savedListEpoch = remember { mutableIntStateOf(0) }
    // Bumped by every store write (toggle/unsave) right where it lands, off-Main — single
    // writer at a time. The publish step's saved-set snapshot (publishedFor, on Default) is
    // stale if this moved mid-snapshot; the generation guard in the publish then re-derives.
    val savedWrites = remember { mutableIntStateOf(0) }
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
                // Dictionary rejection — foreign schema version, missing asset, fold
                // drift — is a plain failure here; the stack trace belongs in logcat only.
                Log.w("SearchScreen", "dictionary load failed", refused)
                DictionaryLoad.Failed
            }
        }
    }
    // The saved store opens on its own path, never queued behind the dictionary: it is
    // a small independent database, and the blank-query home mounts from it while the
    // seconds-long dictionary open is still in flight (snapshot rows until it lands).
    val savedStore by produceState<Result<SavedWordsRepository>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { runCatching { savedLoader() } }
    }
    val engine = (dictionary as? DictionaryLoad.Opened)?.engine
    val savedRepository = savedStore?.getOrNull()

    // Homonym selection lives here, not inside the subview: the wash must follow the
    // candidate on screen. Keyed on the query, not the answer instance — recreation
    // recomputes a fresh answer object, and the choice must ride through rotation:
    // the same query yields the same deterministically ordered candidates, so the
    // index stays valid (the getOrNull guard stays as the backstop). A new query
    // restarts the selection at zero. Declared before the lookup effect because the
    // publish step seeds it from a pending saved-row open.
    var homonymSelection by rememberSaveable(query) { mutableIntStateOf(0) }
    // A saved-row tap awaiting the answer's first publish: which variant to select
    // once the query resolves. Stored in its seven-field saveable form (decode below);
    // consumed exactly once, by the publish of its own query; never resurrected.
    var pendingOpen by rememberSaveable { mutableStateOf<List<String>?>(null) }

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
            // Snapshot-vs-write race (Review Focus line 3): if a star write landed while this
            // generation's saved set was being read, re-derive it. A flip completing after
            // this re-check queues its Main block after this publish, so it applies on top.
            val writesBefore = savedWrites.intValue
            var publishedAnswers = publishedFor(engine, savedRepository, outcome)
            if (savedWrites.intValue != writesBefore) {
                publishedAnswers = publishedAnswers.copy(
                    saved = publishedAnswers.identities.values
                        .filter { savedRepository?.isSaved(it) == true }
                        .toSet(),
                )
            }
            withContext(Dispatchers.Main) {
                if (generation.intValue == revision) {
                    result = outcome
                    resultQuery = searched
                    published = publishedAnswers
                    pendingOpen?.let { targetFields ->
                        pendingOpen = null
                        val target = decodeSavedWord(targetFields)
                        // Only the query the tap came from may consume the target —
                        // a retype in between must not select across words.
                        if (target.identity.lemmaFolded == foldForm(searched) &&
                            outcome?.answer is Homonyms
                        ) {
                            val candidates = (outcome.answer as Homonyms).candidates
                            val index = candidates.indexOfFirst { candidate ->
                                publishedAnswers.identities[candidate.entry.entryId]?.let {
                                    it.articleKey == target.identity.articleKey &&
                                        it.senseNo == target.identity.senseNo
                                } == true
                            }
                            if (index >= 0) homonymSelection = index
                        }
                    }
                }
            }
        }
    }

    // Star = membership of the tapped candidate's (lemma, article, sense). The write
    // runs off-main; the published set flips only when the write completed, so the
    // filled star never promises a save the store refused.
    fun toggleSaved(entry: Entry) {
        val repository = savedRepository ?: return
        val identity = published.identities[entry.entryId] ?: return
        lookupScope.launch(Dispatchers.IO) {
            val wasSaved = repository.isSaved(identity)
            if (wasSaved) {
                repository.unsave(identity)
            } else {
                repository.save(
                    SavedWord(
                        lemma = entry.lemma,
                        identity = identity,
                        firstMeaning = entry.meaningLines.firstOrNull(),
                        savedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
            savedWrites.intValue++
            withContext(Dispatchers.Main) {
                published = if (wasSaved) {
                    published.copy(saved = published.saved - identity)
                } else {
                    published.copy(saved = published.saved + identity)
                }
                savedListEpoch.intValue++
            }
        }
    }

    // Row star = un-save of exactly that variant, the detail star's inverse; the
    // epoch bump remounts the mounted list so the row vanishes without a reload.
    fun unsaveSavedWord(repository: SavedWordsRepository, saved: SavedWord) {
        lookupScope.launch(Dispatchers.IO) {
            repository.unsave(saved.identity)
            savedWrites.intValue++
            withContext(Dispatchers.Main) {
                published = published.copy(saved = published.saved - saved.identity)
                savedListEpoch.intValue++
            }
        }
    }

    val answer = result?.answer

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
                // no answer: still opening / blank query, or refused for good —
                // a refused dictionary OR a refused saved store is the plain error
                // screen; a blank query with the saved store open is the list's home
                // state, snapshot-rendered until the dictionary joins live entries.
                null -> {
                    val store = savedRepository
                    when {
                        dictionary is DictionaryLoad.Failed || savedStore?.isFailure == true ->
                            DictionaryLoadError()
                        store != null && query.isEmpty() -> SavedWordsList(
                            repository = store,
                            hydrator = { saved ->
                                val opened = engine
                                if (opened == null) {
                                    saved.map { ResolvedSaved(it, null) }
                                } else {
                                    withContext(Dispatchers.IO) {
                                        resolveSaved(opened.wordRepository, saved)
                                    }
                                }
                            },
                            epoch = savedListEpoch.intValue,
                            dictionaryReady = engine != null,
                            dark = dark,
                            modifier = Modifier.weight(1f),
                            onOpen = { saved ->
                                pendingOpen = encodeSavedWord(saved)
                                query = saved.lemma
                            },
                            onUnsave = { saved -> unsaveSavedWord(store, saved) },
                        )
                        else -> Unit // dictionary still opening behind a typed query: blank wash
                    }
                }
                is Single -> {
                    val entry = answer.candidate.entry
                    CandidateDetails(
                        answer.candidate,
                        dark,
                        isSaved = published.saved.contains(published.identities[entry.entryId]),
                        onToggleSaved = { toggleSaved(entry) },
                    )
                }
                is Homonyms -> {
                    val selected = answer.candidates.getOrNull(homonymSelection)?.entry
                    HomonymDetails(
                        answer,
                        homonymSelection,
                        dark,
                        onSelect = { homonymSelection = it },
                        isSaved = selected?.entryId?.let { published.saved.contains(published.identities[it]) } == true,
                        onToggleSaved = { selected?.let { toggleSaved(it) } },
                    )
                }
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
    data class Opened(
        val engine: LookupEngine,
    ) : DictionaryLoad
}

/**
 * The identity view of a published answer: every candidate's [WordIdentity] (keyed by
 * entry id) and the subset the saved store holds. Lives in one holder so the two can
 * never disagree about which answer generation they describe.
 */
private data class PublishedAnswers(
    val identities: Map<Long, WordIdentity> = emptyMap(),
    val saved: Set<WordIdentity> = emptySet(),
)

/**
 * Off-main half of a publish: batched identity resolution for the answer's candidates
 * plus their saved-state, all read from the store right here so what Main publishes
 * belongs to this generation. No candidates (suggestions/no-result/blank) → no stars.
 */
private fun publishedFor(
    engine: LookupEngine,
    repository: SavedWordsRepository?,
    outcome: LookupResult?,
): PublishedAnswers {
    val entries = when (val answer = outcome?.answer) {
        is Single -> listOf(answer.candidate.entry)
        is Homonyms -> answer.candidates.map { it.entry }
        else -> emptyList()
    }
    if (repository == null || entries.isEmpty()) return PublishedAnswers()
    val identities = engine.identitiesOf(entries)
    val saved = identities.values.filter { repository.isSaved(it) }.toSet()
    return PublishedAnswers(identities, saved)
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


/**
 * The pending saved-row open round-trips through process death in its saveable
 * field list (Compose's `rememberSaveable` hosts primitives, not custom classes).
 * Only the identity triple steers selection; the rest rides along unchanged.
 */
private fun encodeSavedWord(word: SavedWord): List<String> =
    listOf(
        word.lemma,
        word.identity.lemmaFolded,
        word.identity.articleKey,
        word.identity.senseNo.toString(),
        word.firstMeaning ?: "",
        word.savedAtMillis.toString(),
        word.rowid.toString(),
    )

private fun decodeSavedWord(fields: List<String>): SavedWord =
    SavedWord(
        lemma = fields[0],
        identity = WordIdentity(fields[1], fields[2], fields[3].toInt()),
        firstMeaning = fields[4].ifEmpty { null },
        savedAtMillis = fields[5].toLong(),
        rowid = fields[6].toLong(),
    )
