pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "derdieapp"

include(":core")
include(":exporter")
// include(":app") // :app added in Task 4
