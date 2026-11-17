package pl.kwitukiewicz.handygerman.exporter

import pl.kwitukiewicz.handygerman.core.NounsSchema
import wiktiopeggynary.model.WiktionaryEntrySerializer
import wiktiopeggynary.model.substantiv.Substantiv
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

private val log = org.slf4j.LoggerFactory.getLogger("pl.kwitukiewicz.handygerman.exporter.ExportMain")

/**
 * Turns scrolled index documents into the two DB tables.
 *
 * ES delivers in arbitrary order, so the docs are first sorted by their `_id` string —
 * fixed-width zero-padded ids make that the page order — and `pageOrder` is the rank of
 * each DOC in that order (each document carries exactly one entry, so no grouping by id
 * prefix is needed). A document whose entry has no genders contributes neither an
 * [EntryRow] nor any [FormRow], and grants no id: form entry ids stay exactly the
 * 1-based positions of [EntryRow] in the returned list, which is the identity
 * [writeDb] inserts under.
 */
fun reduce(docs: List<Pair<String, String>>): Pair<List<EntryRow>, List<FormRow>> {
    val serializer = WiktionaryEntrySerializer()
    val entries = ArrayList<EntryRow>(docs.size)
    val forms = ArrayList<FormRow>(docs.size * 3)
    var nonNoun = 0
    var noGender = 0
    docs.sortedBy { (id, _) -> id }.forEachIndexed { rank, (id, json) ->
        val entry = try {
            serializer.deserializeWiktionaryEntry(json, Substantiv::class.java)
        } catch (badDoc: Exception) {
            throw IllegalStateException("undecodable _source for doc '$id'", badDoc)
        }
        if (entry !is Substantiv) {
            log.debug("doc {} is {}, not a Substantiv", id, entry?.javaClass?.simpleName)
            nonNoun++
            return@forEachIndexed
        }
        entryRow(entry, rank)?.let { row ->
            val entryId = entries.size + 1L
            entries += row
            forms += formRows(entry, entryId)
        } ?: run {
            log.debug("doc {} ({}) has no gender", id, entry.lemma)
            noGender++
        }
    }
    log.info(
        "reduced {} docs: entries={} nonNoun={} noGender={}",
        docs.size, entries.size, nonNoun, noGender,
    )
    return entries to forms
}

/** `--out <dir> [--dump <name>] [--es-user user:pass]`: scrollAll → reduce → writeDb. */
fun main(args: Array<String>) {
    var out = "appdb"
    var dump = ES_INDEX
    var esUser = "elastic:changeme"
    var i = 0
    while (i < args.size) {
        when (val option = args[i]) {
            "--out" -> { out = optionValue(args, ++i) }
            "--dump" -> { dump = optionValue(args, ++i) }
            "--es-user" -> { esUser = optionValue(args, ++i) }
            else -> usage()
        }
        i++
    }

    val docs = ArrayList<Pair<String, String>>(170_000)
    val scrollMillis = measureTimeMillis { scrollAll("localhost", esUser) { id, json -> docs += id to json } }
    log.info("scrolled {} docs in {} s", docs.size, scrollMillis / 1_000)

    val (entries, forms) = reduce(docs)
    if (entries.isEmpty()) {
        System.err.println("entries=0 — nothing exported")
        exitProcess(1)
    }

    val outDir = Path.of(out)
    val db = outDir.resolve("nouns.db")
    // Vectors BEFORE the db: a crashed rerun-to-success job may leave the next batch's
    // fold_vectors.txt fresh but its nouns.db unpublished — never the reverse, where a
    // published db would sit beside an absent or older stamp source.
    copyFoldVectorsBeside(db)
    writeDb(entries, forms, db, dump)

    println("entries=${entries.size} forms=${forms.size} size=${Files.size(db) / 1024} KiB")
}

/**
 * Publishes the fold-vectors copy beside the db. Nothing in any app variant verifies
 * it at runtime: the `fold_vectors_sha256` meta stamp is kept for offline forensics —
 * proving a shipped asset and a shipped jar were folded from the same bytes. Real fold
 * drift is caught in debug builds instead, where the app scans the shipped `forms`
 * corpus against its own fold function (FoldSelfCheck) and refuses to hide a mismatch.
 */
private fun copyFoldVectorsBeside(db: Path) {
    Files.createDirectories(db.parent) // runs before writeDb now, so the out dir may not exist yet
    val bytes = NounsSchema::class.java.classLoader
        .getResourceAsStream(NounsSchema.FOLD_VECTORS_RESOURCE)
        ?.readBytes()
        ?: error("missing classpath resource ${NounsSchema.FOLD_VECTORS_RESOURCE}")
    Files.write(db.resolveSibling(NounsSchema.FOLD_VECTORS_RESOURCE), bytes)
}

/** The value following an option; a dangling trailing option fails like an unknown one. */
private fun optionValue(args: Array<String>, index: Int): String = args.getOrNull(index) ?: usage()

private fun usage(): Nothing {
    System.err.println("usage: ExportMain [--out <dir>] [--dump <name>] [--es-user <user:pass>]")
    exitProcess(2)
}

