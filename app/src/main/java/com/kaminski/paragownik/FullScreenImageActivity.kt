package com.kaminski.paragownik

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

/**
 * Aktywność wyświetlająca pojedynczy obraz na pełnym ekranie z możliwością zoomowania.
 */
class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var photoView: PhotoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        // Ukryj paski systemowe (pasek statusu i nawigacji) dla pełnego zanurzenia
        hideSystemBars()

        photoView = findViewById(R.id.photo_view)

        // Pobierz URI obrazu przekazane w Intencie
        val imageUriString = intent.getStringExtra("IMAGE_URI")

        if (imageUriString != null) {
            try {
                val imageUri = imageUriString.toUri()
                // Załaduj obraz do PhotoView za pomocą Glide
                Glide.with(this)
                    .load(imageUri)
                    // Nie ustawiamy placeholdera, bo tło jest ciemne
                    .error(R.drawable.ic_photo_placeholder) // Pokaż placeholder w razie błędu ładowania
                    .into(photoView)

                // Ustaw listener, aby zamknąć aktywność po kliknięciu poza obszarem zdjęcia
                photoView.setOnOutsidePhotoTapListener {
                    finish() // Zamknij aktywność
                }
                // Opcjonalnie: Zamknij również po kliknięciu na samo zdjęcie
                // photoView.setOnPhotoTapListener { _, _, _ -> finish() }

            } catch (e: Exception) {
                Log.e("FullScreenImageActivity", "Błąd podczas ładowania obrazu: $imageUriString", e)
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_LONG).show()
                finish() // Zamknij, jeśli URI jest nieprawidłowe lub wystąpił błąd
            }
        } else {
            Log.e("FullScreenImageActivity", "Nie przekazano URI obrazu w Intencie.")
            Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_LONG).show()
            finish() // Zamknij, jeśli brakuje URI
        }
    }

    /**
     * Ukrywa paski systemowe (pasek statusu i nawigacji).
     */
    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Skonfiguruj zachowanie ukrytych pasków systemowych (pojawią się po przesunięciu)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Ukryj pasek statusu i nawigacji
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}



