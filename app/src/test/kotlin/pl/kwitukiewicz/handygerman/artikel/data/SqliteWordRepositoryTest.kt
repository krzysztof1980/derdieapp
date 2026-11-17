package pl.kwitukiewicz.handygerman.artikel.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import pl.kwitukiewicz.handygerman.artikel.core.FakeWordRepository
import pl.kwitukiewicz.handygerman.artikel.core.Gender
import pl.kwitukiewicz.handygerman.artikel.core.Kind
import pl.kwitukiewicz.handygerman.artikel.core.NounsSchema
import pl.kwitukiewicz.handygerman.artikel.core.foldForm
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.GZIPOutputStream

/**
 * Pins the data layer against [TestNounsDb] fixtures: the Task 5 brief's behaviors —
 * both-Boots exact hit, böte singularity, zeit prefix order, meta stamps, schema-version
 * rejection, re-copy semantics on `built_at` change, and the fold self-check naming the
 * poison triple — plus LIKE-escaping, LIMIT, and fail-fast on unknown genders.
 */
@RunWith(RobolectricTestRunner::class)
class SqliteWordRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Opens the JDBC-built fixture through the production reader stack, closed after [block]. */
    private fun withRepo(fixture: Path, block: (SqliteWordRepository) -> Unit): Unit =
        SQLiteDatabase.openDatabase(fixture.toString(), null, SQLiteDatabase.OPEN_READONLY)
            .use { block(SqliteWordRepository(it)) }

    private fun wordsDbOpening(fixture: Path, onCopy: () -> Unit): WordsDb =
        WordsDb(context, openAsset = { FileInputStream(fixture.toFile()) }, assetName = "nouns.db", onCopy = onCopy)

    // --- exactForm ---

    @Test
    fun exactFormReturnsBothBootsInPageOrder() = withRepo(TestNounsDb.create(tmp.root.toPath())) { repo ->
        val hits = repo.exactForm(foldForm("booten"))

        assertEquals(listOf(1L, 2L), hits.map { it.entry.entryId })
        assertEquals(listOf("Boot", "Boot"), hits.map { it.entry.lemma })
        assertEquals(
            listOf(listOf(Gender.NEUTRUM), listOf(Gender.MASKULINUM)),
            hits.map { it.entry.genders },
        )
        assertEquals(listOf("booten", "booten"), hits.map { it.form })
        assertEquals(
            listOf("Plural" to "Dativ", "Singular" to "Genitiv"),
            hits.map { it.numerus to it.kasus },
        )
        assertEquals(listOf(false, false), hits.map { it.isLemma })
        assertEquals("Boote", hits[0].entry.pluralForms)
        assertEquals(listOf("(das) Boot, ein Wasserfahrzeug"), hits[0].entry.meaningLines)
        assertEquals(Kind.WORD, hits[1].entry.kind)
    }

    @Test
    fun exactFoldedBoeteHitsOnlyTheNeuterBoot() = withRepo(TestNounsDb.create(tmp.root.toPath())) { repo ->
        val hits = repo.exactForm(foldForm("böte"))

        assertEquals(1, hits.size)
        assertEquals(1L, hits[0].entry.entryId)
        assertEquals("böte", hits[0].form)
    }

    @Test
    fun exactFormOnUnknownGenderEntryFailsFast() =
        withRepo(TestNounsDb.create(tmp.root.toPath(), unknownGender = true)) { repo ->
            assertThrows(IllegalArgumentException::class.java) { repo.exactForm(foldForm("kevin")) }
        }

    // --- prefixForms ---

    @Test
    fun prefixFindsBothZeitEntriesInPageOrder() = withRepo(TestNounsDb.create(tmp.root.toPath())) { repo ->
        val hits = repo.prefixForms("zeit", limit = 10)

        assertEquals(listOf(4L, 5L), hits.map { it.entry.entryId })
        assertEquals(listOf(Kind.WORD, Kind.PERSON_NAME), hits.map { it.entry.kind })
    }

    @Test
    fun prefixHonoursLimitAndTreatsWildcardsAndEscapeAsLiterals() = withRepo(TestNounsDb.create(tmp.root.toPath())) { repo ->
        assertEquals(1, repo.prefixForms("zeit", limit = 1).size)
        assertFalse("`%` escaped", repo.prefixForms("zei%", limit = 10).any())
        assertFalse("`_` escaped", repo.prefixForms("boa_t", limit = 10).any())
        // Positive discriminator on the ESCAPE char itself: only a correctly escaped
        // backslash prefix finds the fixture's literal `ke\vin` form.
        assertEquals(listOf("ke\\vin"), repo.prefixForms("ke\\", limit = 10).map { it.form })
    }

    // --- meta ---

    @Test
    fun metaExposesTheFixtureStamps() = withRepo(TestNounsDb.create(tmp.root.toPath())) { repo ->
        assertEquals(
            mapOf(
                NounsSchema.META_SCHEMA_VERSION to "1",
                NounsSchema.META_BUILT_FROM to "test",
                NounsSchema.META_BUILT_AT to "t1",
                NounsSchema.META_FOLD_VECTORS_SHA256 to FakeWordRepository.DUMMY_FOLD_VECTORS_SHA256,
            ),
            repo.meta(),
        )
    }

    // --- WordsDb seeding / version gate ---

    @Test
    fun openRejectsForeignSchemaVersion() {
        val fixture = TestNounsDb.create(tmp.root.toPath(), schemaVersion = "0")

        assertThrows(IllegalStateException::class.java) { wordsDbOpening(fixture, onCopy = {}).open().close() }
    }

    @Test
    fun openCopiesOnFirstUseAndRecopiesOnlyWhenBuiltAtChanges() {
        var copies = 0
        var asset = TestNounsDb.create(tmp.newFolder("v1").toPath(), builtAt = "t1")
        fun openMeta(): Map<String, String> =
            wordsDbOpening(asset, onCopy = { copies++ }).open().use { SqliteWordRepository(it).meta() }

        assertEquals("t1", openMeta()[NounsSchema.META_BUILT_AT])
        assertEquals(1, copies)

        openMeta() // unchanged built_at
        assertEquals(1, copies)

        asset = TestNounsDb.create(tmp.newFolder("v2").toPath(), builtAt = "t2")
        assertEquals("t2", openMeta()[NounsSchema.META_BUILT_AT])
        assertEquals(2, copies)
    }

    @Test
    fun seedInflatesGzippedAssetWithoutTruncatingIt() {
        // The asset shape the shipped dictionary actually takes. Sizes dividing the 8 KiB
        // copy buffer hide an unflushed tail — that is exactly how the shipped 93 MiB
        // export (whole 8 KiB blocks) masked the lost flush. The meta pad pushes this
        // fixture one page over an 8 KiB boundary (28672 B); the guard re-proves it.
        val fixture = TestNounsDb.create(tmp.newFolder("gz").toPath(), builtAt = "gz", metaPadBytes = 4200)
        assertTrue(
            "fixture must not divide the copy buffer, got ${fixture.toFile().length()} B",
            fixture.toFile().length() % 8192L != 0L,
        )
        val asset = tmp.newFile("nouns.db.gzip")
        FileInputStream(fixture.toFile()).use { input ->
            GZIPOutputStream(FileOutputStream(asset)).use { output -> input.copyTo(output) }
        }

        var copies = 0
        val db = WordsDb(
            context,
            openAsset = { FileInputStream(asset) },
            assetName = WordsDb.ASSET_NAME,
            onCopy = { copies++ },
        ).open()
        val meta = db.use { SqliteWordRepository(it).meta() }

        assertEquals(1, copies)
        // (the full meta map is pinned by metaExposesTheFixtureStamps; this fixture carries a pad row)
        assertEquals("1", meta[NounsSchema.META_SCHEMA_VERSION])
        assertEquals("test", meta[NounsSchema.META_BUILT_FROM])
        assertEquals("gz", meta[NounsSchema.META_BUILT_AT])
        val seeded = context.getDatabasePath(WordsDb.DB_NAME)
        assertEquals(fixture.toFile().length(), seeded.length())
        SQLiteDatabase.openDatabase(seeded.path, null, SQLiteDatabase.OPEN_READONLY).use { seededDb ->
            seededDb.rawQuery("PRAGMA integrity_check", null).use { check ->
                assertTrue(check.moveToNext())
                assertEquals("ok", check.getString(0))
            }
        }
    }

    @Test
    fun seedSweepsTmpFilesStrandedByKilledCopies() {
        val dbDir = requireNotNull(context.getDatabasePath(WordsDb.DB_NAME).parentFile)
        dbDir.mkdirs()
        val stranded = File(dbDir, "${WordsDb.DB_NAME}.999.tmp").apply { writeText("truncated seed") }

        wordsDbOpening(TestNounsDb.create(tmp.newFolder("sweep").toPath()), onCopy = {}).open().close()

        assertFalse(stranded.exists())
    }

    // --- fold self-check ---

    @Test
    fun foldSelfCheckNamesThePoisonTriple() {
        val db = SQLiteDatabase.openDatabase(
            TestNounsDb.create(tmp.root.toPath(), poison = true).toString(), null, SQLiteDatabase.OPEN_READONLY,
        )

        val failure = assertThrows(IllegalStateException::class.java) { verifyFoldConsistency(db) }
        db.close()
        val message = requireNotNull(failure.message)
        assertTrue(message, message.contains("Straße"))
        assertTrue(message, message.contains("straße"))
        assertTrue(message, message.contains("strasse"))
    }

    @Test
    fun foldSelfCheckPassesOnCleanFixture() {
        val db = SQLiteDatabase.openDatabase(
            TestNounsDb.create(tmp.root.toPath()).toString(), null, SQLiteDatabase.OPEN_READONLY,
        )
        verifyFoldConsistency(db)
        db.close()
    }
}
