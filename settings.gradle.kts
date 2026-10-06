pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        // 7-Zip-JBinding for Android (RAR5/7z/zip) is only published on JitPack.
        maven { url = uri("https://jitpack.io"); content { includeGroup("com.github.omicronapps") } }
    }
}
rootProject.name = "RamROMEmuManager"
include(":app", ":core:ui", ":core:knowledge", ":core:access", ":core:data", ":core:entitlements", ":core:engine")
