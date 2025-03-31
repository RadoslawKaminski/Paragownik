package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.ClientWithThumbnail
import com.kaminski.paragownik.viewmodel.ClientListViewModel

/**
 * Aktywność wyświetlająca listę wszystkich klientów zarejestrowanych w aplikacji.
 * Umożliwia nawigację do szczegółów klienta (ClientReceiptsActivity).
 */
class ClientListActivity : AppCompatActivity(), ClientAdapter.OnClientClickListener {

    private lateinit var clientRecyclerView: RecyclerView

    private lateinit var clientAdapter: ClientAdapter
    private lateinit var clientListViewModel: ClientListViewModel

    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     */
    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_list)

        clientRecyclerView = findViewById(R.id.clientRecyclerView)
        clientRecyclerView.layoutManager = LinearLayoutManager(this)

        clientAdapter = ClientAdapter(emptyList<ClientWithThumbnail>(), this)
        clientRecyclerView.adapter = clientAdapter

        clientListViewModel = ViewModelProvider(this).get(ClientListViewModel::class.java)

        clientListViewModel.allClients.observe(this) { clients ->
            clients?.let {
                clientAdapter.clientList = it
                clientAdapter.notifyDataSetChanged()
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
        val intent = Intent(this, ClientReceiptsActivity::class.java)
        intent.putExtra("CLIENT_ID", clientId)
        startActivity(intent)
    }
}





