package com.kaminski.paragownik

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible // Import do łatwego zarządzania widocznością
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.ReceiptWithClient
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter dla RecyclerView wyświetlającego listę paragonów w ReceiptListActivity.
 */
class ReceiptAdapter(
    var receiptList: List<ReceiptWithClient>, // Lista danych do wyświetlenia
    private val editButtonClickListener: OnEditButtonClickListener // Listener kliknięć przycisku edycji
) : RecyclerView.Adapter<ReceiptAdapter.ReceiptViewHolder>() {

    /**
     * Interfejs do komunikacji zdarzenia kliknięcia przycisku edycji do Aktywności.
     */
    interface OnEditButtonClickListener {
        /**
         * Wywoływane, gdy użytkownik kliknie przycisk edycji dla danego paragonu.
         * @param receiptId ID paragonu, który ma być edytowany.
         */
        fun onEditButtonClick(receiptId: Long)
    }

    /**
     * ViewHolder przechowujący referencje do widoków w pojedynczym elemencie listy (receipt_item.xml).
     */
    class ReceiptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Referencje do widoków z layoutu receipt_item.xml
        val receiptNumberTextView: TextView = itemView.findViewById(R.id.receiptNumberTextView)
        val receiptDateTextView: TextView = itemView.findViewById(R.id.receiptDateTextView)
        val verificationDateTextView: TextView = itemView.findViewById(R.id.verificationDateTextView)
        val clientDescriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
        val editReceiptButton: ImageView = itemView.findViewById(R.id.editReceiptButton)
        // --- NOWE TEXTVIEW DLA DANYCH KLIENTA ---
        val clientAppNumberTextView: TextView = itemView.findViewById(R.id.clientAppNumberTextView)
        val amoditNumberTextView: TextView = itemView.findViewById(R.id.amoditNumberTextView)
        // --- KONIEC NOWYCH TEXTVIEW ---
    }

    /**
     * Tworzy nowy ViewHolder (wywoływane przez LayoutManager).
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptViewHolder {
        // Inflate (utworzenie) widoku pojedynczego elementu listy z pliku XML
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.receipt_item, parent, false)
        // Zwrócenie nowo utworzonego ViewHoldera
        return ReceiptViewHolder(itemView)
    }

    /**
     * Łączy dane z określonej pozycji listy z widokami w ViewHolderze (wywoływane przez LayoutManager).
     */
    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        // Pobranie obiektu danych (paragon z klientem) dla bieżącej pozycji
        val currentReceiptWithClient = receiptList[position]
        val currentReceipt = currentReceiptWithClient.receipt
        val client = currentReceiptWithClient.client // Klient może być null w rzadkich przypadkach błędów bazy

        // Formatter do wyświetlania daty w formacie DD-MM-YYYY
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        // Ustawienie danych paragonu w odpowiednich TextView
        holder.receiptNumberTextView.text = currentReceipt.receiptNumber
        holder.receiptDateTextView.text = dateFormat.format(currentReceipt.receiptDate)
        // Ustawienie daty weryfikacji lub tekstu "Brak", jeśli jest null
        holder.verificationDateTextView.text = currentReceipt.verificationDate?.let {
            dateFormat.format(it)
        } ?: "Brak"

        // Ustawienie danych klienta (opis, numer aplikacji, numer Amodit)
        // Używamy takeIf { it.isNotBlank() } aby traktować puste stringi jak null
        holder.clientDescriptionTextView.text = client?.description?.takeIf { it.isNotBlank() } ?: "Brak opisu klienta"

        // --- BINDOWANIE NOWYCH PÓL KLIENTA ---
        // Ustaw numer aplikacji klienta, jeśli istnieje i nie jest pusty
        val appNumberText = client?.clientAppNumber?.takeIf { it.isNotBlank() }?.let { "Nr aplikacji: $it" }
        holder.clientAppNumberTextView.text = appNumberText
        // Pokaż TextView tylko jeśli tekst nie jest null (czyli numer istnieje i nie jest pusty)
        holder.clientAppNumberTextView.isVisible = appNumberText != null

        // Ustaw numer Amodit, jeśli istnieje i nie jest pusty
        val amoditNumberText = client?.amoditNumber?.takeIf { it.isNotBlank() }?.let { "Amodit: $it" }
        holder.amoditNumberTextView.text = amoditNumberText
        // Pokaż TextView tylko jeśli tekst nie jest null
        holder.amoditNumberTextView.isVisible = amoditNumberText != null
        // --- KONIEC BINDOWANIA NOWYCH PÓL ---

        // Ustawienie listenera kliknięcia dla przycisku (ikony) edycji
        holder.editReceiptButton.setOnClickListener {
            // Wywołanie metody interfejsu przekazującej ID klikniętego paragonu
            editButtonClickListener.onEditButtonClick(currentReceipt.id)
        }
    }

    /**
     * Zwraca całkowitą liczbę elementów na liście.
     */
    override fun getItemCount() = receiptList.size
}