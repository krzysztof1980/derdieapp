package pl.kwitukiewicz.handygerman.artikel.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.kwitukiewicz.handygerman.artikel.core.Gender.FEMININUM
import pl.kwitukiewicz.handygerman.artikel.core.Gender.MASKULINUM
import pl.kwitukiewicz.handygerman.artikel.core.Gender.M_PLURAL
import pl.kwitukiewicz.handygerman.artikel.core.Gender.NEUTRUM
import pl.kwitukiewicz.handygerman.artikel.core.Gender.PLURAL
import pl.kwitukiewicz.handygerman.artikel.core.Gender.UTRUM
import pl.kwitukiewicz.handygerman.artikel.core.Gender.F_PLURAL

class GenderDisplayTest {

    @Test
    fun `articles are joined in the given order`() {
        assertEquals("das/der", articlesText(listOf(NEUTRUM, MASKULINUM)))
    }

    @Test
    fun `every gender renders its article, plural pseudo-genders with their article gender`() {
        assertEquals("der/die/die/das/–/der/die",
            articlesText(listOf(MASKULINUM, FEMININUM, PLURAL, NEUTRUM, UTRUM, M_PLURAL, F_PLURAL)))
    }

    @Test
    fun `plural washes as feminine`() {
        assertEquals(FEMININUM, washGenderKey(listOf(PLURAL)))
    }

    @Test
    fun `m-plural washes as masculine`() {
        assertEquals(MASKULINUM, washGenderKey(listOf(M_PLURAL)))
    }

    @Test
    fun `f-plural washes as feminine`() {
        assertEquals(FEMININUM, washGenderKey(listOf(F_PLURAL)))
    }

    @Test
    fun `utrum stays utrum - the app maps it to neutral`() {
        assertEquals(UTRUM, washGenderKey(listOf(UTRUM)))
    }

    @Test
    fun `a plural-only noun is plural-only`() {
        assertTrue(isPluralOnly(listOf(PLURAL)))
    }

    @Test
    fun `a neutrum noun is not plural-only`() {
        assertFalse(isPluralOnly(listOf(NEUTRUM)))
    }
}
