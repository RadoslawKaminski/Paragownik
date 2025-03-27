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
    // Lista obiektów Client do wyświetlenia. `var` pozwala na aktualizację z zewnątrz.
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
     * Influje (tworzy) widok z pliku XML `client_item.xml`.
     * Pobiera również kontekst z widoku nadrzędnego.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        // Pobierz kontekst z widoku nadrzędnego (parent)
        context = parent.context
        // Utwórz widok pojedynczego elementu listy na podstawie layoutu XML
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.client_item, parent, false)
        // Zwróć nowo utworzony ViewHolder, przekazując mu utworzony widok
        return ClientViewHolder(itemView)
    }

    /**
     * Łączy dane klienta z określonej pozycji listy (`position`) z widokami wewnątrz ViewHoldera (`holder`).
     * Wywoływane przez RecyclerView, gdy element ma zostać wyświetlony lub zaktualizowany.
     */
    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        // Pobranie obiektu danych (Client) dla bieżącej pozycji
        val currentClient = clientList[position]

        // --- Ustawienie danych klienta w widokach ---

        // Ustaw opis klienta. Jeśli jest pusty lub null, wyświetl "ID: [id_klienta]".
        holder.descriptionTextView.text =
            if (currentClient.description.isNullOrBlank()) {
                context.getString(R.string.client_item_id_prefix) + currentClient.id.toString() // "ID: 123"
            } else {
                currentClient.description // Wyświetl opis
            }

        // Ustaw numer aplikacji klienta, jeśli istnieje i nie jest pusty.
        val appNumberText = currentClient.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_app_number_prefix) + it // "Nr app: 123..."
        }
        holder.appNumberTextView.text = appNumberText
        // Pokaż TextView tylko jeśli `appNumberText` nie jest null.
        holder.appNumberTextView.isVisible = appNumberText != null

        // Ustaw numer Amodit, jeśli istnieje i nie jest pusty.
        val amoditNumberText = currentClient.amoditNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_amodit_number_prefix) + it // "Amodit: 456..."
        }
        holder.amoditNumberTextView.text = amoditNumberText
        // Pokaż TextView tylko jeśli `amoditNumberText` nie jest null.
        holder.amoditNumberTextView.isVisible = amoditNumberText != null

        // --- Ustawienie listenera dla kliknięcia całego elementu listy ---
        holder.itemView.setOnClickListener {
            // Wywołaj metodę interfejsu `onClientClick` przekazując ID klikniętego klienta.
            // Listener (Aktywność) obsłuży to zdarzenie, np. uruchamiając ClientReceiptsActivity.
            itemClickListener.onClientClick(currentClient.id)
        }
    }

    /**
     * Zwraca całkowitą liczbę elementów na liście danych.
     */
    override fun getItemCount() = clientList.size
}
