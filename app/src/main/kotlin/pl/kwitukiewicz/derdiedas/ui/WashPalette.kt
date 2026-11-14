package pl.kwitukiewicz.derdiedas.ui

import androidx.compose.ui.graphics.Color
import pl.kwitukiewicz.derdiedas.core.Gender
import pl.kwitukiewicz.derdiedas.core.washGenderKey

/**
 * Gender wash colors from the design document's pinned palette. The plural
 * pseudo-genders route to their article's bucket via core's [washGenderKey],
 * so every color is defined once per visual group (der / die / das / neutral).
 * [onWash] is the readable text color on the matching wash — WCAG AA >= 4.5:1,
 * pinned by WashPaletteTest.
 */
object WashPalette {

    // washes: light / dark
    private val DER_WASH_LIGHT = Color(0xFFEAF0FB)
    private val DER_WASH_DARK = Color(0xFF17203A)
    private val DIE_WASH_LIGHT = Color(0xFFFBECEA)
    private val DIE_WASH_DARK = Color(0xFF2B1512)
    private val DAS_WASH_LIGHT = Color(0xFFE9F5EE)
    private val DAS_WASH_DARK = Color(0xFF10231A)
    private val NEUTRAL_WASH_LIGHT = Color(0xFFF1F2F4)
    private val NEUTRAL_WASH_DARK = Color(0xFF1B1C1F)

    // accents
    private val DER_ACCENT = Color(0xFF2F6BD8)
    private val DIE_ACCENT = Color(0xFFD1493C)
    private val DAS_ACCENT = Color(0xFF2E8B57)
    private val NEUTRAL_ACCENT = Color(0xFF919296)

    // text on wash: dark ink on light washes, near-white on dark washes
    private val ON_WASH_LIGHT = Color(0xFF1B1C1F)
    private val ON_WASH_DARK = Color(0xFFF1F2F4)

    fun wash(g: Gender, dark: Boolean): Color =
        when (washGenderKey(listOf(g))) {
            Gender.MASKULINUM -> if (dark) DER_WASH_DARK else DER_WASH_LIGHT
            Gender.FEMININUM -> if (dark) DIE_WASH_DARK else DIE_WASH_LIGHT
            Gender.NEUTRUM -> if (dark) DAS_WASH_DARK else DAS_WASH_LIGHT
            else -> if (dark) NEUTRAL_WASH_DARK else NEUTRAL_WASH_LIGHT // UTRUM
        }

    fun accent(g: Gender): Color =
        when (washGenderKey(listOf(g))) {
            Gender.MASKULINUM -> DER_ACCENT
            Gender.FEMININUM -> DIE_ACCENT
            Gender.NEUTRUM -> DAS_ACCENT
            else -> NEUTRAL_ACCENT // UTRUM
        }

    fun onWash(g: Gender, dark: Boolean): Color =
        if (dark) ON_WASH_DARK else ON_WASH_LIGHT
}
