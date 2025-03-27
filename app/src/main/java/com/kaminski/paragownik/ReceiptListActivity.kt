package com.kaminski.paragownik

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kaminski.paragownik.viewmodel.ReceiptViewModel

/**
 * Aktywność wyświetlająca listę paragonów dla konkretnej, wybranej drogerii.
 * ID drogerii jest przekazywane do tej aktywności przez Intent.
 * Umożliwia edycję wybranego paragonu oraz dodanie nowego paragonu/klienta
 * w kontekście tej drogerii.
 */
class ReceiptListActivity : AppCompatActivity(), ReceiptAdapter.OnEditButtonClickListener {

    // Widoki UI
    private lateinit var receiptRecyclerView: RecyclerView
    private lateinit var fabAddClient: FloatingActionButton // Przycisk do dodawania

    // Adapter i ViewModel
    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var receiptViewModel: ReceiptViewModel

    // ID sklepu, dla którego wyświetlamy paragony
    private var storeId: Long = -1L

    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     * Inicjalizuje UI, ViewModel, pobiera ID sklepu z Intentu, ładuje dane
     * i ustawia listenery.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_list) // Ustawienie layoutu

        // Inicjalizacja RecyclerView
        receiptRecyclerView = findViewById(R.id.receiptRecyclerView)
        receiptRecyclerView.layoutManager = LinearLayoutManager(this)

        // Inicjalizacja Adaptera z pustą listą i listenerem kliknięć edycji (this)
        receiptAdapter = ReceiptAdapter(emptyList(), this)
        receiptRecyclerView.adapter = receiptAdapter

        // Inicjalizacja ViewModelu
        receiptViewModel = ViewModelProvider(this).get(ReceiptViewModel::class.java)

        // Pobierz ID sklepu przekazane z MainActivity
        storeId = intent.getLongExtra("STORE_ID", -1L)
        Log.d("ReceiptListActivity", "Otrzymano STORE_ID: $storeId")

        // Sprawdź, czy ID sklepu jest poprawne
        if (storeId == -1L) {
            // Jeśli ID jest nieprawidłowe, zaloguj błąd i ewentualnie zamknij aktywność lub pokaż komunikat
            Log.e("ReceiptListActivity", "Nieprawidłowe STORE_ID (-1) otrzymane w Intencie.")
            // Można dodać Toast i finish()
            // Toast.makeText(this, "Błąd: Nieprawidłowy identyfikator sklepu.", Toast.LENGTH_LONG).show()
            // finish()
            // return
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
                receiptAdapter.notifyDataSetChanged()
            }
        }

        // Inicjalizacja FloatingActionButton
        fabAddClient = findViewById(R.id.fabAddClient)
        // Ustawienie listenera kliknięcia dla FAB
        fabAddClient.setOnClickListener {
            // Utwórz Intent do uruchomienia AddClientActivity
            val intent = Intent(this, AddClientActivity::class.java)
            // Przekaż ID bieżącego sklepu do AddClientActivity.
            // Dzięki temu pole numeru sklepu dla pierwszego paragonu będzie wypełnione i zablokowane.
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

