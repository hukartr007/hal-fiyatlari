plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Fiyat JSON dosyalarının adresi. GitHub Actions derlemesi bunu otomatik verir:
//   -PDATA_BASE_URL=https://raw.githubusercontent.com/<kullanici>/<repo>/main/data
// Elle derliyorsan KULLANICI/REPO kısmını kendi deponla değiştir.
val dataBaseUrl: String = (project.findProperty("DATA_BASE_URL") as String?)
    ?: "https://raw.githubusercontent.com/KULLANICI/hal-fiyatlari/main/data"

android {
    namespace = "tr.hal.fiyatlari"
    compileSdk = 35

    defaultConfig {
        applicationId = "tr.hal.fiyatlari"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "DATA_BASE_URL", "\"$dataBaseUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
