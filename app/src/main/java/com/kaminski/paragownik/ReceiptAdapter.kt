package com.kaminski.paragownik

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.ReceiptWithClient
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tryby wyświetlania dla adaptera paragonów, określające, które informacje pokazać.
 */
enum class DisplayMode {
    STORE_LIST, // Pokazuje dane klienta (opis, numery, zdjęcie)
    CLIENT_LIST // Pokazuje numer sklepu zamiast danych klienta
}

/**
 * Adapter dla RecyclerView wyświetlającego listę paragonów.
 * Może działać w dwóch trybach: pokazywania danych klienta lub numeru sklepu.
 */
class ReceiptAdapter(
    var receiptList: List<ReceiptWithClient>,
    private val itemClickListener: OnReceiptClickListener,
    private val displayMode: DisplayMode // Tryb wyświetlania decyduje o zawartości
) : RecyclerView.Adapter<ReceiptAdapter.ReceiptViewHolder>() {

    private lateinit var context: Context
    // Mapa przechowująca ID sklepu -> Numer sklepu (używana w trybie CLIENT_LIST)
    var storeMap: Map<Long, String> = emptyMap()
        private set // Ustawiana tylko przez metodę updateStoreMap

    /**
     * Interfejs dla obsługi kliknięcia elementu listy paragonów.
     */
    interface OnReceiptClickListener {
        fun onReceiptClick(receiptId: Long)
    }

    /**
     * ViewHolder przechowujący referencje do widoków elementu listy.
     */
    class ReceiptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val receiptNumberTextView: TextView = itemView.findViewById(R.id.receiptNumberTextView)
        val receiptDateTextView: TextView = itemView.findViewById(R.id.receiptDateTextView)
        val storeNumberTextView: TextView = itemView.findViewById(R.id.storeNumberTextView) // TextView dla numeru sklepu
        val verificationDateLayout: LinearLayout = itemView.findViewById(R.id.verificationDateLayout)
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
     * Aktualizuje mapę numerów sklepów używaną w trybie CLIENT_LIST.
     * @param newMap Nowa mapa [ID sklepu -> Numer sklepu].
     */
    fun updateStoreMap(newMap: Map<Long, String>) {
        storeMap = newMap
    }

    /**
     * Łączy dane z widokami w ViewHolderze w zależności od trybu wyświetlania.
     */
    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        val currentReceiptWithClient = receiptList[position]
        val currentReceipt = currentReceiptWithClient.receipt
        val client = currentReceiptWithClient.client

        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        // --- Dane wspólne dla obu trybów ---
        holder.receiptNumberTextView.text = currentReceipt.receiptNumber
        holder.receiptDateTextView.text = dateFormat.format(currentReceipt.receiptDate)
        val verificationDateText = currentReceipt.verificationDate?.let { dateFormat.format(it) }
        holder.verificationDateTextView.text = verificationDateText ?: context.getString(R.string.no_verification_date)
        holder.verificationDateLayout.isVisible = verificationDateText != null

        // --- Logika zależna od trybu wyświetlania ---
        when (displayMode) {
            DisplayMode.STORE_LIST -> {
                // Tryb listy paragonów sklepu: pokaż dane klienta, ukryj numer sklepu
                holder.storeNumberTextView.visibility = View.GONE

                // Pokaż opis klienta (lub "Brak opisu")
                holder.clientDescriptionTextView.text = client?.description?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.no_client_description)
                holder.clientDescriptionTextView.isVisible = !holder.clientDescriptionTextView.text.isNullOrBlank()

                // Pokaż numer aplikacji klienta, jeśli istnieje
                val appNumberText = client?.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
                    context.getString(R.string.client_item_app_number_prefix) + " " + it
                }
                holder.clientAppNumberTextView.text = appNumberText
                holder.clientAppNumberTextView.isVisible = appNumberText != null

                // Pokaż numer Amodit, jeśli istnieje
                val amoditNumberText = client?.amoditNumber?.takeIf { it.isNotBlank() }?.let {
                    context.getString(R.string.client_item_amodit_number_prefix) + " " + it
                }
                holder.amoditNumberTextView.text = amoditNumberText
                holder.amoditNumberTextView.isVisible = amoditNumberText != null

                // Pokaż miniaturę zdjęcia klienta, jeśli istnieje
                if (!client?.photoUri.isNullOrBlank()) {
                    try {
                        val photoUri = client!!.photoUri!!.toUri()
                        holder.clientPhotoImageView.setImageURI(photoUri)
                        holder.clientPhotoImageView.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        Log.w("ReceiptAdapter", "Błąd ładowania zdjęcia klienta ${client?.id}, URI: ${client?.photoUri}", e)
                        holder.clientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                        holder.clientPhotoImageView.visibility = View.VISIBLE
                    }
                } else {
                    holder.clientPhotoImageView.visibility = View.GONE
                }
            }
            DisplayMode.CLIENT_LIST -> {
                // Tryb listy paragonów klienta: pokaż numer sklepu, ukryj dane klienta
                holder.clientDescriptionTextView.visibility = View.GONE
                holder.clientAppNumberTextView.visibility = View.GONE
                holder.amoditNumberTextView.visibility = View.GONE
                holder.clientPhotoImageView.visibility = View.GONE

                // Pobierz i ustaw numer sklepu z mapy
                val storeNumber = storeMap[currentReceipt.storeId] ?: "?"
                // Dodano spację po prefiksie
                holder.storeNumberTextView.text = context.getString(R.string.store_number_prefix) + " " + storeNumber
                holder.storeNumberTextView.visibility = View.VISIBLE
            }
        }

        // Ustawienie listenera kliknięcia na cały element
        holder.itemView.setOnClickListener {
            itemClickListener.onReceiptClick(currentReceipt.id)
        }
    }

    /**
     * Zwraca liczbę elementów listy.
     */
    override fun getItemCount() = receiptList.size
}
