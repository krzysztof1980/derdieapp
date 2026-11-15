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
// that goes into each variant's APK: hand AGP's merged-manifest artifact to the test JVM as
// a system property and order the unit-test task after its production, so the test always
// (re)merges instead of depending on whoever ran assemble* last.
androidComponents {
    onVariants(selector().all()) { variant ->
        val mergedManifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
        val unitTestTask = "test${variant.name.replaceFirstChar(Char::uppercase)}UnitTest"
        tasks.withType<org.gradle.api.tasks.testing.Test>().matching { it.name == unitTestTask }.configureEach {
            dependsOn(mergedManifest)
            // Resolved in doFirst: the artifact path is only final once the merge task ran.
            doFirst {
                systemProperty("derdieapp.merged.manifest", mergedManifest.get().asFile.absolutePath)
            }
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
