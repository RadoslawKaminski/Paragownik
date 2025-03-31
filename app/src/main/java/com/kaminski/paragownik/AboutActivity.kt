package com.kaminski.paragownik

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

/**
 * Aktywność wyświetlająca informacje o aplikacji, jej funkcjonalnościach oraz twórcy.
 */
class AboutActivity : AppCompatActivity() {

    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     * Ustawia layout i obsługuje przycisk powrotu.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ustawienie widoku dla tej aktywności.
        setContentView(R.layout.activity_about)

        // Znalezienie przycisku powrotu (strzałki) w layoucie.
        val backButton: ImageButton = findViewById(R.id.backButton)

        // Ustawienie listenera kliknięcia dla przycisku powrotu.
        backButton.setOnClickListener {
            // Zamknięcie bieżącej aktywności (powrót do poprzedniego ekranu).
            finish()
        }
    }
}
