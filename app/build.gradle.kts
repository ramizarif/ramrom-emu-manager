import java.util.Properties

plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.serialization") }

android {
    namespace = "com.ramrom.emumanager"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.ramrom.emumanager"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

    // Release builds are signed with a key that never enters the repo: keystore.properties (git-ignored)
    // names the file and passwords. Without it, release builds come out unsigned.
    val keys = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keys != null) create("release") {
            storeFile = rootProject.file(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            if (keys != null) signingConfig = signingConfigs.getByName("release")
            // Shrinking stays off for the beta: Shizuku's binder, serialization and 7-Zip's JNI would
            // each need keep rules, and a crash only in release builds is the worst kind.
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:entitlements"))
    implementation(project(":core:knowledge"))
    implementation(project(":core:access"))
    implementation(project(":core:data"))
    implementation(project(":core:engine"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    // Unpacks imports: RAR5, 7z and zip through one native decoder, bundled per ABI.
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.02")
}
