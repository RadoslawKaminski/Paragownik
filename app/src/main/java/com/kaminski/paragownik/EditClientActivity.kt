package com.kaminski.paragownik

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Import Glide
import com.kaminski.paragownik.adapter.GlideScaleType // Import enuma
import com.kaminski.paragownik.adapter.PhotoAdapter
import com.kaminski.paragownik.data.Photo
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.viewmodel.EditClientViewModel // <-- NOWY ViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Aktywność odpowiedzialna za przeglądanie i edycję danych istniejącego klienta,
 * w tym zarządzanie jego zdjęciami. Używa Glide do ładowania obrazów.
 */
class EditClientActivity : AppCompatActivity() {

    // --- Widoki UI ---
    private lateinit var titleTextView: TextView // TextView dla tytułu
    private lateinit var editClientDescriptionEditText: EditText
    private lateinit var editClientAppNumberEditText: EditText
    private lateinit var editAmoditNumberEditText: EditText
    private lateinit var saveClientButton: Button
    private lateinit var deleteClientButton: Button
    private lateinit var editModeClientButton: ImageButton // Ikona "Edytuj"
    // Layouty i etykiety do ukrywania/pokazywania
    private lateinit var editDescriptionLayout: LinearLayout
    private lateinit var editAppNumberLayout: LinearLayout
    private lateinit var editAmoditNumberLayout: LinearLayout
    private lateinit var clientDataSectionTitleEdit: TextView // Etykieta edycji

    // Widoki dla zdjęć
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
    private lateinit var clientPhotosAdapter: PhotoAdapter
    private lateinit var transactionPhotosAdapter: PhotoAdapter

    // --- ViewModel ---
    private lateinit var editClientViewModel: EditClientViewModel // <-- NOWY ViewModel

    // --- Dane pomocnicze ---
    private var currentClientId: Long = -1L // Zmieniono na non-nullable, bo jest wymagane
    private var loadDataJob: Job? = null
    private var isEditMode = false

    // Listy do zarządzania zdjęciami w UI
    private val currentClientPhotos = mutableListOf<Photo>()
    private val currentTransactionPhotos = mutableListOf<Photo>()
    private val photosToAdd = mutableMapOf<PhotoType, MutableList<Uri>>()
    private val photosToRemove = mutableListOf<Uri>()
    private val photoUriToViewMapEdit = mutableMapOf<Uri, View>()
    private var currentPhotoTypeToAdd: PhotoType? = null

    // Launcher do wybierania zdjęcia z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            Log.d("EditClientActivity", "Otrzymano tymczasowe URI: $sourceUri")
            val destinationUri = copyImageToInternalStorage(sourceUri)
            destinationUri?.let { finalUri ->
                val type = currentPhotoTypeToAdd
                if (type != null) {
                    val alreadyExists = photosToAdd[type]?.contains(finalUri) == true ||
                                        (if (type == PhotoType.CLIENT) currentClientPhotos else currentTransactionPhotos)
                                            .any { it.uri == finalUri.toString() }

                    if (!alreadyExists) {
                        photosToAdd.getOrPut(type) { mutableListOf() }.add(finalUri)
                        val container = if (type == PhotoType.CLIENT) clientPhotosContainerEdit else transactionPhotosContainerEdit
                        addPhotoThumbnail(finalUri, container, type, true)
                        Log.d("EditClientActivity", "Przygotowano do dodania zdjęcie ($type): $finalUri")
                        updatePhotoSectionVisibility(type, true)
                    } else {
                         Log.d("EditClientActivity", "Zdjęcie $finalUri już istnieje lub zostało dodane.")
                         Toast.makeText(this, R.string.photo_already_added, Toast.LENGTH_SHORT).show()
                    }
                } else {
                     Log.w("EditClientActivity", "Nieznany typ zdjęcia do dodania (currentPhotoTypeToAdd is null)")
                }
            }
        } ?: run {
            Log.d("EditClientActivity", "Nie wybrano nowego zdjęcia.")
        }
        currentPhotoTypeToAdd = null
    }

    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_client) // Ustawienie nowego layoutu

        initializeViews()
        initializeViewModel()

        // Odczytaj ID klienta z Intentu
        currentClientId = intent.getLongExtra("CLIENT_ID", -1L)
        Log.d("EditClientActivity", "Otrzymano clientId: $currentClientId")

        if (currentClientId == -1L) {
            Toast.makeText(this, R.string.error_invalid_client_id, Toast.LENGTH_LONG).show()
            Log.e("EditClientActivity", "Nieprawidłowe CLIENT_ID przekazane w Intencie.")
            finish()
            return
        }

        // Inicjalizacja adapterów i RecyclerView dla zdjęć w trybie widoku
        // Używamy GlideScaleType.FIT_CENTER dla dużych zdjęć
        clientPhotosAdapter = PhotoAdapter(emptyList(), R.layout.large_photo_item, R.id.largePhotoImageViewItem, GlideScaleType.FIT_CENTER) { uri -> /* TODO: Obsługa kliknięcia */ }
        clientPhotosRecyclerViewView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        clientPhotosRecyclerViewView.adapter = clientPhotosAdapter

        transactionPhotosAdapter = PhotoAdapter(emptyList(), R.layout.large_photo_item, R.id.largePhotoImageViewItem, GlideScaleType.FIT_CENTER) { uri -> /* TODO: Obsługa kliknięcia */ }
        transactionPhotosRecyclerViewView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        transactionPhotosRecyclerViewView.adapter = transactionPhotosAdapter

        loadClientData() // Załaduj dane klienta
        setupButtonClickListeners()
        updateUiMode(false) // Ustaw tryb widoku na starcie
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI.
     */
    private fun initializeViews() {
        // Tytuł
        titleTextView = findViewById(R.id.editClientTitleTextView) // Inicjalizacja TextView tytułu
        // Pola klienta
        editClientDescriptionEditText = findViewById(R.id.editClientDescriptionEditText)
        editClientAppNumberEditText = findViewById(R.id.editClientAppNumberEditText)
        editAmoditNumberEditText = findViewById(R.id.editAmoditNumberEditText)
        // Przyciski akcji
        saveClientButton = findViewById(R.id.saveClientButton)
        deleteClientButton = findViewById(R.id.deleteClientButton)
        editModeClientButton = findViewById(R.id.editModeClientButton) // Ikona Edytuj
        // Layouty i etykiety do ukrywania/pokazywania
        editDescriptionLayout = findViewById(R.id.editDescriptionLayout)
        editAppNumberLayout = findViewById(R.id.editAppNumberLayout)
        editAmoditNumberLayout = findViewById(R.id.editAmoditNumberLayout)
        clientDataSectionTitleEdit = findViewById(R.id.clientDataSectionTitleEdit)

        // Widoki zdjęć
        clientPhotosTitleEdit = findViewById(R.id.clientPhotosTitleEdit)
        clientPhotosTitleView = findViewById(R.id.clientPhotosTitleView)
        clientPhotosScrollViewEdit = findViewById(R.id.clientPhotosScrollViewEdit)
        clientPhotosContainerEdit = findViewById(R.id.clientPhotosContainerEdit)
        clientPhotosRecyclerViewView = findViewById(R.id.clientPhotosRecyclerViewView)
        addClientPhotoButtonEdit = findViewById(R.id.addClientPhotoButtonEdit)

        transactionPhotosTitleEdit = findViewById(R.id.transactionPhotosTitleEdit)
        transactionPhotosTitleView = findViewById(R.id.transactionPhotosTitleView)
        transactionPhotosScrollViewEdit = findViewById(R.id.transactionPhotosScrollViewEdit)
        transactionPhotosContainerEdit = findViewById(R.id.transactionPhotosContainerEdit)
        transactionPhotosRecyclerViewView = findViewById(R.id.transactionPhotosRecyclerViewView)
        addTransactionPhotoButtonEdit = findViewById(R.id.addTransactionPhotoButtonEdit)
    }

    /**
     * Inicjalizuje ViewModel.
     */
    private fun initializeViewModel() {
        editClientViewModel = ViewModelProvider(this).get(EditClientViewModel::class.java) // Użycie nowego ViewModelu
    }

    /**
     * Ustawia listenery kliknięć dla przycisków.
     */
    private fun setupButtonClickListeners() {
        saveClientButton.setOnClickListener { saveClientChanges() } // Wywołanie nowej metody zapisu
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
        editModeClientButton.setOnClickListener {
            updateUiMode(true) // Włącz tryb edycji
        }
    }

    /**
     * Wczytuje dane klienta i jego zdjęć z ViewModelu i wypełnia formularz.
     */
    private fun loadClientData() {
        loadDataJob?.cancel() // Anuluj poprzednie zadanie ładowania, jeśli istnieje
        loadDataJob = lifecycleScope.launch {
            // Obserwuj Flow z ViewModelu
            editClientViewModel.getClientWithPhotos(currentClientId)
                .collectLatest { pair -> // Odbieramy Pair<Client?, List<Photo>?>
                    if (!isActive) return@collectLatest // Sprawdź, czy korutyna jest nadal aktywna

                    val client = pair.first
                    val photos = pair.second

                    // Wyczyść kontenery i listy przed załadowaniem nowych danych
                    clearPhotoData()

                    if (client != null) {
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
                         // Obsługa sytuacji, gdy dane klienta nie zostały znalezione
                         if (isActive) {
                             Log.e("EditClientActivity", "Nie znaleziono danych dla clientId: $currentClientId (prawdopodobnie usunięto)")
                             Toast.makeText(this@EditClientActivity, R.string.error_client_not_found, Toast.LENGTH_SHORT).show()
                             finish() // Zamknij aktywność, jeśli klient nie istnieje
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
        clientPhotosAdapter.updatePhotos(emptyList())
        transactionPhotosAdapter.updatePhotos(emptyList())
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
            val editContainer = container ?: return
            editContainer.removeAllViews()
            val mapToUse = photoUriToViewMapEdit
            // Nie czyścimy mapy tutaj, bo addPhotoThumbnail dodaje do niej wpisy

            // Wyświetl istniejące zdjęcia (które nie są do usunięcia)
            photos.forEach { photo ->
                try {
                    if (!photosToRemove.contains(photo.uri.toUri())) {
                        addPhotoThumbnail(photo.uri.toUri(), editContainer, photoType, true)
                    }
                } catch (e: Exception) {
                    Log.e("EditClientActivity", "Błąd podczas dodawania miniatury dla URI: ${photo.uri}", e)
                }
            }
            // Wyświetl nowo dodane zdjęcia
            photosToAdd[photoType]?.forEach { uri ->
                if (!mapToUse.containsKey(uri)) { // Sprawdź, czy już nie dodano
                    try {
                        addPhotoThumbnail(uri, editContainer, photoType, true)
                    } catch (e: Exception) {
                        Log.e("EditClientActivity", "Błąd podczas dodawania NOWEJ miniatury dla URI: $uri", e)
                    }
                }
            }
        } else {
            // Logika dla trybu widoku (RecyclerView)
            val adapter = if (photoType == PhotoType.CLIENT) clientPhotosAdapter else transactionPhotosAdapter
            val recyclerView = if (photoType == PhotoType.CLIENT) clientPhotosRecyclerViewView else transactionPhotosRecyclerViewView

            val photosToShow = photos.filter { photo ->
                try {
                    !photosToRemove.contains(photo.uri.toUri())
                } catch (e: Exception) {
                    Log.w("EditClientActivity", "Błąd parsowania URI ${photo.uri} podczas filtrowania zdjęć do wyświetlenia.", e)
                    false
                }
            }
            adapter.updatePhotos(photosToShow)
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
                container.removeView(thumbnailView)
                val wasAddedInThisSession = photosToAdd[photoType]?.remove(photoUri) ?: false
                if (!wasAddedInThisSession) {
                    if (!photosToRemove.contains(photoUri)) {
                        photosToRemove.add(photoUri)
                        Log.d("EditClientActivity", "Oznaczono do usunięcia istniejące zdjęcie: $photoUri")
                    }
                } else {
                    Log.d("EditClientActivity", "Usunięto nowo dodane zdjęcie (przed zapisem): $photoUri")
                }
                photoUriToViewMapEdit.remove(photoUri)
                updatePhotoSectionVisibility(photoType, true)
            }
            photoUriToViewMapEdit[photoUri] = thumbnailView // Zapisz mapowanie URI na widok
            container.addView(thumbnailView)

        } catch (e: Exception) {
            Log.e("EditClientActivity", "Błąd ładowania miniatury $photoUri", e)
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
        val recyclerViewView = if (photoType == PhotoType.CLIENT) clientPhotosRecyclerViewView else transactionPhotosRecyclerViewView
        val addButton = if (photoType == PhotoType.CLIENT) addClientPhotoButtonEdit else addTransactionPhotoButtonEdit

        // Widoczność w trybie edycji
        titleEdit.visibility = if (isEditing) View.VISIBLE else View.GONE
        scrollViewEdit.visibility = if (isEditing && photosExist) View.VISIBLE else View.GONE
        addButton.visibility = if (isEditing) View.VISIBLE else View.GONE

        // Widoczność w trybie widoku
        titleView.visibility = if (!isEditing && photosExist) View.VISIBLE else View.GONE
        recyclerViewView.visibility = if (!isEditing && photosExist) View.VISIBLE else View.GONE
    }

    /**
     * Aktualizuje widoczność i stan edytowalności elementów UI w zależności od trybu.
     */
    private fun updateUiMode(isEditing: Boolean) {
        isEditMode = isEditing

        // Ustaw tytuł aktywności
        titleTextView.text = getString(if (isEditing) R.string.edit_client_title else R.string.view_client_title)

        // Włącz/Wyłącz EditTexty
        editClientDescriptionEditText.isEnabled = isEditing
        editClientAppNumberEditText.isEnabled = isEditing
        editAmoditNumberEditText.isEnabled = isEditing

        // Pokaż/Ukryj przyciski akcji i ikonę edycji
        saveClientButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteClientButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        editModeClientButton.visibility = if (isEditing) View.GONE else View.VISIBLE // Ikona edycji

        // Pokaż/Ukryj opcjonalne sekcje i przełącz etykiety
        val hasDescription = !editClientDescriptionEditText.text.isNullOrBlank()
        editDescriptionLayout.visibility = if (isEditing || hasDescription) View.VISIBLE else View.GONE

        val hasAppNumber = !editClientAppNumberEditText.text.isNullOrBlank()
        editAppNumberLayout.visibility = if (isEditing || hasAppNumber) View.VISIBLE else View.GONE

        val hasAmoditNumber = !editAmoditNumberEditText.text.isNullOrBlank()
        editAmoditNumberLayout.visibility = if (isEditing || hasAmoditNumber) View.VISIBLE else View.GONE

        // Przełącz etykietę sekcji danych klienta (tylko w trybie edycji)
        clientDataSectionTitleEdit.visibility = if (isEditing) View.VISIBLE else View.GONE

        // Zaktualizuj widoczność sekcji zdjęć
        updatePhotoSectionVisibility(PhotoType.CLIENT, isEditing)
        updatePhotoSectionVisibility(PhotoType.TRANSACTION, isEditing)

        // Odśwież kontenery zdjęć, aby pokazać/ukryć przyciski usuwania i zaktualizować adaptery
        populatePhotoContainer(clientPhotosContainerEdit, currentClientPhotos, PhotoType.CLIENT, isEditing)
        populatePhotoContainer(null, currentClientPhotos, PhotoType.CLIENT, isEditing) // Dla RecyclerView
        populatePhotoContainer(transactionPhotosContainerEdit, currentTransactionPhotos, PhotoType.TRANSACTION, isEditing)
        populatePhotoContainer(null, currentTransactionPhotos, PhotoType.TRANSACTION, isEditing) // Dla RecyclerView
    }

    /**
     * Zbiera dane z formularza i zapisuje zmiany w ViewModelu.
     */
    private fun saveClientChanges() {
        val clientDescription = editClientDescriptionEditText.text.toString().trim()
        val clientAppNumber = editClientAppNumberEditText.text.toString().trim()
        val amoditNumber = editAmoditNumberEditText.text.toString().trim()

        lifecycleScope.launch {
            // Wywołanie metody ViewModelu do aktualizacji klienta i zdjęć
            val result = editClientViewModel.updateClientAndPhotos(
                clientId = currentClientId,
                clientDescription = clientDescription.takeIf { it.isNotEmpty() },
                clientAppNumber = clientAppNumber.takeIf { it.isNotEmpty() },
                amoditNumber = amoditNumber.takeIf { it.isNotEmpty() },
                clientPhotoUrisToAdd = photosToAdd[PhotoType.CLIENT]?.map { it.toString() } ?: emptyList(),
                transactionPhotoUrisToAdd = photosToAdd[PhotoType.TRANSACTION]?.map { it.toString() } ?: emptyList(),
                photoUrisToRemove = photosToRemove.map { it.toString() }
            )
            handleEditResult(result) // Obsługa wyniku
        }
    }

    /**
     * Wyświetla dialog potwierdzenia usunięcia klienta.
     */
    private fun showDeleteClientDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_client_confirmation_title_short) // Użyjmy dedykowanego tytułu
            .setMessage(R.string.delete_client_confirmation_message_short) // Użyjmy dedykowanej wiadomości
            .setPositiveButton(R.string.delete) { _, _ -> deleteClient() }
            .setNegativeButton(R.string.cancel, null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    /**
     * Usuwa bieżącego klienta i jego paragony, anulując najpierw obserwację danych.
     */
    private fun deleteClient() {
        loadDataJob?.cancel() // Anuluj obserwację danych
        Log.d("EditClientActivity", "Anulowano loadDataJob przed usunięciem klienta.")

        lifecycleScope.launch {
            // Tworzymy "stub" klienta tylko z ID, bo reszta danych nie jest potrzebna do usunięcia
            val clientStub = com.kaminski.paragownik.data.Client(id = currentClientId, description = null)
            // Wywołanie metody ViewModelu do usunięcia klienta
            val result = editClientViewModel.deleteClient(clientStub)
            handleEditResult(result, true) // Obsługa wyniku (z flagą isDeleteOperation = true)
        }
    }

    /**
     * Obsługuje wynik operacji edycji/usuwania zwrócony przez ViewModel.
     */
    private fun handleEditResult(result: EditClientViewModel.EditResult, isDeleteOperation: Boolean = false) {
        val messageResId = when (result) {
            EditClientViewModel.EditResult.SUCCESS -> if (isDeleteOperation) R.string.delete_success_message else R.string.save_success_message
            EditClientViewModel.EditResult.ERROR_NOT_FOUND -> R.string.error_client_not_found // Zmieniono na błąd klienta
            EditClientViewModel.EditResult.ERROR_DATABASE -> R.string.error_database
            EditClientViewModel.EditResult.ERROR_UNKNOWN -> R.string.error_unknown
            // Dodaj inne przypadki, jeśli EditClientViewModel.EditResult będzie miał więcej stanów
        }
        val message = getString(messageResId)

        Toast.makeText(this@EditClientActivity, message, if (result == EditClientViewModel.EditResult.SUCCESS) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()

        if (result == EditClientViewModel.EditResult.SUCCESS) {
            if (isDeleteOperation) {
                // Po udanym USUNIĘCIU wróć do MainActivity
                finishAffinity() // Zamknij wszystkie aktywności z tej samej grupy
                startActivity(Intent(this@EditClientActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Uruchom MainActivity jako nową
                })
            } else {
                // Po udanej EDYCJI:
                photosToAdd.clear() // Wyczyść listę dodanych
                photosToRemove.clear() // Wyczyść listę usuniętych
                loadClientData() // Załaduj dane ponownie, aby odświeżyć widok zdjęć
                updateUiMode(false) // Przełącz z powrotem do trybu widoku
            }
        }
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
            Log.d("EditClientActivity", "Skopiowano zdjęcie do: ${destinationFile.absolutePath}")
            destinationFile.toUri()

        } catch (e: IOException) {
            Log.e("EditClientActivity", "Błąd podczas kopiowania zdjęcia", e)
            Toast.makeText(this, R.string.error_copying_image, Toast.LENGTH_SHORT).show()
            null
        } catch (e: SecurityException) {
            Log.e("EditClientActivity", "Brak uprawnień do odczytu URI źródłowego (SecurityException)", e)
            Toast.makeText(this, R.string.error_permission_read_photo, Toast.LENGTH_SHORT).show()
            null
        }
    }
}



