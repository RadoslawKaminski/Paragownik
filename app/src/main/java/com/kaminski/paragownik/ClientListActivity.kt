package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent // Import potrzebny dla przyszłej nawigacji
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.viewmodel.ClientListViewModel

/**
 * Aktywność wyświetlająca listę wszystkich klientów zarejestrowanych w aplikacji.
 * Umożliwia nawigację do szczegółów klienta (w przyszłości).
 */
class ClientListActivity : AppCompatActivity(), ClientAdapter.OnClientClickListener {

    // Widoki UI
    private lateinit var clientRecyclerView: RecyclerView

    // Adapter i ViewModel
    private lateinit var clientAdapter: ClientAdapter
    private lateinit var clientListViewModel: ClientListViewModel

    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     * Inicjalizuje UI, ViewModel, RecyclerView, Adapter i obserwuje zmiany na liście klientów.
     */
    @SuppressLint("NotifyDataSetChanged") // Używane dla uproszczenia, rozważ DiffUtil w przyszłości
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_list) // Ustawienie layoutu

        // Inicjalizacja RecyclerView
        clientRecyclerView = findViewById(R.id.clientRecyclerView)
        clientRecyclerView.layoutManager = LinearLayoutManager(this)

        // Inicjalizacja Adaptera z pustą listą i listenerem kliknięć (this)
        clientAdapter = ClientAdapter(emptyList(), this)
        clientRecyclerView.adapter = clientAdapter

        // Inicjalizacja ViewModelu
        clientListViewModel = ViewModelProvider(this).get(ClientListViewModel::class.java)

        // Obserwuj LiveData `allClients` z ViewModelu
        clientListViewModel.allClients.observe(this) { clients ->
            // Gdy lista klientów się zmieni:
            clients?.let { // Sprawdź null
                // Zaktualizuj dane w adapterze
                clientAdapter.clientList = it
                // Powiadom adapter o zmianie danych
                // TODO: Rozważyć użycie DiffUtil dla lepszej wydajności zamiast notifyDataSetChanged()
                clientAdapter.notifyDataSetChanged()
                Log.d("ClientListActivity", "Zaktualizowano listę klientów, liczba: ${it.size}")
            }
        }
    }

    /**
     * Metoda wywoływana, gdy użytkownik kliknie element na liście klientów.
     * Implementacja interfejsu [ClientAdapter.OnClientClickListener].
     * @param clientId ID klikniętego klienta.
     */
    override fun onClientClick(clientId: Long) {
        // TODO: Zaimplementować nawigację do ClientReceiptsActivity (Ekran Paragonów Klienta)
        // Na razie tylko wyświetlamy Toast z ID klienta.
        Toast.makeText(this, "Kliknięto klienta o ID: $clientId", Toast.LENGTH_SHORT).show()
        Log.d("ClientListActivity", "Kliknięto klienta o ID: $clientId")

        // Przykład przyszłej nawigacji:
        // val intent = Intent(this, ClientReceiptsActivity::class.java)
        // intent.putExtra("CLIENT_ID", clientId)
        // startActivity(intent)
    }
}
