package pl.kwitukiewicz.handygerman.artikel.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import pl.kwitukiewicz.handygerman.artikel.core.FakeWordRepository.Row
import pl.kwitukiewicz.handygerman.artikel.core.Gender.FEMININUM
import pl.kwitukiewicz.handygerman.artikel.core.Gender.MASKULINUM
import pl.kwitukiewicz.handygerman.artikel.core.Gender.NEUTRUM
import pl.kwitukiewicz.handygerman.artikel.core.Gender.PLURAL
import pl.kwitukiewicz.handygerman.artikel.core.Kind.WORD

/**
 * The saved-words identity seam: (folded lemma, article key, sense number) over the
 * pure [WordRepository] view — the same triple the `saved_words.db` table keys on.
 */
class SavedTest {

    // Corpus: two same-article homographs (die Bank ×2) and one split-article pair
    // (das/der Teil). `banken` exists on the SECOND Bank entry only — the declined-
    // subset trap that sense numbers must survive.
    private val bank1 = Entry(1, "Bank", WORD, listOf(FEMININUM), "Bänke", listOf("Sitz- oder Ablagegelegenheit"))
    private val bank2 = Entry(2, "Bank", WORD, listOf(FEMININUM), "Banken", listOf("[Finanzwesen]: Geldinstitut"))
    private val teilNeutrum = Entry(3, "Teil", WORD, listOf(NEUTRUM), "Teile", listOf("Stück; Teilmenge"))
    private val teilMaskulinum = Entry(4, "Teil", WORD, listOf(MASKULINUM), "Teile", listOf("Element des Ganzen"))
    private val entries = listOf(bank1, bank2, teilNeutrum, teilMaskulinum)
    private val rows = listOf(
        Row("bank", 1, "Singular", "Nominativ", isLemma = true),
        Row("bank", 2, "Singular", "Nominativ", isLemma = true),
        Row("banken", 2, "Plural", "Nominativ", isLemma = false),
        Row("teil", 3, "Singular", "Nominativ", isLemma = true),
        Row("teil", 4, "Singular", "Nominativ", isLemma = true),
    )
    private val repo = FakeWordRepository(entries, rows)

    /** Counts [exactForm] calls per folded query, to pin the one-per-lemma batching. */
    private class CountingRepo(private val delegate: FakeWordRepository) : WordRepository by delegate {
        val exactFormCalls = mutableListOf<String>()
        override fun exactForm(folded: String): List<FormHit> {
            exactFormCalls += folded
            return delegate.exactForm(folded)
        }
    }

    @Test
    fun articleKeySortsAndDedupsRegardlessOfSourceOrder() {
        // Gender declares MASKULINUM before FEMININUM — ordinal order, not alphabetical.
        assertEquals("MASKULINUM,FEMININUM", articleKey(listOf(FEMININUM, MASKULINUM)))
        assertEquals(articleKey(listOf(FEMININUM, MASKULINUM)), articleKey(listOf(MASKULINUM, FEMININUM)))
        assertEquals("MASKULINUM,FEMININUM", articleKey(listOf(MASKULINUM, FEMININUM, MASKULINUM)))
        assertEquals("PLURAL", articleKey(listOf(PLURAL)))
    }

    @Test
    fun senseNumbersOfHomonymGroups() {
        assertEquals(1, senseNo(entries, bank1))
        assertEquals(2, senseNo(entries, bank2))
        assertEquals(1, senseNo(entries, teilNeutrum))
        assertEquals(1, senseNo(entries, teilMaskulinum))
    }

    @Test
    fun senseNoFallsBackToOneForEntryAbsentFromGroup() {
        // Defensive path (dictionary data-inconsistency only): an entry missing from
        // its own lemma group still gets a valid 1-based sense — never 0, which no
        // resolver or tap-target matcher would ever hit again.
        assertEquals(1, senseNo(entries.take(1), bank2))
        assertEquals(1, senseNo(emptyList(), bank2))
    }

    @Test
    fun identityResolvesOverFullLemmaNotQueryHits() {
        val identity = resolveWordIdentity(repo, bank2)
        assertEquals(WordIdentity("bank", "FEMININUM", 2), identity)
        // Contrast: ranking inside the declined-form subset alone would say 1.
        val subset = repo.exactForm(foldForm("banken")).map { it.entry }
        assertEquals(1, senseNo(subset, bank2))
    }

    @Test
    fun identitiesOfEngineGroupsByLemma() {
        val counting = CountingRepo(repo)
        val engine = LookupEngine(counting)
        val identities = engine.identitiesOf(listOf(bank1, bank2, teilNeutrum))
        assertEquals(setOf(1L, 2L, 3L), identities.keys)
        assertEquals(WordIdentity("bank", "FEMININUM", 1), identities[1L])
        assertEquals(WordIdentity("bank", "FEMININUM", 2), identities[2L])
        assertEquals(WordIdentity("teil", "NEUTRUM", 1), identities[3L])
        assertEquals(listOf("bank", "teil"), counting.exactFormCalls)
    }

    @Test
    fun resolveSavedPicksEntryBySenseAndFallsBackToSnapshot() {
        val counting = CountingRepo(repo)
        val senseTwo = SavedWord("Bank", WordIdentity("bank", "FEMININUM", 2), "[Finanzwesen]: Geldinstitut", 1L)
        val vanished = SavedWord("Bank", WordIdentity("bank", "FEMININUM", 5), "kommentloser Fund", 2L)

        val resolved = resolveSaved(counting, listOf(senseTwo, vanished))

        assertEquals(2, resolved.size)
        assertSame(bank2, resolved[0].entry)
        assertSame(senseTwo, resolved[0].word)
        assertSame(vanished, resolved[1].word)
        assertNull(resolved[1].entry, "sense beyond the live group keeps the snapshot")
        assertEquals(listOf("bank"), counting.exactFormCalls)
    }
}
