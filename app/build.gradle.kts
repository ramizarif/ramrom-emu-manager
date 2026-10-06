plugins { id("com.android.application"); kotlin("android") }

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
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:entitlements"))
    implementation(project(":core:knowledge"))
    implementation(project(":core:access"))
    implementation(project(":core:data"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
}
