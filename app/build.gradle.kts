
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt") // Plugin kapt jest już obecny
}

android {
    namespace = "com.kaminski.paragownik"
    compileSdk = 35 // Zachowano Twoją wersję

    defaultConfig {
        applicationId = "com.kaminski.paragownik"
        minSdk = 24
        targetSdk = 35 // Zachowano Twoją wersję
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11 // Zachowano Twoją wersję
        targetCompatibility = JavaVersion.VERSION_11 // Zachowano Twoją wersję
    }
    kotlinOptions {
        jvmTarget = "11" // Zachowano Twoją wersję
    }
    // Włącz View Binding (jeśli nie jest jeszcze włączone, choć nie używamy go aktywnie w tym projekcie)
    // buildFeatures {
    //     viewBinding = true
    // }
}

dependencies {

    // Podstawowe biblioteki AndroidX i Material Design
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.6.1") // Użyto jawnej wersji (zamiast libs.androidx.appcompat)
    implementation("com.google.android.material:material:1.11.0") // Użyto jawnej wersji (zamiast libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.recyclerview) // Użyto aliasu (zamiast libs.recyclerview)

    // Komponenty cyklu życia (Lifecycle)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0") // Użyto jawnej wersji (zamiast libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0") // Użyto jawnej wersji

    // Aktywności KTX
    implementation("androidx.activity:activity-ktx:1.8.2") // Użyto jawnej wersji (zamiast libs.androidx.activity)

    // Room (Baza danych)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Coroutines (Współbieżność)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3") // Zachowano jawną wersję
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Zachowano jawną wersję

    // Glide (Ładowanie obrazów)
    implementation("com.github.bumptech.glide:glide:4.16.0") // Najnowsza stabilna wersja
    kapt("com.github.bumptech.glide:compiler:4.16.0") // Procesor adnotacji dla Glide

    // PhotoView (Zoomowanie obrazów) - DODANO
    implementation("com.github.chrisbanes:PhotoView:2.3.0") // Sprawdzona, stabilna wersja

    // Testy
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}