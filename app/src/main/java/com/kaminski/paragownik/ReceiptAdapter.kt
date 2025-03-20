package com.kaminski.paragownik

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.ReceiptWithClient
import java.text.SimpleDateFormat
import java.util.Locale

class ReceiptAdapter(var receiptList: List<ReceiptWithClient>) :
    RecyclerView.Adapter<ReceiptAdapter.ReceiptViewHolder>() {

    class ReceiptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val receiptNumberTextView: TextView = itemView.findViewById(R.id.receiptNumberTextView)
        val receiptDateTextView: TextView = itemView.findViewById(R.id.receiptDateTextView)
        val verificationDateTextView: TextView = itemView.findViewById(R.id.verificationDateTextView)
        val clientDescriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.receipt_item, parent, false)
        return ReceiptViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        val currentReceiptWithClient = receiptList[position] // Zmień typ zmiennej
        val currentReceipt = currentReceiptWithClient.receipt // Pobierz Receipt z ReceiptWithClient
        val client = currentReceiptWithClient.clients.firstOrNull() // Pobierz pierwszego klienta z listy (powinien być co najwyżej jeden)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        holder.receiptNumberTextView.text = "Numer paragonu: ${currentReceipt.receiptNumber}"
        holder.receiptDateTextView.text = "Data paragonu: ${dateFormat.format(currentReceipt.receiptDate)}"
        holder.verificationDateTextView.text = currentReceipt.verificationDate?.let {
            "Data weryfikacji: ${dateFormat.format(it)}"
        } ?: "Data weryfikacji: Brak"
        holder.clientDescriptionTextView.text = client?.description ?: "Brak opisu klienta" // Wyświetl opis klienta lub "Brak opisu klienta"
    }

    override fun getItemCount() = receiptList.size
}