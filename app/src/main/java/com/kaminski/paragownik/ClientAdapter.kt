package com.kaminski.paragownik

import android.content.Context
import android.util.Log // Do logowania błędów
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView // Potrzebny dla ImageView
import android.widget.TextView
import androidx.core.net.toUri // Potrzebny do konwersji String na Uri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Import Glide
import com.kaminski.paragownik.data.Client // Nadal potrzebne dla ClientDao
import com.kaminski.paragownik.data.ClientWithThumbnail // Dodano import

/**
 * Adapter dla RecyclerView wyświetlającego listę klientów w [ClientListActivity].
 * Pokazuje miniaturę zdjęcia klienta, używając Glide.
 */
class ClientAdapter(
    var clientList: List<ClientWithThumbnail>, // Zmieniono typ listy
    private val itemClickListener: OnClientClickListener
) : RecyclerView.Adapter<ClientAdapter.ClientViewHolder>() {

    // Usunięto lateinit var context, pobieramy z holdera

    /**
     * Interfejs dla obsługi kliknięcia elementu listy.
     */
    interface OnClientClickListener {
        fun onClientClick(clientId: Long)
    }

    /**
     * ViewHolder przechowujący referencje do widoków elementu listy.
     */
    class ClientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val descriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
        val appNumberTextView: TextView = itemView.findViewById(R.id.clientAppNumberTextView)
        val amoditNumberTextView: TextView = itemView.findViewById(R.id.amoditNumberTextView)
        val clientPhotoImageView: ImageView = itemView.findViewById(R.id.clientItemPhotoImageView) // Referencja do ImageView zdjęcia
    }

    /**
     * Tworzy nowy ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        // context = parent.context // Usunięto
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.client_item, parent, false)
        return ClientViewHolder(itemView)
    }

    /**
     * Łączy dane z widokami w ViewHolderze, w tym ładuje miniaturę zdjęcia za pomocą Glide.
     */
    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        val currentClientWithThumbnail = clientList[position] // Zmieniono nazwę i typ
        val currentClient = currentClientWithThumbnail.client // Pobierz obiekt Client
        val thumbnailUri = currentClientWithThumbnail.thumbnailUri // Pobierz URI miniatury
        val context = holder.itemView.context // Pobierz kontekst z widoku elementu

        // Ustawienie danych tekstowych klienta
        holder.descriptionTextView.text =
            if (currentClient.description.isNullOrBlank()) {
                context.getString(R.string.client_item_id_prefix) + currentClient.id.toString()
            } else {
                currentClient.description
            }

        val appNumberText = currentClient.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_app_number_prefix) + " " + it
        }
        holder.appNumberTextView.text = appNumberText
        holder.appNumberTextView.isVisible = appNumberText != null

        val amoditNumberText = currentClient.amoditNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_amodit_number_prefix) + " " + it
        }
        holder.amoditNumberTextView.text = amoditNumberText
        holder.amoditNumberTextView.isVisible = amoditNumberText != null

        // Ładowanie miniatury zdjęcia klienta za pomocą Glide
        Glide.with(context) // Użyj kontekstu z holdera
            .load(thumbnailUri?.toUri()) // Bezpieczne ładowanie URI (może być null)
            .placeholder(R.drawable.ic_photo_placeholder) // Placeholder na czas ładowania
            .error(R.drawable.ic_photo_placeholder) // Obrazek w razie błędu
            .centerCrop() // Skalowanie, aby wypełnić ImageView, przycinając nadmiar
            .into(holder.clientPhotoImageView) // Docelowy ImageView

        // Ustawienie listenera dla kliknięcia całego elementu listy
        holder.itemView.setOnClickListener {
            itemClickListener.onClientClick(currentClient.id)
        }
    }

    /**
     * Zwraca liczbę elementów listy.
     */
    override fun getItemCount() = clientList.size
}



