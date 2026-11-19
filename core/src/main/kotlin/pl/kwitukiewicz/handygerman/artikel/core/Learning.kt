package pl.kwitukiewicz.handygerman.artikel.core

/** Levels start at 0; a correct answer at [QUIZ_GRADUATION_LEVEL] minus one graduates the word. */
const val QUIZ_GRADUATION_LEVEL = 3

/** Per-word learning progress: quiz level plus graduation stamp (`null` while unlearned). */
data class LearningState(val level: Int, val graduatedAt: Long?)

/**
 * The pure read/write seam of the `learning` table. A saved row whose state has
 * [LearningState.graduatedAt] set is a re-starred (or half-graduated) word: the
 * session planner reports it for [requeue] repair.
 */
interface LearningRepository {

    /** Every stored state, keyed by the shared [WordIdentity]. */
    fun states(): Map<WordIdentity, LearningState>

    /** Upsert the level and clear any graduation stamp (a re-learned graduate). */
    fun putLevel(identity: WordIdentity, level: Int)

    /** Mark learned: level [QUIZ_GRADUATION_LEVEL] with [atMillis] as the timestamp. */
    fun graduate(identity: WordIdentity, atMillis: Long)

    /** Repair rule: level 0, no graduation stamp — re-queues the word from scratch. */
    fun requeue(identity: WordIdentity)

    /** Forget the word entirely (its save was removed — "the star is the learning"). */
    fun delete(identity: WordIdentity)

    /** Rows counted as learned by the About dialog: the graduated ones. */
    fun learnedCount(): Int
}

/** One quiz card. [answers] = exactly the articles the app displays; [meaning] disambiguates, nullable. */
data class QuizItem(val word: SavedWord, val meaning: String?, val answers: Set<String>)

/** Result of [planQuizSession]: the shuffled-later run, or the empty-queue signal. */
sealed interface SessionPlan {
    /** [items] in store order; [repairs] = saved rows whose state still says graduated. */
    data class Run(val items: List<QuizItem>, val repairs: List<WordIdentity>) : SessionPlan
    data object NothingToPractice : SessionPlan
}

/**
 * Snapshot session build: every saved row is one card (graduates normally left
 * the saved store, so only rows make the queue). Rows whose state carries a
 * graduation stamp are re-starred graduates: they stay in [Run.items] at level 0
 * — the caller executes `requeue` on [Run.repairs], making the repair once-only.
 */
fun planQuizSession(rows: List<ResolvedSaved>, states: Map<WordIdentity, LearningState>): SessionPlan {
    if (rows.isEmpty()) return SessionPlan.NothingToPractice
    val repairs = rows.mapNotNull { row ->
        row.word.identity.takeIf { states[it]?.graduatedAt != null }
    }
    val items = rows.map { row ->
        val genders = row.entry?.genders
            ?: row.word.identity.articleKey.split(",").map(Gender::valueOf)
        QuizItem(
            word = row.word,
            meaning = row.entry?.meaningLines?.firstOrNull() ?: row.word.firstMeaning,
            answers = quizAnswers(genders),
        )
    }
    return SessionPlan.Run(items, repairs)
}

/** One answer's outcome: next level, reset to zero, or graduation (the third correct session). */
sealed interface AnswerOutcome {
    data class Correct(val newLevel: Int) : AnswerOutcome
    object Missed : AnswerOutcome
    data object Graduated : AnswerOutcome
}

/** Correct advances one level and graduates at the cap; a miss reports [AnswerOutcome.Missed] (level 0). */
fun onAnswer(currentLevel: Int, correct: Boolean): AnswerOutcome {
    if (!correct) return AnswerOutcome.Missed
    val next = currentLevel + 1
    return if (next >= QUIZ_GRADUATION_LEVEL) AnswerOutcome.Graduated else AnswerOutcome.Correct(next)
}

/** The article strings the app would display for [genders] — exactly the acceptable answers. */
fun quizAnswers(genders: List<Gender>): Set<String> = genders.mapTo(mutableSetOf()) { article(it) }

/** The fixed button set and display order on every card — never a per-card subset. */
val QUIZ_ANSWERS: List<String> = listOf("der", "die", "das", "–")
