package com.kaminski.paragownik

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.kaminski.paragownik.adapter.FullScreenImageAdapter // Import nowego adaptera

/**
 * Aktywność wyświetlająca zdjęcia na pełnym ekranie z możliwością zoomowania, przesuwania
 * oraz przełączania między zdjęciami za pomocą gestu swipe.
 */
class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var imageAdapter: FullScreenImageAdapter

    companion object {
        // Klucze do przekazywania danych w Intencie
        const val IMAGE_URIS = "IMAGE_URIS"
        const val START_POSITION = "START_POSITION"
    }

    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        // Ukrycie pasków systemowych dla trybu pełnoekranowego
        hideSystemBars()

        // Inicjalizacja ViewPager2
        viewPager = findViewById(R.id.fullScreenViewPager)

        // Pobranie danych z Intentu
        val imageUriStrings = intent.getStringArrayListExtra(IMAGE_URIS)
        val startPosition = intent.getIntExtra(START_POSITION, 0)

        // Sprawdzenie, czy dane zostały poprawnie przekazane
        if (imageUriStrings != null && imageUriStrings.isNotEmpty()) {
            try {
                // Utworzenie i ustawienie adaptera dla ViewPager2
                imageAdapter = FullScreenImageAdapter(imageUriStrings, this) // Przekazujemy this (Activity)
                viewPager.adapter = imageAdapter

                // Ustawienie początkowej strony (zdjęcia, które zostało kliknięte)
                // `false` oznacza brak animacji przewijania przy ustawianiu początkowej strony
                if (startPosition >= 0 && startPosition < imageUriStrings.size) {
                    viewPager.setCurrentItem(startPosition, false)
                } else {
                    Log.w("FullScreenImageActivity", "Nieprawidłowa pozycja startowa ($startPosition), ustawiono na 0.")
                    viewPager.setCurrentItem(0, false)
                }

            } catch (e: Exception) {
                Log.e("FullScreenImageActivity", "Błąd podczas inicjalizacji ViewPager2 lub adaptera.", e)
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_LONG).show()
                finish() // Zamknij aktywność w przypadku błędu
            }
        } else {
            // Jeśli lista URI jest pusta lub null, wyświetl błąd i zamknij aktywność
            Log.e("FullScreenImageActivity", "Nie przekazano listy URI obrazów w Intencie lub lista jest pusta.")
            Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Ukrywa paski systemowe (pasek statusu i nawigacji), aby uzyskać tryb pełnoekranowy.
     */
    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Ustawienie zachowania pasków systemowych (pojawiają się po przesunięciu od krawędzi)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Ukrycie pasków systemowych
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}

