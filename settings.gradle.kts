pluginManagement {
    repositories { gradlePluginPortal(); google(); mavenCentral() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "leapy"
include(":core")
if (System.getenv("LEAPY_CORE_ONLY") == null) include(":app")
