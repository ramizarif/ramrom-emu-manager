pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "RamROMEmuManager"
include(":app", ":core:ui", ":core:knowledge", ":core:access", ":core:data", ":core:entitlements")
