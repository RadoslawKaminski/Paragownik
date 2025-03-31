package com.kaminski.paragownik

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kaminski.paragownik.data.ClientWithThumbnail

/**
 * Adapter dla RecyclerView wyświetlającego listę klientów w [ClientListActivity].
 * Pokazuje miniaturę zdjęcia klienta, używając Glide.
 */
class ClientAdapter(
    var clientList: List<ClientWithThumbnail>,
    private val itemClickListener: OnClientClickListener
) : RecyclerView.Adapter<ClientAdapter.ClientViewHolder>() {

    /**
     * Interfejs dla obsługi kliknięcia elementu listy klientów.
     */
    interface OnClientClickListener {
        fun onClientClick(clientId: Long)
    }

    /**
     * ViewHolder przechowujący referencje do widoków elementu listy klienta.
     */
    class ClientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val descriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
        val appNumberTextView: TextView = itemView.findViewById(R.id.clientAppNumberTextView)
        val amoditNumberTextView: TextView = itemView.findViewById(R.id.amoditNumberTextView)
        val clientPhotoImageView: ImageView = itemView.findViewById(R.id.clientItemPhotoImageView)
    }

    /**
     * Tworzy nowy ViewHolder (wywoływane przez LayoutManager).
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.client_item, parent, false)
        return ClientViewHolder(itemView)
    }

    /**
     * Łączy dane klienta z widokami w ViewHolderze.
     * Ładuje miniaturę zdjęcia klienta za pomocą Glide.
     */
    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        val currentClientWithThumbnail = clientList[position]
        val currentClient = currentClientWithThumbnail.client
        val thumbnailUri = currentClientWithThumbnail.thumbnailUri
        val context = holder.itemView.context

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

        Glide.with(context)
            .load(thumbnailUri?.toUri())
            .placeholder(R.drawable.ic_photo_placeholder)
            .error(R.drawable.ic_photo_placeholder)
            .centerCrop()
            .into(holder.clientPhotoImageView)

        holder.itemView.setOnClickListener {
            itemClickListener.onClientClick(currentClient.id)
        }
    }

    /**
     * Zwraca liczbę elementów na liście klientów.
     */
    override fun getItemCount() = clientList.size
}







