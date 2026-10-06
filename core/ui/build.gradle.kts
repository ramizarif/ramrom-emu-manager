plugins { id("com.android.library"); kotlin("android") }

android {
    namespace = "com.ramrom.emumanager.ui"
    compileSdk = 34
    defaultConfig { minSdk = 33 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
