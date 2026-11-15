plugins {
    alias(libs.plugins.kotlin.jvm)
    // FakeWordRepository is shared: engine tests use it directly, :app UI tests consume
    // it through the generated test-fixtures variant (single fixture source).
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
