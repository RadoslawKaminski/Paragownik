package com.kaminski.paragownik.adapter // Lub com.kaminski.paragownik

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Import Glide
import com.kaminski.paragownik.R
import com.kaminski.paragownik.data.Photo

/**
 * Enum określający sposób skalowania obrazu przez Glide.
 */
enum class GlideScaleType {
    CENTER_CROP, // Wypełnia ImageView, przycinając nadmiar
    FIT_CENTER   // Skaluje obraz, aby zmieścił się w ImageView, zachowując proporcje
}

/**
 * Adapter do wyświetlania listy zdjęć (dużych lub miniatur) w RecyclerView.
 * Używa Glide do efektywnego ładowania obrazów.
 */
class PhotoAdapter(
    private var photos: List<Photo>,
    private val layoutResId: Int, // ID layoutu elementu (np. R.layout.large_photo_item)
    private val imageViewId: Int, // ID ImageView w layoucie elementu
    private val scaleType: GlideScaleType = GlideScaleType.CENTER_CROP, // Dodano parametr typu skalowania, domyślnie CENTER_CROP
    private val onPhotoClickListener: ((Uri) -> Unit)? = null // Opcjonalny listener kliknięcia
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(itemView: View, val imageViewId: Int) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(imageViewId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return PhotoViewHolder(view, imageViewId)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        try {
            val photoUri = photo.uri.toUri()

            // Użycie Glide do załadowania obrazu
            val requestBuilder = Glide.with(holder.itemView.context)
                .load(photoUri)
                .placeholder(R.drawable.ic_photo_placeholder) // Placeholder na czas ładowania
                .error(R.drawable.ic_photo_placeholder) // Obrazek w razie błędu

            // Zastosuj odpowiedni typ skalowania
            when (scaleType) {
                GlideScaleType.CENTER_CROP -> requestBuilder.centerCrop()
                GlideScaleType.FIT_CENTER -> requestBuilder.fitCenter()
            }

            // Załaduj obraz do ImageView
            requestBuilder.into(holder.imageView)

            // Ustaw listener kliknięcia (jeśli zdefiniowany)
            holder.itemView.setOnClickListener {
                onPhotoClickListener?.invoke(photoUri)
            }
        } catch (e: Exception) {
            Log.e("PhotoAdapter", "Błąd ładowania zdjęcia URI: ${photo.uri}", e)
            // W razie błędu Glide sam ustawi obrazek z .error()
            holder.itemView.setOnClickListener(null) // Wyłącz klikanie dla błędnego elementu
        }
    }

    override fun getItemCount(): Int = photos.size

    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil
    fun updatePhotos(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }
}



