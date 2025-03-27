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
import com.kaminski.paragownik.data.ReceiptWithClient
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter dla RecyclerView wyświetlającego listę paragonów w [ReceiptListActivity].
 * Pokazuje miniaturę zdjęcia klienta.
 */
class ReceiptAdapter(
    var receiptList: List<ReceiptWithClient>,
    private val editButtonClickListener: OnEditButtonClickListener
) : RecyclerView.Adapter<ReceiptAdapter.ReceiptViewHolder>() {

    private lateinit var context: Context

    /**
     * Interfejs dla obsługi kliknięcia przycisku edycji.
     */
    interface OnEditButtonClickListener {
        fun onEditButtonClick(receiptId: Long)
    }

    /**
     * ViewHolder przechowujący referencje do widoków elementu listy.
     */
    class ReceiptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val receiptNumberTextView: TextView = itemView.findViewById(R.id.receiptNumberTextView)
        val receiptDateTextView: TextView = itemView.findViewById(R.id.receiptDateTextView)
        val verificationDateTextView: TextView = itemView.findViewById(R.id.verificationDateTextView)
        val clientDescriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
        val editReceiptButton: ImageView = itemView.findViewById(R.id.editReceiptButton)
        val clientAppNumberTextView: TextView = itemView.findViewById(R.id.clientAppNumberTextView)
        val amoditNumberTextView: TextView = itemView.findViewById(R.id.amoditNumberTextView)
        val clientPhotoImageView: ImageView = itemView.findViewById(R.id.receiptItemClientPhotoImageView) // Referencja do ImageView zdjęcia
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
     * Łączy dane z widokami w ViewHolderze, w tym ładuje miniaturę zdjęcia.
     */
    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        val currentReceiptWithClient = receiptList[position]
        val currentReceipt = currentReceiptWithClient.receipt
        val client = currentReceiptWithClient.client // Może być null, jeśli coś pójdzie nie tak z relacją

        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        // Ustawienie danych paragonu
        holder.receiptNumberTextView.text = currentReceipt.receiptNumber
        holder.receiptDateTextView.text = dateFormat.format(currentReceipt.receiptDate)
        holder.verificationDateTextView.text = currentReceipt.verificationDate?.let { dateFormat.format(it) }
            ?: context.getString(R.string.no_verification_date)

        // Ustawienie danych klienta
        holder.clientDescriptionTextView.text = client?.description?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.no_client_description)

        val appNumberText = client?.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_app_number_prefix) + " " + it
        }
        holder.clientAppNumberTextView.text = appNumberText
        holder.clientAppNumberTextView.isVisible = appNumberText != null

        val amoditNumberText = client?.amoditNumber?.takeIf { it.isNotBlank() }?.let {
            context.getString(R.string.client_item_amodit_number_prefix) + " " + it
        }
        holder.amoditNumberTextView.text = amoditNumberText
        holder.amoditNumberTextView.isVisible = amoditNumberText != null

        // Ładowanie miniatury zdjęcia klienta
        // UWAGA: Bezpośrednie użycie setImageURI w adapterze może być nieefektywne.
        // Rozważ użycie biblioteki Coil lub Glide w przyszłości.
        if (!client?.photoUri.isNullOrBlank()) {
            try {
                // Używamy !! po sprawdzeniu isNullOrBlank - bezpieczne
                val photoUri = client!!.photoUri!!.toUri()
                holder.clientPhotoImageView.setImageURI(photoUri)
            } catch (e: Exception) {
                // Błąd parsowania URI lub ładowania obrazu
                Log.w("ReceiptAdapter", "Błąd ładowania zdjęcia dla klienta ${client?.id}, URI: ${client?.photoUri}", e)
                holder.clientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder) // Placeholder w razie błędu
            }
        } else {
            // Jeśli brak URI, ustaw placeholder
            holder.clientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
        }

        // Ustawienie listenera dla przycisku edycji
        holder.editReceiptButton.setOnClickListener {
            editButtonClickListener.onEditButtonClick(currentReceipt.id)
        }
    }

    /**
     * Zwraca liczbę elementów listy.
     */
    override fun getItemCount() = receiptList.size
}
