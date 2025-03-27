package com.kaminski.paragownik

import android.content.Context // Potrzebne do pobierania stringów z zasobów
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.Client // Import modelu danych Client

/**
 * Adapter dla RecyclerView wyświetlającego listę klientów w [ClientListActivity].
 */
class ClientAdapter(
    // Lista obiektów Client do wyświetlenia.
    var clientList: List<Client>,
    // Listener (np. Aktywność), który będzie powiadamiany o kliknięciu elementu listy.
    private val itemClickListener: OnClientClickListener
) : RecyclerView.Adapter<ClientAdapter.ClientViewHolder>() {

    // Przechowuje kontekst, potrzebny do dostępu do zasobów (np. stringów)
    private lateinit var context: Context

    /**
     * Interfejs definiujący metodę zwrotną (callback) wywoływaną po kliknięciu
     * elementu listy klientów.
     */
    interface OnClientClickListener {
        /**
         * Wywoływane, gdy użytkownik kliknie element reprezentujący klienta.
         * @param clientId ID klikniętego klienta.
         */
        fun onClientClick(clientId: Long)
    }

    /**
     * ViewHolder przechowuje referencje do widoków (TextView)
     * w pojedynczym elemencie listy (layout `client_item.xml`).
     */
    class ClientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Referencje do widoków w layoucie client_item.xml
        val descriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
        val appNumberTextView: TextView = itemView.findViewById(R.id.clientAppNumberTextView)
        val amoditNumberTextView: TextView = itemView.findViewById(R.id.amoditNumberTextView)
        // TODO: Dodać referencję do ImageView dla zdjęcia
    }

    /**
     * Tworzy nowy obiekt ViewHolder, gdy RecyclerView potrzebuje nowego elementu do wyświetlenia.
     * Influje widok z pliku XML `client_item.xml` i pobiera kontekst.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        context = parent.context // Pobranie kontekstu
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.client_item, parent, false)
        return ClientViewHolder(itemView)
    }

    /**
     * Łączy dane klienta z określonej pozycji listy (`position`) z widokami wewnątrz ViewHoldera (`holder`).
     */
    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        val currentClient = clientList[position]

        // Ustawienie opisu klienta lub ID, jeśli opis jest pusty
        holder.descriptionTextView.text =
            if (currentClient.description.isNullOrBlank()) {
                context.getString(R.string.client_item_id_prefix) + currentClient.id.toString()
            } else {
                currentClient.description
            }

        // Ustawienie numeru aplikacji klienta z jawnym dodaniem spacji
        val appNumberText = currentClient.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_app_number_prefix) + " " + it // Dodano spację
        }
        holder.appNumberTextView.text = appNumberText
        holder.appNumberTextView.isVisible = appNumberText != null

        // Ustawienie numeru Amodit z jawnym dodaniem spacji
        val amoditNumberText = currentClient.amoditNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_amodit_number_prefix) + " " + it // Dodano spację
        }
        holder.amoditNumberTextView.text = amoditNumberText
        holder.amoditNumberTextView.isVisible = amoditNumberText != null

        // Ustawienie listenera dla kliknięcia całego elementu listy
        holder.itemView.setOnClickListener {
            itemClickListener.onClientClick(currentClient.id)
        }
    }

    /**
     * Zwraca całkowitą liczbę elementów na liście danych.
     */
    override fun getItemCount() = clientList.size
}
