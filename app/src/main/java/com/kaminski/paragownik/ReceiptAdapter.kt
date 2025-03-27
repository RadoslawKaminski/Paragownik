package com.kaminski.paragownik

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout // Import dla LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.ReceiptWithClient
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter dla RecyclerView wyświetlającego listę paragonów w [ReceiptListActivity].
 * Kliknięcie elementu uruchamia widok szczegółów paragonu.
 */
class ReceiptAdapter(
    var receiptList: List<ReceiptWithClient>,
    private val itemClickListener: OnReceiptClickListener // Zmieniono nazwę listenera
) : RecyclerView.Adapter<ReceiptAdapter.ReceiptViewHolder>() {

    private lateinit var context: Context

    /**
     * Interfejs dla obsługi kliknięcia elementu listy paragonów.
     */
    interface OnReceiptClickListener { // Zmieniono nazwę interfejsu
        /**
         * Wywoływane, gdy użytkownik kliknie element reprezentujący paragon.
         * @param receiptId ID klikniętego paragonu.
         */
        fun onReceiptClick(receiptId: Long) // Zmieniono nazwę metody
    }

    /**
     * ViewHolder przechowujący referencje do widoków elementu listy.
     */
    class ReceiptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val receiptNumberTextView: TextView = itemView.findViewById(R.id.receiptNumberTextView)
        val receiptDateTextView: TextView = itemView.findViewById(R.id.receiptDateTextView)
        val verificationDateLayout: LinearLayout = itemView.findViewById(R.id.verificationDateLayout) // Layout do ukrywania
        val verificationDateTextView: TextView = itemView.findViewById(R.id.verificationDateTextView)
        val clientDescriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
        val clientAppNumberTextView: TextView = itemView.findViewById(R.id.clientAppNumberTextView)
        val amoditNumberTextView: TextView = itemView.findViewById(R.id.amoditNumberTextView)
        val clientPhotoImageView: ImageView = itemView.findViewById(R.id.receiptItemClientPhotoImageView)
    }

    /**
     * Tworzy nowy ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptViewHolder {
        context = parent.context
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.receipt_item, parent, false)
        return ReceiptViewHolder(itemView)
    }

    /**
     * Łączy dane z widokami w ViewHolderze, w tym ładuje miniaturę zdjęcia
     * i ustawia listener kliknięcia na cały element.
     */
    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        val currentReceiptWithClient = receiptList[position]
        val currentReceipt = currentReceiptWithClient.receipt
        val client = currentReceiptWithClient.client

        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        // Ustawienie danych paragonu
        holder.receiptNumberTextView.text = currentReceipt.receiptNumber
        holder.receiptDateTextView.text = dateFormat.format(currentReceipt.receiptDate)

        // Ustawienie daty weryfikacji i widoczności layoutu
        val verificationDateText = currentReceipt.verificationDate?.let { dateFormat.format(it) }
        holder.verificationDateTextView.text = verificationDateText ?: context.getString(R.string.no_verification_date)
        holder.verificationDateLayout.isVisible = verificationDateText != null // Pokaż tylko jeśli data istnieje

        // Ustawienie danych klienta
        holder.clientDescriptionTextView.text = client?.description?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.no_client_description)
        holder.clientDescriptionTextView.isVisible = !holder.clientDescriptionTextView.text.isNullOrBlank() // Pokaż tylko jeśli opis istnieje

        val appNumberText = client?.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_app_number_prefix) + " " + it
        }
        holder.clientAppNumberTextView.text = appNumberText
        holder.clientAppNumberTextView.isVisible = appNumberText != null // Pokaż tylko jeśli numer istnieje

        val amoditNumberText = client?.amoditNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_amodit_number_prefix) + " " + it
        }
        holder.amoditNumberTextView.text = amoditNumberText
        holder.amoditNumberTextView.isVisible = amoditNumberText != null // Pokaż tylko jeśli numer istnieje

        // Ładowanie miniatury zdjęcia klienta
        if (!client?.photoUri.isNullOrBlank()) {
            try {
                val photoUri = client!!.photoUri!!.toUri()
                holder.clientPhotoImageView.setImageURI(photoUri)
                holder.clientPhotoImageView.visibility = View.VISIBLE // Pokaż ImageView
            } catch (e: Exception) {
                Log.w("ReceiptAdapter", "Błąd ładowania zdjęcia dla klienta ${client?.id}, URI: ${client?.photoUri}", e)
                holder.clientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                holder.clientPhotoImageView.visibility = View.VISIBLE // Pokaż placeholder w razie błędu
            }
        } else {
            // Jeśli brak URI, ukryj ImageView
            holder.clientPhotoImageView.visibility = View.GONE
        }

        // Ustawienie listenera kliknięcia na cały element
        holder.itemView.setOnClickListener {
            itemClickListener.onReceiptClick(currentReceipt.id) // Wywołanie nowej metody interfejsu
        }
    }

    /**
     * Zwraca liczbę elementów listy.
     */
    override fun getItemCount() = receiptList.size
}
