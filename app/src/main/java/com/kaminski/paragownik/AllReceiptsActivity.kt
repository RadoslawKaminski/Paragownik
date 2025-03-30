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
import com.kaminski.paragownik.viewmodel.AllReceiptsViewModel // NOWY ViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel // Potrzebny do mapy miniatur

/**
 * Aktywność wyświetlająca listę wszystkich paragonów ze wszystkich sklepów.
 * Paragony są sortowane wg numeru sklepu, a następnie wg daty weryfikacji.
 */
class AllReceiptsActivity : AppCompatActivity(), ReceiptAdapter.OnReceiptClickListener {

    // Widoki UI
    private lateinit var allReceiptsRecyclerView: RecyclerView
    private lateinit var titleTextView: TextView

    // Adapter i ViewModels
    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var allReceiptsViewModel: AllReceiptsViewModel // NOWY ViewModel
    private lateinit var storeViewModel: StoreViewModel // Do pobrania mapy miniatur

    /**
     * Metoda onCreate.
     */
    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_receipts) // Ustawienie nowego layoutu

        // Inicjalizacja widoków
        allReceiptsRecyclerView = findViewById(R.id.allReceiptsRecyclerView)
        allReceiptsRecyclerView.layoutManager = LinearLayoutManager(this)
        titleTextView = findViewById(R.id.allReceiptsTitleTextView)
        titleTextView.text = getString(R.string.all_receipts_activity_title) // Ustawienie tytułu

        // Inicjalizacja Adaptera (tryb STORE_LIST, aby pokazać dane klienta)
        receiptAdapter = ReceiptAdapter(emptyList(), this, DisplayMode.STORE_LIST)
        allReceiptsRecyclerView.adapter = receiptAdapter

        // Inicjalizacja ViewModeli
        allReceiptsViewModel = ViewModelProvider(this).get(AllReceiptsViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        // Obserwacja listy wszystkich paragonów
        allReceiptsViewModel.allReceipts.observe(this) { receiptsWithClients ->
            receiptsWithClients?.let {
                Log.d("AllReceiptsActivity", "Otrzymano ${it.size} wszystkich paragonów.")
                receiptAdapter.receiptList = it
                // Odświeżamy adapter dopiero po aktualizacji mapy miniatur
                // receiptAdapter.notifyDataSetChanged()
            }
        }

        // Obserwacja mapy miniatur klientów (z StoreViewModel)
        storeViewModel.clientThumbnailsMap.observe(this) { thumbnailsMap ->
            thumbnailsMap?.let {
                Log.d("AllReceiptsActivity", "Otrzymano mapę miniatur klientów, rozmiar: ${it.size}")
                receiptAdapter.updateClientThumbnailsMap(it)
                // Odśwież adapter, bo mogły już być załadowane paragony
                if (receiptAdapter.receiptList.isNotEmpty()) {
                    receiptAdapter.notifyDataSetChanged() // TODO: DiffUtil
                }
            }
        }
    }

    /**
     * Metoda wywoływana po kliknięciu elementu na liście paragonów.
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

