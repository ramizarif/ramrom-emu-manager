plugins { id("com.android.library"); kotlin("android"); kotlin("plugin.serialization") }

android {
    namespace = "com.ramrom.emumanager.knowledge"
    compileSdk = 34
    defaultConfig { minSdk = 33; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
