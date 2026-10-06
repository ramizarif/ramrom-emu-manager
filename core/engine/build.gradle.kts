plugins { id("com.android.library"); kotlin("android"); kotlin("plugin.serialization") }

android {
    namespace = "com.ramrom.emumanager.engine"
    compileSdk = 34
    defaultConfig { minSdk = 33 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
    testOptions { unitTests.isReturnDefaultValues = true }
    // Tests read the real knowledge bundle, never a copy that drifts out of date.
    sourceSets["test"].resources.srcDir("../knowledge/src/main/assets")
}

dependencies {
    api(project(":core:knowledge"))
    api(project(":core:access"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.25")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.25")
}
