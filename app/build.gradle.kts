plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")      // para Room (KTS)
    id("com.google.gms.google-services") // Google services (Firebase)
}

android {
    namespace = "cl.andres.semana4"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.andres.semana4"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Compose habilitado
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    // ------------ Compose ------------
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")

    // ------------ Google Play Services (ubicación) ------------
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // ------------ Google Maps (NUEVO) ------------
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")

    // ------------ Firebase (BoM) ------------
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    // Solo RTDB para mantener minSdk 21
    implementation("com.google.firebase:firebase-database-ktx")

    // ------------ Room (BD local) ------------
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // ------------ Debug ------------
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}