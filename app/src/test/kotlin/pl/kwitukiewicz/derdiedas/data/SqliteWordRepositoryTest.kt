package pl.kwitukiewicz.derdiedas.data

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
import pl.kwitukiewicz.derdiedas.core.Gender
import pl.kwitukiewicz.derdiedas.core.Kind
import pl.kwitukiewicz.derdiedas.core.NounsSchema
import pl.kwitukiewicz.derdiedas.core.foldForm
import java.io.FileInputStream
import java.nio.file.Path

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
    fun prefixHonoursLimitAndTreatsWildcardsAsLiterals() = withRepo(TestNounsDb.create(tmp.root.toPath())) { repo ->
        assertEquals(1, repo.prefixForms("zeit", limit = 1).size)
        assertFalse(repo.prefixForms("zei%", limit = 10).any())
        assertFalse(repo.prefixForms("boa_t", limit = 10).any())
    }

    // --- meta ---

    @Test
    fun metaExposesTheFixtureStamps() = withRepo(TestNounsDb.create(tmp.root.toPath())) { repo ->
        assertEquals(
            mapOf(
                NounsSchema.META_SCHEMA_VERSION to "1",
                NounsSchema.META_BUILT_FROM to "test",
                NounsSchema.META_BUILT_AT to "t1",
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
