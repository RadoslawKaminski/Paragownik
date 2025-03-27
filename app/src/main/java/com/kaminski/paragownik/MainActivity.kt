package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button // Dodano import dla Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
// import com.kaminski.paragownik.data.Store // Nieużywany bezpośrednio
import com.kaminski.paragownik.viewmodel.StoreViewModel

/**
 * Główna aktywność aplikacji. Wyświetla listę dostępnych drogerii (sklepów).
 * Umożliwia przejście do listy paragonów dla wybranej drogerii,
 * przejście do dodawania nowego klienta (bez kontekstu sklepu)
 * oraz przejście do listy wszystkich klientów.
 */
class MainActivity : AppCompatActivity(), StoreAdapter.OnItemClickListener {

    // ViewModel do zarządzania danymi sklepów
    private lateinit var storeViewModel: StoreViewModel
    // Adapter dla RecyclerView wyświetlającego sklepy
    private lateinit var storeAdapter: StoreAdapter
    // Przycisk FAB do dodawania nowego klienta
    private lateinit var fabAddClientMain: FloatingActionButton
    // Przycisk do przejścia do listy klientów
    private lateinit var viewClientsButton: Button // Dodano

    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     * Inicjalizuje RecyclerView, Adapter, ViewModel, obserwuje dane sklepów
     * i ustawia listenery dla FAB i przycisku listy klientów.
     */
    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Ustawienie layoutu

        // Inicjalizacja RecyclerView
        val storeRecyclerView: RecyclerView = findViewById(R.id.storeRecyclerView)
        storeRecyclerView.layoutManager = LinearLayoutManager(this) // Ustawienie managera layoutu

        // Inicjalizacja Adaptera z pustą listą i listenerem kliknięć (this implementuje interfejs)
        storeAdapter = StoreAdapter(emptyList(), this)
        storeRecyclerView.adapter = storeAdapter // Przypisanie adaptera do RecyclerView

        // Inicjalizacja ViewModelu
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        // Obserwacja LiveData `allStores` z ViewModelu
        storeViewModel.allStores.observe(this) { stores ->
            // Gdy lista sklepów się zmieni (lub zostanie załadowana po raz pierwszy):
            stores?.let { // Sprawdź, czy lista nie jest null
                // Zaktualizuj dane w adapterze
                storeAdapter.storeList = it
                // Powiadom adapter, że dane się zmieniły, aby odświeżył widok
                // TODO: Rozważyć użycie DiffUtil dla lepszej wydajności zamiast notifyDataSetChanged()
                storeAdapter.notifyDataSetChanged()
            }
        }

        // Inicjalizacja FloatingActionButton
        fabAddClientMain = findViewById(R.id.fabAddClientMain)
        // Ustawienie listenera kliknięcia dla FAB
        fabAddClientMain.setOnClickListener {
            // Utwórz Intent do uruchomienia AddClientActivity
            val intent = Intent(this, AddClientActivity::class.java)
            // Uruchom AddClientActivity bez przekazywania STORE_ID
            // (użytkownik będzie musiał wpisać numer sklepu dla pierwszego paragonu)
            startActivity(intent)
        }

        // --- DODANY KOD ---
        // Inicjalizacja przycisku "Pokaż Klientów"
        viewClientsButton = findViewById(R.id.viewClientsButton)
        // Ustawienie listenera kliknięcia dla przycisku
        viewClientsButton.setOnClickListener {
            // Utwórz Intent do uruchomienia ClientListActivity
            val intent = Intent(this, ClientListActivity::class.java)
            // Uruchom ClientListActivity
            startActivity(intent)
        }
        // --- KONIEC DODANEGO KODU ---
    }

    // Funkcja insertSampleStores została usunięta jako nieużywana

    /**
     * Metoda wywoływana, gdy użytkownik kliknie element na liście sklepów.
     * Implementacja interfejsu [StoreAdapter.OnItemClickListener].
     * @param storeId ID klikniętego sklepu.
     */
    override fun onItemClick(storeId: Long) {
        // Utwórz Intent do uruchomienia ReceiptListActivity
        val intent = Intent(this, ReceiptListActivity::class.java)
        // Dodaj ID klikniętego sklepu jako dodatkową informację do Intentu
        intent.putExtra("STORE_ID", storeId)
        // Uruchom ReceiptListActivity
        startActivity(intent)
    }
}