package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.viewmodel.ClientReceiptsViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel

/**
 * Aktywność wyświetlająca szczegóły klienta, listę jego paragonów oraz listę jego zdjęć.
 * Umożliwia dodawanie paragonów oraz edycję/usuwanie klienta.
 * Używa Glide do wyświetlania miniatur.
 */
class ClientReceiptsActivity : AppCompatActivity(), ReceiptAdapter.OnReceiptClickListener {

    // --- Widoki UI ---
    private lateinit var clientPhotoImageView: ImageView
    private lateinit var clientDescriptionTextView: TextView
    private lateinit var clientAppNumberTextView: TextView
    private lateinit var clientAmoditNumberTextView: TextView
    private lateinit var clientReceiptsRecyclerView: RecyclerView
    private lateinit var fabAddReceiptToClient: FloatingActionButton
    private lateinit var moreOptionsClientButton: ImageButton
    private lateinit var clientPhotosTitleDetails: TextView
    private lateinit var clientPhotosScrollViewDetails: HorizontalScrollView
    private lateinit var clientPhotosContainerDetails: LinearLayout
    private lateinit var transactionPhotosTitleDetails: TextView
    private lateinit var transactionPhotosScrollViewDetails: HorizontalScrollView
    private lateinit var transactionPhotosContainerDetails: LinearLayout


    // --- Adapter i ViewModels ---
    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var viewModel: ClientReceiptsViewModel
    private lateinit var storeViewModel: StoreViewModel

    // --- Dane pomocnicze ---
    private var clientId: Long = -1L
    private var currentReceipts: List<ReceiptWithClient>? = null
    private var currentStoreMap: Map<Long, String>? = null


    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_receipts)

        clientId = intent.getLongExtra("CLIENT_ID", -1L)
        if (clientId == -1L) {
            Log.e("ClientReceiptsActivity", "Nieprawidłowe CLIENT_ID (-1) otrzymane w Intencie.")
            Toast.makeText(this, R.string.error_invalid_client_id, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.d("ClientReceiptsActivity", "Otrzymano CLIENT_ID: $clientId")

        initializeViews()

        viewModel = ViewModelProvider(this).get(ClientReceiptsViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        setupRecyclerView()
        setupListeners()
        viewModel.loadClientData(clientId)
        observeClientData()
        observeReceiptsAndStores()
        observeClientPhotos()
    }

    /**
     * Inicjalizuje referencje do widoków UI z layoutu.
     */
    private fun initializeViews() {
        clientPhotoImageView = findViewById(R.id.clientDetailsPhotoImageView)
        clientDescriptionTextView = findViewById(R.id.clientDetailsDescriptionTextView)
        clientAppNumberTextView = findViewById(R.id.clientDetailsAppNumberTextView)
        clientAmoditNumberTextView = findViewById(R.id.clientDetailsAmoditNumberTextView)
        clientReceiptsRecyclerView = findViewById(R.id.clientReceiptsRecyclerView)
        fabAddReceiptToClient = findViewById(R.id.fabAddReceiptToClient)
        moreOptionsClientButton = findViewById(R.id.moreOptionsClientButton)
        clientPhotosTitleDetails = findViewById(R.id.clientPhotosTitleDetails)
        clientPhotosScrollViewDetails = findViewById(R.id.clientPhotosScrollViewDetails)
        clientPhotosContainerDetails = findViewById(R.id.clientPhotosContainerDetails)
        transactionPhotosTitleDetails = findViewById(R.id.transactionPhotosTitleDetails)
        transactionPhotosScrollViewDetails = findViewById(R.id.transactionPhotosScrollViewDetails)
        transactionPhotosContainerDetails = findViewById(R.id.transactionPhotosContainerDetails)
    }


    /**
     * Konfiguruje RecyclerView dla listy paragonów i jego Adapter z trybem CLIENT_LIST.
     */
    private fun setupRecyclerView() {
        clientReceiptsRecyclerView.layoutManager = LinearLayoutManager(this)
        receiptAdapter = ReceiptAdapter(this, DisplayMode.CLIENT_LIST)
        clientReceiptsRecyclerView.adapter = receiptAdapter
    }

    /**
     * Ustawia listenery dla interaktywnych elementów UI (FAB, ikona "Więcej").
     */
    private fun setupListeners() {
        fabAddReceiptToClient.setOnClickListener {
            Log.d("ClientReceiptsActivity", "Kliknięto FAB - uruchamianie AddReceiptToClientActivity dla klienta ID: $clientId")
            val intent = Intent(this, AddReceiptToClientActivity::class.java)
            intent.putExtra("CLIENT_ID", clientId)
            startActivity(intent)
        }

        moreOptionsClientButton.setOnClickListener {
            Log.d("ClientReceiptsActivity", "Kliknięto Więcej Opcji - uruchamianie EditClientActivity dla klienta ID: $clientId")
            val intent = Intent(this, EditClientActivity::class.java)
            intent.putExtra("CLIENT_ID", clientId)
            startActivity(intent)
        }
    }

    /**
     * Obserwuje zmiany w danych klienta (Client) i aktualizuje widoki tekstowe.
     * Logika wyświetlania miniatury jest w [displayClientPhotos].
     */
    private fun observeClientData() {
        viewModel.client.observe(this) { client ->
            if (client == null) {
                if (!isFinishing && !isDestroyed) {
                    Log.e("ClientReceiptsActivity", "Klient o ID $clientId nie został znaleziony (być może usunięty).")
                    Toast.makeText(this, R.string.error_client_not_found, Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@observe
            }

            clientDescriptionTextView.text = if (client.description.isNullOrBlank()) {
                getString(R.string.client_item_id_prefix) + client.id.toString()
            } else {
                client.description
            }

            val appNumberText = client.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.client_item_app_number_prefix) + " " + it
            }
            clientAppNumberTextView.text = appNumberText
            clientAppNumberTextView.isVisible = appNumberText != null

            val amoditNumberText = client.amoditNumber?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.client_item_amodit_number_prefix) + " " + it
            }
            clientAmoditNumberTextView.text = amoditNumberText
            clientAmoditNumberTextView.isVisible = amoditNumberText != null
        }
    }


    /**
     * Obserwuje zmiany na liście paragonów klienta oraz mapę sklepów
     * i wywołuje aktualizację adaptera paragonów, gdy oba zestawy danych są dostępne.
     */
    private fun observeReceiptsAndStores() {
        viewModel.receiptsForClient.observe(this) { receipts ->
            Log.d("ClientReceiptsActivity", "Otrzymano ${receipts?.size ?: 0} paragonów dla klienta.")
            currentReceipts = receipts
            tryUpdateReceiptAdapter()
        }

        storeViewModel.allStoresMap.observe(this) { storeMap ->
            Log.d("ClientReceiptsActivity", "Otrzymano mapę sklepów, rozmiar: ${storeMap?.size ?: 0}")
            currentStoreMap = storeMap
            tryUpdateReceiptAdapter()
        }
    }

    /**
     * Sprawdza, czy dane paragonów i mapa sklepów są dostępne,
     * a następnie wywołuje `updateReceipts` w adapterze paragonów.
     */
    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil w ReceiptAdapter.updateReceipts
    private fun tryUpdateReceiptAdapter() {
        val receipts = currentReceipts
        val storeMap = currentStoreMap

        if (receipts != null && storeMap != null) {
            Log.d("ClientReceiptsActivity", "Dane paragonów i mapa sklepów dostępne. Aktualizowanie adaptera paragonów...")
            receiptAdapter.updateReceipts(this, receipts, storeMap, emptyMap(), false)
        } else {
            Log.d("ClientReceiptsActivity", "Nie wszystkie dane są jeszcze dostępne do aktualizacji adaptera paragonów.")
        }
    }


    /** Obserwuje listę zdjęć klienta ([Photo]) z ViewModelu i wywołuje ich wyświetlenie. */
    private fun observeClientPhotos() {
        viewModel.clientPhotos.observe(this) { photos ->
            displayClientPhotos(photos ?: emptyList())
        }
    }

    /**
     * Wyświetla zdjęcia klienta w odpowiednich kontenerach (na razie ukrytych)
     * oraz ustawia miniaturę w nagłówku aktywności za pomocą Glide.
     */
    private fun displayClientPhotos(photos: List<com.kaminski.paragownik.data.Photo>) {
        clientPhotosContainerDetails.removeAllViews()
        transactionPhotosContainerDetails.removeAllViews()

        val clientPhotos = photos.filter { it.type == PhotoType.CLIENT }
        val transactionPhotos = photos.filter { it.type == PhotoType.TRANSACTION }

        // Pokaż/ukryj sekcję zdjęć klienta (NA RAZIE UKRYTE)
        val clientSectionVisible = clientPhotos.isNotEmpty()
        clientPhotosTitleDetails.visibility = View.GONE
        clientPhotosScrollViewDetails.visibility = View.GONE
        // if (clientSectionVisible) {
        //     clientPhotos.forEach { addPhotoThumbnailToDetailsView(it.uri.toUri(), clientPhotosContainerDetails) }
        // }

        // Pokaż/ukryj sekcję zdjęć transakcji (NA RAZIE UKRYTE)
        val transactionSectionVisible = transactionPhotos.isNotEmpty()
        transactionPhotosTitleDetails.visibility = View.GONE
        transactionPhotosScrollViewDetails.visibility = View.GONE
        // if (transactionSectionVisible) {
        //     transactionPhotos.forEach { addPhotoThumbnailToDetailsView(it.uri.toUri(), transactionPhotosContainerDetails) }
        // }

         val firstClientPhotoUri = clientPhotos.firstOrNull()?.uri?.toUri()
         if (firstClientPhotoUri != null) {
             Glide.with(this)
                 .load(firstClientPhotoUri)
                 .placeholder(R.drawable.ic_photo_placeholder)
                 .error(R.drawable.ic_photo_placeholder)
                 .centerCrop()
                 .into(clientPhotoImageView)
             clientPhotoImageView.visibility = View.VISIBLE
         } else {
             Glide.with(this).clear(clientPhotoImageView)
             clientPhotoImageView.visibility = View.GONE
         }
    }


    /**
     * Dodaje miniaturę zdjęcia do podanego kontenera [LinearLayout] w widoku szczegółów.
     * Używa Glide do ładowania obrazu. Przycisk usuwania jest ukryty.
     * (Metoda na razie nieużywana, bo sekcje zdjęć są ukryte).
     */
    private fun addPhotoThumbnailToDetailsView(photoUri: Uri, container: LinearLayout) {
        val inflater = LayoutInflater.from(this)
        val thumbnailView = inflater.inflate(R.layout.photo_thumbnail_item, container, false)
        val imageView = thumbnailView.findViewById<ImageView>(R.id.photoThumbnailImageView)
        val deleteButton = thumbnailView.findViewById<ImageButton>(R.id.deletePhotoButton)

        try {
            Glide.with(this)
                .load(photoUri)
                .placeholder(R.drawable.ic_photo_placeholder)
                .error(R.drawable.ic_photo_placeholder)
                .centerCrop()
                .into(imageView)

            deleteButton.visibility = View.GONE

            // TODO: Dodać listener kliknięcia na imageView, aby otworzyć pełny ekran
            // imageView.setOnClickListener { /* Otwórz pełny ekran */ }

            container.addView(thumbnailView)
        } catch (e: Exception) {
             Log.e("ClientReceiptsActivity", "Błąd ładowania miniatury $photoUri", e)
        }
    }


    /**
     * Obsługa kliknięcia paragonu na liście.
     * Uruchamia EditReceiptActivity dla wybranego paragonu, przekazując kontekst "CLIENT_LIST".
     */
    override fun onReceiptClick(receiptId: Long) {
        val intent = Intent(this, EditReceiptActivity::class.java)
        intent.putExtra("RECEIPT_ID", receiptId)
        intent.putExtra("CONTEXT", "CLIENT_LIST")
        startActivity(intent)
    }
}







