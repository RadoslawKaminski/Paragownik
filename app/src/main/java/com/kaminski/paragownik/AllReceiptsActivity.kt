package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.viewmodel.AllReceiptsViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel
import com.kaminski.paragownik.data.ReceiptWithClient

/**
 * Aktywność wyświetlająca listę wszystkich paragonów ze wszystkich sklepów.
 * Paragony są grupowane według drogerii i sortowane wg numeru sklepu, a następnie wg daty weryfikacji.
 */
class AllReceiptsActivity : AppCompatActivity(), ReceiptAdapter.OnReceiptClickListener {

    private lateinit var allReceiptsRecyclerView: RecyclerView
    private lateinit var titleTextView: TextView

    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var allReceiptsViewModel: AllReceiptsViewModel
    private lateinit var storeViewModel: StoreViewModel

    private var currentReceipts: List<ReceiptWithClient>? = null
    private var currentStoreMap: Map<Long, String>? = null
    private var currentThumbnailsMap: Map<Long, String?>? = null


    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_receipts)

        allReceiptsRecyclerView = findViewById(R.id.allReceiptsRecyclerView)
        allReceiptsRecyclerView.layoutManager = LinearLayoutManager(this)
        titleTextView = findViewById(R.id.allReceiptsTitleTextView)
        titleTextView.text = getString(R.string.all_receipts_activity_title)

        receiptAdapter = ReceiptAdapter(this, DisplayMode.STORE_LIST)
        allReceiptsRecyclerView.adapter = receiptAdapter

        allReceiptsViewModel = ViewModelProvider(this).get(AllReceiptsViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        observeDataAndUpdateAdapter()
    }

    /**
     * Obserwuje wszystkie potrzebne LiveData (paragony, mapa sklepów, mapa miniatur)
     * i wywołuje aktualizację adaptera, gdy wszystkie dane są dostępne.
     */
    private fun observeDataAndUpdateAdapter() {
        allReceiptsViewModel.allReceipts.observe(this) { receipts ->
            Log.d("AllReceiptsActivity", "Otrzymano ${receipts?.size ?: 0} wszystkich paragonów.")
            currentReceipts = receipts
            tryUpdateAdapter()
        }

        storeViewModel.allStoresMap.observe(this) { storeMap ->
            Log.d("AllReceiptsActivity", "Otrzymano mapę sklepów, rozmiar: ${storeMap?.size ?: 0}")
            currentStoreMap = storeMap
            tryUpdateAdapter()
        }

        storeViewModel.clientThumbnailsMap.observe(this) { thumbnailsMap ->
            Log.d("AllReceiptsActivity", "Otrzymano mapę miniatur klientów, rozmiar: ${thumbnailsMap?.size ?: 0}")
            currentThumbnailsMap = thumbnailsMap
            tryUpdateAdapter()
        }
    }

    /**
     * Sprawdza, czy wszystkie potrzebne dane (paragony, mapa sklepów, mapa miniatur) zostały załadowane.
     * Jeśli tak, wywołuje metodę `updateReceipts` w adapterze, aby odświeżyć listę.
     */
    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil w ReceiptAdapter.updateReceipts
    private fun tryUpdateAdapter() {
        val receipts = currentReceipts
        val storeMap = currentStoreMap
        val thumbnailsMap = currentThumbnailsMap

        if (receipts != null && storeMap != null && thumbnailsMap != null) {
            Log.d("AllReceiptsActivity", "Wszystkie dane dostępne. Aktualizowanie adaptera...")
            receiptAdapter.updateReceipts(this, receipts, storeMap, thumbnailsMap, true)
        } else {
            Log.d("AllReceiptsActivity", "Nie wszystkie dane są jeszcze dostępne do aktualizacji adaptera.")
        }
    }


    /**
     * Metoda wywoływana po kliknięciu elementu paragonu na liście.
     * Uruchamia EditReceiptActivity dla wybranego paragonu.
     * Nie przekazuje kontekstu, aby ukryć przyciski nawigacyjne w EditReceiptActivity.
     * @param receiptId ID klikniętego paragonu.
     */
    override fun onReceiptClick(receiptId: Long) {
        val intent = Intent(this, EditReceiptActivity::class.java)
        intent.putExtra("RECEIPT_ID", receiptId)
        startActivity(intent)
    }
}




