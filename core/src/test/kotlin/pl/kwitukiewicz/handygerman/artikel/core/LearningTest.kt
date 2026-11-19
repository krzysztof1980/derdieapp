package pl.kwitukiewicz.handygerman.artikel.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.kwitukiewicz.handygerman.artikel.core.AnswerOutcome.Correct
import pl.kwitukiewicz.handygerman.artikel.core.AnswerOutcome.Graduated
import pl.kwitukiewicz.handygerman.artikel.core.AnswerOutcome.Missed
import pl.kwitukiewicz.handygerman.artikel.core.Gender.FEMININUM
import pl.kwitukiewicz.handygerman.artikel.core.Gender.MASKULINUM
import pl.kwitukiewicz.handygerman.artikel.core.Gender.NEUTRUM
import pl.kwitukiewicz.handygerman.artikel.core.Gender.PLURAL
import pl.kwitukiewicz.handygerman.artikel.core.Gender.UTRUM
import pl.kwitukiewicz.handygerman.artikel.core.Kind.WORD
import pl.kwitukiewicz.handygerman.artikel.core.SessionPlan.NothingToPractice
import pl.kwitukiewicz.handygerman.artikel.core.SessionPlan.Run

/**
 * The learning engine: level math, session planning with the re-queue repair,
 * and card content — pure over [ResolvedSaved] and the states map.
 */
class LearningTest {

    private val bank = WordIdentity("bank", "FEMININUM", 1)
    private val teil = WordIdentity("teil", "NEUTRUM", 1)

    private fun saved(identity: WordIdentity, lemma: String, snapshotMeaning: String?) = SavedWord(
        lemma = lemma,
        identity = identity,
        firstMeaning = snapshotMeaning,
        savedAtMillis = 1L,
    )

    private val bankRow = ResolvedSaved(
        word = saved(bank, "Bank", "Sitzgelegenheit"),
        entry = Entry(1, "Bank", WORD, listOf(FEMININUM), "Bänke", listOf("Sitz- oder Ablagegelegenheit", "Geldinstitut")),
    )
    private val teilRow = ResolvedSaved(
        word = saved(teil, "Teil", "Stück; Teilmenge"),
        entry = Entry(3, "Teil", WORD, listOf(NEUTRUM), "Teile", listOf("Stück; Teilmenge")),
    )

    @Test
    fun correctAnswerAdvancesLevelAndGraduatesAtTheTop() {
        assertEquals(Correct(1), onAnswer(0, correct = true))
        assertEquals(Correct(2), onAnswer(1, correct = true))
        assertTrue(onAnswer(QUIZ_GRADUATION_LEVEL - 1, correct = true) is Graduated)
    }

    @Test
    fun missedAnswerResetsWithoutGraduatingAtAnyLevel() {
        assertTrue(onAnswer(0, correct = false) is Missed)
        assertTrue(onAnswer(QUIZ_GRADUATION_LEVEL - 1, correct = false) is Missed)
    }

    @Test
    fun everySavedRowBecomesOneItem() {
        val plan = planQuizSession(listOf(bankRow, teilRow), states = emptyMap())
        assertEquals(
            listOf(bank, teil),
            (plan as Run).items.map { it.word.identity },
        )
        assertEquals(emptyList<WordIdentity>(), plan.repairs)
    }

    @Test
    fun nothingToPracticeOnlyWhenThereAreNoRows() {
        assertTrue(planQuizSession(emptyList(), states = emptyMap()) is NothingToPractice)
    }

    @Test
    fun quizAnswersAreExactlyTheDisplayedArticles() {
        assertEquals(setOf("die"), quizAnswers(listOf(FEMININUM)))
        assertEquals(setOf("der", "die"), quizAnswers(listOf(MASKULINUM, FEMININUM)))
        assertEquals(setOf("–"), quizAnswers(listOf(UTRUM)))
        assertEquals(setOf("die"), quizAnswers(listOf(PLURAL)))
    }

    @Test
    fun fixedButtonSetNeverChanges() {
        assertEquals(listOf("der", "die", "das", "–"), QUIZ_ANSWERS)
    }

    @Test
    fun cardUsesLiveEntryMeaningAndGenders() {
        val item = (planQuizSession(listOf(bankRow), states = emptyMap()) as Run).items.single()
        assertEquals("Sitz- oder Ablagegelegenheit", item.meaning)
        assertEquals(setOf("die"), item.answers)
    }

    @Test
    fun cardFallsBackToSnapshotMeaningAndArticleKeyWhenEntryGone() {
        val ghost = ResolvedSaved(word = bankRow.word.copy(firstMeaning = "Sitzgelegenheit"), entry = null)
        val item = (planQuizSession(listOf(ghost), states = emptyMap()) as Run).items.single()
        assertEquals("Sitzgelegenheit", item.meaning)
        assertEquals(setOf("die"), item.answers)
    }

    @Test
    fun cardAnswersComeFromMultiArticleIdentityWhenEntryGone() {
        val dual = WordIdentity("teil", "MASKULINUM,FEMININUM", 1)
        val row = ResolvedSaved(word = saved(dual, "Teil", "Element des Ganzen"), entry = null)
        val item = (planQuizSession(listOf(row), states = emptyMap()) as Run).items.single()
        assertEquals(setOf("der", "die"), item.answers)
    }

    @Test
    fun graduatedSavedRowStaysInRunAndIsReportedForRepair() {
        val learning = FakeLearningRepository()
        learning.graduate(bank, atMillis = 123L)
        val plan = planQuizSession(listOf(bankRow, teilRow), learning.states())
        assertEquals(listOf(bank), (plan as Run).repairs)
        assertEquals(listOf(bank, teil), plan.items.map { it.word.identity })
    }

    @Test
    fun requeuedGraduateRepairsOnlyOnceAndStopsCountingLearned() {
        val learning = FakeLearningRepository()
        learning.graduate(bank, atMillis = 123L)
        assertEquals(1, learning.learnedCount())

        val first = planQuizSession(listOf(bankRow), learning.states()) as Run
        assertEquals(listOf(bank), first.repairs)
        first.repairs.forEach(learning::requeue)
        assertEquals(LearningState(0, graduatedAt = null), learning.states()[bank])
        assertEquals(0, learning.learnedCount())

        val second = planQuizSession(listOf(bankRow), learning.states()) as Run
        assertEquals(emptyList<WordIdentity>(), second.repairs)
        assertEquals(listOf(bank), second.items.map { it.word.identity })
    }
}
