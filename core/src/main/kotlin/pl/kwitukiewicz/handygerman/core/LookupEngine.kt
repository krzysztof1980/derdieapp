package pl.kwitukiewicz.handygerman.core

/**
 * The decision rules of a lookup, pure over the [WordRepository] seam:
 * fold the trimmed input; exact hits grouped by entry (page order, first hit = the
 * annotation source); `PERSON_NAME` entries join the footer unless nothing else hit —
 * then they are promoted. Two or more candidates make [Homonyms], one makes [Single];
 * no exact hit degrades to distinct-lemma [Suggestions], and anything else is [NoResult].
 * A blank input is not a lookup at all: `null`.
 */
class LookupEngine(private val repo: WordRepository) {

    fun lookup(input: String): LookupResult? {
        if (input.isBlank()) return null
        val folded = foldForm(input.trim())

        val hits = repo.exactForm(folded)
        if (hits.isNotEmpty()) return answeredFrom(hits)

        val suggestions = repo.prefixForms(folded, PREFIX_FORM_LIMIT)
            .distinctBy { it.entry.lemma }
            // Repo order makes a lemma's first hit its lowest-page_order entry: within each
            // form group hits ascend by page_order, and every entry carries its own lemma
            // row, so the first (lexicographically smallest) form group of a lemma already
            // contains all of that lemma's entries in page order.
            .map { it.entry }
        return if (suggestions.isEmpty()) LookupResult(NoResult)
        else LookupResult(Suggestions(suggestions.take(MAX_SUGGESTIONS)))
    }

    private fun answeredFrom(hits: List<FormHit>): LookupResult {
        // groupBy preserves page order: keys surface at their first hit.
        val candidates = hits.groupBy { it.entry }.map { (entry, forms) -> Candidate(entry, forms.first()) }
        val (persons, words) = candidates.partition { it.entry.kind == Kind.PERSON_NAME }
        if (words.isEmpty()) return LookupResult(answered(persons))

        return LookupResult(answered(words), persons.map { it.entry })
    }

    private fun answered(candidates: List<Candidate>): Answer =
        if (candidates.size >= 2) Homonyms(candidates) else Single(candidates.first())

    private companion object {
        /** Generous form cap: 12 distinct lemmas can sit behind many forms. */
        const val PREFIX_FORM_LIMIT = 100
        const val MAX_SUGGESTIONS = 12
    }
}
