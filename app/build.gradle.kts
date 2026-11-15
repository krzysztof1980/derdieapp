plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "pl.kwitukiewicz.derdiedas"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.kwitukiewicz.derdiedas"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // signingConfig deliberately unset: unsigned release until Play App Signing is set up.
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// ManifestContractTest pins the shipped offline contract against the exact merged manifest
// that goes into each variant's APK. AGP's merged-manifest artifact reaches the test JVM
// through a jvmArgumentProvider, so (a) the manifest BYTES are a declared task input — a
// manifest-only edit can never ride along on an UP-TO-DATE test run — and (b) the provider
// hands the test the artifact path as a system property, ordered via dependsOn so the test
// never reads a stale file from an older build.
androidComponents {
    onVariants(selector().all()) { variant ->
        val mergedManifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
        val unitTestTask = "test${variant.name.replaceFirstChar(Char::uppercase)}UnitTest"
        tasks.withType<org.gradle.api.tasks.testing.Test>().matching { it.name == unitTestTask }.configureEach {
            dependsOn(mergedManifest)
            val contractInputs = objects.newInstance(ManifestContractInputs::class.java)
            contractInputs.mergedManifest.set(mergedManifest)
            jvmArgumentProviders.add(contractInputs)
        }
    }
}

// Compose/Robolectric tests share the JVM with the WordsDb seed test; the 512 MiB default
// OOMed it once they joined (Task 5 review follow-up). AGP 9's DSL no longer exposes
// unitTests.maxHeapSize, so the cap goes on the unit test tasks themselves.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    maxHeapSize = "1g"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(testFixtures(project(":core")))
    // Test scope only: fixtures are written over JDBC; production reads stay on android.database.sqlite.
    testImplementation(libs.sqlite.jdbc)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}

/**
 * The merged manifest as a genuine up-to-date input of the unit-test task: the @InputFile
 * on the provider property makes Gradle hash the manifest bytes (edits re-run the contract
 * test), while [asArguments] delivers the artifact path to the test JVM.
 */
abstract class ManifestContractInputs @javax.inject.Inject constructor() : org.gradle.process.CommandLineArgumentProvider {

    @get:org.gradle.api.tasks.InputFile
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
    abstract val mergedManifest: org.gradle.api.file.RegularFileProperty

    override fun asArguments(): List<String> =
        listOf("-Dderdieapp.merged.manifest=${mergedManifest.get().asFile.absolutePath}")
}
