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

    runtimeOnly(libs.logback.classic)
    runtimeOnly("org.dkpro.jwktl:dkpro-jwktl:1.1.0")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
