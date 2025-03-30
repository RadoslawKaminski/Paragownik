package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Context // Dodano import Context
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
 */
class ReceiptAdapter(
    // Usunięto listę z konstruktora, adapter zarządza nią wewnętrznie
    private val itemClickListener: OnReceiptClickListener,
    private val displayMode: DisplayMode
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() { // Zmieniono na RecyclerView.ViewHolder

    // Usunięto lateinit var context, będzie przekazywany do updateReceipts
    // Mapa przechowująca ID sklepu -> Numer sklepu (używana w trybie CLIENT_LIST i do nagłówków)
    private var storeMap: Map<Long, String> = emptyMap()
    // Mapa przechowująca ID klienta -> URI miniatury (używana w trybie STORE_LIST)
    private var clientThumbnailsMap: Map<Long, String?> = emptyMap()
    // Lista elementów do wyświetlenia (nagłówki i paragony)
    private var displayableItems: List<DisplayableItem> = emptyList()

    // Stałe definiujące typy widoków
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
     * ViewHolder dla elementu paragonu (bez zmian).
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
        val headerTextView: TextView = itemView.findViewById(R.id.headerTextView) // ID z nowego layoutu
    }

    /**
     * Tworzy nowy ViewHolder odpowiedniego typu (nagłówek lub paragon).
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // Kontekst jest teraz pobierany tutaj, ale nie jest już potrzebny jako pole lateinit
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        // Zwraca odpowiedni ViewHolder w zależności od viewType
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
        // Sprawdza typ elementu w przygotowanej liście displayableItems
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
        context: Context, // Dodano parametr Context
        receipts: List<ReceiptWithClient>,
        storeMap: Map<Long, String>,
        clientThumbnails: Map<Long, String?>,
        showStoreHeaders: Boolean // Dodano flagę do kontrolowania nagłówków
    ) {
        this.storeMap = storeMap // Zapisz mapę sklepów
        this.clientThumbnailsMap = clientThumbnails // Zapisz mapę miniatur

        val newItems = mutableListOf<DisplayableItem>()
        var lastStoreId: Long? = null

        for (receiptWithClient in receipts) {
            val currentStoreId = receiptWithClient.receipt.storeId
            // Jeśli ID sklepu się zmieniło (lub to pierwszy element), jesteśmy w trybie STORE_LIST
            // i flaga showStoreHeaders jest true, dodaj nagłówek
            if (showStoreHeaders && displayMode == DisplayMode.STORE_LIST && currentStoreId != lastStoreId) {
                val storeNumber = storeMap[currentStoreId] ?: "?" // Pobierz numer sklepu z mapy
                // Używamy przekazanego kontekstu do pobrania stringa
                newItems.add(DisplayableItem.HeaderItem(context.getString(R.string.store_number_prefix) + " " + storeNumber))
                lastStoreId = currentStoreId
            }
            // Zawsze dodawaj element paragonu
            newItems.add(DisplayableItem.ReceiptItem(receiptWithClient))
        }

        displayableItems = newItems
        notifyDataSetChanged() // Powiadom o zmianie danych
    }


    /**
     * Łączy dane z widokami w odpowiednim ViewHolderze (nagłówek lub paragon).
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = displayableItems[position] // Pobierz element z przygotowanej listy
        val context = holder.itemView.context // Pobierz kontekst z widoku holdera

        when (holder) {
            // Bindowanie danych dla nagłówka
            is HeaderViewHolder -> {
                val headerItem = item as DisplayableItem.HeaderItem
                holder.headerTextView.text = headerItem.storeNumber
                // Nagłówki nie są klikalne
                holder.itemView.setOnClickListener(null)
            }
            // Bindowanie danych dla paragonu
            is ReceiptViewHolder -> {
                val receiptItem = item as DisplayableItem.ReceiptItem
                val currentReceiptWithClient = receiptItem.receiptWithClient
                val currentReceipt = currentReceiptWithClient.receipt
                val client = currentReceiptWithClient.client

                val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

                // --- Dane wspólne dla obu trybów ---
                holder.receiptNumberTextView.text = currentReceipt.receiptNumber
                holder.receiptDateTextView.text = dateFormat.format(currentReceipt.receiptDate)

                // --- Obsługa daty weryfikacji ---
                val verificationDateText = currentReceipt.verificationDate?.let { dateFormat.format(it) }
                // Layout weryfikacji jest teraz zawsze widoczny
                holder.verificationDateLayout.isVisible = true
                // Ustaw tekst daty lub informację o jej braku
                holder.verificationDateTextView.text = verificationDateText ?: context.getString(R.string.no_verification_date)

                // --- Logika zależna od trybu wyświetlania ---
                when (displayMode) {
                    DisplayMode.STORE_LIST -> {
                        // Tryb listy paragonów sklepu: pokaż dane klienta, ukryj numer sklepu (w elemencie paragonu)
                        holder.storeNumberTextView.visibility = View.GONE // Numer sklepu jest teraz w nagłówku

                        if (client != null) {
                            // Pokaż opis klienta lub informację o jego braku
                            holder.clientDescriptionTextView.text = if (client.description.isNullOrBlank()) {
                                context.getString(R.string.no_client_description) // Użyj nowego stringa
                            } else {
                                client.description
                            }
                            holder.clientDescriptionTextView.isVisible = true // Zawsze widoczne, jeśli jest klient

                            // Pokaż numer aplikacji klienta, jeśli istnieje
                            val appNumberText = client.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
                                context.getString(R.string.client_item_app_number_prefix) + " " + it
                            }
                            holder.clientAppNumberTextView.text = appNumberText
                            holder.clientAppNumberTextView.isVisible = appNumberText != null

                            // Pokaż numer Amodit, jeśli istnieje
                            val amoditNumberText = client.amoditNumber?.takeIf { it.isNotBlank() }?.let {
                                context.getString(R.string.client_item_amodit_number_prefix) + " " + it
                            }
                            holder.amoditNumberTextView.text = amoditNumberText
                            holder.amoditNumberTextView.isVisible = amoditNumberText != null

                            // Pokaż miniaturę zdjęcia klienta, jeśli istnieje
                            val thumbnailUriString = clientThumbnailsMap[client.id]
                            if (!thumbnailUriString.isNullOrBlank()) {
                                try {
                                    holder.clientPhotoImageView.setImageURI(thumbnailUriString.toUri())
                                    holder.clientPhotoImageView.visibility = View.VISIBLE
                                } catch (e: Exception) {
                                    Log.w("ReceiptAdapter", "Błąd ładowania miniatury klienta ${client.id}, URI: $thumbnailUriString", e)
                                    holder.clientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                                    holder.clientPhotoImageView.visibility = View.VISIBLE
                                }
                            } else {
                                holder.clientPhotoImageView.visibility = View.GONE
                            }
                        } else {
                            // Sytuacja awaryjna - brak danych klienta
                            holder.clientDescriptionTextView.text = context.getString(R.string.error_client_not_found)
                            holder.clientDescriptionTextView.isVisible = true
                            holder.clientAppNumberTextView.visibility = View.GONE
                            holder.amoditNumberTextView.visibility = View.GONE
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
                        holder.storeNumberTextView.text = context.getString(R.string.store_number_prefix) + " " + storeNumber
                        holder.storeNumberTextView.visibility = View.VISIBLE
                    }
                }

                // Ustawienie listenera kliknięcia na cały element paragonu
                holder.itemView.setOnClickListener {
                    itemClickListener.onReceiptClick(currentReceipt.id)
                }
            }
        }
    }
}
