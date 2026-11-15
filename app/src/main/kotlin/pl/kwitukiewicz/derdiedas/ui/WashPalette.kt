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
 *
 * Accents are side-aware and contrast-locked: one opaque accent per group would
 * need to clear 4.5:1 against both `#EAF0FB` and `#17203A`, and no luminance
 * satisfies both — so the side variants keep the group hue (HLS lightness tuned)
 * and clear >= 5.6:1 against every wash of their side, leaving [onWashMuted]
 * (~4.6:1) room to read de-emphasized while still passing AA. Hierarchy on the
 * segmented article row is muted gray + normal weight versus chromatic accent + bold.
 */
object WashPalette {

    /**
     * The app's single appearance decision (user decision 2026-11-15): the gender
     * washes are the light pastel family in every system mode, so a phone in system
     * dark mode shows exactly the pixels a phone in light mode shows. Nothing may
     * read the system dark mode; DerdiedasTheme branches its Material 3 scheme on
     * this same pin, so washes and chrome can never disagree. Flipping this to true
     * (or, for a real two-appearance mode, making it system-dependent *here and only
     * here*) is the one switch that moves the whole app to the dark family.
     */
    val appLooksDark = false

    // washes: light / dark
    private val DER_WASH_LIGHT = Color(0xFFEAF0FB)
    private val DER_WASH_DARK = Color(0xFF17203A)
    private val DIE_WASH_LIGHT = Color(0xFFFBECEA)
    private val DIE_WASH_DARK = Color(0xFF2B1512)
    private val DAS_WASH_LIGHT = Color(0xFFE9F5EE)
    private val DAS_WASH_DARK = Color(0xFF10231A)
    private val NEUTRAL_WASH_LIGHT = Color(0xFFF1F2F4)
    private val NEUTRAL_WASH_DARK = Color(0xFF1B1C1F)

    // accents on washes: same hues as the design palette, darkened (light side) or
    // lightened (dark side) until they clear 4.5:1 on every wash — measured >= 5.6:1
    private val DER_ACCENT_LIGHT = Color(0xFF235ABC)
    private val DIE_ACCENT_LIGHT = Color(0xFFAB3428)
    private val DAS_ACCENT_LIGHT = Color(0xFF236B43)
    private val NEUTRAL_ACCENT_LIGHT = Color(0xFF5E5E62)
    private val DER_ACCENT_DARK = Color(0xFF6F99E4)
    private val DIE_ACCENT_DARK = Color(0xFFDE7E75)
    private val DAS_ACCENT_DARK = Color(0xFF39AD6C)
    private val NEUTRAL_ACCENT_DARK = Color(0xFF98989C)

    // de-emphasized text on washes (unselected toggle segments): neutral grays just
    // above the AA floor on every wash of their side — secondary to the ink and the
    // chromatic selected accent, yet still >= 4.5:1
    private val ON_WASH_MUTED_LIGHT = Color(0xFF6B6C71)
    private val ON_WASH_MUTED_DARK = Color(0xFF87888D)

    // text on wash: dark ink on light washes, near-white on dark washes
    private val ON_WASH_LIGHT = Color(0xFF1B1C1F)
    private val ON_WASH_DARK = Color(0xFFF1F2F4)

    // query-field container: UI chrome, not gender-coded — deliberately outside the
    // contrast-locked gender system. Pure opaque white: the filled field then reads
    // as a raised box on every pastel wash it can sit on (>= 1.1:1 against all four,
    // pinned by WashPaletteTest), while the wash still tints the screen around it.
    private val FIELD_CONTAINER = Color(0xFFFFFFFF)

    /**
     * The filled query field's opaque container — UI chrome only, never a wash, and
     * never gender-coded. Single value by construction: in the single-appearance app
     * it only ever sits on the light pastel family.
     */
    fun fieldContainer(): Color = FIELD_CONTAINER

    fun wash(g: Gender, dark: Boolean): Color =
        when (washGenderKey(listOf(g))) {
            Gender.MASKULINUM -> if (dark) DER_WASH_DARK else DER_WASH_LIGHT
            Gender.FEMININUM -> if (dark) DIE_WASH_DARK else DIE_WASH_LIGHT
            Gender.NEUTRUM -> if (dark) DAS_WASH_DARK else DAS_WASH_LIGHT
            else -> if (dark) NEUTRAL_WASH_DARK else NEUTRAL_WASH_LIGHT // UTRUM
        }

    fun accent(g: Gender, dark: Boolean): Color =
        when (washGenderKey(listOf(g))) {
            Gender.MASKULINUM -> if (dark) DER_ACCENT_DARK else DER_ACCENT_LIGHT
            Gender.FEMININUM -> if (dark) DIE_ACCENT_DARK else DIE_ACCENT_LIGHT
            Gender.NEUTRUM -> if (dark) DAS_ACCENT_DARK else DAS_ACCENT_LIGHT
            else -> if (dark) NEUTRAL_ACCENT_DARK else NEUTRAL_ACCENT_LIGHT // UTRUM
        }

    /** Dropped-out text on a wash — AA-compliant on every wash, clearly secondary to [accent]. */
    fun onWashMuted(dark: Boolean): Color = if (dark) ON_WASH_MUTED_DARK else ON_WASH_MUTED_LIGHT

    fun onWash(g: Gender, dark: Boolean): Color =
        if (dark) ON_WASH_DARK else ON_WASH_LIGHT
}
