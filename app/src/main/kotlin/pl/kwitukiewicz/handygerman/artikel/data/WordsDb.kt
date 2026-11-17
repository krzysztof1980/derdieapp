package pl.kwitukiewicz.handygerman.artikel.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import pl.kwitukiewicz.handygerman.artikel.BuildConfig
import pl.kwitukiewicz.handygerman.core.NounsSchema
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Makes the shipped dictionary available and opens it read-only.
 *
 * Seeding: the asset is the exporter's `nouns.db` gzipped ([ASSET_NAME]); it is copied —
 * gunzipping on the fly — into the databases dir when absent, or when the asset's
 * `built_at` differs from the stamp kept in SharedPreferences. [onCopy] observes every
 * completed copy (the injectable copy counter of the tests).
 *
 * Why `.gzip` and not `.gz`: AGP ≥9 inflates assets ending in `.gz` at merge time — the
 * APK would carry the raw 93 MiB database (re-deflated to ~31 MiB, over budget) and hand
 * out a *plain* stream under the name `nouns.db`. `nouns.db.gzip` packs byte-identically,
 * so the APK grows by the gzip size and this class does the inflating itself.
 *
 * To know the asset's `built_at` without inflating the whole ~93 MiB, [assetBuiltAt]
 * inflates only the head of the stream: `meta` is the first table in [NounsSchema.DDL],
 * so its pages — and the `built_at` record — sit at the very start of the file. A scan
 * that cannot produce a stamp counts as *changed*: one superfluous copy is safe, a stale
 * dictionary is not.
 *
 * [open] additionally refuses foreign schema versions and, in debug builds, runs
 * [verifyFoldConsistency] over the database it returns.
 */
class WordsDb(
    context: Context,
    private val openAsset: (String) -> InputStream = context.assets::open,
    private val assetName: String = ASSET_NAME,
    private val onCopy: () -> Unit = {},
) {
    private val appContext: Context = context.applicationContext

    fun open(): SQLiteDatabase {
        seedIfNeeded()
        val db = SQLiteDatabase.openDatabase(databaseFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            checkSchemaVersion(db)
            if (BuildConfig.DEBUG) verifyFoldConsistency(db)
        } catch (failure: Throwable) {
            db.close() // the caller never learns of the handle on the throwing paths
            throw failure
        }
        return db
    }

    private fun seedIfNeeded() {
        val target = databaseFile()
        target.parentFile?.let(::sweepStrandedCopies)
        val assetStamp = assetBuiltAt()
        val stamped = prefs().getString(KEY_BUILT_AT, null)
        if (target.exists() && assetStamp != null && assetStamp == stamped) return

        copyAssetTo(target)
        prefs().edit().putString(KEY_BUILT_AT, builtAtOf(target)).apply()
    }

    /**
     * Removes seed copies orphaned by a process killed mid-copy — a SIGKILL during the
     * ~93 MiB gunzip leaves `nouns.db.<random>.tmp` siblings nothing ever revisits, and
     * each killed attempt strands another. Deletion races are fine: whoever is writing a
     * sibling today renames over us tomorrow.
     */
    private fun sweepStrandedCopies(dir: File) {
        dir.listFiles { file -> file.name.startsWith("$DB_NAME.") && file.name.endsWith(".tmp") }
            ?.forEach { stranded -> runCatching(stranded::delete) }
    }

    private fun copyAssetTo(target: File) {
        val dir = target.parentFile ?: error("no databases dir on this device")
        dir.mkdirs()
        val tmp = File.createTempFile("${target.name}.", ".tmp", dir)
        try {
            FileOutputStream(tmp).use { disk ->
                val buffered = BufferedOutputStream(disk)
                inflatingIfGzipped(openAsset(assetName)).use { input -> input.copyTo(buffered) }
                // copyTo neither flushes nor closes its sink: without this, a size not
                // dividing the 8 KiB buffer loses its tail — a silently truncated db that
                // still stamps fine (meta lives on the first pages) and then serves forever.
                buffered.flush()
                disk.fd.sync() // data must reach the medium before the name appears
            }
            check(tmp.renameTo(target)) { "cannot move the freshly copied $assetName into place" }
        } catch (failure: Throwable) {
            tmp.delete()
            throw failure
        }
        onCopy()
    }

    /** Inflates only until the head scan concludes; null on any doubt — callers treat that as *changed*. */
    private fun assetBuiltAt(): String? =
        runCatching {
            inflatingIfGzipped(openAsset(assetName)).use { stream ->
                scanBuiltAt(stream.readNBytes(HEAD_SCAN_LIMIT))
            }
        }.getOrNull()

    private fun inflatingIfGzipped(raw: InputStream): InputStream =
        if (assetName.endsWith(".gz") || assetName.endsWith(".gzip")) GZIPInputStream(raw.buffered()) else raw

    /** Re-reads the stamp from the freshly copied db — the value that goes into prefs. */
    private fun builtAtOf(dbFile: File): String? =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.firstMetaValue(NounsSchema.META_BUILT_AT)
        }

    private fun checkSchemaVersion(db: SQLiteDatabase) {
        val found = db.firstMetaValue(NounsSchema.META_SCHEMA_VERSION)
        check(found == NounsSchema.VERSION) {
            "nouns.db ${NounsSchema.META_SCHEMA_VERSION} is '${found}', app knows '${NounsSchema.VERSION}' — ship a rebuilt asset"
        }
    }

    private fun databaseFile(): File = appContext.getDatabasePath(DB_NAME)

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val DB_NAME = "nouns.db"

        /** Suffix deliberately not `.gz` — see the class docs on AGP's inflation. */
        const val ASSET_NAME = "nouns.db.gzip"
        private const val PREFS = "words_db"
        private const val KEY_BUILT_AT = NounsSchema.META_BUILT_AT

        /** `meta` pages live on the first pages of the file; 64 KiB covers page 16. */
        private const val HEAD_SCAN_LIMIT = 64 * 1024

        /**
         * Extracts `built_at` from the raw head of a database file. SQLite keeps each
         * record as `[header: size, serial per column][key bytes][value bytes]`, so the
         * serial varint ending right before the `built_at` key carries the value's length
         * (text serial = 2·len + 13, single byte below). Requires the serial to sit at
         * [at] - 1 as one odd byte in 13..240; anything else yields null.
         */
        internal fun scanBuiltAt(head: ByteArray): String? {
            val marker = "built_at".toByteArray(Charsets.UTF_8)
            val at = indexOf(head, marker, from = 1)
            if (at < 0) return null
            val serial = head[at - 1].toInt() and 0xFF
            if (serial !in 13..240 || serial % 2 == 0) return null
            val length = (serial - 13) / 2
            if (at + marker.size + length > head.size) return null
            return String(head, at + marker.size, length, Charsets.UTF_8)
        }

        private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
            var i = from.coerceAtLeast(0)
            while (i <= haystack.size - needle.size) {
                var j = 0
                while (j < needle.size && haystack[i + j] == needle[j]) j++
                if (j == needle.size) return i
                i++
            }
            return -1
        }
    }
}

private fun SQLiteDatabase.firstMetaValue(key: String): String? =
    rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { cursor ->
        if (cursor.moveToNext()) cursor.getString(0) else null
    }
