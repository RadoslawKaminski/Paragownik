package com.kaminski.paragownik

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible // Import do łatwego zarządzania widocznością (View.isVisible)
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.ReceiptWithClient // Import modelu danych
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter dla RecyclerView wyświetlającego listę paragonów w [ReceiptListActivity].
 * Każdy element listy reprezentuje jeden paragon wraz z podstawowymi danymi powiązanego klienta.
 */
class ReceiptAdapter(
    // Lista danych (paragony z klientami), którą adapter będzie wyświetlał.
    // `var` pozwala na aktualizację listy z zewnątrz.
    var receiptList: List<ReceiptWithClient>,
    // Listener (zazwyczaj Aktywność), który będzie powiadamiany o kliknięciu przycisku edycji.
    private val editButtonClickListener: OnEditButtonClickListener
) : RecyclerView.Adapter<ReceiptAdapter.ReceiptViewHolder>() {

    /**
     * Interfejs definiujący metodę zwrotną (callback) wywoływaną po kliknięciu
     * przycisku edycji w elemencie listy.
     */
    interface OnEditButtonClickListener {
        /**
         * Wywoływane, gdy użytkownik kliknie ikonę edycji dla danego paragonu.
         * @param receiptId ID paragonu, który ma być edytowany.
         */
        fun onEditButtonClick(receiptId: Long)
    }

    /**
     * ViewHolder przechowuje referencje do widoków (TextView, ImageView)
     * w pojedynczym elemencie listy (layout `receipt_item.xml`).
     * Dzięki temu unikamy wielokrotnego wywoływania `findViewById` podczas przewijania.
     */
    class ReceiptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Referencje do widoków w layoucie receipt_item.xml
        val receiptNumberTextView: TextView = itemView.findViewById(R.id.receiptNumberTextView)
        val receiptDateTextView: TextView = itemView.findViewById(R.id.receiptDateTextView)
        val verificationDateTextView: TextView = itemView.findViewById(R.id.verificationDateTextView)
        val clientDescriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
        val editReceiptButton: ImageView = itemView.findViewById(R.id.editReceiptButton) // Ikona edycji
        // Referencje do nowych TextView dla danych klienta
        val clientAppNumberTextView: TextView = itemView.findViewById(R.id.clientAppNumberTextView)
        val amoditNumberTextView: TextView = itemView.findViewById(R.id.amoditNumberTextView)
    }

    /**
     * Tworzy nowy obiekt ViewHolder, gdy RecyclerView potrzebuje nowego elementu do wyświetlenia.
     * Influje (tworzy) widok z pliku XML `receipt_item.xml`.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptViewHolder {
        // Utwórz widok pojedynczego elementu listy na podstawie layoutu XML
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.receipt_item, parent, false)
        // Zwróć nowo utworzony ViewHolder, przekazując mu utworzony widok
        return ReceiptViewHolder(itemView)
    }

    /**
     * Łączy dane z określonej pozycji listy (`position`) z widokami wewnątrz ViewHoldera (`holder`).
     * Wywoływane przez RecyclerView, gdy element ma zostać wyświetlony lub zaktualizowany.
     */
    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        // Pobranie obiektu danych (paragon z klientem) dla bieżącej pozycji
        val currentReceiptWithClient = receiptList[position]
        val currentReceipt = currentReceiptWithClient.receipt // Obiekt paragonu
        val client = currentReceiptWithClient.client       // Obiekt klienta (może być null w teorii, choć FK powinien zapobiegać)

        // Formatter do wyświetlania daty w formacie DD-MM-YYYY
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        // --- Ustawienie danych paragonu w widokach ---
        holder.receiptNumberTextView.text = currentReceipt.receiptNumber
        holder.receiptDateTextView.text = dateFormat.format(currentReceipt.receiptDate)

        // Ustawienie daty weryfikacji. Jeśli jest null, wyświetl "Brak".
        holder.verificationDateTextView.text = currentReceipt.verificationDate?.let { date ->
            dateFormat.format(date) // Sformatuj datę, jeśli nie jest null
        } ?: "Brak" // Wyświetl "Brak", jeśli data jest null

        // --- Ustawienie danych klienta w widokach ---
        // Używamy `takeIf { it.isNotBlank() }` aby traktować puste stringi jakby były null (nie wyświetlamy "Opis: ").
        // Jeśli opis jest null lub pusty, wyświetl "Brak opisu klienta".
        holder.clientDescriptionTextView.text = client?.description?.takeIf { it.isNotBlank() } ?: "Brak opisu klienta"

        // Ustaw numer aplikacji klienta, jeśli istnieje i nie jest pusty.
        val appNumberText = client?.clientAppNumber?.takeIf { it.isNotBlank() }?.let { "Nr aplikacji: $it" }
        holder.clientAppNumberTextView.text = appNumberText
        // Pokaż TextView tylko jeśli `appNumberText` nie jest null (czyli numer istnieje i nie jest pusty).
        holder.clientAppNumberTextView.isVisible = appNumberText != null

        // Ustaw numer Amodit, jeśli istnieje i nie jest pusty.
        val amoditNumberText = client?.amoditNumber?.takeIf { it.isNotBlank() }?.let { "Amodit: $it" }
        holder.amoditNumberTextView.text = amoditNumberText
        // Pokaż TextView tylko jeśli `amoditNumberText` nie jest null.
        holder.amoditNumberTextView.isVisible = amoditNumberText != null

        // --- Ustawienie listenera dla przycisku (ikony) edycji ---
        holder.editReceiptButton.setOnClickListener {
            // Wywołaj metodę interfejsu `onEditButtonClick` przekazując ID klikniętego paragonu.
            // Listener (Aktywność) obsłuży to zdarzenie, np. uruchamiając EditReceiptActivity.
            editButtonClickListener.onEditButtonClick(currentReceipt.id)
        }
    }

    /**
     * Zwraca całkowitą liczbę elementów na liście danych.
     * RecyclerView używa tej informacji do określenia, ile elementów ma wyświetlić.
     */
    override fun getItemCount() = receiptList.size
}

