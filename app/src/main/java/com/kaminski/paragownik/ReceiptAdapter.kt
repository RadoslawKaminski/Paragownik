package com.kaminski.paragownik

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.ReceiptWithClient
import java.text.SimpleDateFormat
import java.util.Locale

class ReceiptAdapter(
    var receiptList: List<ReceiptWithClient>,
    private val editButtonClickListener: OnEditButtonClickListener

) :
    RecyclerView.Adapter<ReceiptAdapter.ReceiptViewHolder>() {

    interface OnEditButtonClickListener {
        fun onEditButtonClick(receiptId: Long)
    }
    class ReceiptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val receiptNumberTextView: TextView = itemView.findViewById(R.id.receiptNumberTextView)
        val receiptDateTextView: TextView = itemView.findViewById(R.id.receiptDateTextView)
        val verificationDateTextView: TextView = itemView.findViewById(R.id.verificationDateTextView)
        val clientDescriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
        val editReceiptButton: ImageView = itemView.findViewById(R.id.editReceiptButton) // Zmiana na ImageView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.receipt_item, parent, false)
        return ReceiptViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        val currentReceiptWithClient = receiptList[position]
        val currentReceipt = currentReceiptWithClient.receipt
        val client = currentReceiptWithClient.client // Pobierz klienta bezpośrednio z ReceiptWithClient
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        holder.receiptNumberTextView.text = currentReceipt.receiptNumber // Bez "Numer paragonu: "
        holder.receiptDateTextView.text = dateFormat.format(currentReceipt.receiptDate) // Bez "Data paragonu: "
        holder.verificationDateTextView.text = currentReceipt.verificationDate?.let {
            dateFormat.format(it)
        } ?: "Brak" // Zmieniono "Data weryfikacji: Brak" na "Brak"
        holder.clientDescriptionTextView.text = client?.description ?: "Brak opisu klienta" // Użyj client?.description

        holder.editReceiptButton.setOnClickListener { // Listener kliknięcia dla ImageView
            editButtonClickListener.onEditButtonClick(currentReceipt.id)
        }
    }

    override fun getItemCount() = receiptList.size
}