package pl.kwitukiewicz.handygerman.artikel.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class FoldTest {

    @ParameterizedTest(name = "foldForm({0}) == {1}")
    @MethodSource("vectors")
    fun foldFormMatchesOriginVector(input: String, expected: String) {
        assertEquals(expected, foldForm(input))
    }

    /**
     * Pinned query↔candidate convergence: the umlautless query `Mueller` and the umlauted
     * candidate `Müller` must meet at the one DB key `mueller` — the device-smoke `Mueller`
     * row, pinned here rather than in `fold_vectors.txt`, whose bytes are frozen because
     * their sha256 stamp is baked into the shipped asset (adding a line there orphans it).
     */
    @Test
    fun umlautlessQueryAndUmlautCandidateMeetAtOneKey() {
        assertEquals("mueller", foldForm("Mueller"))
        assertEquals("mueller", foldForm("Müller"))
    }

    companion object {
        @JvmStatic
        fun vectors(): List<Array<String>> =
            FoldVectors.load().map { arrayOf(it.first, it.second) }
    }
}
