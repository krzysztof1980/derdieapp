package pl.kwitukiewicz.handygerman.artikel.ui

import androidx.compose.ui.graphics.Color
import pl.kwitukiewicz.handygerman.artikel.core.Gender
import pl.kwitukiewicz.handygerman.artikel.core.washGenderKey

/**
 * Gender wash colors from the design document's pinned palette. The plural
 * pseudo-genders route to their article's bucket via core's [washGenderKey],
 * so every color is defined once per visual group (der / die / das / neutral).
 * The light family was re-saturated on 2026-11-15 (user-approved tuning, spec
 * amendment #2): clearly colored next to white, still light enough for the dark
 * ink — the exact hexes are pinned by WashPaletteTest.
 * [onWash] is the readable text color on the matching wash — WCAG AA >= 4.5:1,
 * pinned by WashPaletteTest.
 *
 * Accents are side-aware and contrast-locked: one opaque accent per group would
 * need to clear 4.5:1 against both `#BFD7F7` and `#17203A`, and no luminance
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
     * read the system dark mode; HandyGermanTheme branches its Material 3 scheme on
     * this same pin, so washes and chrome can never disagree. Flipping this to true
     * (or, for a real two-appearance mode, making it system-dependent *here and only
     * here*) is the one switch that moves the whole app to the dark family.
     */
    val appLooksDark = false

    // washes: light / dark — light family re-saturated 2026-11-15 (spec amendment #2)
    private val DER_WASH_LIGHT = Color(0xFFBFD7F7)
    private val DER_WASH_DARK = Color(0xFF17203A)
    private val DIE_WASH_LIGHT = Color(0xFFF6C9C1)
    private val DIE_WASH_DARK = Color(0xFF2B1512)
    private val DAS_WASH_LIGHT = Color(0xFFC3E8D3)
    private val DAS_WASH_DARK = Color(0xFF10231A)
    private val NEUTRAL_WASH_LIGHT = Color(0xFFE2E4E8)
    private val NEUTRAL_WASH_DARK = Color(0xFF1B1C1F)

    // accents on washes: same hues as the design palette, darkened (light side) or
    // lightened (dark side) until they clear 4.5:1 on every wash — measured >= 5.6:1;
    // the light four were darkened again (hue held, HLS lightness dropped) to keep
    // that floor when the light wash family was re-saturated on 2026-11-15
    private val DER_ACCENT_LIGHT = Color(0xFF1C4997)
    private val DIE_ACCENT_LIGHT = Color(0xFF8C2B21)
    private val DAS_ACCENT_LIGHT = Color(0xFF1C5636)
    private val NEUTRAL_ACCENT_LIGHT = Color(0xFF4B4B4E)
    private val DER_ACCENT_DARK = Color(0xFF6F99E4)
    private val DIE_ACCENT_DARK = Color(0xFFDE7E75)
    private val DAS_ACCENT_DARK = Color(0xFF39AD6C)
    private val NEUTRAL_ACCENT_DARK = Color(0xFF98989C)

    // de-emphasized text on washes (unselected toggle segments): neutral grays just
    // above the AA floor on every wash of their side — secondary to the ink and the
    // chromatic selected accent, yet still >= 4.5:1 (light side re-darkened 2026-11-15
    // with the saturated washes; measured ~4.6:1 minimum again)
    private val ON_WASH_MUTED_LIGHT = Color(0xFF5A5A5F)
    private val ON_WASH_MUTED_DARK = Color(0xFF87888D)

    // text on wash: dark ink on light washes, near-white on dark washes
    private val ON_WASH_LIGHT = Color(0xFF1B1C1F)
    private val ON_WASH_DARK = Color(0xFFF1F2F4)

    // query-field outline: UI chrome, not gender-coded — deliberately outside the
    // contrast-locked gender system. The query field is an outlined box the wash
    // shows through, so its discoverability rides on the outline: the resting gray
    // measures >= 3:1 and the focused darkening >= 1.75:1 against every wash of both
    // families (the pin floor is 1.5, enforced by WashPaletteTest).
    private val FIELD_OUTLINE = Color(0xFF79747E)
    private val FIELD_OUTLINE_FOCUSED = Color(0xFF48484B)

    /**
     * Resting outline of the outlined query field — UI chrome, never a wash, never
     * gender-coded. Single-appearance values like the washes they sit on.
     */
    fun fieldOutline(): Color = FIELD_OUTLINE

    /** The focused outline: the resting gray darkened, same chrome role. */
    fun fieldOutlineFocused(): Color = FIELD_OUTLINE_FOCUSED

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
