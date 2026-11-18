package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.kwitukiewicz.handygerman.artikel.R
import pl.kwitukiewicz.handygerman.artikel.core.Gender
import pl.kwitukiewicz.handygerman.artikel.core.ResolvedSaved
import pl.kwitukiewicz.handygerman.artikel.core.SavedCursor
import pl.kwitukiewicz.handygerman.artikel.core.SavedWord
import pl.kwitukiewicz.handygerman.artikel.core.SavedWordsRepository
import pl.kwitukiewicz.handygerman.artikel.core.articlesText
import pl.kwitukiewicz.handygerman.artikel.core.washGenderKey

private const val PAGE_LIMIT = 50

/**
 * The saved list — the home screen's blank-query state. Rows render live dictionary
 * data ([ResolvedSaved.entry]) or, when a dictionary update took the sense away, the
 * stored snapshot (lemma + article + meaning), so a save is never silently lost.
 * Newest first, keyset-paged: the next page loads when the visible end comes within
 * three rows, [hydrator] (the dictionary join) and [repository] reads run off-main —
 * this composable owns neither dispatcher choice nor engine access. [epoch] is the
 * unsave-mutation counter: a row-star un-save remounts the first page in place
 * (detail-side toggles need no remount — the list re-reads when it re-mounts).
 * [dictionaryReady]
 * flips when the dictionary finishes opening: it re-keys the reload, so rows that
 * mounted from the stored snapshot re-render with live entries.
 */
@Composable
fun SavedWordsList(
    repository: SavedWordsRepository,
    hydrator: suspend (List<SavedWord>) -> List<ResolvedSaved>,
    epoch: Int,
    dictionaryReady: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onOpen: (SavedWord) -> Unit,
    onUnsave: (SavedWord) -> Unit,
) {
    val listState = rememberLazyListState()
    val rows = remember(epoch, repository, dictionaryReady) { mutableStateListOf<ResolvedSaved>() }
    var cursor by remember(epoch, repository, dictionaryReady) { mutableStateOf<SavedCursor?>(null) }
    var exhausted by remember(epoch, repository, dictionaryReady) { mutableStateOf(false) }
    var loadedOnce by remember(epoch, repository, dictionaryReady) { mutableStateOf(false) }

    suspend fun loadPage(after: SavedCursor?) {
        val page = withContext(Dispatchers.IO) { repository.page(after, PAGE_LIMIT) }
        rows += hydrator(page)
        cursor = page.lastOrNull()?.let { SavedCursor(it.savedAtMillis, it.rowid) }
        exhausted = page.size < PAGE_LIMIT
    }

    LaunchedEffect(epoch, repository, dictionaryReady) {
        loadPage(null)
        loadedOnce = true
    }

    // Continuous load: every frame the tail approaches, another page is fetched.
    // Emissions are consumed sequentially, so `cursor` can never double-advance.
    LaunchedEffect(epoch, repository, dictionaryReady) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            !exhausted && rows.isNotEmpty() && lastVisible >= rows.size - 3
        }.collect { nearEnd ->
            val after = cursor
            if (nearEnd && after != null) loadPage(after)
        }
    }

    Column(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(UiTags.SAVED_LIST),
        ) {
            items(rows, key = { resolved ->
                if (resolved.word.rowid > 0L) resolved.word.rowid else resolved.word.identity
            }) { resolved ->
                SavedWordRow(resolved, dark, onOpen, onUnsave)
            }
        }
        if (loadedOnce && rows.isEmpty()) {
            Text(
                text = stringResource(R.string.saved_empty_hint),
                modifier = Modifier
                    .testTag(UiTags.SAVED_EMPTY)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = WashPalette.onWashMuted(dark),
            )
        }
    }
}

@Composable
private fun SavedWordRow(
    resolved: ResolvedSaved,
    dark: Boolean,
    onOpen: (SavedWord) -> Unit,
    onUnsave: (SavedWord) -> Unit,
) {
    val word = resolved.word
    // Live entry when the sense still exists; otherwise the snapshot taken at save
    // time — the article set comes back through the canonical key it was stored under.
    val genders = resolved.entry?.genders
        ?: word.identity.articleKey.split(",").map(Gender::valueOf)
    val accent = WashPalette.accent(washGenderKey(genders), dark)
    val muted = WashPalette.onWashMuted(dark)
    val lemma = resolved.entry?.lemma ?: word.lemma
    val meaning = resolved.entry?.meaningLines?.firstOrNull() ?: word.firstMeaning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(word) }
            .testTag(UiTags.SAVED_ROW)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = articlesText(genders),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    text = "  $lemma",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WashPalette.onWash(washGenderKey(genders), dark),
                )
            }
            meaning?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = { onUnsave(word) },
            modifier = Modifier.testTag(UiTags.SAVE_TOGGLE),
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.unsave_word),
                tint = accent,
            )
        }
    }
}

