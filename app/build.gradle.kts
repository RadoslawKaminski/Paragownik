
// Wtyczki dla aplikacji Android i Kotlina
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt") // Kapt do obsługi adnotacji Room
}

android {
    // Przestrzeń nazw aplikacji
    namespace = "com.kaminski.paragownik"
    // Wersja SDK używana do kompilacji
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kaminski.paragownik"
        // Minimalna wersja SDK obsługiwana przez aplikację
        minSdk = 24
        // Docelowa wersja SDK
        targetSdk = 34
        // Wersja kodu aplikacji
        versionCode = 1
        // Wersja nazwy aplikacji
        versionName = "1.0"

        // Konfiguracja testów instrumentowanych
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Konfiguracja wektorowych zasobów drawable
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        // Konfiguracja buildu typu 'release'
        release {
            // Czy włączyć minifikację kodu (usuwanie nieużywanego kodu)
            isMinifyEnabled = false
            // Pliki reguł ProGuard (do minifikacji i obfuskacji)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // Opcje kompilacji - ZMIANA na Java 11
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Opcje Kotlina - ZMIANA na JVM 11
    kotlinOptions {
        jvmTarget = "11"
    }
    // Włączenie funkcji buildFeatures (np. viewBinding, dataBinding - tu nieużywane)
    buildFeatures {
        compose = false // Wyłączenie Jetpack Compose, jeśli nie jest używane
        viewBinding = false // Wyłączenie ViewBinding, jeśli nie jest używane
    }
    // Konfiguracja pakowania zasobów
    packaging {
        resources {
            // Wykluczenie niektórych plików z zależności, aby uniknąć konfliktów
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Definicje zależności aplikacji
dependencies {

    // Podstawowe biblioteki AndroidX
    implementation("androidx.core:core-ktx:1.12.0") // Rdzeń Kotlina dla AndroidX
    implementation("androidx.appcompat:appcompat:1.6.1") // Kompatybilność wsteczna UI

    // Biblioteki Material Design
    implementation("com.google.android.material:material:1.11.0") // Komponenty Material Design

    // Biblioteki do obsługi layoutów
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Zaawansowany layout manager

    // Biblioteki cyklu życia (ViewModel, LiveData)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0") // ViewModel z Kotlin extensions
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0") // LiveData z Kotlin extensions
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0") // Cykl życia dla Coroutines

    // Biblioteka Room (baza danych)
    val room_version = "2.6.1" // Definicja wersji Room
    implementation("androidx.room:room-runtime:$room_version") // Główna biblioteka Room
    kapt("androidx.room:room-compiler:$room_version") // Procesor adnotacji Room (Kapt)
    implementation("androidx.room:room-ktx:$room_version") // Kotlin extensions dla Room (Coroutines)

    // Biblioteka Glide (ładowanie obrazów)
    implementation("com.github.bumptech.glide:glide:4.16.0") // Główna biblioteka Glide
    kapt("com.github.bumptech.glide:compiler:4.16.0") // Procesor adnotacji Glide (Kapt)

    // Biblioteka PhotoView (zoomowanie zdjęć)
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // Zależności testowe
    testImplementation("junit:junit:4.13.2") // Framework do testów jednostkowych JUnit 4
    testImplementation("androidx.arch.core:core-testing:2.2.0") // Do testowania LiveData (InstantTaskExecutorRule)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3") // Do testowania Coroutines
    testImplementation("org.mockito:mockito-core:5.10.0") // Framework do tworzenia mocków
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1") // Kotlin extensions dla Mockito

    // Zależności dla testów instrumentowanych (androidTest)
    androidTestImplementation("androidx.test.ext:junit:1.1.5") // Rozszerzenia JUnit dla AndroidTest
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1") // Framework do testów UI Espresso
}


