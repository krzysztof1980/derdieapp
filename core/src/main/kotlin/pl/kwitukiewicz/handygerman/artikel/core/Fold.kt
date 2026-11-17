package pl.kwitukiewicz.handygerman.artikel.core

import java.text.Normalizer
import java.util.Locale

/**
 * Canonical folding of a German word form to its DB key (`forms.form_folded`).
 * NFC first so decomposed input (base + U+0308) composes into the umlauts the
 * replacements below match; lowercase before the ß pass so `ẞ` folds via `ß` to `ss`.
 */
fun foldForm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")

/** The origin vectors pinning every [foldForm] rule branch, shared by exporter and tests. */
object FoldVectors {

    private const val RESOURCE = "/fold_vectors.txt"

    /** Parses `core/src/main/resources/fold_vectors.txt` from the classpath. */
    fun load(): List<Pair<String, String>> =
        FoldVectors::class.java.getResourceAsStream(RESOURCE)
            ?.reader(Charsets.UTF_8)
            ?.useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { line ->
                        val parts = line.split('\t', limit = 2)
                        require(parts.size == 2) { "vector line without TAB separator: $line" }
                        parts[0] to parts[1]
                    }
                    .toList()
            }
            ?: error("missing classpath resource $RESOURCE")
}
