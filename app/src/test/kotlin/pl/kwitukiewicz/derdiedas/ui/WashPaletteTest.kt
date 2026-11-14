package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.kwitukiewicz.derdiedas.core.Gender
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pins the wash palette: exact design hexes, WCAG AA (>= 4.5:1) for the text that
 * sits on each wash in both schemes, and plural routing through core's washGenderKey.
 * Contrast is computed here from hex arithmetic only (no Compose rendering);
 * the sole Compose touch is reading WashPalette's Color back to an sRGB int.
 */
@RunWith(RobolectricTestRunner::class)
class WashPaletteTest {

    @Test
    fun washHexesArePinned() {
        assertEquals("EAF0FB", hex(WashPalette.wash(Gender.MASKULINUM, dark = false)))
        assertEquals("FBECEA", hex(WashPalette.wash(Gender.FEMININUM, dark = false)))
        assertEquals("E9F5EE", hex(WashPalette.wash(Gender.NEUTRUM, dark = false)))
        assertEquals("F1F2F4", hex(WashPalette.wash(Gender.UTRUM, dark = false)))
        assertEquals("17203A", hex(WashPalette.wash(Gender.MASKULINUM, dark = true)))
        assertEquals("2B1512", hex(WashPalette.wash(Gender.FEMININUM, dark = true)))
        assertEquals("10231A", hex(WashPalette.wash(Gender.NEUTRUM, dark = true)))
        assertEquals("1B1C1F", hex(WashPalette.wash(Gender.UTRUM, dark = true)))
    }

    @Test
    fun accentHexesArePinned() {
        assertEquals("2F6BD8", hex(WashPalette.accent(Gender.MASKULINUM)))
        assertEquals("D1493C", hex(WashPalette.accent(Gender.FEMININUM)))
        assertEquals("2E8B57", hex(WashPalette.accent(Gender.NEUTRUM)))
        assertEquals("919296", hex(WashPalette.accent(Gender.UTRUM)))
    }

    @Test
    fun onWashMeetsWcagAaOnItsWash() {
        val genders = listOf(
            Gender.MASKULINUM, // der
            Gender.FEMININUM,  // die
            Gender.NEUTRUM,    // das
            Gender.UTRUM,      // neutral
            Gender.PLURAL,     // routed to die
        )
        for (g in genders) {
            for (dark in listOf(false, true)) {
                val ratio = contrast(
                    hex(WashPalette.onWash(g, dark)),
                    hex(WashPalette.wash(g, dark)),
                )
                assertTrue(
                    "onWash($g, dark=$dark) contrast ${"%.2f".format(ratio)} < 4.5",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun pluralGendersDelegateThroughWashGenderKey() {
        for (dark in listOf(false, true)) {
            assertEquals(wash(Gender.FEMININUM, dark), wash(Gender.PLURAL, dark))
            assertEquals(wash(Gender.FEMININUM, dark), wash(Gender.F_PLURAL, dark))
            assertEquals(wash(Gender.MASKULINUM, dark), wash(Gender.M_PLURAL, dark))
            assertEquals(onWash(Gender.FEMININUM, dark), onWash(Gender.PLURAL, dark))
            assertEquals(onWash(Gender.FEMININUM, dark), onWash(Gender.F_PLURAL, dark))
            assertEquals(onWash(Gender.MASKULINUM, dark), onWash(Gender.M_PLURAL, dark))
        }
    }

    private fun wash(g: Gender, dark: Boolean) = hex(WashPalette.wash(g, dark))

    private fun onWash(g: Gender, dark: Boolean) = hex(WashPalette.onWash(g, dark))

    // --- WCAG 2.x relative-luminance/contrast from #RRGGBB hex, per the spec ---

    /** sRGB hex (RRGGBB, opaque alpha dropped) of an opaque palette color. */
    private fun hex(color: Color): String =
        "%06X".format(toRgb(color) and 0xFFFFFF)

    private fun toRgb(color: Color): Int = color.toArgb()

    private fun luminance(rgbHex: String): Double {
        fun linearize(channelHex: String): Double {
            val c = channelHex.toInt(16) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = linearize(rgbHex.substring(0, 2))
        val g = linearize(rgbHex.substring(2, 4))
        val b = linearize(rgbHex.substring(4, 6))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(hexA: String, hexB: String): Double {
        val la = luminance(hexA)
        val lb = luminance(hexB)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
