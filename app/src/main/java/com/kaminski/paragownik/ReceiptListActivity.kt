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
import com.kaminski.paragownik.viewmodel.ReceiptViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel
import kotlinx.coroutines.launch

/**
 * Aktywność wyświetlająca listę paragonów dla konkretnej drogerii.
 * Kliknięcie paragonu przenosi do widoku szczegółów/edycji.
 */
// Zmieniono implementowany interfejs
class ReceiptListActivity : AppCompatActivity(), ReceiptAdapter.OnReceiptClickListener {

    // Widoki UI
    private lateinit var receiptRecyclerView: RecyclerView
    private lateinit var fabAddClient: FloatingActionButton
    private lateinit var titleTextView: TextView

    // Adapter i ViewModels
    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var receiptViewModel: ReceiptViewModel
    private lateinit var storeViewModel: StoreViewModel

    // ID sklepu
    private var storeId: Long = -1L

    /**
     * Metoda onCreate.
     */
    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_list)

        // Inicjalizacja widoków
        receiptRecyclerView = findViewById(R.id.receiptRecyclerView)
        receiptRecyclerView.layoutManager = LinearLayoutManager(this)
        titleTextView = findViewById(R.id.receiptListTitleTextView)
        fabAddClient = findViewById(R.id.fabAddClient)

        // Inicjalizacja Adaptera (przekazujemy 'this' jako OnReceiptClickListener)
        receiptAdapter = ReceiptAdapter(emptyList(), this)
        receiptRecyclerView.adapter = receiptAdapter

        // Inicjalizacja ViewModeli
        receiptViewModel = ViewModelProvider(this).get(ReceiptViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        // Pobranie i walidacja ID sklepu
        storeId = intent.getLongExtra("STORE_ID", -1L)
        Log.d("ReceiptListActivity", "Otrzymano STORE_ID: $storeId")
        if (storeId == -1L) {
            Log.e("ReceiptListActivity", "Nieprawidłowe STORE_ID (-1) otrzymane w Intencie.")
            Toast.makeText(this, R.string.error_invalid_store_id, Toast.LENGTH_LONG).show()
            titleTextView.text = getString(R.string.receipt_list_activity_title_prefix) + " ?"
            finish()
            return
        }

        // Ustawienie dynamicznego tytułu
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

        // Ładowanie i obserwacja paragonów
        receiptViewModel.loadReceiptsForStore(storeId)
        receiptViewModel.receiptsForStore.observe(this) { receiptsWithClients ->
            receiptsWithClients?.let {
                receiptAdapter.receiptList = it
                receiptAdapter.notifyDataSetChanged() // TODO: Rozważyć DiffUtil
            }
        }

        // Listener dla FAB
        fabAddClient.setOnClickListener {
            val intent = Intent(this, AddClientActivity::class.java)
            intent.putExtra("STORE_ID", storeId)
            Log.d("ReceiptListActivity", "Uruchamiam AddClientActivity z STORE_ID: $storeId")
            startActivity(intent)
        }
    }

    /**
     * Metoda wywoływana po kliknięciu elementu na liście paragonów.
     * Implementacja interfejsu [ReceiptAdapter.OnReceiptClickListener].
     * @param receiptId ID klikniętego paragonu.
     */
    // Zmieniono nazwę metody
    override fun onReceiptClick(receiptId: Long) {
        // Uruchom EditReceiptActivity (teraz jako widok/edycja)
        val intent = Intent(this, EditReceiptActivity::class.java)
        intent.putExtra("RECEIPT_ID", receiptId)
        startActivity(intent)
    }
}
