package pl.kwitukiewicz.handygerman.artikel.core

/**
 * The identity of one saved word variant, stable across dictionary rebuilds:
 * *(folded lemma, article key, sense number)*. Never an `entry_id` — ids are
 * global dump ranks and shift on every re-seed, while this triple reproduces
 * the reading order of the Wiktionary page the entry was parsed from (the
 * parser's per-page entry index, [page_order] within a lemma).
 *
 * The sense number is always computed over the lemma's FULL entry set
 * ([lemmaGroup]) — never the current query's hits, which can be a declined-form
 * subset and would mis-rank the variant.
 */
data class WordIdentity(val lemmaFolded: String, val articleKey: String, val senseNo: Int)

/**
 * Canonical key of an article set: [Gender] names sorted by enum ordinal,
 * deduplicated, comma-joined. Raw dictionary text is unsafe — the dump carries
 * both `MASKULINUM,FEMININUM` and `FEMININUM,MASKULINUM` orderings.
 */
fun articleKey(genders: List<Gender>): String =
    genders.distinct().sortedBy(Gender::ordinal).joinToString(",") { it.name }

/** 1-based rank of [target] among [entriesInPageOrder] entries sharing its [articleKey]. */
fun senseNo(entriesInPageOrder: List<Entry>, target: Entry): Int =
    entriesInPageOrder.filter { articleKey(it.genders) == articleKey(target.genders) }
        .indexOfFirst { it.entryId == target.entryId } + 1

/** The live entries owning [foldedLemma], distinct by entry, in page order. */
private fun lemmaGroup(repo: WordRepository, foldedLemma: String): List<Entry> =
    repo.exactForm(foldedLemma).map { it.entry }.distinctBy { it.entryId }

/** The identity of [entry] against the live dictionary (one full-lemma lookup). */
fun resolveWordIdentity(repo: WordRepository, entry: Entry): WordIdentity {
    val folded = foldForm(entry.lemma)
    // senseNo yields 1 for an entry absent from the hits — the defensive fallback.
    return WordIdentity(folded, articleKey(entry.genders), senseNo(lemmaGroup(repo, folded), entry))
}

/**
 * Batched [resolveWordIdentity] for a published answer: one `exactForm` per
 * distinct folded lemma, keyed by entry id.
 */
fun LookupEngine.identitiesOf(entries: List<Entry>): Map<Long, WordIdentity> {
    val groups = entries.map { foldForm(it.lemma) }.distinct()
        .associateWith { folded -> lemmaGroup(wordRepository, folded) }
    return entries.associate { entry ->
        val folded = foldForm(entry.lemma)
        entry.entryId to WordIdentity(
            lemmaFolded = folded,
            articleKey = articleKey(entry.genders),
            senseNo = senseNo(groups.getValue(folded), entry),
        )
    }
}

/** One saved word variant; [rowid] is the storage row id (0 for an unsaved value). */
data class SavedWord(
    val lemma: String,
    val identity: WordIdentity,
    val firstMeaning: String?,
    val savedAtMillis: Long,
    val rowid: Long = 0L,
)

/** Keyset-paging cursor: the last (savedAtMillis, rowid) of the previous page. */
data class SavedCursor(val savedAtMillis: Long, val rowid: Long)

/** A saved word paired with its live dictionary entry — null when the sense no longer resolves. */
data class ResolvedSaved(val word: SavedWord, val entry: Entry?)

/** The pure read-side seam of `saved_words.db`; `:app` implements it over SQLite. */
interface SavedWordsRepository {

    fun save(word: SavedWord)

    fun unsave(identity: WordIdentity)

    fun isSaved(identity: WordIdentity): Boolean

    /** Page of [limit] rows newer-than-[after] (all rows when null), newest first. */
    fun page(after: SavedCursor?, limit: Int): List<SavedWord>
}

/**
 * Re-joins [saved] to the live dictionary: within the lemma's group, the entry
 * whose [articleKey] matches and whose rank equals `senseNo`; null entry keeps
 * the stored snapshot (a dictionary update removed the variant — never drop a
 * save silently). One shared lookup per distinct folded lemma.
 */
fun resolveSaved(repo: WordRepository, saved: List<SavedWord>): List<ResolvedSaved> {
    val groups = saved.map { it.identity.lemmaFolded }.distinct()
        .associateWith { folded -> lemmaGroup(repo, folded) }
    return saved.map { word ->
        val live = groups.getValue(word.identity.lemmaFolded)
            .filter { articleKey(it.genders) == word.identity.articleKey }
        ResolvedSaved(word, live.getOrNull(word.identity.senseNo - 1))
    }
}
