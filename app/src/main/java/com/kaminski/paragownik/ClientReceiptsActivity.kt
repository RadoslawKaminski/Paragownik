package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri // Dodano import
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater // Dodano import
import android.view.View
import android.widget.HorizontalScrollView // Dodano import
import android.widget.ImageButton // Dodano import
import android.widget.ImageView
import android.widget.LinearLayout // Dodano import
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kaminski.paragownik.data.PhotoType // Dodano import
import com.kaminski.paragownik.data.ReceiptWithClient // Dodano import
import com.kaminski.paragownik.viewmodel.ClientReceiptsViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel // Import StoreViewModel

/**
 * Aktywność wyświetlająca szczegóły klienta, listę jego paragonów oraz listę jego zdjęć.
 * Umożliwia dodawanie paragonów oraz edycję/usuwanie klienta.
 */
class ClientReceiptsActivity : AppCompatActivity(), ReceiptAdapter.OnReceiptClickListener {

    // Widoki UI
    private lateinit var clientPhotoImageView: ImageView // Miniatura w nagłówku
    private lateinit var clientDescriptionTextView: TextView
    private lateinit var clientAppNumberTextView: TextView
    private lateinit var clientAmoditNumberTextView: TextView
    private lateinit var clientReceiptsRecyclerView: RecyclerView
    private lateinit var fabAddReceiptToClient: FloatingActionButton
    private lateinit var moreOptionsClientButton: ImageButton // Zmieniono nazwę z editClientButton
    // Nowe widoki dla zdjęć
    private lateinit var clientPhotosTitleDetails: TextView
    private lateinit var clientPhotosScrollViewDetails: HorizontalScrollView
    private lateinit var clientPhotosContainerDetails: LinearLayout
    private lateinit var transactionPhotosTitleDetails: TextView
    private lateinit var transactionPhotosScrollViewDetails: HorizontalScrollView
    private lateinit var transactionPhotosContainerDetails: LinearLayout


    // Adapter i ViewModels
    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var viewModel: ClientReceiptsViewModel
    private lateinit var storeViewModel: StoreViewModel // ViewModel do pobrania mapy sklepów

    // ID klienta
    private var clientId: Long = -1L

    // Dane potrzebne do aktualizacji adaptera paragonów
    private var currentReceipts: List<ReceiptWithClient>? = null
    private var currentStoreMap: Map<Long, String>? = null
    // Mapa miniatur nie jest potrzebna w trybie CLIENT_LIST

    // Usunięto @SuppressLint, bo aktualizacja jest teraz w dedykowanej metodzie
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

        // Ustawienie listenerów dla przycisków
        setupListeners() // <-- Zmieniono nazwę metody

        // Rozpoczęcie ładowania danych w ViewModelu
        viewModel.loadClientData(clientId)

        // Obserwacja danych klienta
        observeClientData()

        // Obserwacja listy paragonów klienta i mapy sklepów
        observeReceiptsAndStores() // Zmieniono na jedną metodę

        observeClientPhotos() // <-- Wywołanie obserwacji zdjęć
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
        moreOptionsClientButton = findViewById(R.id.moreOptionsClientButton) // Zmieniono ID
        // Inicjalizacja widoków zdjęć
        clientPhotosTitleDetails = findViewById(R.id.clientPhotosTitleDetails)
        clientPhotosScrollViewDetails = findViewById(R.id.clientPhotosScrollViewDetails)
        clientPhotosContainerDetails = findViewById(R.id.clientPhotosContainerDetails)
        transactionPhotosTitleDetails = findViewById(R.id.transactionPhotosTitleDetails)
        transactionPhotosScrollViewDetails = findViewById(R.id.transactionPhotosScrollViewDetails)
        transactionPhotosContainerDetails = findViewById(R.id.transactionPhotosContainerDetails)
    }


    /**
     * Konfiguruje RecyclerView i jego Adapter z trybem CLIENT_LIST.
     */
    private fun setupRecyclerView() {
        clientReceiptsRecyclerView.layoutManager = LinearLayoutManager(this)
        // Używamy ReceiptAdapter w trybie CLIENT_LIST, przekazujemy tylko listener i tryb
        receiptAdapter = ReceiptAdapter(this, DisplayMode.CLIENT_LIST) // Poprawiona inicjalizacja
        clientReceiptsRecyclerView.adapter = receiptAdapter
        // TODO: Konfiguracja RecyclerView dla zdjęć w Kroku 4 (na razie używamy LinearLayout)
    }

    /**
     * Ustawia listenery dla interaktywnych elementów UI (FAB, ikona edycji).
     */
    private fun setupListeners() {
        // Listener dla FAB dodawania paragonu
        fabAddReceiptToClient.setOnClickListener {
            Log.d("ClientReceiptsActivity", "Kliknięto FAB - uruchamianie AddReceiptToClientActivity dla klienta ID: $clientId")
            val intent = Intent(this, AddReceiptToClientActivity::class.java)
            intent.putExtra("CLIENT_ID", clientId)
            startActivity(intent)
        }

        // Listener dla ikony "Więcej" (trzykropek)
        moreOptionsClientButton.setOnClickListener {
            Log.d("ClientReceiptsActivity", "Kliknięto Więcej Opcji - uruchamianie EditClientActivity dla klienta ID: $clientId")
            val intent = Intent(this, EditClientActivity::class.java)
            intent.putExtra("CLIENT_ID", clientId)
            startActivity(intent)
        }
    }

    /**
     * Obserwuje zmiany w danych klienta i aktualizuje UI (bez zdjęcia).
     */
    private fun observeClientData() {
        viewModel.client.observe(this) { client ->
            if (client == null) {
                // Obsługa sytuacji, gdy klient nie istnieje (np. został usunięty)
                if (!isFinishing && !isDestroyed) { // Sprawdź stan aktywności
                    Log.e("ClientReceiptsActivity", "Klient o ID $clientId nie został znaleziony (być może usunięty).")
                    Toast.makeText(this, R.string.error_client_not_found, Toast.LENGTH_SHORT).show()
                    finish() // Zamknij aktywność, jeśli klient nie istnieje
                }
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

            // Logika ustawiania zdjęcia (miniatury w nagłówku) jest teraz w displayClientPhotos
        }
    }


    /**
     * Obserwuje zmiany na liście paragonów klienta oraz mapę sklepów
     * i wywołuje aktualizację adaptera, gdy oba zestawy danych są dostępne.
     */
    private fun observeReceiptsAndStores() {
        // Obserwacja listy paragonów klienta
        viewModel.receiptsForClient.observe(this) { receipts ->
            Log.d("ClientReceiptsActivity", "Otrzymano ${receipts?.size ?: 0} paragonów dla klienta.")
            currentReceipts = receipts
            tryUpdateReceiptAdapter() // Spróbuj zaktualizować adapter
        }

        // Obserwacja mapy sklepów (potrzebna dla adaptera w trybie CLIENT_LIST)
        storeViewModel.allStoresMap.observe(this) { storeMap ->
            Log.d("ClientReceiptsActivity", "Otrzymano mapę sklepów, rozmiar: ${storeMap?.size ?: 0}")
            currentStoreMap = storeMap
            tryUpdateReceiptAdapter() // Spróbuj zaktualizować adapter
        }
    }

    /**
     * Sprawdza, czy dane paragonów i mapa sklepów są dostępne,
     * a następnie wywołuje `updateReceipts` w adapterze.
     */
    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil w ReceiptAdapter.updateReceipts
    private fun tryUpdateReceiptAdapter() {
        val receipts = currentReceipts
        val storeMap = currentStoreMap

        if (receipts != null && storeMap != null) {
            Log.d("ClientReceiptsActivity", "Dane paragonów i mapa sklepów dostępne. Aktualizowanie adaptera paragonów...")
            // Wywołaj nową metodę adaptera, przekazując paragony, mapę sklepów, kontekst i flagę showStoreHeaders.
            // Mapa miniatur nie jest potrzebna w trybie CLIENT_LIST.
            // Ustaw showStoreHeaders na false dla tego widoku.
            receiptAdapter.updateReceipts(this, receipts, storeMap, emptyMap(), false) // Dodano showStoreHeaders = false
        } else {
            Log.d("ClientReceiptsActivity", "Nie wszystkie dane są jeszcze dostępne do aktualizacji adaptera paragonów.")
        }
    }


    /** Obserwuje listę zdjęć klienta i aktualizuje UI. */
    private fun observeClientPhotos() {
        viewModel.clientPhotos.observe(this) { photos ->
            displayClientPhotos(photos ?: emptyList()) // Wywołaj nową metodę
        }
    }

    /** Wyświetla zdjęcia klienta w odpowiednich kontenerach. */
    private fun displayClientPhotos(photos: List<com.kaminski.paragownik.data.Photo>) {
        // Wyczyść kontenery
        clientPhotosContainerDetails.removeAllViews()
        transactionPhotosContainerDetails.removeAllViews()

        val clientPhotos = photos.filter { it.type == PhotoType.CLIENT }
        val transactionPhotos = photos.filter { it.type == PhotoType.TRANSACTION }

        // Pokaż/ukryj sekcję zdjęć klienta - NA RAZIE UKRYTE
        clientPhotosTitleDetails.visibility = View.GONE // UKRYTE
        clientPhotosScrollViewDetails.visibility = View.GONE // UKRYTE
        // if (clientSectionVisible) {
        //     clientPhotos.forEach { addPhotoThumbnailToDetailsView(it.uri.toUri(), clientPhotosContainerDetails) }
        // }

        // Pokaż/ukryj sekcję zdjęć transakcji - NA RAZIE UKRYTE
        transactionPhotosTitleDetails.visibility = View.GONE // UKRYTE
        transactionPhotosScrollViewDetails.visibility = View.GONE // UKRYTE
        // if (transactionSectionVisible) {
        //     transactionPhotos.forEach { addPhotoThumbnailToDetailsView(it.uri.toUri(), transactionPhotosContainerDetails) }
        // }

         // Ustaw miniaturę w nagłówku (pierwsze zdjęcie klienta)
         val firstClientPhotoUri = clientPhotos.firstOrNull()?.uri?.toUri()
         if (firstClientPhotoUri != null) {
             try {
                 clientPhotoImageView.setImageURI(firstClientPhotoUri)
                 clientPhotoImageView.visibility = View.VISIBLE
             } catch (e: Exception) {
                 Log.w("ClientReceiptsActivity", "Błąd ładowania miniatury w nagłówku, URI: $firstClientPhotoUri", e)
                 clientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                 clientPhotoImageView.visibility = View.VISIBLE
             }
         } else {
             clientPhotoImageView.visibility = View.GONE
         }
    }


    /** Dodaje miniaturę zdjęcia do kontenera w widoku szczegółów (bez usuwania). */
    private fun addPhotoThumbnailToDetailsView(photoUri: Uri, container: LinearLayout) {
        val inflater = LayoutInflater.from(this)
        val thumbnailView = inflater.inflate(R.layout.photo_thumbnail_item, container, false)
        val imageView = thumbnailView.findViewById<ImageView>(R.id.photoThumbnailImageView)
        val deleteButton = thumbnailView.findViewById<ImageButton>(R.id.deletePhotoButton)

        try {
            imageView.setImageURI(photoUri)
            deleteButton.visibility = View.GONE // Ukryj przycisk usuwania w tym widoku

            // TODO: Dodać listener kliknięcia na imageView w przyszłości, aby otworzyć pełny ekran
            // imageView.setOnClickListener { /* Otwórz pełny ekran */ }

            container.addView(thumbnailView)
        } catch (e: Exception) {
             Log.e("ClientReceiptsActivity", "Błąd ładowania miniatury $photoUri", e)
             // Można dodać placeholder błędu zamiast nie dodawać widoku
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
