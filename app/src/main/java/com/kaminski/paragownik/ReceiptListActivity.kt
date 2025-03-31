package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.viewmodel.ReceiptViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel
import kotlinx.coroutines.launch

/**
 * Aktywność wyświetlająca listę paragonów dla konkretnej drogerii.
 * Kliknięcie paragonu przenosi do widoku szczegółów/edycji.
 * Używa ReceiptAdapter w trybie STORE_LIST.
 */
class ReceiptListActivity : AppCompatActivity(), ReceiptAdapter.OnReceiptClickListener {

    private lateinit var receiptRecyclerView: RecyclerView
    private lateinit var fabAddClient: FloatingActionButton
    private lateinit var titleTextView: TextView

    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var receiptViewModel: ReceiptViewModel
    private lateinit var storeViewModel: StoreViewModel

    private var storeId: Long = -1L

    private var currentReceipts: List<ReceiptWithClient>? = null
    private var currentStoreMap: Map<Long, String>? = null
    private var currentThumbnailsMap: Map<Long, String?>? = null

    /**
     * Metoda onCreate.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_list)

        receiptRecyclerView = findViewById(R.id.receiptRecyclerView)
        receiptRecyclerView.layoutManager = LinearLayoutManager(this)
        titleTextView = findViewById(R.id.receiptListTitleTextView)
        fabAddClient = findViewById(R.id.fabAddClient)

        receiptAdapter = ReceiptAdapter(this, DisplayMode.STORE_LIST)
        receiptRecyclerView.adapter = receiptAdapter

        receiptViewModel = ViewModelProvider(this).get(ReceiptViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        storeId = intent.getLongExtra("STORE_ID", -1L)
        Log.d("ReceiptListActivity", "Otrzymano STORE_ID: $storeId")
        if (storeId == -1L) {
            Log.e("ReceiptListActivity", "Nieprawidłowe STORE_ID (-1) otrzymane w Intencie.")
            Toast.makeText(this, R.string.error_invalid_store_id, Toast.LENGTH_LONG).show()
            titleTextView.text = getString(R.string.receipt_list_activity_title_prefix) + " ?"
            finish()
            return
        }

        lifecycleScope.launch {
            val store = storeViewModel.getStoreById(storeId)
            val titlePrefix = getString(R.string.receipt_list_activity_title_prefix)
            titleTextView.text = if (store != null) {
                titlePrefix + " " + store.storeNumber
            } else {
                Log.w("ReceiptListActivity", "Nie znaleziono sklepu o ID $storeId do ustawienia tytułu.")
                titlePrefix + " ?"
            }
        }

        receiptViewModel.loadReceiptsForStore(storeId)

        observeDataAndUpdateAdapter()

        fabAddClient.setOnClickListener {
            val intent = Intent(this, AddClientActivity::class.java)
            intent.putExtra("STORE_ID", storeId)
            Log.d("ReceiptListActivity", "Uruchamiam AddClientActivity z STORE_ID: $storeId")
            startActivity(intent)
        }
    }

    /**
     * Obserwuje wszystkie potrzebne LiveData i wywołuje aktualizację adaptera,
     * gdy wszystkie dane są dostępne.
     */
    private fun observeDataAndUpdateAdapter() {
        receiptViewModel.receiptsForStore.observe(this) { receipts ->
            Log.d("ReceiptListActivity", "Otrzymano ${receipts?.size ?: 0} paragonów dla sklepu $storeId.")
            currentReceipts = receipts
            tryUpdateAdapter()
        }

        storeViewModel.allStoresMap.observe(this) { storeMap ->
            Log.d("ReceiptListActivity", "Otrzymano mapę sklepów, rozmiar: ${storeMap?.size ?: 0}")
            currentStoreMap = storeMap
            tryUpdateAdapter()
        }

        storeViewModel.clientThumbnailsMap.observe(this) { thumbnailsMap ->
            Log.d("ReceiptListActivity", "Otrzymano mapę miniatur klientów, rozmiar: ${thumbnailsMap?.size ?: 0}")
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

        if (receipts != null && storeMap != null && thumbnailsMap != null) {
            Log.d("ReceiptListActivity", "Wszystkie dane dostępne. Aktualizowanie adaptera...")
            receiptAdapter.updateReceipts(this, receipts, storeMap, thumbnailsMap, false)
        } else {
            Log.d("ReceiptListActivity", "Nie wszystkie dane są jeszcze dostępne do aktualizacji adaptera.")
        }
    }


    /**
     * Metoda wywoływana po kliknięciu elementu na liście paragonów.
     * Uruchamia EditReceiptActivity dla wybranego paragonu, przekazując kontekst.
     * @param receiptId ID klikniętego paragonu.
     */
    override fun onReceiptClick(receiptId: Long) {
        val intent = Intent(this, EditReceiptActivity::class.java)
        intent.putExtra("RECEIPT_ID", receiptId)
        intent.putExtra("CONTEXT", "STORE_LIST")
        startActivity(intent)
    }
}


