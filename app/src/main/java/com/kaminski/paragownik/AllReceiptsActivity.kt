package com.kaminski.paragownik

import android.annotation.SuppressLint // Dodano import
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.viewmodel.AllReceiptsViewModel // NOWY ViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel // Potrzebny do mapy miniatur i sklepów
import androidx.lifecycle.MediatorLiveData // Dodano import dla MediatorLiveData
import com.kaminski.paragownik.data.ReceiptWithClient // Dodano import

/**
 * Aktywność wyświetlająca listę wszystkich paragonów ze wszystkich sklepów.
 * Paragony są grupowane według drogerii i sortowane wg numeru sklepu, a następnie wg daty weryfikacji.
 */
class AllReceiptsActivity : AppCompatActivity(), ReceiptAdapter.OnReceiptClickListener {

    // Widoki UI
    private lateinit var allReceiptsRecyclerView: RecyclerView
    private lateinit var titleTextView: TextView

    // Adapter i ViewModels
    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var allReceiptsViewModel: AllReceiptsViewModel
    private lateinit var storeViewModel: StoreViewModel

    // Dane potrzebne do aktualizacji adaptera
    private var currentReceipts: List<ReceiptWithClient>? = null
    private var currentStoreMap: Map<Long, String>? = null
    private var currentThumbnailsMap: Map<Long, String?>? = null


    /**
     * Metoda onCreate.
     */
    // Usunięto @SuppressLint, bo aktualizacja jest teraz w dedykowanej metodzie
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_receipts) // Ustawienie nowego layoutu

        // Inicjalizacja widoków
        allReceiptsRecyclerView = findViewById(R.id.allReceiptsRecyclerView)
        allReceiptsRecyclerView.layoutManager = LinearLayoutManager(this)
        titleTextView = findViewById(R.id.allReceiptsTitleTextView)
        titleTextView.text = getString(R.string.all_receipts_activity_title) // Ustawienie tytułu

        // Inicjalizacja Adaptera (tryb STORE_LIST, aby pokazać dane klienta w elemencie paragonu)
        // Adapter teraz wewnętrznie zarządza listą elementów (nagłówki + paragony)
        receiptAdapter = ReceiptAdapter(this, DisplayMode.STORE_LIST) // Usunięto początkową listę
        allReceiptsRecyclerView.adapter = receiptAdapter

        // Inicjalizacja ViewModeli
        allReceiptsViewModel = ViewModelProvider(this).get(AllReceiptsViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        // Obserwacja danych i aktualizacja adaptera, gdy wszystkie będą dostępne
        observeDataAndUpdateAdapter()
    }

    /**
     * Obserwuje wszystkie potrzebne LiveData i wywołuje aktualizację adaptera,
     * gdy wszystkie dane są dostępne.
     */
    private fun observeDataAndUpdateAdapter() {
        // Obserwacja listy wszystkich paragonów
        allReceiptsViewModel.allReceipts.observe(this) { receipts ->
            Log.d("AllReceiptsActivity", "Otrzymano ${receipts?.size ?: 0} wszystkich paragonów.")
            currentReceipts = receipts
            tryUpdateAdapter()
        }

        // Obserwacja mapy sklepów (z StoreViewModel)
        storeViewModel.allStoresMap.observe(this) { storeMap ->
            Log.d("AllReceiptsActivity", "Otrzymano mapę sklepów, rozmiar: ${storeMap?.size ?: 0}")
            currentStoreMap = storeMap
            tryUpdateAdapter()
        }

        // Obserwacja mapy miniatur klientów (z StoreViewModel)
        storeViewModel.clientThumbnailsMap.observe(this) { thumbnailsMap ->
            Log.d("AllReceiptsActivity", "Otrzymano mapę miniatur klientów, rozmiar: ${thumbnailsMap?.size ?: 0}")
            currentThumbnailsMap = thumbnailsMap
            tryUpdateAdapter()
        }
    }

    /**
     * Sprawdza, czy wszystkie potrzebne dane (paragony, mapa sklepów, mapa miniatur) są dostępne.
     * Jeśli tak, wywołuje metodę `updateReceipts` w adapterze.
     */
    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil w ReceiptAdapter.updateReceipts
    private fun tryUpdateAdapter() {
        val receipts = currentReceipts
        val storeMap = currentStoreMap
        val thumbnailsMap = currentThumbnailsMap

        // Sprawdź, czy wszystkie dane zostały już załadowane
        if (receipts != null && storeMap != null && thumbnailsMap != null) {
            Log.d("AllReceiptsActivity", "Wszystkie dane dostępne. Aktualizowanie adaptera...")
            // Wywołaj nową metodę adaptera, przekazując wszystkie potrzebne dane, w tym kontekst
            // Ustaw showStoreHeaders na true dla tego widoku
            receiptAdapter.updateReceipts(this, receipts, storeMap, thumbnailsMap, true)
        } else {
            Log.d("AllReceiptsActivity", "Nie wszystkie dane są jeszcze dostępne do aktualizacji adaptera.")
        }
    }


    /**
     * Metoda wywoływana po kliknięciu elementu paragonu na liście.
     * Uruchamia EditReceiptActivity dla wybranego paragonu.
     * Nie przekazujemy kontekstu, aby ukryć przyciski nawigacyjne w EditReceiptActivity.
     * @param receiptId ID klikniętego paragonu.
     */
    override fun onReceiptClick(receiptId: Long) {
        val intent = Intent(this, EditReceiptActivity::class.java)
        intent.putExtra("RECEIPT_ID", receiptId)
        // Nie dodajemy "CONTEXT", aby przyciski nawigacyjne w EditReceiptActivity były ukryte
        startActivity(intent)
    }
}
