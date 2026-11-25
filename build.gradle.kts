plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.exemplo.tvfiles"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.exemplo.tvfiles"
        minSdk = 24
        targetSdk = 28
        versionCode = 2
        versionName = "1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}
