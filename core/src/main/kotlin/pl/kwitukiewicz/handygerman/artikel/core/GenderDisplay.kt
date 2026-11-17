package pl.kwitukiewicz.handygerman.artikel.core

/** Wiktionary `genus` values plus the plural pseudo-genders the exporter emits. */
enum class Gender { MASKULINUM, FEMININUM, NEUTRUM, UTRUM, PLURAL, M_PLURAL, F_PLURAL }

enum class Kind { WORD, PLACE, PERSON_NAME }

private val PLURAL_PSEUDO_GENDERS = setOf(Gender.PLURAL, Gender.M_PLURAL, Gender.F_PLURAL)

/** In-order `/`-joined articles, e.g. `das/der`. UTRUM renders an en dash. */
fun articlesText(genders: List<Gender>): String =
    genders.joinToString("/") { article(it) }

private fun article(gender: Gender): String = when (gender) {
    Gender.MASKULINUM, Gender.M_PLURAL -> "der"
    Gender.FEMININUM, Gender.F_PLURAL, Gender.PLURAL -> "die"
    Gender.NEUTRUM -> "das"
    Gender.UTRUM -> "–"
}

/** The gender whose wash color the app shows: the first one, plural keys washed to their article gender. */
fun washGenderKey(genders: List<Gender>): Gender =
    when (val first = genders.first()) {
        Gender.PLURAL, Gender.F_PLURAL -> Gender.FEMININUM
        Gender.M_PLURAL -> Gender.MASKULINUM
        else -> first
    }

/** True when the noun is attested in the plural only (every entry a plural pseudo-gender). */
fun isPluralOnly(genders: List<Gender>): Boolean =
    genders.isNotEmpty() && genders.all { it in PLURAL_PSEUDO_GENDERS }
