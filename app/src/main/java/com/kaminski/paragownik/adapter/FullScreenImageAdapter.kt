package com.kaminski.paragownik.adapter

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.kaminski.paragownik.R

/**
 * Adapter dla ViewPager2 w FullScreenImageActivity.
 * Odpowiada za tworzenie i wiązanie widoków PhotoView dla każdego URI zdjęcia.
 *
 * @param imageUris Lista URI zdjęć (jako String) do wyświetlenia.
 * @param activity Kontekst aktywności (potrzebny do zamknięcia jej przy kliknięciu tła).
 */
class FullScreenImageAdapter(
    private val imageUris: List<String>,
    private val activity: Activity // Przekazujemy referencję do aktywności
) : RecyclerView.Adapter<FullScreenImageAdapter.ImageViewHolder>() {

    /**
     * ViewHolder przechowujący referencję do PhotoView na pojedynczej stronie ViewPagera.
     */
    class ImageViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

    /**
     * Tworzy nowy ViewHolder, inflując layout strony (fullscreen_image_page.xml).
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        // Influjemy layout strony, który zawiera tylko PhotoView
        val photoView = LayoutInflater.from(parent.context)
            .inflate(R.layout.fullscreen_image_page, parent, false) as PhotoView
        return ImageViewHolder(photoView)
    }

    /**
     * Zwraca liczbę zdjęć do wyświetlenia.
     */
    override fun getItemCount(): Int = imageUris.size

    /**
     * Łączy dane (URI zdjęcia) z widokiem (PhotoView) w ViewHolderze.
     * Ładuje obraz za pomocą Glide i ustawia listener do zamykania aktywności.
     */
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUriString = imageUris[position]
        try {
            val imageUri = imageUriString.toUri()
            // Ładowanie obrazu do PhotoView za pomocą Glide
            Glide.with(holder.photoView.context)
                .load(imageUri)
                .error(R.drawable.ic_photo_placeholder) // Placeholder w razie błędu
                .into(holder.photoView)

            // Ustawienie listenera kliknięcia poza zdjęciem (na tle) do zamknięcia aktywności
            holder.photoView.setOnOutsidePhotoTapListener {
                activity.finish() // Zamyka aktywność FullScreenImageActivity
            }
            // Opcjonalnie: Zamykanie po kliknięciu na samo zdjęcie
            // holder.photoView.setOnPhotoTapListener { _, _, _ -> activity.finish() }

        } catch (e: Exception) {
            Log.e("FullScreenImageAdapter", "Błąd podczas ładowania obrazu: $imageUriString", e)
            // Można ustawić domyślny obrazek błędu lub zostawić placeholder
            holder.photoView.setImageResource(R.drawable.ic_photo_placeholder)
        }
    }
}

