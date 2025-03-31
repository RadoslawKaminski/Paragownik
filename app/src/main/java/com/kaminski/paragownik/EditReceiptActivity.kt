package com.kaminski.paragownik

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView // Dodano import
import android.widget.TextView // Import TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager // Dodano import
import androidx.recyclerview.widget.RecyclerView // Dodano import
import com.bumptech.glide.Glide // Import Glide
import com.kaminski.paragownik.adapter.GlideScaleType // Import enuma
import com.kaminski.paragownik.adapter.PhotoAdapter // Dodano import
import com.kaminski.paragownik.data.Photo // Dodano import
import com.kaminski.paragownik.data.PhotoType // Dodano import
import com.kaminski.paragownik.viewmodel.EditReceiptViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.Calendar // Dodano import

/**
 * Aktywność odpowiedzialna za przeglądanie i edycję istniejącego paragonu oraz danych klienta,
 * w tym zarządzanie wieloma zdjęciami. Używa Glide do ładowania obrazów.
 */
class EditReceiptActivity : AppCompatActivity() {

    // --- Widoki UI ---
    private lateinit var editReceiptStoreNumberEditText: EditText
    private lateinit var editReceiptNumberEditText: EditText
    private lateinit var editReceiptDateEditText: EditText
    private lateinit var editVerificationDateEditText: EditText
    private lateinit var editVerificationDateTodayCheckBox: CheckBox
    private lateinit var editClientDescriptionEditText: EditText
    private lateinit var editClientAppNumberEditText: EditText
    private lateinit var editAmoditNumberEditText: EditText
    private lateinit var saveReceiptButton: Button
    private lateinit var deleteReceiptButton: Button
    private lateinit var deleteClientButton: Button
    private lateinit var editModeImageButton: ImageButton // Ikona "Edytuj"
    private lateinit var showClientReceiptsButton: Button // Przycisk nawigacji do paragonów klienta
    private lateinit var showStoreReceiptsButton: Button // Przycisk nawigacji do paragonów sklepu
    // Layouty i etykiety do ukrywania/pokazywania
    private lateinit var editVerificationSectionLayout: LinearLayout
    private lateinit var verificationSectionTitleEdit: TextView // Etykieta edycji
    private lateinit var verificationSectionTitleView: TextView // Etykieta widoku
    private lateinit var editDescriptionLayout: LinearLayout
    private lateinit var editAppNumberLayout: LinearLayout
    private lateinit var editAmoditNumberLayout: LinearLayout
    private lateinit var clientDataSectionTitleEdit: TextView // Etykieta edycji
    private lateinit var clientDataSectionTitleView: TextView // Etykieta widoku

    // Nowe widoki dla zdjęć
    private lateinit var clientPhotosTitleEdit: TextView
    private lateinit var clientPhotosTitleView: TextView
    private lateinit var clientPhotosScrollViewEdit: HorizontalScrollView
    private lateinit var clientPhotosContainerEdit: LinearLayout
    private lateinit var clientPhotosRecyclerViewView: RecyclerView // NOWE
    private lateinit var addClientPhotoButtonEdit: Button

    private lateinit var transactionPhotosTitleEdit: TextView
    private lateinit var transactionPhotosTitleView: TextView
    private lateinit var transactionPhotosScrollViewEdit: HorizontalScrollView
    private lateinit var transactionPhotosContainerEdit: LinearLayout
    private lateinit var transactionPhotosRecyclerViewView: RecyclerView // NOWE
    private lateinit var addTransactionPhotoButtonEdit: Button

    // Adaptery dla RecyclerView zdjęć
    private lateinit var clientPhotosAdapter: PhotoAdapter // NOWE
    private lateinit var transactionPhotosAdapter: PhotoAdapter // NOWE


    // --- ViewModel ---
    private lateinit var editReceiptViewModel: EditReceiptViewModel

    // --- Dane pomocnicze ---
    private var receiptId: Long = -1L
    private var currentClientId: Long? = null
    private var currentStoreId: Long = -1L
    private var loadDataJob: Job? = null
    private var isEditMode = false
    private var navigationContext: String? = null

    // Nowe listy do zarządzania zdjęciami w UI
    private val currentClientPhotos = mutableListOf<Photo>() // Istniejące zdjęcia klienta
    private val currentTransactionPhotos = mutableListOf<Photo>() // Istniejące zdjęcia transakcji
    private val photosToAdd = mutableMapOf<PhotoType, MutableList<Uri>>() // Nowe URI do dodania
    private val photosToRemove = mutableListOf<Uri>() // Istniejące URI do usunięcia
    private val photoUriToViewMapEdit = mutableMapOf<Uri, View>() // Mapa URI -> Widok miniatury (tryb edycji)
    private var currentPhotoTypeToAdd: PhotoType? = null // Typ zdjęcia dla launchera


    // Launcher do wybierania zdjęcia z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            Log.d("EditReceiptActivity", "Otrzymano tymczasowe URI: $sourceUri")
            val destinationUri = copyImageToInternalStorage(sourceUri)
            destinationUri?.let { finalUri ->
                // Dodaj URI do listy do dodania i wyświetl miniaturę w trybie edycji
                val type = currentPhotoTypeToAdd
                if (type != null) {
                    // Sprawdź czy URI już nie istnieje w listach (dodanych lub istniejących)
                    val alreadyExists = photosToAdd[type]?.contains(finalUri) == true ||
                                        (if (type == PhotoType.CLIENT) currentClientPhotos else currentTransactionPhotos)
                                            .any { it.uri == finalUri.toString() }

                    if (!alreadyExists) {
                        photosToAdd.getOrPut(type) { mutableListOf() }.add(finalUri)
                        val container = if (type == PhotoType.CLIENT) clientPhotosContainerEdit else transactionPhotosContainerEdit
                        addPhotoThumbnail(finalUri, container, type, true) // true = tryb edycji
                        Log.d("EditReceiptActivity", "Przygotowano do dodania zdjęcie ($type): $finalUri")
                        // Pokaż sekcję, jeśli była ukryta
                        updatePhotoSectionVisibility(type, true)
                    } else {
                         Log.d("EditReceiptActivity", "Zdjęcie $finalUri już istnieje lub zostało dodane.")
                         Toast.makeText(this, R.string.photo_already_added, Toast.LENGTH_SHORT).show()
                    }
                } else {
                     Log.w("EditReceiptActivity", "Nieznany typ zdjęcia do dodania (currentPhotoTypeToAdd is null)")
                }
            }
        } ?: run {
            Log.d("EditReceiptActivity", "Nie wybrano nowego zdjęcia.")
        }
        currentPhotoTypeToAdd = null // Zresetuj typ
    }


    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_receipt)

        initializeViews()
        initializeViewModel()

        // Odczytaj ID paragonu i kontekst nawigacji z Intentu
        receiptId = intent.getLongExtra("RECEIPT_ID", -1L)
        navigationContext = intent.getStringExtra("CONTEXT")
        Log.d("EditReceiptActivity", "Otrzymano receiptId: $receiptId, kontekst: $navigationContext")

        if (receiptId == -1L) {
            Toast.makeText(this, R.string.error_invalid_receipt_id, Toast.LENGTH_LONG).show()
            Log.e("EditReceiptActivity", "Nieprawidłowe RECEIPT_ID przekazane w Intencie.")
            finish()
            return
        }

        setupDateEditText(editReceiptDateEditText)
        setupDateEditText(editVerificationDateEditText)
        setupVerificationDateCheckBox()
        loadReceiptData()
        setupButtonClickListeners()

        // Inicjalizacja adapterów i RecyclerView dla zdjęć w trybie widoku
        // Używamy GlideScaleType.FIT_CENTER dla dużych zdjęć
        clientPhotosAdapter = PhotoAdapter(emptyList(), R.layout.large_photo_item, R.id.largePhotoImageViewItem, GlideScaleType.FIT_CENTER) { uri -> /* TODO: Obsługa kliknięcia */ }
        clientPhotosRecyclerViewView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        clientPhotosRecyclerViewView.adapter = clientPhotosAdapter

        transactionPhotosAdapter = PhotoAdapter(emptyList(), R.layout.large_photo_item, R.id.largePhotoImageViewItem, GlideScaleType.FIT_CENTER) { uri -> /* TODO: Obsługa kliknięcia */ }
        transactionPhotosRecyclerViewView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        transactionPhotosRecyclerViewView.adapter = transactionPhotosAdapter

        updateUiMode(false) // Ustaw tryb widoku na starcie
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI.
     */
    private fun initializeViews() {
        editReceiptStoreNumberEditText = findViewById(R.id.editReceiptStoreNumberEditText)
        editReceiptNumberEditText = findViewById(R.id.editReceiptNumberEditText)
        editReceiptDateEditText = findViewById(R.id.editReceiptDateEditText)
        editVerificationDateEditText = findViewById(R.id.editVerificationDateEditText)
        editVerificationDateTodayCheckBox = findViewById(R.id.editVerificationDateTodayCheckBox)
        editClientDescriptionEditText = findViewById(R.id.editClientDescriptionEditText)
        editClientAppNumberEditText = findViewById(R.id.editClientAppNumberEditText)
        editAmoditNumberEditText = findViewById(R.id.editAmoditNumberEditText)
        saveReceiptButton = findViewById(R.id.saveReceiptButton)
        deleteReceiptButton = findViewById(R.id.deleteReceiptButton)
        deleteClientButton = findViewById(R.id.deleteClientButton)
        editModeImageButton = findViewById(R.id.editModeImageButton) // Ikona Edytuj
        showClientReceiptsButton = findViewById(R.id.showClientReceiptsButton)
        showStoreReceiptsButton = findViewById(R.id.showStoreReceiptsButton)
        // Layouty i etykiety do ukrywania/pokazywania
        editVerificationSectionLayout = findViewById(R.id.editVerificationSectionLayout)
        verificationSectionTitleEdit = findViewById(R.id.verificationSectionTitleEdit)
        verificationSectionTitleView = findViewById(R.id.verificationSectionTitleView)
        editDescriptionLayout = findViewById(R.id.editDescriptionLayout)
        editAppNumberLayout = findViewById(R.id.editAppNumberLayout)
        editAmoditNumberLayout = findViewById(R.id.editAmoditNumberLayout)
        clientDataSectionTitleEdit = findViewById(R.id.clientDataSectionTitleEdit)
        clientDataSectionTitleView = findViewById(R.id.clientDataSectionTitleView)

        // Inicjalizacja nowych widoków zdjęć
        clientPhotosTitleEdit = findViewById(R.id.clientPhotosTitleEdit)
        clientPhotosTitleView = findViewById(R.id.clientPhotosTitleView)
        clientPhotosScrollViewEdit = findViewById(R.id.clientPhotosScrollViewEdit)
        clientPhotosContainerEdit = findViewById(R.id.clientPhotosContainerEdit)
        clientPhotosRecyclerViewView = findViewById(R.id.clientPhotosRecyclerViewView) // NOWE
        addClientPhotoButtonEdit = findViewById(R.id.addClientPhotoButtonEdit)

        transactionPhotosTitleEdit = findViewById(R.id.transactionPhotosTitleEdit)
        transactionPhotosTitleView = findViewById(R.id.transactionPhotosTitleView)
        transactionPhotosScrollViewEdit = findViewById(R.id.transactionPhotosScrollViewEdit)
        transactionPhotosContainerEdit = findViewById(R.id.transactionPhotosContainerEdit)
        transactionPhotosRecyclerViewView = findViewById(R.id.transactionPhotosRecyclerViewView) // NOWE
        addTransactionPhotoButtonEdit = findViewById(R.id.addTransactionPhotoButtonEdit)
    }


    /**
     * Inicjalizuje ViewModel.
     */
    private fun initializeViewModel() {
        editReceiptViewModel = ViewModelProvider(this).get(EditReceiptViewModel::class.java)
    }

    /**
     * Ustawia listenery kliknięć dla przycisków.
     */
    private fun setupButtonClickListeners() {
        saveReceiptButton.setOnClickListener { saveChanges() }
        deleteReceiptButton.setOnClickListener { showDeleteReceiptDialog() }
        deleteClientButton.setOnClickListener { showDeleteClientDialog() }

        // Listener dla przycisku dodawania zdjęcia KLIENTA (w trybie edycji)
        addClientPhotoButtonEdit.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.CLIENT
            pickImageLauncher.launch("image/*")
        }

        // Listener dla przycisku dodawania zdjęcia TRANSAKCJI (w trybie edycji)
        addTransactionPhotoButtonEdit.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.TRANSACTION
            pickImageLauncher.launch("image/*")
        }

        // Listener dla ikony "Edytuj"
        editModeImageButton.setOnClickListener {
            updateUiMode(true) // Włącz tryb edycji
        }

        // Listener dla przycisku "Pokaż paragony klienta"
        showClientReceiptsButton.setOnClickListener {
            currentClientId?.let { clientId ->
                val intent = Intent(this, ClientReceiptsActivity::class.java)
                intent.putExtra("CLIENT_ID", clientId)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                Log.d("EditReceiptActivity", "Nawigacja do ClientReceiptsActivity dla klienta ID: $clientId")
            } ?: run {
                Toast.makeText(this, R.string.error_cannot_identify_client, Toast.LENGTH_SHORT).show()
            }
        }

        // Listener dla przycisku "Pokaż paragony drogerii"
        showStoreReceiptsButton.setOnClickListener {
            if (currentStoreId != -1L) {
                val intent = Intent(this, ReceiptListActivity::class.java)
                intent.putExtra("STORE_ID", currentStoreId)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                Log.d("EditReceiptActivity", "Nawigacja do ReceiptListActivity dla sklepu ID: $currentStoreId")
            } else {
                Toast.makeText(this, R.string.error_invalid_store_id, Toast.LENGTH_SHORT).show()
            }
        }
    }


    /**
     * Wczytuje dane paragonu, klienta, sklepu i zdjęć z ViewModelu i wypełnia formularz.
     */
    private fun loadReceiptData() {
        loadDataJob?.cancel()
        loadDataJob = lifecycleScope.launch {
            // Zmieniono odbierany typ na Triple
            editReceiptViewModel.getReceiptWithClientAndStoreNumber(receiptId)
                .collectLatest { triple -> // Zmieniono nazwę zmiennej
                    if (!isActive) return@collectLatest

                    val receiptWithClient = triple.first
                    val storeNumber = triple.second
                    val photos = triple.third // Pobierz listę zdjęć

                    // Wyczyść kontenery i listy przed załadowaniem nowych danych
                    clearPhotoData()

                    if (receiptWithClient != null && receiptWithClient.client != null) {
                        val receipt = receiptWithClient.receipt
                        val client = receiptWithClient.client
                        currentClientId = client.id
                        currentStoreId = receipt.storeId

                        // Wypełnianie pól tekstowych paragonu
                        editReceiptStoreNumberEditText.setText(storeNumber ?: "")
                        editReceiptNumberEditText.setText(receipt.receiptNumber)
                        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        editReceiptDateEditText.setText(dateFormat.format(receipt.receiptDate))

                        // Obsługa daty weryfikacji
                        receipt.verificationDate?.let { verificationDate ->
                            val formattedVerificationDate = dateFormat.format(verificationDate)
                            editVerificationDateEditText.setText(formattedVerificationDate)
                            val todayDate = dateFormat.format(Calendar.getInstance().time)
                            editVerificationDateTodayCheckBox.isChecked = formattedVerificationDate == todayDate
                        } ?: run {
                            editVerificationDateEditText.text.clear()
                            editVerificationDateTodayCheckBox.isChecked = false
                        }

                        // Wypełnianie pól tekstowych klienta
                        editClientDescriptionEditText.setText(client.description ?: "")
                        editClientAppNumberEditText.setText(client.clientAppNumber ?: "")
                        editAmoditNumberEditText.setText(client.amoditNumber ?: "")

                        // Rozdziel zdjęcia na typy i wypełnij kontenery
                        photos?.let { photoList ->
                            currentClientPhotos.addAll(photoList.filter { it.type == PhotoType.CLIENT })
                            currentTransactionPhotos.addAll(photoList.filter { it.type == PhotoType.TRANSACTION })

                            // Wypełnij kontenery dla trybu widoku i edycji
                            populatePhotoContainer(clientPhotosContainerEdit, currentClientPhotos, PhotoType.CLIENT, true) // true = tryb edycji
                            populatePhotoContainer(null, currentClientPhotos, PhotoType.CLIENT, false) // false = tryb widoku (kontener niepotrzebny)
                            populatePhotoContainer(transactionPhotosContainerEdit, currentTransactionPhotos, PhotoType.TRANSACTION, true)
                            populatePhotoContainer(null, currentTransactionPhotos, PhotoType.TRANSACTION, false)
                        }

                        // Zaktualizuj widoczność pól na podstawie załadowanych danych i bieżącego trybu
                        updateUiMode(isEditMode) // Przekaż bieżący tryb

                    } else {
                         // Obsługa sytuacji, gdy dane nie zostały znalezione
                         if (isActive) {
                             Log.e("EditReceiptActivity", "Nie znaleziono danych dla receiptId: $receiptId (prawdopodobnie usunięto)")
                             // Toast.makeText(this@EditReceiptActivity, R.string.error_receipt_not_found, Toast.LENGTH_SHORT).show()
                             // finish()
                         }
                         // Wyczyść też kontenery zdjęć w razie błędu
                         clearPhotoData()
                         updateUiMode(isEditMode)
                    }
                }
        }
    }

    /** Wyczyść kontenery i listy danych zdjęć. */
    private fun clearPhotoData() {
        clientPhotosContainerEdit.removeAllViews()
        transactionPhotosContainerEdit.removeAllViews()
        clientPhotosAdapter.updatePhotos(emptyList()) // NOWE
        transactionPhotosAdapter.updatePhotos(emptyList()) // NOWE
        currentClientPhotos.clear()
        currentTransactionPhotos.clear()
        photosToAdd.clear()
        photosToRemove.clear()
        photoUriToViewMapEdit.clear()
    }


    /**
     * Wypełnia kontener miniaturami zdjęć (tryb edycji) lub RecyclerView (tryb widoku).
     * @param container LinearLayout dla trybu edycji (lub null dla trybu widoku).
     * @param photos Lista obiektów Photo do wyświetlenia.
     * @param photoType Typ zdjęć w tej liście.
     * @param isEditing Określa, czy jesteśmy w trybie edycji.
     */
    private fun populatePhotoContainer(container: LinearLayout?, photos: List<Photo>, photoType: PhotoType, isEditing: Boolean) {
        if (isEditing) {
            // Logika dla trybu edycji (LinearLayout)
            val editContainer = container ?: return // Potrzebujemy kontenera w trybie edycji
            editContainer.removeAllViews()
            val mapToUse = photoUriToViewMapEdit
            // Nie czyścimy mapy tutaj, bo addPhotoThumbnail dodaje do niej wpisy

            for (photo in photos) {
                 try {
                    // Sprawdź, czy to zdjęcie nie jest na liście do usunięcia
                    if (!photosToRemove.contains(photo.uri.toUri())) {
                        addPhotoThumbnail(photo.uri.toUri(), editContainer, photoType, true)
                    }
                } catch (e: Exception) {
                    Log.e("EditReceiptActivity", "Błąd podczas dodawania miniatury dla URI: ${photo.uri}", e)
                 }
            }
             photosToAdd[photoType]?.forEach { uri ->
                 if (!mapToUse.containsKey(uri)) {
                     try {
                        addPhotoThumbnail(uri, editContainer, photoType, true)
                    } catch (e: Exception) {
                        Log.e("EditReceiptActivity", "Błąd podczas dodawania NOWEJ miniatury dla URI: $uri", e)
                    }
                }
            }
        } else {
            // Logika dla trybu widoku (RecyclerView)
            val adapter = if (photoType == PhotoType.CLIENT) clientPhotosAdapter else transactionPhotosAdapter
            val recyclerView = if (photoType == PhotoType.CLIENT) clientPhotosRecyclerViewView else transactionPhotosRecyclerViewView

            // Filtrujemy zdjęcia, które nie są na liście do usunięcia (choć w trybie widoku nie powinno być nic do usunięcia)
            val photosToShow = photos.filter { photo ->
                try {
                    !photosToRemove.contains(photo.uri.toUri())
                } catch (e: Exception) {
                    Log.w("EditReceiptActivity", "Błąd parsowania URI ${photo.uri} podczas filtrowania zdjęć do wyświetlenia.", e)
                    false // Pomiń błędne URI
                }
            }
            adapter.updatePhotos(photosToShow)
            // Widoczność RecyclerView jest zarządzana w updatePhotoSectionVisibility
        }
        // Zaktualizuj widoczność sekcji po wypełnieniu
        updatePhotoSectionVisibility(photoType, isEditing)
    }


    /**
     * Dodaje widok miniatury zdjęcia do określonego kontenera (tylko dla trybu edycji), używając Glide.
     * @param photoUri URI zdjęcia do wyświetlenia.
     * @param container LinearLayout, do którego zostanie dodana miniatura.
     * @param photoType Typ zdjęcia.
     * @param isEditing Zawsze true w tej wersji metody.
     */
    private fun addPhotoThumbnail(photoUri: Uri, container: LinearLayout, photoType: PhotoType, isEditing: Boolean) {
        if (!isEditing) return // Ta metoda jest tylko dla trybu edycji

        val inflater = LayoutInflater.from(this)
        val thumbnailView = inflater.inflate(R.layout.photo_thumbnail_item, container, false)
        val imageView = thumbnailView.findViewById<ImageView>(R.id.photoThumbnailImageView)
        val deleteButton = thumbnailView.findViewById<ImageButton>(R.id.deletePhotoButton)

        try {
            // Użycie Glide do załadowania miniatury
            Glide.with(this)
                .load(photoUri)
                .placeholder(R.drawable.ic_photo_placeholder)
                .error(R.drawable.ic_photo_placeholder)
                .centerCrop()
                .into(imageView)

            deleteButton.visibility = View.VISIBLE // Zawsze widoczny w trybie edycji

            deleteButton.setOnClickListener {
                // Usuń widok z kontenera edycji
                container.removeView(thumbnailView)
                // Sprawdź, czy to było nowo dodane zdjęcie czy istniejące
                val wasAddedInThisSession = photosToAdd[photoType]?.remove(photoUri) ?: false
                if (!wasAddedInThisSession) {
                    // Jeśli to było istniejące zdjęcie, dodaj je do listy do usunięcia
                    // Tylko jeśli jeszcze go tam nie ma
                    if (!photosToRemove.contains(photoUri)) {
                        photosToRemove.add(photoUri)
                        Log.d("EditReceiptActivity", "Oznaczono do usunięcia istniejące zdjęcie: $photoUri")
                    }
                } else {
                    Log.d("EditReceiptActivity", "Usunięto nowo dodane zdjęcie (przed zapisem): $photoUri")
                }
                // Usuń z mapy śledzenia dla trybu edycji
                photoUriToViewMapEdit.remove(photoUri)
                // Zaktualizuj widoczność sekcji
                updatePhotoSectionVisibility(photoType, true)
            }
            // Zapisz mapowanie URI na widok dla trybu edycji
            photoUriToViewMapEdit[photoUri] = thumbnailView
            container.addView(thumbnailView)

        } catch (e: Exception) {
            Log.e("EditReceiptActivity", "Błąd ładowania miniatury $photoUri", e)
            // Można dodać placeholder błędu zamiast nie dodawać widoku
        }
    }


    /** Aktualizuje widoczność kontenerów i tytułów sekcji zdjęć. */
    private fun updatePhotoSectionVisibility(photoType: PhotoType, isEditing: Boolean) {
        // Sprawdź, czy są jakiekolwiek zdjęcia (istniejące LUB nowo dodane) tego typu, które nie są do usunięcia
        val photosExist = if (photoType == PhotoType.CLIENT) {
            currentClientPhotos.any { !photosToRemove.contains(it.uri.toUri()) } || photosToAdd[PhotoType.CLIENT]?.isNotEmpty() == true
        } else {
            currentTransactionPhotos.any { !photosToRemove.contains(it.uri.toUri()) } || photosToAdd[PhotoType.TRANSACTION]?.isNotEmpty() == true
        }

        val titleEdit = if (photoType == PhotoType.CLIENT) clientPhotosTitleEdit else transactionPhotosTitleEdit
        val titleView = if (photoType == PhotoType.CLIENT) clientPhotosTitleView else transactionPhotosTitleView
        val scrollViewEdit = if (photoType == PhotoType.CLIENT) clientPhotosScrollViewEdit else transactionPhotosScrollViewEdit
        val recyclerViewView = if (photoType == PhotoType.CLIENT) clientPhotosRecyclerViewView else transactionPhotosRecyclerViewView // NOWE
        val addButton = if (photoType == PhotoType.CLIENT) addClientPhotoButtonEdit else addTransactionPhotoButtonEdit

        // Widoczność w trybie edycji
        titleEdit.visibility = if (isEditing) View.VISIBLE else View.GONE
        scrollViewEdit.visibility = if (isEditing && photosExist) View.VISIBLE else View.GONE
        addButton.visibility = if (isEditing) View.VISIBLE else View.GONE

        // Widoczność w trybie widoku
        titleView.visibility = if (!isEditing && photosExist) View.VISIBLE else View.GONE
        recyclerViewView.visibility = if (!isEditing && photosExist) View.VISIBLE else View.GONE // NOWE
    }


    /**
     * Aktualizuje widoczność i stan edytowalności elementów UI w zależności od trybu.
     */
    private fun updateUiMode(isEditing: Boolean) {
        isEditMode = isEditing

        // Włącz/Wyłącz EditTexty i CheckBox
        editReceiptStoreNumberEditText.isEnabled = isEditing
        editReceiptNumberEditText.isEnabled = isEditing
        editReceiptDateEditText.isEnabled = isEditing
        editVerificationDateEditText.isEnabled = isEditing && !editVerificationDateTodayCheckBox.isChecked
        editVerificationDateTodayCheckBox.isEnabled = isEditing
        // Ukryj CheckBox w trybie widoku
        editVerificationDateTodayCheckBox.visibility = if (isEditing) View.VISIBLE else View.GONE
        editClientDescriptionEditText.isEnabled = isEditing
        editClientAppNumberEditText.isEnabled = isEditing
        editAmoditNumberEditText.isEnabled = isEditing

        // Pokaż/Ukryj przyciski akcji i ikonę edycji
        saveReceiptButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteReceiptButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteClientButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        editModeImageButton.visibility = if (isEditing) View.GONE else View.VISIBLE // Ikona edycji

        // Pokaż/Ukryj opcjonalne sekcje i przełącz etykiety
        val hasVerificationDate = !editVerificationDateEditText.text.isNullOrBlank()
        editVerificationSectionLayout.visibility = if (isEditing || hasVerificationDate) View.VISIBLE else View.GONE
        // Pokaż odpowiednią etykietę sekcji weryfikacji
        verificationSectionTitleEdit.visibility = if (isEditing && editVerificationSectionLayout.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        verificationSectionTitleView.visibility = if (!isEditing && hasVerificationDate) View.VISIBLE else View.GONE

        val hasDescription = !editClientDescriptionEditText.text.isNullOrBlank()
        editDescriptionLayout.visibility = if (isEditing || hasDescription) View.VISIBLE else View.GONE

        val hasAppNumber = !editClientAppNumberEditText.text.isNullOrBlank()
        editAppNumberLayout.visibility = if (isEditing || hasAppNumber) View.VISIBLE else View.GONE

        val hasAmoditNumber = !editAmoditNumberEditText.text.isNullOrBlank()
        editAmoditNumberLayout.visibility = if (isEditing || hasAmoditNumber) View.VISIBLE else View.GONE

        // Przełącz etykietę sekcji danych klienta
        clientDataSectionTitleEdit.visibility = if (isEditing) View.VISIBLE else View.GONE
        clientDataSectionTitleView.visibility = if (!isEditing) View.VISIBLE else View.GONE

        // Zaktualizuj widoczność sekcji zdjęć
        updatePhotoSectionVisibility(PhotoType.CLIENT, isEditing)
        updatePhotoSectionVisibility(PhotoType.TRANSACTION, isEditing)

        // Pokaż/Ukryj przyciski nawigacyjne (tylko w trybie widoku i w odpowiednim kontekście)
        showClientReceiptsButton.visibility = if (!isEditing && navigationContext == "STORE_LIST") View.VISIBLE else View.GONE
        showStoreReceiptsButton.visibility = if (!isEditing && navigationContext == "CLIENT_LIST") View.VISIBLE else View.GONE

        // Odśwież kontenery zdjęć, aby pokazać/ukryć przyciski usuwania i zaktualizować adaptery
        populatePhotoContainer(clientPhotosContainerEdit, currentClientPhotos, PhotoType.CLIENT, isEditing)
        populatePhotoContainer(null, currentClientPhotos, PhotoType.CLIENT, isEditing) // Dla RecyclerView
        populatePhotoContainer(transactionPhotosContainerEdit, currentTransactionPhotos, PhotoType.TRANSACTION, isEditing)
        populatePhotoContainer(null, currentTransactionPhotos, PhotoType.TRANSACTION, isEditing) // Dla RecyclerView
    }


    /**
     * Zbiera dane z formularza i zapisuje zmiany w ViewModelu.
     */
    private fun saveChanges() {
        val storeNumberString = editReceiptStoreNumberEditText.text.toString().trim()
        val receiptNumber = editReceiptNumberEditText.text.toString().trim()
        val receiptDateString = editReceiptDateEditText.text.toString().trim()
        val verificationDateString = editVerificationDateEditText.text.toString().trim()
        val clientDescription = editClientDescriptionEditText.text.toString().trim()
        val clientAppNumber = editClientAppNumberEditText.text.toString().trim()
        val amoditNumber = editAmoditNumberEditText.text.toString().trim()

        if (storeNumberString.isEmpty() || receiptNumber.isEmpty() || receiptDateString.isEmpty()) {
            Toast.makeText(this, R.string.error_fill_required_edit_fields, Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            val result = editReceiptViewModel.updateReceiptAndClient(
                receiptId = receiptId,
                storeNumberString = storeNumberString,
                receiptNumber = receiptNumber,
                receiptDateString = receiptDateString,
                verificationDateString = verificationDateString.takeIf { it.isNotEmpty() },
                clientDescription = clientDescription.takeIf { it.isNotEmpty() },
                clientAppNumber = clientAppNumber.takeIf { it.isNotEmpty() },
                amoditNumber = amoditNumber.takeIf { it.isNotEmpty() },
                // Przekaż listy URI dodawanych i usuwanych zdjęć
                clientPhotoUrisToAdd = photosToAdd[PhotoType.CLIENT]?.map { it.toString() } ?: emptyList(),
                transactionPhotoUrisToAdd = photosToAdd[PhotoType.TRANSACTION]?.map { it.toString() } ?: emptyList(),
                photoUrisToRemove = photosToRemove.map { it.toString() }
            )
            handleEditResult(result)
        }
    }

    /**
     * Wyświetla dialog potwierdzenia usunięcia paragonu.
     */
    private fun showDeleteReceiptDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_receipt_confirmation_title)
            .setMessage(R.string.delete_receipt_confirmation_message)
            .setPositiveButton(R.string.delete) { _, _ -> deleteReceipt() }
            .setNegativeButton(R.string.cancel, null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    /**
     * Usuwa bieżący paragon, anulując najpierw obserwację danych.
     */
    private fun deleteReceipt() {
        loadDataJob?.cancel()
        Log.d("EditReceiptActivity", "Anulowano loadDataJob przed usunięciem paragonu.")

        lifecycleScope.launch {
            // Pobieramy tylko ID, bo reszta może być nieaktualna
            val currentReceipt = editReceiptViewModel.getReceiptWithClientAndStoreNumber(receiptId)
                .map { it.first?.receipt }
                .firstOrNull() // Pobierz pierwszą wartość lub null

            if (currentReceipt == null) {
                Toast.makeText(this@EditReceiptActivity, R.string.error_cannot_get_receipt_data, Toast.LENGTH_LONG).show()
                Log.e("EditReceiptActivity", "Nie udało się pobrać Receipt (id: $receiptId) do usunięcia.")
                // Możemy spróbować usunąć na podstawie samego ID, ale to ryzykowne
                // Na razie traktujemy to jako błąd
                handleEditResult(EditReceiptViewModel.EditResult.ERROR_NOT_FOUND, true)
                return@launch
            }

            val result = editReceiptViewModel.deleteReceipt(currentReceipt)
            handleEditResult(result, true)
        }
    }


    /**
     * Wyświetla dialog potwierdzenia usunięcia klienta.
     */
    private fun showDeleteClientDialog() {
        if (currentClientId == null) {
            Toast.makeText(this, R.string.error_cannot_identify_client, Toast.LENGTH_LONG).show()
            Log.e("EditReceiptActivity", "Próba usunięcia klienta, ale currentClientId jest null.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.delete_client_confirmation_title)
            .setMessage(R.string.delete_client_confirmation_message)
            .setPositiveButton(R.string.delete) { _, _ -> deleteClient() }
            .setNegativeButton(R.string.cancel, null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    /**
     * Usuwa bieżącego klienta i jego paragony, anulując najpierw obserwację danych.
     */
    private fun deleteClient() {
        loadDataJob?.cancel()
        Log.d("EditReceiptActivity", "Anulowano loadDataJob przed usunięciem klienta.")

        val clientIdToDelete = currentClientId ?: return

        lifecycleScope.launch {
            // Tworzymy "stub" klienta tylko z ID, bo reszta danych nie jest potrzebna do usunięcia
            val clientStub = com.kaminski.paragownik.data.Client(id = clientIdToDelete, description = null)
            val result = editReceiptViewModel.deleteClient(clientStub)
            handleEditResult(result, true)
        }
    }


    /**
     * Obsługuje wynik operacji edycji/usuwania zwrócony przez ViewModel.
     */
    private fun handleEditResult(result: EditReceiptViewModel.EditResult, isDeleteOperation: Boolean = false) {
        val messageResId = when (result) {
            EditReceiptViewModel.EditResult.SUCCESS -> if (isDeleteOperation) R.string.delete_success_message else R.string.save_success_message
            EditReceiptViewModel.EditResult.ERROR_NOT_FOUND -> R.string.error_not_found
            EditReceiptViewModel.EditResult.ERROR_DATE_FORMAT -> R.string.error_invalid_date_format
            EditReceiptViewModel.EditResult.ERROR_DUPLICATE_RECEIPT -> R.string.error_duplicate_receipt
            EditReceiptViewModel.EditResult.ERROR_STORE_NUMBER_MISSING -> R.string.error_store_number_missing
            EditReceiptViewModel.EditResult.ERROR_DATABASE -> R.string.error_database
            EditReceiptViewModel.EditResult.ERROR_UNKNOWN -> R.string.error_unknown
        }
        val message = getString(messageResId)

        Toast.makeText(this@EditReceiptActivity, message, if (result == EditReceiptViewModel.EditResult.SUCCESS) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()

        if (result == EditReceiptViewModel.EditResult.SUCCESS) {
            if (isDeleteOperation) {
                // Po udanym usunięciu wróć do MainActivity
                finishAffinity()
                startActivity(Intent(this@EditReceiptActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } else {
                // Po udanej EDYCJI:
                photosToAdd.clear() // Wyczyść listę dodanych
                photosToRemove.clear() // Wyczyść listę usuniętych
                loadReceiptData() // Załaduj dane ponownie, aby odświeżyć widok zdjęć
                updateUiMode(false) // Przełącz z powrotem do trybu widoku
            }
        }
    }


    /**
     * Konfiguruje działanie CheckBoxa "Dzisiaj" dla daty weryfikacji.
     */
    private fun setupVerificationDateCheckBox() {
        editVerificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isEditMode) { // Sprawdzaj tryb edycji
                if (isChecked) {
                    val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                    editVerificationDateEditText.setText(currentDate)
                    editVerificationDateEditText.isEnabled = false
                } else {
                    editVerificationDateEditText.isEnabled = true
                }
            }
        }
    }

    /**
     * Konfiguruje formatowanie daty w [EditText] (DD-MM-YYYY).
     */
    private fun setupDateEditText(editText: EditText) {
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        editText.addTextChangedListener(object : TextWatcher {
            private var current = ""
            private var isFormatting: Boolean = false
            private var cursorPosBefore: Int = 0
            private var textLengthBefore: Int = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isFormatting) return
                cursorPosBefore = editText.selectionStart
                textLengthBefore = s?.length ?: 0
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                val userInput = s.toString()
                if (userInput == current) {
                    isFormatting = false
                    return
                }

                val digitsOnly = userInput.replace("[^\\d]".toRegex(), "")
                val len = digitsOnly.length
                val formatted = StringBuilder()
                if (len >= 1) formatted.append(digitsOnly.substring(0, minOf(len, 2)))
                if (len >= 3) formatted.append("-").append(digitsOnly.substring(2, minOf(len, 4)))
                if (len >= 5) formatted.append("-").append(digitsOnly.substring(4, minOf(len, 8)))
                current = formatted.toString()

                editText.setText(current)

                try {
                    val lengthDiff = current.length - textLengthBefore
                    var newCursorPos = cursorPosBefore + lengthDiff
                    newCursorPos = maxOf(0, minOf(newCursorPos, current.length))
                    editText.setSelection(newCursorPos)
                } catch (e: Exception) {
                    try { editText.setSelection(current.length) } catch (e2: Exception) { /* Ignoruj */ }
                    Log.e("DateTextWatcher", "Błąd ustawiania kursora", e)
                }

                isFormatting = false
            }
        })
    }

    /**
     * Kopiuje obraz z podanego źródłowego URI do wewnętrznego magazynu aplikacji.
     */
    private fun copyImageToInternalStorage(sourceUri: Uri): Uri? {
        return try {
            val inputStream = contentResolver.openInputStream(sourceUri) ?: return null
            val imagesDir = File(filesDir, "images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            val uniqueFileName = "${UUID.randomUUID()}.jpg"
            val destinationFile = File(imagesDir, uniqueFileName)
            val outputStream = FileOutputStream(destinationFile)

            inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
            Log.d("EditReceiptActivity", "Skopiowano zdjęcie do: ${destinationFile.absolutePath}")
            destinationFile.toUri()

        } catch (e: IOException) {
            Log.e("EditReceiptActivity", "Błąd podczas kopiowania zdjęcia", e)
            Toast.makeText(this, R.string.error_copying_image, Toast.LENGTH_SHORT).show()
            null
        } catch (e: SecurityException) {
            Log.e("EditReceiptActivity", "Brak uprawnień do odczytu URI źródłowego (SecurityException)", e)
            Toast.makeText(this, R.string.error_permission_read_photo, Toast.LENGTH_SHORT).show()
            null
        }
    }
}