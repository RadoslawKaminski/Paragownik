package com.kaminski.paragownik

import android.annotation.SuppressLint
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
import com.bumptech.glide.Glide
import com.kaminski.paragownik.data.ReceiptWithClient
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tryby wyświetlania dla adaptera paragonów, określające, które informacje pokazać.
 * W trybie STORE_LIST (używanym w AllReceiptsActivity i ReceiptListActivity)
 * adapter teraz również obsługuje wyświetlanie nagłówków z numerem sklepu.
 */
enum class DisplayMode {
    STORE_LIST, // Pokazuje dane klienta (opis, numery, zdjęcie) i potencjalnie nagłówki sklepów
    CLIENT_LIST // Pokazuje numer sklepu zamiast danych klienta (używane w ClientReceiptsActivity)
}

// Definicja typów elementów, które mogą być wyświetlane w RecyclerView
sealed interface DisplayableItem {
    // Reprezentuje nagłówek sekcji sklepu
    data class HeaderItem(val storeNumber: String) : DisplayableItem
    // Reprezentuje pojedynczy paragon
    data class ReceiptItem(val receiptWithClient: ReceiptWithClient) : DisplayableItem
}


/**
 * Adapter dla RecyclerView wyświetlającego listę paragonów.
 * Może działać w dwóch trybach: pokazywania danych klienta lub numeru sklepu.
 * W trybie STORE_LIST obsługuje teraz wyświetlanie nagłówków z numerem sklepu.
 * Używa Glide do ładowania miniatur zdjęć klientów.
 */
class ReceiptAdapter(
    private val itemClickListener: OnReceiptClickListener,
    private val displayMode: DisplayMode
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var storeMap: Map<Long, String> = emptyMap()
    private var clientThumbnailsMap: Map<Long, String?> = emptyMap()
    private var displayableItems: List<DisplayableItem> = emptyList()

    companion object {
        private const val VIEW_TYPE_RECEIPT = 0
        private const val VIEW_TYPE_HEADER = 1
    }

    /**
     * Interfejs dla obsługi kliknięcia elementu listy paragonów.
     */
    interface OnReceiptClickListener {
        fun onReceiptClick(receiptId: Long)
    }

    /**
     * ViewHolder dla elementu paragonu.
     */
    class ReceiptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val receiptNumberTextView: TextView = itemView.findViewById(R.id.receiptNumberTextView)
        val receiptDateTextView: TextView = itemView.findViewById(R.id.receiptDateTextView)
        val storeNumberTextView: TextView = itemView.findViewById(R.id.storeNumberTextView)
        val verificationDateLayout: LinearLayout = itemView.findViewById(R.id.verificationDateLayout)
        val verificationDateTextView: TextView = itemView.findViewById(R.id.verificationDateTextView)
        val clientDescriptionTextView: TextView = itemView.findViewById(R.id.clientDescriptionTextView)
        val clientAppNumberTextView: TextView = itemView.findViewById(R.id.clientAppNumberTextView)
        val amoditNumberTextView: TextView = itemView.findViewById(R.id.amoditNumberTextView)
        val clientPhotoImageView: ImageView = itemView.findViewById(R.id.receiptItemClientPhotoImageView)
    }

    /**
     * ViewHolder dla elementu nagłówka sklepu.
     */
    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerTextView: TextView = itemView.findViewById(R.id.headerTextView)
    }

    /**
     * Tworzy nowy ViewHolder odpowiedniego typu (nagłówek lub paragon).
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.receipt_header_item, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_RECEIPT -> {
                val view = inflater.inflate(R.layout.receipt_item, parent, false)
                ReceiptViewHolder(view)
            }
            else -> throw IllegalArgumentException("Nieznany typ widoku: $viewType")
        }
    }

    /**
     * Zwraca typ widoku dla elementu na danej pozycji.
     */
    override fun getItemViewType(position: Int): Int {
        return when (displayableItems[position]) {
            is DisplayableItem.HeaderItem -> VIEW_TYPE_HEADER
            is DisplayableItem.ReceiptItem -> VIEW_TYPE_RECEIPT
        }
    }

    /**
     * Zwraca całkowitą liczbę elementów do wyświetlenia (nagłówki + paragony).
     */
    override fun getItemCount(): Int = displayableItems.size

    /**
     * Aktualizuje dane adaptera, przetwarzając listę paragonów na listę
     * elementów do wyświetlenia (nagłówki + paragony).
     * @param context Kontekst aplikacji (potrzebny do pobrania stringów).
     * @param receipts Lista paragonów z klientami (posortowana!).
     * @param storeMap Mapa ID sklepu -> Numer sklepu.
     * @param clientThumbnails Mapa ID klienta -> URI miniatury.
     * @param showStoreHeaders Czy pokazywać nagłówki z numerem sklepu (dla trybu STORE_LIST).
     */
    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil dla lepszej wydajności
    fun updateReceipts(
        context: Context,
        receipts: List<ReceiptWithClient>,
        storeMap: Map<Long, String>,
        clientThumbnails: Map<Long, String?>,
        showStoreHeaders: Boolean
    ) {
        this.storeMap = storeMap
        this.clientThumbnailsMap = clientThumbnails

        val newItems = mutableListOf<DisplayableItem>()
        var lastStoreId: Long? = null

        for (receiptWithClient in receipts) {
            val currentStoreId = receiptWithClient.receipt.storeId
            if (showStoreHeaders && displayMode == DisplayMode.STORE_LIST && currentStoreId != lastStoreId) {
                val storeNumber = storeMap[currentStoreId] ?: "?"
                newItems.add(DisplayableItem.HeaderItem(context.getString(R.string.store_number_prefix) + " " + storeNumber))
                lastStoreId = currentStoreId
            }
            newItems.add(DisplayableItem.ReceiptItem(receiptWithClient))
        }

        displayableItems = newItems
        notifyDataSetChanged()
    }


    /**
     * Łączy dane z widokami w odpowiednim ViewHolderze (nagłówek lub paragon).
     * Używa Glide do ładowania miniatur zdjęć klientów.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayableItems[position]
        val context = holder.itemView.context

        when (holder) {
            is HeaderViewHolder -> {
                val headerItem = item as DisplayableItem.HeaderItem
                holder.headerTextView.text = headerItem.storeNumber
                holder.itemView.setOnClickListener(null)
            }
            is ReceiptViewHolder -> {
                val receiptItem = item as DisplayableItem.ReceiptItem
                val currentReceiptWithClient = receiptItem.receiptWithClient
                val currentReceipt = currentReceiptWithClient.receipt
                val client = currentReceiptWithClient.client

                val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

                holder.receiptNumberTextView.text = currentReceipt.receiptNumber
                holder.receiptDateTextView.text = dateFormat.format(currentReceipt.receiptDate)

                val verificationDateText = currentReceipt.verificationDate?.let { dateFormat.format(it) }
                holder.verificationDateLayout.isVisible = true
                holder.verificationDateTextView.text = verificationDateText ?: context.getString(R.string.no_verification_date)

                when (displayMode) {
                    DisplayMode.STORE_LIST -> {
                        holder.storeNumberTextView.visibility = View.GONE

                        if (client != null) {
                            holder.clientDescriptionTextView.text = if (client.description.isNullOrBlank()) {
                                context.getString(R.string.no_client_description)
                            } else {
                                client.description
                            }
                            holder.clientDescriptionTextView.isVisible = true

                            val appNumberText = client.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
                                context.getString(R.string.client_item_app_number_prefix) + " " + it
                            }
                            holder.clientAppNumberTextView.text = appNumberText
                            holder.clientAppNumberTextView.isVisible = appNumberText != null

                            val amoditNumberText = client.amoditNumber?.takeIf { it.isNotBlank() }?.let {
                                context.getString(R.string.client_item_amodit_number_prefix) + " " + it
                            }
                            holder.amoditNumberTextView.text = amoditNumberText
                            holder.amoditNumberTextView.isVisible = amoditNumberText != null

                            val thumbnailUriString = clientThumbnailsMap[client.id]
                            if (!thumbnailUriString.isNullOrBlank()) {
                                Glide.with(context)
                                    .load(thumbnailUriString.toUri())
                                    .placeholder(R.drawable.ic_photo_placeholder)
                                    .error(R.drawable.ic_photo_placeholder)
                                    .centerCrop()
                                    .into(holder.clientPhotoImageView)
                                holder.clientPhotoImageView.visibility = View.VISIBLE
                            } else {
                                Glide.with(context).clear(holder.clientPhotoImageView)
                                holder.clientPhotoImageView.visibility = View.GONE
                            }
                        } else {
                            holder.clientDescriptionTextView.text = context.getString(R.string.error_client_not_found)
                            holder.clientDescriptionTextView.isVisible = true
                            holder.clientAppNumberTextView.visibility = View.GONE
                            holder.amoditNumberTextView.visibility = View.GONE
                            Glide.with(context).clear(holder.clientPhotoImageView)
                            holder.clientPhotoImageView.visibility = View.GONE
                        }
                    }
                    DisplayMode.CLIENT_LIST -> {
                        holder.clientDescriptionTextView.visibility = View.GONE
                        holder.clientAppNumberTextView.visibility = View.GONE
                        holder.amoditNumberTextView.visibility = View.GONE
                        holder.clientPhotoImageView.visibility = View.GONE

                        val storeNumber = storeMap[currentReceipt.storeId] ?: "?"
                        holder.storeNumberTextView.text = context.getString(R.string.store_number_prefix) + " " + storeNumber
                        holder.storeNumberTextView.visibility = View.VISIBLE
                    }
                }

                holder.itemView.setOnClickListener {
                    itemClickListener.onReceiptClick(currentReceipt.id)
                }
            }
        }
    }
}





