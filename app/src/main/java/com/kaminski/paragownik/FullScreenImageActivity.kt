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
 * Aktywność wyświetlająca pojedynczy obraz na pełnym ekranie z możliwością zoomowania i przesuwania.
 */
class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var photoView: PhotoView

    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        hideSystemBars()

        photoView = findViewById(R.id.photo_view)

        val imageUriString = intent.getStringExtra("IMAGE_URI")

        if (imageUriString != null) {
            try {
                val imageUri = imageUriString.toUri()
                Glide.with(this)
                    .load(imageUri)
                    .error(R.drawable.ic_photo_placeholder)
                    .into(photoView)

                photoView.setOnOutsidePhotoTapListener {
                    finish()
                }
                // photoView.setOnPhotoTapListener { _, _, _ -> finish() }

            } catch (e: Exception) {
                Log.e("FullScreenImageActivity", "Błąd podczas ładowania obrazu: $imageUriString", e)
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            Log.e("FullScreenImageActivity", "Nie przekazano URI obrazu w Intencie.")
            Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Ukrywa paski systemowe (pasek statusu i nawigacji), aby uzyskać tryb pełnoekranowy.
     */
    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}







