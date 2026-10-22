plugins { id("com.android.application") }
android {
    namespace = "studio.sundown.weather"
    compileSdk = 36
    defaultConfig {
        applicationId = "studio.sundown.weather"
        minSdk = 29
        targetSdk = 36
        versionCode = 11
        versionName = "0.9.2"
    }
    sourceSets["main"].assets.srcDir("../../dist")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = false } }
}
