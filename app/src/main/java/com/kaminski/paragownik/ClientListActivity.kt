package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent // Import potrzebny dla nawigacji
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.ClientWithThumbnail // Dodano import
import com.kaminski.paragownik.viewmodel.ClientListViewModel

/**
 * Aktywność wyświetlająca listę wszystkich klientów zarejestrowanych w aplikacji.
 * Umożliwia nawigację do szczegółów klienta (ClientReceiptsActivity).
 */
class ClientListActivity : AppCompatActivity(), ClientAdapter.OnClientClickListener {

    // Widoki UI
    private lateinit var clientRecyclerView: RecyclerView

    // Adapter i ViewModel
    private lateinit var clientAdapter: ClientAdapter
    private lateinit var clientListViewModel: ClientListViewModel

    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     */
    @SuppressLint("NotifyDataSetChanged") // Używane dla uproszczenia, rozważ DiffUtil w przyszłości
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_list) // Ustawienie layoutu

        // Inicjalizacja RecyclerView
        clientRecyclerView = findViewById(R.id.clientRecyclerView)
        clientRecyclerView.layoutManager = LinearLayoutManager(this)

        // Inicjalizacja Adaptera z pustą listą ClientWithThumbnail
        clientAdapter = ClientAdapter(emptyList<ClientWithThumbnail>(), this) // Zmieniono typ
        clientRecyclerView.adapter = clientAdapter

        // Inicjalizacja ViewModelu
        clientListViewModel = ViewModelProvider(this).get(ClientListViewModel::class.java)

        // Obserwuj LiveData `allClients` z ViewModelu
        clientListViewModel.allClients.observe(this) { clients -> // clients jest teraz List<ClientWithThumbnail>
            // Gdy lista klientów się zmieni:
            clients?.let { // Sprawdź null
                // Zaktualizuj dane w adapterze
                clientAdapter.clientList = it // Przypisanie List<ClientWithThumbnail> do pola adaptera
                // Powiadom adapter o zmianie danych
                clientAdapter.notifyDataSetChanged() // TODO: Rozważyć DiffUtil
                Log.d("ClientListActivity", "Zaktualizowano listę klientów, liczba: ${it.size}")
            }
        }
    }

    /**
     * Metoda wywoływana, gdy użytkownik kliknie element na liście klientów.
     * Uruchamia ClientReceiptsActivity dla wybranego klienta.
     * @param clientId ID klikniętego klienta.
     */
    override fun onClientClick(clientId: Long) {
        Log.d("ClientListActivity", "Kliknięto klienta o ID: $clientId - uruchamianie ClientReceiptsActivity")
        // Utwórz Intent do uruchomienia ClientReceiptsActivity
        val intent = Intent(this, ClientReceiptsActivity::class.java)
        // Dodaj ID klikniętego klienta jako dodatkową informację
        intent.putExtra("CLIENT_ID", clientId)
        // Uruchom ClientReceiptsActivity
        startActivity(intent)
    }
}

