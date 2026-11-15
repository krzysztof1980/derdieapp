plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// The published wiktio jar contains Java 25 class files; a toolchain < 25
// cannot even read them.
kotlin {
    jvmToolchain(25)
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation("wiktiopeggynary:wiktiopeggynary:1.0-SNAPSHOT")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.2")
    implementation(libs.slf4j.api)
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.elasticsearch.client:elasticsearch-rest-client:8.15.1")

    runtimeOnly(libs.logback.classic)
    runtimeOnly("org.dkpro.jwktl:dkpro-jwktl:1.1.0")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "pl.kwitukiewicz.derdiedas.exporter.ExportMainKt"
}

tasks.named<JavaExec>("run") {
    // The export holds every (id, _source) pair of the 168k-doc index in memory so it
    // can sort client-side by _id before reducing; raw sources are ~100 MB but the
    // scroll of a live cluster benefits from headroom.
    jvmArgs("-Xmx3g")
    // ./gradlew :exporter:run -PlogLevel=DEBUG traces per-doc skip reasons.
    providers.gradleProperty("logLevel").orNull?.let { systemProperty("DERDIEDAS_LOG", it) }
}

tasks.test {
    useJUnitPlatform()
}
