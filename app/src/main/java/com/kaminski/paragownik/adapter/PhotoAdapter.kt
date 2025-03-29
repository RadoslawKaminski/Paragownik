package com.kaminski.paragownik.adapter // Lub com.kaminski.paragownik

import android.annotation.SuppressLint // Dodano import
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.R
import com.kaminski.paragownik.data.Photo

/**
 * Adapter do wyświetlania listy zdjęć (dużych lub miniatur) w RecyclerView.
 */
class PhotoAdapter(
    private var photos: List<Photo>,
    private val layoutResId: Int, // ID layoutu elementu (np. R.layout.large_photo_item)
    private val imageViewId: Int, // ID ImageView w layoucie elementu
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
            holder.imageView.setImageURI(photoUri)
            holder.itemView.setOnClickListener {
                onPhotoClickListener?.invoke(photoUri)
            }
        } catch (e: Exception) {
            Log.e("PhotoAdapter", "Błąd ładowania zdjęcia URI: ${photo.uri}", e)
            holder.imageView.setImageResource(R.drawable.ic_photo_placeholder) // Placeholder w razie błędu
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

