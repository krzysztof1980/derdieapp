package pl.kwitukiewicz.derdiedas.core

/** What the app screens as the answer to a lookup; [LookupResult.footerNames] rides along. */
sealed interface Answer

/** The lookup found neither an exact form nor a single prefix suggestion. */
object NoResult : Answer

/** No exact hit: entries completing the input, one per distinct lemma. */
data class Suggestions(val entries: List<Entry>) : Answer

/** One entry answers the lookup — even with several genders ("both accepted" via [articlesText]). */
data class Single(val candidate: Candidate) : Answer

/** Several entries share the typed form; [selectedIndex] marks the one expanded, Task 7 toggles it. */
data class Homonyms(val candidates: List<Candidate>, val selectedIndex: Int = 0) : Answer

/** One answer entry with the [typedForm] the user actually typed, kept for its kasus annotation. */
data class Candidate(val entry: Entry, val typedForm: FormHit)

/**
 * The answer plus person-name homonyms demoted to a footer, in page order.
 * [footerNames] is an internal lane since the surname footer left the UI
 * (user decision 2026-11-15): the engine still guarantees persons never reach the
 * main answer and promotes them when they are the only hits.
 */
data class LookupResult(val answer: Answer, val footerNames: List<Entry> = emptyList())
