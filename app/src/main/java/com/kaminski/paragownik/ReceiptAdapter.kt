package com.kaminski.paragownik

import android.content.Context // Potrzebne do pobierania stringów z zasobów
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
    var receiptList: List<ReceiptWithClient>,
    // Listener (zazwyczaj Aktywność), który będzie powiadamiany o kliknięciu przycisku edycji.
    private val editButtonClickListener: OnEditButtonClickListener
) : RecyclerView.Adapter<ReceiptAdapter.ReceiptViewHolder>() {

    // Przechowuje kontekst, potrzebny do dostępu do zasobów (np. stringów)
    private lateinit var context: Context

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
     */
    class ReceiptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Referencje do widoków w layoucie receipt_item.xml
        val receiptNumberTextView: TextView = itemView.findViewById(R.id.receiptNumberTextView)
        val receiptDateTextView: TextView = itemView.findViewById(R.id.receiptDateTextView)
        val verificationDateTextView: TextView = itemView.findViewById(R.id.verificationDateTextView)
        val clientDescriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
        val editReceiptButton: ImageView = itemView.findViewById(R.id.editReceiptButton)
        val clientAppNumberTextView: TextView = itemView.findViewById(R.id.clientAppNumberTextView)
        val amoditNumberTextView: TextView = itemView.findViewById(R.id.amoditNumberTextView)
    }

    /**
     * Tworzy nowy obiekt ViewHolder, gdy RecyclerView potrzebuje nowego elementu do wyświetlenia.
     * Influje widok z pliku XML `receipt_item.xml` i pobiera kontekst.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptViewHolder {
        context = parent.context // Pobranie kontekstu
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.receipt_item, parent, false)
        return ReceiptViewHolder(itemView)
    }

    /**
     * Łączy dane z określonej pozycji listy (`position`) z widokami wewnątrz ViewHoldera (`holder`).
     */
    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        val currentReceiptWithClient = receiptList[position]
        val currentReceipt = currentReceiptWithClient.receipt
        val client = currentReceiptWithClient.client

        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        // Ustawienie danych paragonu
        holder.receiptNumberTextView.text = currentReceipt.receiptNumber
        holder.receiptDateTextView.text = dateFormat.format(currentReceipt.receiptDate)

        // Ustawienie daty weryfikacji (z obsługą null)
        holder.verificationDateTextView.text = currentReceipt.verificationDate?.let { date ->
            dateFormat.format(date)
        } ?: context.getString(R.string.no_verification_date)

        // Ustawienie danych klienta
        holder.clientDescriptionTextView.text = client?.description?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.no_client_description)

        // Ustawienie numeru aplikacji klienta z jawnym dodaniem spacji
        val appNumberText = client?.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_app_number_prefix) + " " + it // Dodano spację
        }
        holder.clientAppNumberTextView.text = appNumberText
        holder.clientAppNumberTextView.isVisible = appNumberText != null

        // Ustawienie numeru Amodit z jawnym dodaniem spacji
        val amoditNumberText = client?.amoditNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_amodit_number_prefix) + " " + it // Dodano spację
        }
        holder.amoditNumberTextView.text = amoditNumberText
        holder.amoditNumberTextView.isVisible = amoditNumberText != null

        // Ustawienie listenera dla przycisku edycji
        holder.editReceiptButton.setOnClickListener {
            editButtonClickListener.onEditButtonClick(currentReceipt.id)
        }
    }

    /**
     * Zwraca całkowitą liczbę elementów na liście danych.
     */
    override fun getItemCount() = receiptList.size
}
