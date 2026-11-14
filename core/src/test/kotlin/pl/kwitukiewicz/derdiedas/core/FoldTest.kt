package pl.kwitukiewicz.derdiedas.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class FoldTest {

    @ParameterizedTest(name = "foldForm({0}) == {1}")
    @MethodSource("vectors")
    fun foldFormMatchesOriginVector(input: String, expected: String) {
        assertEquals(expected, foldForm(input))
    }

    companion object {
        @JvmStatic
        fun vectors(): List<Array<String>> =
            FoldVectors.load().map { arrayOf(it.first, it.second) }
    }
}
