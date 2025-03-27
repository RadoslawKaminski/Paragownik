package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView // Potrzebne dla referencji do TextView tytułu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope // Potrzebne dla korutyn cyklu życia
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kaminski.paragownik.viewmodel.ReceiptViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel // Potrzebne do pobrania numeru sklepu
import kotlinx.coroutines.launch // Potrzebne dla launch

/**
 * Aktywność wyświetlająca listę paragonów dla konkretnej, wybranej drogerii.
 * ID drogerii jest przekazywane do tej aktywności przez Intent.
 * Umożliwia edycję wybranego paragonu oraz dodanie nowego paragonu/klienta
 * w kontekście tej drogerii.
 */
class ReceiptListActivity : AppCompatActivity(), ReceiptAdapter.OnEditButtonClickListener {

    // Widoki UI
    private lateinit var receiptRecyclerView: RecyclerView
    private lateinit var fabAddClient: FloatingActionButton
    private lateinit var titleTextView: TextView // Referencja do TextView tytułu

    // Adapter i ViewModels
    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var receiptViewModel: ReceiptViewModel
    private lateinit var storeViewModel: StoreViewModel // ViewModel do pobrania danych sklepu

    // ID sklepu, dla którego wyświetlamy paragony
    private var storeId: Long = -1L

    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     * Inicjalizuje UI, ViewModel, pobiera ID sklepu z Intentu, ustawia dynamiczny tytuł,
     * ładuje dane paragonów i ustawia listenery.
     */
    @SuppressLint("NotifyDataSetChanged") // Używane dla uproszczenia, rozważ DiffUtil w przyszłości
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_list) // Ustawienie layoutu

        // Inicjalizacja RecyclerView i TextView tytułu
        receiptRecyclerView = findViewById(R.id.receiptRecyclerView)
        receiptRecyclerView.layoutManager = LinearLayoutManager(this)
        titleTextView = findViewById(R.id.receiptListTitleTextView) // Inicjalizacja TextView tytułu

        // Inicjalizacja Adaptera z pustą listą i listenerem kliknięć edycji (this)
        receiptAdapter = ReceiptAdapter(emptyList(), this)
        receiptRecyclerView.adapter = receiptAdapter

        // Inicjalizacja ViewModeli
        receiptViewModel = ViewModelProvider(this).get(ReceiptViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java) // Inicjalizacja StoreViewModel

        // Pobierz ID sklepu przekazane z MainActivity
        storeId = intent.getLongExtra("STORE_ID", -1L)
        Log.d("ReceiptListActivity", "Otrzymano STORE_ID: $storeId")

        // Sprawdź, czy ID sklepu jest poprawne
        if (storeId == -1L) {
            // Jeśli ID jest nieprawidłowe, zaloguj błąd, pokaż komunikat i zakończ aktywność
            Log.e("ReceiptListActivity", "Nieprawidłowe STORE_ID (-1) otrzymane w Intencie.")
            Toast.makeText(this, R.string.error_invalid_store_id, Toast.LENGTH_LONG).show()
            // Ustaw domyślny tytuł w razie błędu
            titleTextView.text = getString(R.string.receipt_list_activity_title_prefix) + " ?" // Dodano spację
            finish() // Zamknij aktywność
            return   // Zakończ wykonywanie onCreate
        }

        // Ustawienie dynamicznego tytułu ekranu
        lifecycleScope.launch { // Uruchomienie korutyny w cyklu życia aktywności
            val store = storeViewModel.getStoreById(storeId) // Pobranie danych sklepu z ViewModelu
            val titlePrefix = getString(R.string.receipt_list_activity_title_prefix) // Pobranie prefiksu tytułu ("Paragony Drogerii")
            // Ustawienie tekstu tytułu - prefiks + spacja + numer sklepu lub "?"
            titleTextView.text = if (store != null) {
                titlePrefix + " " + store.storeNumber // Dodano spację jawnie
            } else {
                Log.w("ReceiptListActivity", "Nie znaleziono sklepu o ID $storeId do ustawienia tytułu.")
                titlePrefix + " ?" // Dodano spację jawnie
            }
        }

        // Poinformuj ViewModel, dla którego sklepu ma załadować paragony
        receiptViewModel.loadReceiptsForStore(storeId)

        // Obserwuj LiveData `receiptsForStore` z ViewModelu
        receiptViewModel.receiptsForStore.observe(this) { receiptsWithClients ->
            // Gdy lista paragonów (z klientami) się zmieni:
            receiptsWithClients?.let { // Sprawdź null
                // Zaktualizuj dane w adapterze
                receiptAdapter.receiptList = it
                // Powiadom adapter o zmianie danych
                receiptAdapter.notifyDataSetChanged() // TODO: Rozważyć DiffUtil
            }
        }

        // Inicjalizacja FloatingActionButton
        fabAddClient = findViewById(R.id.fabAddClient)
        // Ustawienie listenera kliknięcia dla FAB
        fabAddClient.setOnClickListener {
            // Utwórz Intent do uruchomienia AddClientActivity
            val intent = Intent(this, AddClientActivity::class.java)
            // Przekaż ID bieżącego sklepu do AddClientActivity.
            intent.putExtra("STORE_ID", storeId)
            Log.d("ReceiptListActivity", "Uruchamiam AddClientActivity z STORE_ID: $storeId")
            // Uruchom AddClientActivity
            startActivity(intent)
        }
    }

    /**
     * Metoda wywoływana, gdy użytkownik kliknie ikonę edycji na liście paragonów.
     * Implementacja interfejsu [ReceiptAdapter.OnEditButtonClickListener].
     * @param receiptId ID klikniętego paragonu.
     */
    override fun onEditButtonClick(receiptId: Long) {
        // Utwórz Intent do uruchomienia EditReceiptActivity
        val intent = Intent(this, EditReceiptActivity::class.java)
        // Dodaj ID klikniętego paragonu jako dodatkową informację
        intent.putExtra("RECEIPT_ID", receiptId)
        // Uruchom EditReceiptActivity
        startActivity(intent)
    }
}