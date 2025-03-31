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
import com.bumptech.glide.Glide
import com.kaminski.paragownik.adapter.GlideScaleType
import com.kaminski.paragownik.adapter.PhotoAdapter
import com.kaminski.paragownik.data.Photo
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.viewmodel.EditClientViewModel
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
 * Umożliwia otwarcie zdjęcia na pełnym ekranie.
 */
class EditClientActivity : AppCompatActivity() {

    // --- Widoki UI ---
    private lateinit var titleTextView: TextView
    private lateinit var editClientDescriptionEditText: EditText
    private lateinit var editClientAppNumberEditText: EditText
    private lateinit var editAmoditNumberEditText: EditText
    private lateinit var saveClientButton: Button
    private lateinit var deleteClientButton: Button
    private lateinit var editModeClientButton: ImageButton
    private lateinit var editDescriptionLayout: LinearLayout
    private lateinit var editAppNumberLayout: LinearLayout
    private lateinit var editAmoditNumberLayout: LinearLayout
    private lateinit var clientDataSectionTitleEdit: TextView

    private lateinit var clientPhotosTitleEdit: TextView
    private lateinit var clientPhotosTitleView: TextView
    private lateinit var clientPhotosScrollViewEdit: HorizontalScrollView
    private lateinit var clientPhotosContainerEdit: LinearLayout
    private lateinit var clientPhotosRecyclerViewView: RecyclerView
    private lateinit var addClientPhotoButtonEdit: Button

    private lateinit var transactionPhotosTitleEdit: TextView
    private lateinit var transactionPhotosTitleView: TextView
    private lateinit var transactionPhotosScrollViewEdit: HorizontalScrollView
    private lateinit var transactionPhotosContainerEdit: LinearLayout
    private lateinit var transactionPhotosRecyclerViewView: RecyclerView
    private lateinit var addTransactionPhotoButtonEdit: Button

    // --- Adaptery ---
    private lateinit var clientPhotosAdapter: PhotoAdapter
    private lateinit var transactionPhotosAdapter: PhotoAdapter

    // --- ViewModel ---
    private lateinit var editClientViewModel: EditClientViewModel

    // --- Dane pomocnicze ---
    private var currentClientId: Long = -1L
    private var loadDataJob: Job? = null
    private var isEditMode = false

    private val currentClientPhotos = mutableListOf<Photo>()
    private val currentTransactionPhotos = mutableListOf<Photo>()
    private val photosToAdd = mutableMapOf<PhotoType, MutableList<Uri>>()
    private val photosToRemove = mutableListOf<Uri>()
    private val photoUriToViewMapEdit = mutableMapOf<Uri, View>()
    private var currentPhotoTypeToAdd: PhotoType? = null

    // Launcher ActivityResult do wybierania obrazu z galerii
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
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_client)

        initializeViews()
        initializeViewModel()

        currentClientId = intent.getLongExtra("CLIENT_ID", -1L)
        Log.d("EditClientActivity", "Otrzymano clientId: $currentClientId")
        if (currentClientId == -1L) {
            Toast.makeText(this, R.string.error_invalid_client_id, Toast.LENGTH_LONG).show()
            Log.e("EditClientActivity", "Nieprawidłowe CLIENT_ID przekazane w Intencie.")
            finish()
            return
        }

        clientPhotosAdapter = PhotoAdapter(
            emptyList(),
            R.layout.large_photo_item,
            R.id.largePhotoImageViewItem,
            GlideScaleType.FIT_CENTER
        ) { uri -> openFullScreenImage(uri) }
        clientPhotosRecyclerViewView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        clientPhotosRecyclerViewView.adapter = clientPhotosAdapter

        transactionPhotosAdapter = PhotoAdapter(
            emptyList(),
            R.layout.large_photo_item,
            R.id.largePhotoImageViewItem,
            GlideScaleType.FIT_CENTER
        ) { uri -> openFullScreenImage(uri) }
        transactionPhotosRecyclerViewView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        transactionPhotosRecyclerViewView.adapter = transactionPhotosAdapter

        loadClientData()
        setupButtonClickListeners()
        updateUiMode(false)
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI z layoutu.
     */
    private fun initializeViews() {
        titleTextView = findViewById(R.id.editClientTitleTextView)
        editClientDescriptionEditText = findViewById(R.id.editClientDescriptionEditText)
        editClientAppNumberEditText = findViewById(R.id.editClientAppNumberEditText)
        editAmoditNumberEditText = findViewById(R.id.editAmoditNumberEditText)
        saveClientButton = findViewById(R.id.saveClientButton)
        deleteClientButton = findViewById(R.id.deleteClientButton)
        editModeClientButton = findViewById(R.id.editModeClientButton)
        editDescriptionLayout = findViewById(R.id.editDescriptionLayout)
        editAppNumberLayout = findViewById(R.id.editAppNumberLayout)
        editAmoditNumberLayout = findViewById(R.id.editAmoditNumberLayout)
        clientDataSectionTitleEdit = findViewById(R.id.clientDataSectionTitleEdit)

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
        editClientViewModel = ViewModelProvider(this).get(EditClientViewModel::class.java)
    }

    /**
     * Ustawia listenery kliknięć dla przycisków (Zapisz, Usuń, Dodaj zdjęcie, Edytuj).
     */
    private fun setupButtonClickListeners() {
        saveClientButton.setOnClickListener { saveClientChanges() }
        deleteClientButton.setOnClickListener { showDeleteClientDialog() }

        addClientPhotoButtonEdit.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.CLIENT
            pickImageLauncher.launch("image/*")
        }

        addTransactionPhotoButtonEdit.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.TRANSACTION
            pickImageLauncher.launch("image/*")
        }

        editModeClientButton.setOnClickListener {
            updateUiMode(true)
        }
    }

    /**
     * Wczytuje dane klienta i jego zdjęć z ViewModelu i wypełnia formularz oraz kontenery zdjęć.
     */
    private fun loadClientData() {
        loadDataJob?.cancel()
        loadDataJob = lifecycleScope.launch {
            editClientViewModel.getClientWithPhotos(currentClientId)
                .collectLatest { pair ->
                    if (!isActive) return@collectLatest

                    val client = pair.first
                    val photos = pair.second

                    clearPhotoData()

                    if (client != null) {
                        editClientDescriptionEditText.setText(client.description ?: "")
                        editClientAppNumberEditText.setText(client.clientAppNumber ?: "")
                        editAmoditNumberEditText.setText(client.amoditNumber ?: "")

                        photos?.let { photoList ->
                            currentClientPhotos.addAll(photoList.filter { it.type == PhotoType.CLIENT })
                            currentTransactionPhotos.addAll(photoList.filter { it.type == PhotoType.TRANSACTION })

                            populatePhotoContainer(clientPhotosContainerEdit, currentClientPhotos, PhotoType.CLIENT, true)
                            populatePhotoContainer(null, currentClientPhotos, PhotoType.CLIENT, false)
                            populatePhotoContainer(transactionPhotosContainerEdit, currentTransactionPhotos, PhotoType.TRANSACTION, true)
                            populatePhotoContainer(null, currentTransactionPhotos, PhotoType.TRANSACTION, false)
                        }

                        updateUiMode(isEditMode)

                    } else {
                         if (isActive) {
                             Log.e("EditClientActivity", "Nie znaleziono danych dla clientId: $currentClientId (prawdopodobnie usunięto)")
                             Toast.makeText(this@EditClientActivity, R.string.error_client_not_found, Toast.LENGTH_SHORT).show()
                             finish()
                         }
                         clearPhotoData()
                         updateUiMode(isEditMode)
                    }
                }
        }
    }

    /** Wyczyść kontenery zdjęć (edycja i widok) oraz listy pomocnicze. */
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
            val editContainer = container ?: return
            editContainer.removeAllViews()
            val mapToUse = photoUriToViewMapEdit

            photos.forEach { photo ->
                try {
                    if (!photosToRemove.contains(photo.uri.toUri())) {
                        addPhotoThumbnail(photo.uri.toUri(), editContainer, photoType, true)
                    }
                } catch (e: Exception) {
                    Log.e("EditClientActivity", "Błąd podczas dodawania miniatury dla URI: ${photo.uri}", e)
                 }
            }
            photosToAdd[photoType]?.forEach { uri ->
                if (!mapToUse.containsKey(uri)) {
                    try {
                        addPhotoThumbnail(uri, editContainer, photoType, true)
                    } catch (e: Exception) {
                        Log.e("EditClientActivity", "Błąd podczas dodawania NOWEJ miniatury dla URI: $uri", e)
                    }
                }
            }
        } else {
            val adapter = if (photoType == PhotoType.CLIENT) clientPhotosAdapter else transactionPhotosAdapter
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
        updatePhotoSectionVisibility(photoType, isEditing)
    }

    /**
     * Dodaje widok miniatury zdjęcia do określonego kontenera [LinearLayout] (tylko dla trybu edycji).
     * Używa Glide do ładowania obrazu i dodaje przycisk usuwania.
     * @param photoUri URI zdjęcia do wyświetlenia.
     * @param container LinearLayout, do którego zostanie dodana miniatura.
     * @param photoType Typ zdjęcia.
     * @param isEditing Zawsze true w tej wersji metody.
     */
    private fun addPhotoThumbnail(photoUri: Uri, container: LinearLayout, photoType: PhotoType, isEditing: Boolean) {
        if (!isEditing) return

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

            deleteButton.visibility = View.VISIBLE

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
            photoUriToViewMapEdit[photoUri] = thumbnailView
            container.addView(thumbnailView)

        } catch (e: Exception) {
            Log.e("EditClientActivity", "Błąd ładowania miniatury $photoUri", e)
        }
    }

    /** Aktualizuje widoczność kontenerów i tytułów sekcji zdjęć w zależności od trybu i obecności zdjęć. */
    private fun updatePhotoSectionVisibility(photoType: PhotoType, isEditing: Boolean) {
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

        titleEdit.visibility = if (isEditing) View.VISIBLE else View.GONE
        scrollViewEdit.visibility = if (isEditing && photosExist) View.VISIBLE else View.GONE
        addButton.visibility = if (isEditing) View.VISIBLE else View.GONE

        titleView.visibility = if (!isEditing && photosExist) View.VISIBLE else View.GONE
        recyclerViewView.visibility = if (!isEditing && photosExist) View.VISIBLE else View.GONE
    }

    /**
     * Aktualizuje widoczność i stan edytowalności elementów UI w zależności od trybu (edycja/widok).
     */
    private fun updateUiMode(isEditing: Boolean) {
        isEditMode = isEditing

        titleTextView.text = getString(if (isEditing) R.string.edit_client_title else R.string.view_client_title)

        editClientDescriptionEditText.isEnabled = isEditing
        editClientAppNumberEditText.isEnabled = isEditing
        editAmoditNumberEditText.isEnabled = isEditing

        saveClientButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteClientButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        editModeClientButton.visibility = if (isEditing) View.GONE else View.VISIBLE

        val hasDescription = !editClientDescriptionEditText.text.isNullOrBlank()
        editDescriptionLayout.visibility = if (isEditing || hasDescription) View.VISIBLE else View.GONE

        val hasAppNumber = !editClientAppNumberEditText.text.isNullOrBlank()
        editAppNumberLayout.visibility = if (isEditing || hasAppNumber) View.VISIBLE else View.GONE

        val hasAmoditNumber = !editAmoditNumberEditText.text.isNullOrBlank()
        editAmoditNumberLayout.visibility = if (isEditing || hasAmoditNumber) View.VISIBLE else View.GONE

        clientDataSectionTitleEdit.visibility = if (isEditing) View.VISIBLE else View.GONE

        updatePhotoSectionVisibility(PhotoType.CLIENT, isEditing)
        updatePhotoSectionVisibility(PhotoType.TRANSACTION, isEditing)

        populatePhotoContainer(clientPhotosContainerEdit, currentClientPhotos, PhotoType.CLIENT, isEditing)
        populatePhotoContainer(null, currentClientPhotos, PhotoType.CLIENT, isEditing)
        populatePhotoContainer(transactionPhotosContainerEdit, currentTransactionPhotos, PhotoType.TRANSACTION, isEditing)
        populatePhotoContainer(null, currentTransactionPhotos, PhotoType.TRANSACTION, isEditing)
    }

    /**
     * Zbiera dane z formularza i wywołuje metodę zapisu zmian w ViewModelu.
     */
    private fun saveClientChanges() {
        val clientDescription = editClientDescriptionEditText.text.toString().trim()
        val clientAppNumber = editClientAppNumberEditText.text.toString().trim()
        val amoditNumber = editAmoditNumberEditText.text.toString().trim()

        lifecycleScope.launch {
            val result = editClientViewModel.updateClientAndPhotos(
                clientId = currentClientId,
                clientDescription = clientDescription.takeIf { it.isNotEmpty() },
                clientAppNumber = clientAppNumber.takeIf { it.isNotEmpty() },
                amoditNumber = amoditNumber.takeIf { it.isNotEmpty() },
                clientPhotoUrisToAdd = photosToAdd[PhotoType.CLIENT]?.map { it.toString() } ?: emptyList(),
                transactionPhotoUrisToAdd = photosToAdd[PhotoType.TRANSACTION]?.map { it.toString() } ?: emptyList(),
                photoUrisToRemove = photosToRemove.map { it.toString() }
            )
            handleEditResult(result)
        }
    }

    /**
     * Wyświetla dialog potwierdzenia usunięcia klienta.
     */
    private fun showDeleteClientDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_client_confirmation_title_short)
            .setMessage(R.string.delete_client_confirmation_message_short)
            .setPositiveButton(R.string.delete) { _, _ -> deleteClient() }
            .setNegativeButton(R.string.cancel, null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    /**
     * Wywołuje metodę usuwania klienta w ViewModelu. Anuluje najpierw obserwację danych.
     */
    private fun deleteClient() {
        loadDataJob?.cancel()
        Log.d("EditClientActivity", "Anulowano loadDataJob przed usunięciem klienta.")

        lifecycleScope.launch {
            val clientStub = com.kaminski.paragownik.data.Client(id = currentClientId, description = null)
            val result = editClientViewModel.deleteClient(clientStub)
            handleEditResult(result, true)
        }
    }

    /**
     * Obsługuje wynik operacji edycji/usuwania zwrócony przez ViewModel.
     * Wyświetla Toast i odpowiednio zarządza stanem aktywności.
     */
    private fun handleEditResult(result: EditClientViewModel.EditResult, isDeleteOperation: Boolean = false) {
        val messageResId = when (result) {
            EditClientViewModel.EditResult.SUCCESS -> if (isDeleteOperation) R.string.delete_success_message else R.string.save_success_message
            EditClientViewModel.EditResult.ERROR_NOT_FOUND -> R.string.error_client_not_found
            EditClientViewModel.EditResult.ERROR_DATABASE -> R.string.error_database
            EditClientViewModel.EditResult.ERROR_UNKNOWN -> R.string.error_unknown
        }
        val message = getString(messageResId)

        Toast.makeText(this@EditClientActivity, message, if (result == EditClientViewModel.EditResult.SUCCESS) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()

        if (result == EditClientViewModel.EditResult.SUCCESS) {
            if (isDeleteOperation) {
                finishAffinity()
                startActivity(Intent(this@EditClientActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } else {
                photosToAdd.clear()
                photosToRemove.clear()
                loadClientData()
                updateUiMode(false)
            }
        }
    }

    /**
     * Kopiuje obraz z podanego źródłowego URI (np. z galerii) do wewnętrznego magazynu aplikacji.
     * Zwraca URI skopiowanego pliku lub null w przypadku błędu.
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

    /**
     * Otwiera aktywność [FullScreenImageActivity] dla podanego URI obrazu.
     */
    private fun openFullScreenImage(imageUri: Uri) {
        val intent = Intent(this, FullScreenImageActivity::class.java)
        intent.putExtra("IMAGE_URI", imageUri.toString())
        startActivity(intent)
    }
}







