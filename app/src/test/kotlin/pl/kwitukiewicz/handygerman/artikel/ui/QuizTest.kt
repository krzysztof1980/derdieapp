package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.kwitukiewicz.handygerman.artikel.core.Entry
import pl.kwitukiewicz.handygerman.artikel.core.FakeLearningRepository
import pl.kwitukiewicz.handygerman.artikel.core.FakeSavedWordsRepository
import pl.kwitukiewicz.handygerman.artikel.core.FakeWordRepository
import pl.kwitukiewicz.handygerman.artikel.core.Gender
import pl.kwitukiewicz.handygerman.artikel.core.Kind
import pl.kwitukiewicz.handygerman.artikel.core.LearningState
import pl.kwitukiewicz.handygerman.artikel.core.SavedWord
import pl.kwitukiewicz.handygerman.artikel.core.WordIdentity

/**
 * The quiz run over the saved queue: menu entry with the queue count, the card flow,
 * the level writes (their receipt is the next card / counter, never the store read),
 * graduation un-saving the row, the exit and end cards, and the two saveable-state
 * review focuses — rotation mid-run and membership frozen to the run's snapshot.
 *
 * Driven through [SearchScreenRecreationTestHost] so `scenario.recreate()` is the
 * real Activity-recreation pathway, with the fakes behind the host's providers.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class QuizTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<SearchScreenRecreationTestHost>
    private val fakeSaved = FakeSavedWordsRepository()
    private val fakeLearning = FakeLearningRepository()

    private val bank = Entry(1, "Bank", Kind.WORD, listOf(Gender.FEMININUM), "Bänke", listOf("Sitzgelegenheit"))
    private val zeit = Entry(2, "Zeit", Kind.WORD, listOf(Gender.FEMININUM), "Zeiten", listOf("Verlauf"))
    private val teil = Entry(3, "Teil", Kind.WORD, listOf(Gender.NEUTRUM), "Teile", listOf("Stück"))
    private val bankId = WordIdentity("bank", "FEMININUM", 1)
    private val zeitId = WordIdentity("zeit", "FEMININUM", 1)
    private val teilId = WordIdentity("teil", "NEUTRUM", 1)

    private fun launch() {
        SearchScreenRecreationTestHost.SEARCH_REPOSITORY_PROVIDER = {
            FakeWordRepository(
                entries = listOf(bank, zeit, teil),
                rows = listOf(
                    FakeWordRepository.Row("bank", 1, "Singular", "Nominativ", isLemma = true),
                    FakeWordRepository.Row("zeit", 2, "Singular", "Nominativ", isLemma = true),
                    FakeWordRepository.Row("teil", 3, "Singular", "Nominativ", isLemma = true),
                ),
            )
        }
        SearchScreenRecreationTestHost.SEARCH_SAVED_PROVIDER = { fakeSaved }
        SearchScreenRecreationTestHost.SEARCH_LEARNING_PROVIDER = { fakeLearning }
        scenario = ActivityScenario.launch(SearchScreenRecreationTestHost::class.java)
    }

    @After
    fun closeHost() {
        scenario.close()
        SearchScreenRecreationTestHost.SEARCH_REPOSITORY_PROVIDER = { FakeWordRepository() }
        SearchScreenRecreationTestHost.SEARCH_SAVED_PROVIDER = { FakeSavedWordsRepository() }
        SearchScreenRecreationTestHost.SEARCH_LEARNING_PROVIDER = { FakeLearningRepository() }
    }

    private fun saveBank() = fakeSaved.save(SavedWord("Bank", bankId, "Sitzgelegenheit", 100L))
    private fun saveZeit() = fakeSaved.save(SavedWord("Zeit", zeitId, "Verlauf", 200L))
    private fun saveTeil() = fakeSaved.save(SavedWord("Teil", teilId, "Stück", 300L))

    private fun openMenuAndStartQuiz(count: Int) {
        compose.onNodeWithTag(UiTags.MENU).performClick()
        val label = "Start quiz · $count"
        compose.awaitSemanticsText(UiTags.MENU_ITEM, label)
        compose.onNodeWithText(label).performClick()
    }

    /** The word on the current card, waiting past the feedback wash for the next one. */
    private fun awaitCardWord(excluding: String?): String {
        repeat(CLOCK_SLICES) {
            compose.mainClock.advanceTimeBy(CLOCK_SLICE_MS)
            Thread.sleep(WALL_SLEEP_MS)
            val texts = compose.semanticsTexts(UiTags.QUIZ_WORD)
            if (texts.size == 1 && texts[0] != excluding) return texts[0]
        }
        throw AssertionError("quiz card ${if (excluding == null) "" else "after $excluding "}never came on screen")
    }

    private fun articleOf(word: String): String = when (word) {
        "Bank", "Zeit" -> "die"
        "Teil" -> "das"
        else -> throw AssertionError("unexpected quiz word: $word")
    }

    private fun identityOf(word: String): WordIdentity = when (word) {
        "Bank" -> bankId
        "Zeit" -> zeitId
        "Teil" -> teilId
        else -> throw AssertionError("unexpected quiz word: $word")
    }

    private fun tapAnswer(label: String) = compose.onNodeWithText(label).performClick()

    private fun assertCounter(needle: String) = compose.awaitSemanticsText(UiTags.QUIZ_COUNTER, needle)

    @Test
    fun menuCountOpensCardWithWordMeaningAndFourButtons() {
        saveBank()
        saveZeit()
        saveTeil()
        launch()
        openMenuAndStartQuiz(3)

        val word = awaitCardWord(null)
        assertTrue(word in setOf("Bank", "Zeit", "Teil"))
        compose.awaitTag(UiTags.QUIZ_MEANING)
        assertEquals(4, compose.onAllNodesWithTag(UiTags.QUIZ_ANSWER).fetchSemanticsNodes().size)
        // The search field is unmounted for the run: starring and searching are
        // impossible mid-quiz by construction.
        assertTrue(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun correctTapRaisesLevelWrongTapResetsAndCounterAdvancesAnyway() {
        saveBank()
        saveZeit()
        saveTeil()
        launch()
        openMenuAndStartQuiz(3)

        val first = awaitCardWord(null)
        tapAnswer(articleOf(first))
        assertCounter("2 of 3")
        assertEquals(LearningState(1, null), fakeLearning.statesMap[identityOf(first)])

        val second = awaitCardWord(first)
        assertTrue(second != first)
        tapAnswer(if (articleOf(second) == "der") "die" else "der")
        assertCounter("3 of 3")
        assertEquals(LearningState(0, null), fakeLearning.statesMap[identityOf(second)])
    }

    @Test
    fun runDrainsToTallyAndGraduationUnsavesTheRow() {
        saveBank()
        saveTeil()
        fakeLearning.putLevel(bankId, 2)
        fakeLearning.putLevel(teilId, 2)
        launch()
        openMenuAndStartQuiz(2)

        val first = awaitCardWord(null)
        tapAnswer(if (articleOf(first) == "der") "die" else "der") // miss
        assertCounter("2 of 2")
        val second = awaitCardWord(first)
        tapAnswer(articleOf(second)) // correct at level 2 → graduates, unsaves
        compose.awaitTag(UiTags.QUIZ_END)
        compose.assertTextContainsViaSemantics(UiTags.QUIZ_END, "Graduated 1 · Missed 1")

        // The graduate left the saved queue; the missed row stayed at level 0.
        assertEquals(listOf(identityOf(first)), fakeSaved.saved.map { it.identity })
        assertEquals(LearningState(0, null), fakeLearning.statesMap[identityOf(first)])
        val graduated = fakeLearning.statesMap.getValue(identityOf(second))
        assertEquals(3, graduated.level)
        assertTrue(graduated.graduatedAt != null)

        compose.onNodeWithText("Back to list").performClick()
        compose.awaitRowCount(UiTags.SAVED_ROW, 1)
    }

    @Test
    fun emptyQueueOpensExplainingCard() {
        launch()
        openMenuAndStartQuiz(0)
        compose.awaitTag(UiTags.QUIZ_EMPTY)
    }

    @Test
    fun exitMidRunKeepsWritesAndShowsNoEndCard() {
        saveBank()
        saveZeit()
        launch()
        openMenuAndStartQuiz(2)

        val first = awaitCardWord(null)
        tapAnswer(articleOf(first))
        assertCounter("2 of 2")
        compose.onNodeWithTag(UiTags.QUIZ_EXIT).performClick()
        compose.awaitRowCount(UiTags.SAVED_ROW, 2)
        compose.pumpFrames()
        assertTrue(compose.onAllNodesWithTag(UiTags.QUIZ_END).fetchSemanticsNodes().isEmpty())
        assertEquals(LearningState(1, null), fakeLearning.statesMap[identityOf(first)])
    }

    /**
     * Review Focus 4: a row saved mid-run joins the NEXT session, never the active
     * one — the plan survives rotation at two items, and the fresh third run sees
     * all three.
     */
    @Test
    fun membershipFrozenDuringRunGrowsNextRun() {
        saveBank()
        saveZeit()
        launch()
        openMenuAndStartQuiz(2)

        val first = awaitCardWord(null)
        tapAnswer(articleOf(first))
        assertCounter("2 of 2")
        saveTeil() // save mid-run — straight to the store, no star UI exists here

        scenario.recreate()
        awaitCardWord(null)
        assertCounter("2 of 2")
        val second = awaitCardWord(first)
        tapAnswer(articleOf(second))
        compose.awaitTag(UiTags.QUIZ_END)
        compose.onNodeWithText("Back to list").performClick()

        compose.onNodeWithTag(UiTags.MENU).performClick()
        compose.awaitSemanticsText(UiTags.MENU_ITEM, "Start quiz · 3")
    }

    /**
     * Review Focus 1: process death and rotation mid-run continue the run — the
     * counter, the never-re-offered answered item, and the end tally across the
     * pre-rotation answer all survive.
     */
    @Test
    fun rotationMidRunContinuesTheRunAndKeepsTheTally() {
        saveBank()
        saveZeit()
        saveTeil()
        launch()
        openMenuAndStartQuiz(3)

        val first = awaitCardWord(null)
        tapAnswer(articleOf(first))
        assertCounter("2 of 3")

        scenario.recreate()
        assertCounter("2 of 3")
        val second = awaitCardWord(first)
        tapAnswer(if (articleOf(second) == "der") "die" else "der") // miss
        assertCounter("3 of 3")
        val third = awaitCardWord(second)
        assertTrue(third !in listOf(first, second))
        tapAnswer(articleOf(third))

        compose.awaitTag(UiTags.QUIZ_END)
        compose.assertTextContainsViaSemantics(UiTags.QUIZ_END, "Graduated 0 · Missed 1")
        assertEquals(LearningState(1, null), fakeLearning.statesMap[identityOf(first)])
        assertEquals(LearningState(0, null), fakeLearning.statesMap[identityOf(second)])
        assertEquals(LearningState(1, null), fakeLearning.statesMap[identityOf(third)])
    }
}

/** Wall time per clock slice in [QuizTest.awaitCardWord] — UiTestSupport keeps its copy private. */
private const val WALL_SLEEP_MS = 5L
