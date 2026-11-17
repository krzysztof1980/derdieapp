package pl.kwitukiewicz.handygerman.artikel.core

/**
 * The frozen on-disk format of `nouns.db`, declared once here: `:exporter` executes
 * [DDL] verbatim and stamps the [META_*] keys; `:app` creates the same DDL as a
 * version check before use. Changing anything here without bumping [VERSION]
 * breaks every shipped asset.
 */
object NounsSchema {

    /** Statements in creation order, copied verbatim from the plan's frozen schema block. */
    val DDL: List<String> = listOf(
        "CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);",
        """
        CREATE TABLE entries(entry_id INTEGER PRIMARY KEY, lemma TEXT NOT NULL,
            page_order INTEGER NOT NULL, kind TEXT NOT NULL, genders TEXT NOT NULL,
            plural_forms TEXT, meaning_lines TEXT NOT NULL DEFAULT '[]');
        """.trimIndent(),
        """
        CREATE TABLE forms(form TEXT NOT NULL, form_folded TEXT NOT NULL,
            entry_id INTEGER NOT NULL REFERENCES entries(entry_id),
            numerus TEXT NULL, kasus TEXT NULL, is_lemma INTEGER NOT NULL DEFAULT 0);
        """.trimIndent(),
        "CREATE INDEX forms_folded ON forms(form_folded);",
    )

    const val VERSION = "1"

    const val META_SCHEMA_VERSION = "schema_version"
    const val META_BUILT_AT = "built_at"
    const val META_BUILT_FROM = "built_from"
    const val META_FOLD_VECTORS_SHA256 = "fold_vectors_sha256"

    const val FOLD_VECTORS_RESOURCE = "fold_vectors.txt"
}
