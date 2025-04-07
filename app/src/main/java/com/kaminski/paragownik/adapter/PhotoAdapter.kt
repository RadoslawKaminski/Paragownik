package com.kaminski.paragownik.adapter

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
    private var photos: List<Photo>, // Zmieniono na var, aby umożliwić aktualizację
    private val layoutResId: Int,
    private val imageViewId: Int,
    private val scaleType: GlideScaleType = GlideScaleType.CENTER_CROP,
    private val onPhotoClickListener: ((Uri) -> Unit)? = null
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    /**
     * ViewHolder przechowujący referencję do ImageView.
     */
    class PhotoViewHolder(itemView: View, val imageViewId: Int) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(imageViewId)
    }

    /**
     * Tworzy nowy ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return PhotoViewHolder(view, imageViewId)
    }

    /**
     * Łączy dane zdjęcia z widokiem w ViewHolderze.
     */
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        try {
            val photoUri = photo.uri.toUri()

            // Konfiguracja Glide
            val requestBuilder = Glide.with(holder.itemView.context)
                .load(photoUri)
                .placeholder(R.drawable.ic_photo_placeholder)
                .error(R.drawable.ic_photo_placeholder)

            // Ustawienie typu skalowania
            when (scaleType) {
                GlideScaleType.CENTER_CROP -> requestBuilder.centerCrop()
                GlideScaleType.FIT_CENTER -> requestBuilder.fitCenter()
            }

            // Załadowanie obrazu
            requestBuilder.into(holder.imageView)

            // Ustawienie listenera kliknięcia (jeśli został przekazany)
            holder.itemView.setOnClickListener {
                onPhotoClickListener?.invoke(photoUri)
            }
        } catch (e: Exception) {
            // Obsługa błędu ładowania URI
            Log.e("PhotoAdapter", "Błąd ładowania zdjęcia URI: ${photo.uri}", e)
            // Można ustawić domyślny obrazek błędu
            holder.imageView.setImageResource(R.drawable.ic_photo_placeholder)
            holder.itemView.setOnClickListener(null) // Wyłącz klikanie, jeśli błąd
        }
    }

    /**
     * Zwraca liczbę zdjęć.
     */
    override fun getItemCount(): Int = photos.size

    /**
     * Aktualizuje listę zdjęć w adapterze i powiadamia o zmianie danych.
     * @param newPhotos Nowa lista obiektów Photo.
     */
    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil dla lepszej wydajności
    fun updatePhotos(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged() // Informuje RecyclerView o konieczności odświeżenia widoku
    }

    /**
     * Zwraca aktualnie przechowywaną listę zdjęć.
     * Potrzebne do pobrania listy URI w aktywnościach edycji.
     */
    fun getCurrentPhotos(): List<Photo> {
        return photos
    }
}