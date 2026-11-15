package pl.kwitukiewicz.derdiedas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element

/**
 * The shipped-app contract, pinned against the real product rather than the source file:
 * [mergedManifest] parses the *merged* AndroidManifest of the variant under test — main
 * manifest plus every library contribution plus AGP's own injections, i.e. exactly what
 * lands in that APK. DerDieDas is fully offline, so the merged manifest must never gain
 * INTERNET, any other permission, a network-requiring feature, or a cleartext flag.
 *
 * The file is handed to the test JVM as the system property set by `app/build.gradle.kts`
 * (pointing at AGP's merged-manifest artifact and ordering the test after its production);
 * the path walk below is only the IDE-run fallback. If the file is missing, run
 * `:app:assembleDebug` (or `:app:processDebugMainManifest`) first.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestContractTest {

    private val manifest: Element by lazy {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(mergedManifest()).documentElement
    }

    @Test
    fun mergedManifestDeclaresNoInternetPermission() {
        val internet = childElements(manifest, "uses-permission")
            .map { it.getAttribute(ANDROID_NS + "name") }
            .filter { it == "android.permission.INTERNET" }
        assertTrue("INTERNET leaked into the merged manifest", internet.isEmpty())
    }

    @Test
    fun mergedManifestPermissionsAreExactlyThePinnedSet() {
        // The set is EMPTY by contract, with one tolerated exception: on targetSdk 34+ AGP
        // injects the androidx.core dynamic-receiver permission — an app-id-prefixed,
        // signature-protected self-pair (a <permission> twin plus this uses-permission)
        // guarding runtime-registered RECEIVER_NOT_EXPORTED receivers. Neither network nor
        // data access, and tools:node-removing it is wrong — runtime registration needs it.
        val dynamicReceiver = "pl.kwitukiewicz.derdiedas.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val expected = setOf(dynamicReceiver)
        val actual = childElements(manifest, "uses-permission")
            .map { it.getAttribute(ANDROID_NS + "name") }
            .toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun mergedManifestRequiresNoNetworkFeaturesAndForbidsCleartext() {
        // Offline means offline: no Wi-Fi/ethernet/telephony feature gate either. glEsVersion
        // (androidx/Compose) passes; anything name-matching network hardware does not.
        val networkFeature = Regex("(?i)wifi|ethernet|network|telephony|usb")
        val offenders = childElements(manifest, "uses-feature")
            .map { it.getAttribute(ANDROID_NS + "name") }
            .filter { networkFeature.containsMatchIn(it) }
        assertTrue("network uses-feature entries: $offenders", offenders.isEmpty())
        val app = childElements(manifest, "application").singleOrNull()
        assertTrue(
            "usesCleartextTraffic must not be true",
            app?.getAttribute(ANDROID_NS + "usesCleartextTraffic")?.let { it != "true" } ?: true,
        )
    }

    @Test
    fun launcherLabelIsDerDieDas() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("DerDieDas", context.getString(R.string.app_name))
    }

    private companion object {
        const val ANDROID_NS = "android:"

        /** Property set by app/build.gradle.kts on the debug unit-test task. */
        const val MANIFEST_PROPERTY = "derdieapp.merged.manifest"

        fun mergedManifest(): File {
            System.getProperty(MANIFEST_PROPERTY)?.let { declared ->
                val file = File(declared)
                assertTrue(
                    "merged manifest at -P$MANIFEST_PROPERTY not found: $declared " +
                        "— run :app:assembleDebug first",
                    file.isFile,
                )
                return file
            }
            // IDE fallback: walk up to the Gradle root, then glob AGP's merged-manifest
            // intermediates (`**/merged_manifest*/**/AndroidManifest.xml`), preferring the
            // debug variant; a deterministic path sort breaks any remaining tie.
            val start = File(System.getProperty("user.dir")).absoluteFile
            var dir: File? = start
            while (dir != null) {
                val globRoot = listOf(File(dir, "build/intermediates"), File(dir, "app/build/intermediates"))
                    .firstOrNull { it.isDirectory }
                if (globRoot != null) {
                    val candidates = globRoot.walkTopDown()
                        .filter { file ->
                            file.name == "AndroidManifest.xml" &&
                                "merged_manifest" in file.invariantSeparatorsPath &&
                                "debug" in file.invariantSeparatorsPath.lowercase()
                        }
                        .sortedBy { it.invariantSeparatorsPath }
                        .toList()
                    if (candidates.isNotEmpty()) return candidates.first()
                    throw AssertionError("no merged manifest under $globRoot — run :app:assembleDebug first")
                }
                dir = dir.parentFile
            }
            throw AssertionError("no build/intermediates found from $start — run :app:assembleDebug first")
        }

        /**
         * Every [tag] element below [root] at any depth — merged manifests keep
         * uses-permission/uses-feature as direct children, but the contract wants to catch
         * them at any depth a library merge might produce.
         */
        fun childElements(root: Element, tag: String): List<Element> =
            root.getElementsByTagName(tag).let { nodes ->
                (0 until nodes.length).asSequence().map(nodes::item).filterIsInstance<Element>().toList()
            }
    }
}
