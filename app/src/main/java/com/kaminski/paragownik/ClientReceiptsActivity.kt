package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kaminski.paragownik.viewmodel.ClientReceiptsViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel // Import StoreViewModel

/**
 * Aktywność wyświetlająca szczegóły klienta i listę jego paragonów.
 * Używa ReceiptAdapter w trybie CLIENT_LIST.
 */
class ClientReceiptsActivity : AppCompatActivity(), ReceiptAdapter.OnReceiptClickListener {

    // Widoki UI
    private lateinit var clientPhotoImageView: ImageView
    private lateinit var clientDescriptionTextView: TextView
    private lateinit var clientAppNumberTextView: TextView
    private lateinit var clientAmoditNumberTextView: TextView
    private lateinit var clientReceiptsRecyclerView: RecyclerView
    private lateinit var fabAddReceiptToClient: FloatingActionButton

    // Adapter i ViewModels
    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var viewModel: ClientReceiptsViewModel
    private lateinit var storeViewModel: StoreViewModel // ViewModel do pobrania mapy sklepów

    // ID klienta
    private var clientId: Long = -1L

    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_receipts)

        // Pobranie ID klienta z Intentu
        clientId = intent.getLongExtra("CLIENT_ID", -1L)
        if (clientId == -1L) {
            Log.e("ClientReceiptsActivity", "Nieprawidłowe CLIENT_ID (-1) otrzymane w Intencie.")
            Toast.makeText(this, R.string.error_invalid_client_id, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.d("ClientReceiptsActivity", "Otrzymano CLIENT_ID: $clientId")

        // Inicjalizacja widoków
        initializeViews()

        // Inicjalizacja ViewModeli
        viewModel = ViewModelProvider(this).get(ClientReceiptsViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java) // Inicjalizacja StoreViewModel

        // Inicjalizacja RecyclerView i Adaptera (z trybem CLIENT_LIST)
        setupRecyclerView()

        // Ustawienie listenera dla FAB
        setupFabListener()

        // Rozpoczęcie ładowania danych w ViewModelu
        viewModel.loadClientData(clientId)

        // Obserwacja danych klienta
        observeClientData()

        // Obserwacja listy paragonów klienta
        observeReceiptsData()

        // Obserwacja mapy sklepów (potrzebna dla adaptera w trybie CLIENT_LIST)
        observeStoreMap()
    }

    /**
     * Inicjalizuje referencje do widoków UI.
     */
    private fun initializeViews() {
        clientPhotoImageView = findViewById(R.id.clientDetailsPhotoImageView)
        clientDescriptionTextView = findViewById(R.id.clientDetailsDescriptionTextView)
        clientAppNumberTextView = findViewById(R.id.clientDetailsAppNumberTextView)
        clientAmoditNumberTextView = findViewById(R.id.clientDetailsAmoditNumberTextView)
        clientReceiptsRecyclerView = findViewById(R.id.clientReceiptsRecyclerView)
        fabAddReceiptToClient = findViewById(R.id.fabAddReceiptToClient)
    }

    /**
     * Konfiguruje RecyclerView i jego Adapter z trybem CLIENT_LIST.
     */
    private fun setupRecyclerView() {
        clientReceiptsRecyclerView.layoutManager = LinearLayoutManager(this)
        // Używamy ReceiptAdapter w trybie CLIENT_LIST
        receiptAdapter = ReceiptAdapter(emptyList(), this, DisplayMode.CLIENT_LIST)
        clientReceiptsRecyclerView.adapter = receiptAdapter
    }

    /**
     * Ustawia listener dla FloatingActionButton.
     */
    private fun setupFabListener() {
        fabAddReceiptToClient.setOnClickListener {
            // TODO: Zaimplementować nawigację do AddClientActivity w trybie dodawania paragonu
            Log.d("ClientReceiptsActivity", "Kliknięto FAB - docelowo uruchomi AddClientActivity dla klienta ID: $clientId")
            Toast.makeText(this, "TODO: Dodaj paragon dla klienta $clientId", Toast.LENGTH_SHORT).show()

            // Przykład przyszłej nawigacji:
            // val intent = Intent(this, AddClientActivity::class.java)
            // intent.putExtra("EXISTING_CLIENT_ID", clientId)
            // startActivity(intent)
        }
    }

    /**
     * Obserwuje zmiany w danych klienta i aktualizuje UI.
     */
    private fun observeClientData() {
        viewModel.client.observe(this) { client ->
            if (client == null) {
                Log.e("ClientReceiptsActivity", "Nie znaleziono klienta o ID: $clientId")
                Toast.makeText(this, R.string.error_client_not_found, Toast.LENGTH_SHORT).show()
                return@observe
            }

            // Ustaw opis lub ID
            clientDescriptionTextView.text = if (client.description.isNullOrBlank()) {
                getString(R.string.client_item_id_prefix) + client.id.toString()
            } else {
                client.description
            }

            // Ustaw numer aplikacji
            val appNumberText = client.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.client_item_app_number_prefix) + " " + it
            }
            clientAppNumberTextView.text = appNumberText
            clientAppNumberTextView.isVisible = appNumberText != null

            // Ustaw numer Amodit
            val amoditNumberText = client.amoditNumber?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.client_item_amodit_number_prefix) + " " + it
            }
            clientAmoditNumberTextView.text = amoditNumberText
            clientAmoditNumberTextView.isVisible = amoditNumberText != null

            // Ustaw zdjęcie
            if (!client.photoUri.isNullOrBlank()) {
                try {
                    clientPhotoImageView.setImageURI(client.photoUri.toUri())
                    clientPhotoImageView.visibility = View.VISIBLE
                } catch (e: Exception) {
                    Log.w("ClientReceiptsActivity", "Błąd ładowania zdjęcia klienta ${client.id}, URI: ${client.photoUri}", e)
                    clientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                    clientPhotoImageView.visibility = View.VISIBLE
                }
            } else {
                clientPhotoImageView.visibility = View.GONE
            }
        }
    }

    /**
     * Obserwuje zmiany na liście paragonów klienta i aktualizuje adapter.
     * Odświeżenie adaptera następuje dopiero po aktualizacji mapy sklepów.
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun observeReceiptsData() {
        viewModel.receiptsForClient.observe(this) { receipts ->
            receipts?.let {
                receiptAdapter.receiptList = it
                Log.d("ClientReceiptsActivity", "Otrzymano listę paragonów klienta, liczba: ${it.size}")
                // Odśwież adapter tylko jeśli mapa sklepów jest już dostępna lub lista paragonów jest pusta
                if (receiptAdapter.storeMap.isNotEmpty() || it.isEmpty()) {
                     receiptAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    /**
     * Obserwuje mapę sklepów z StoreViewModel i aktualizuje ją w adapterze.
     * Odświeża adapter po aktualizacji mapy.
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun observeStoreMap() {
        storeViewModel.allStoresMap.observe(this) { storeMap ->
            storeMap?.let {
                Log.d("ClientReceiptsActivity", "Otrzymano mapę sklepów, rozmiar: ${it.size}")
                receiptAdapter.updateStoreMap(it)
                // Odśwież adapter, bo mogły już być załadowane paragony
                if (receiptAdapter.receiptList.isNotEmpty()) {
                     receiptAdapter.notifyDataSetChanged()
                }
            }
        }
    }


    /**
     * Obsługa kliknięcia paragonu na liście.
     * Uruchamia EditReceiptActivity dla wybranego paragonu, przekazując kontekst.
     */
    override fun onReceiptClick(receiptId: Long) {
        val intent = Intent(this, EditReceiptActivity::class.java)
        intent.putExtra("RECEIPT_ID", receiptId)
        // Dodaj informację o kontekście
        intent.putExtra("CONTEXT", "CLIENT_LIST") // Kontekst: lista klienta
        startActivity(intent)
    }
}
