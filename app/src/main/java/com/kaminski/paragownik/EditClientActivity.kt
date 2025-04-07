
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
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import com.kaminski.paragownik.data.Client

/**
 * Aktywność odpowiedzialna za przeglądanie i edycję danych istniejącego klienta,
 * w tym zarządzanie jego zdjęciami. Używa ViewModelu z MutableLiveData.
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
    private lateinit var clientDataSectionTitleView: TextView


    // --- Adaptery ---
    private lateinit var clientPhotosAdapter: PhotoAdapter
    private lateinit var transactionPhotosAdapter: PhotoAdapter

    // --- ViewModel ---
    private lateinit var editClientViewModel: EditClientViewModel

    // --- Dane pomocnicze ---
    private var currentClientId: Long = -1L
    private val photoUriToViewMapEdit = mutableMapOf<Uri, View>()
    private var currentPhotoTypeToAdd: PhotoType? = null
    private var isViewInitialized = false
    private var loadedClientPhotos: List<Photo> = emptyList()
    private var loadedTransactionPhotos: List<Photo> = emptyList()

    // Launcher ActivityResult do wybierania obrazu z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            Log.d("EditClientActivity", "Otrzymano tymczasowe URI: $sourceUri")
            val destinationUri = copyImageToInternalStorage(sourceUri)
            destinationUri?.let { finalUri ->
                val type = currentPhotoTypeToAdd
                if (type != null) {
                    val isAlreadyLoaded = (if (type == PhotoType.CLIENT) loadedClientPhotos else loadedTransactionPhotos)
                        .any { it.uri == finalUri.toString() }
                    val isAlreadyAdded = (if (type == PhotoType.CLIENT) editClientViewModel.clientPhotosToAddUris.value else editClientViewModel.transactionPhotosToAddUris.value)
                        ?.contains(finalUri) ?: false
                    val isMarkedForRemoval = editClientViewModel.photosToRemoveUris.value?.contains(finalUri) ?: false

                    if (!isAlreadyLoaded && !isAlreadyAdded) {
                        if (isMarkedForRemoval) {
                            editClientViewModel.removePhotoToRemove(finalUri)
                        }
                        editClientViewModel.addPhotoToAdd(finalUri, type)
                        Log.d("EditClientActivity", "Przygotowano do dodania zdjęcie ($type): $finalUri")
                    } else if (isAlreadyLoaded && isMarkedForRemoval) {
                        editClientViewModel.removePhotoToRemove(finalUri)
                        Log.d("EditClientActivity", "Odznaczono zdjęcie ($type) do usunięcia: $finalUri")
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
        editClientViewModel = ViewModelProvider(this).get(EditClientViewModel::class.java)

        currentClientId = intent.getLongExtra("CLIENT_ID", -1L)
        Log.d("EditClientActivity", "Otrzymano clientId: $currentClientId")
        if (currentClientId == -1L) {
            Toast.makeText(this, R.string.error_invalid_client_id, Toast.LENGTH_LONG).show()
            Log.e("EditClientActivity", "Nieprawidłowe CLIENT_ID przekazane w Intencie.")
            finish()
            return
        }

        setupAdapters()
        setupFieldListeners()
        setupButtonClickListeners()
        observeViewModel()
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
        clientDataSectionTitleView = findViewById(R.id.clientDataSectionTitleView)

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
     * Inicjalizuje adaptery RecyclerView i ustawia listenery kliknięć zdjęć.
     * Listener przekazuje teraz listę URI i pozycję do FullScreenImageActivity.
     */
    private fun setupAdapters() {
        // Adapter dla zdjęć klienta
        clientPhotosAdapter = PhotoAdapter(
            emptyList(),
            R.layout.large_photo_item,
            R.id.largePhotoImageViewItem,
            GlideScaleType.FIT_CENTER
        ) { clickedUri ->
            val currentPhotoList = clientPhotosAdapter.getCurrentPhotos()
            val allUris = currentPhotoList.map { it.uri }
            val clickedIndex = allUris.indexOf(clickedUri.toString())

            if (clickedIndex != -1) {
                openFullScreenImage(ArrayList(allUris), clickedIndex)
            } else {
                Log.e("EditClientActivity", "Nie znaleziono klikniętego URI ($clickedUri) w liście zdjęć klienta.")
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show()
            }
        }
        clientPhotosRecyclerViewView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        clientPhotosRecyclerViewView.adapter = clientPhotosAdapter

        // Adapter dla zdjęć transakcji
        transactionPhotosAdapter = PhotoAdapter(
            emptyList(),
            R.layout.large_photo_item,
            R.id.largePhotoImageViewItem,
            GlideScaleType.FIT_CENTER
        ) { clickedUri ->
            val currentPhotoList = transactionPhotosAdapter.getCurrentPhotos()
            val allUris = currentPhotoList.map { it.uri }
            val clickedIndex = allUris.indexOf(clickedUri.toString())

            if (clickedIndex != -1) {
                openFullScreenImage(ArrayList(allUris), clickedIndex)
            } else {
                Log.e("EditClientActivity", "Nie znaleziono klikniętego URI ($clickedUri) w liście zdjęć transakcji.")
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show()
            }
        }
        transactionPhotosRecyclerViewView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        transactionPhotosRecyclerViewView.adapter = transactionPhotosAdapter
    }

    /** Ustawia listenery dla pól edycyjnych, które aktualizują stan w ViewModelu. */
    private fun setupFieldListeners() {
        editClientDescriptionEditText.addTextChangedListener { if (editClientViewModel.isEditMode.value == true) editClientViewModel.setClientDescription(it.toString()) }
        editClientAppNumberEditText.addTextChangedListener { if (editClientViewModel.isEditMode.value == true) editClientViewModel.setClientAppNumber(it.toString()) }
        editAmoditNumberEditText.addTextChangedListener { if (editClientViewModel.isEditMode.value == true) editClientViewModel.setAmoditNumber(it.toString()) }
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
            editClientViewModel.setEditMode(true)
        }
    }

    /** Obserwuje zmiany w ViewModelu. */
    private fun observeViewModel() {
        // Obserwacja danych klienta i zdjęć z bazy (StateFlow)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                editClientViewModel.getClientWithPhotosFlow(currentClientId).collectLatest { pair ->
                    val client = pair.first
                    val photos = pair.second

                    if (client == null) {
                        // ZMIANA: Logujemy błąd, ale nie zamykamy aktywności od razu
                        if (isViewInitialized) {
                            Log.e("EditClientActivity", "Dane klienta (ID: $currentClientId) stały się null po inicjalizacji. Aktywność może zostać zamknięta, jeśli stan się nie poprawi.")
                            // Usunięto Toast i finish() - pozwalamy na ewentualne odświeżenie danych
                        } else {
                            Log.d("EditClientActivity", "Otrzymano null klienta (przed inicjalizacją), ignorowanie.")
                        }
                        // Nie przerywamy collectLatest, aby dać szansę na otrzymanie danych
                        // return@collectLatest // Usunięto return
                    } else {
                        // Mamy poprawne dane klienta
                        Log.d("EditClientActivity", "Otrzymano aktualne dane klienta z Flow.")
                        loadedClientPhotos = photos?.filter { it.type == PhotoType.CLIENT } ?: emptyList()
                        loadedTransactionPhotos = photos?.filter { it.type == PhotoType.TRANSACTION } ?: emptyList()

                        // Inicjalizuj stan MutableLiveData w ViewModelu tylko raz
                        if (!isViewInitialized) {
                            editClientViewModel.initializeStateIfNeeded(client)
                            isViewInitialized = true
                            updateUiMode(editClientViewModel.isEditMode.value ?: false)
                            Log.d("EditClientActivity", "Wymuszono aktualizację UI po inicjalizacji danych.")
                        }

                        // Aktualizuj UI zdjęć (reszta UI aktualizuje się przez obserwatory LiveData)
                        updatePhotoUiIfNeeded(PhotoType.CLIENT)
                        updatePhotoUiIfNeeded(PhotoType.TRANSACTION)
                    }
                }
            }
        }

        // Obserwacja LiveData stanu UI z ViewModelu
        editClientViewModel.isEditMode.observe(this, Observer { isEditing ->
            updateUiMode(isEditing)
        })

        // Obserwatory dla pól tekstowych
        editClientViewModel.clientDescriptionState.observe(this, Observer { value -> if (editClientDescriptionEditText.text.toString() != value) editClientDescriptionEditText.setText(value) })
        editClientViewModel.clientAppNumberState.observe(this, Observer { value -> if (editClientAppNumberEditText.text.toString() != value) editClientAppNumberEditText.setText(value) })
        editClientViewModel.amoditNumberState.observe(this, Observer { value -> if (editAmoditNumberEditText.text.toString() != value) editAmoditNumberEditText.setText(value) })

        // Obserwatory dla list zdjęć
        editClientViewModel.clientPhotosToAddUris.observe(this, Observer { updatePhotoUiIfNeeded(PhotoType.CLIENT) })
        editClientViewModel.transactionPhotosToAddUris.observe(this, Observer { updatePhotoUiIfNeeded(PhotoType.TRANSACTION) })
        editClientViewModel.photosToRemoveUris.observe(this, Observer {
            updatePhotoUiIfNeeded(PhotoType.CLIENT)
            updatePhotoUiIfNeeded(PhotoType.TRANSACTION)
        })
    }

    /** Pomocnicza funkcja do aktualizacji UI zdjęć po zmianie list w ViewModelu. */
    private fun updatePhotoUiIfNeeded(photoType: PhotoType) {
        val isEditing = editClientViewModel.isEditMode.value ?: false
        val loadedPhotos = if (photoType == PhotoType.CLIENT) loadedClientPhotos else loadedTransactionPhotos
        val container = if (photoType == PhotoType.CLIENT) clientPhotosContainerEdit else transactionPhotosContainerEdit

        if (::clientPhotosContainerEdit.isInitialized && ::transactionPhotosContainerEdit.isInitialized &&
            ::clientPhotosRecyclerViewView.isInitialized && ::transactionPhotosRecyclerViewView.isInitialized) {
            populatePhotoContainer(container, loadedPhotos, photoType, isEditing)
            populatePhotoContainer(null, loadedPhotos, photoType, isEditing) // Dla RecyclerView
        } else {
            Log.w("EditClientActivity", "Próba aktualizacji UI zdjęć przed inicjalizacją widoków.")
        }
    }

    /**
     * Wypełnia kontener miniaturami zdjęć (tryb edycji) lub RecyclerView (tryb widoku).
     * Odczytuje stan zdjęć do dodania/usunięcia z ViewModelu.
     */
    private fun populatePhotoContainer(container: LinearLayout?, photos: List<Photo>, photoType: PhotoType, isEditing: Boolean) {
        val photosToAddUris = (if (photoType == PhotoType.CLIENT) editClientViewModel.clientPhotosToAddUris.value else editClientViewModel.transactionPhotosToAddUris.value) ?: emptyList()
        val photosToRemoveUris = editClientViewModel.photosToRemoveUris.value ?: emptyList()
        val photosToAddUrisSet = photosToAddUris.map { it.toString() }.toSet()
        val photosToRemoveUrisSet = photosToRemoveUris.map { it.toString() }.toSet()

        if (isEditing) {
            val editContainer = container ?: return
            editContainer.removeAllViews()
            photoUriToViewMapEdit.clear()

            photos.forEach { photo ->
                try {
                    val photoUri = photo.uri.toUri()
                    if (photoUri.toString() !in photosToRemoveUrisSet) {
                        addPhotoThumbnail(photoUri, editContainer, photoType, true)
                    }
                } catch (e: Exception) {
                    Log.e("EditClientActivity", "Błąd dodawania miniatury istniejącego zdjęcia: ${photo.uri}", e)
                }
            }
            photosToAddUris.forEach { photoUri ->
                try {
                    if (!photoUriToViewMapEdit.containsKey(photoUri)) {
                        addPhotoThumbnail(photoUri, editContainer, photoType, true)
                    }
                } catch (e: Exception) {
                    Log.e("EditClientActivity", "Błąd dodawania miniatury nowego zdjęcia: $photoUri", e)
                }
            }
        } else {
            val adapter = if (photoType == PhotoType.CLIENT) clientPhotosAdapter else transactionPhotosAdapter
            val photosToShow = photos.filter { photo ->
                try {
                    photo.uri !in photosToRemoveUrisSet
                } catch (e: Exception) {
                    Log.w("EditClientActivity", "Błąd parsowania URI ${photo.uri} podczas filtrowania.", e)
                    false
                }
            }
            if (::clientPhotosAdapter.isInitialized && ::transactionPhotosAdapter.isInitialized) {
                adapter.updatePhotos(photosToShow)
            }
        }
        if (::clientPhotosTitleEdit.isInitialized) {
           updatePhotoSectionVisibility(photoType, isEditing)
        }
    }


    /**
     * Dodaje widok miniatury zdjęcia do kontenera (tylko tryb edycji).
     */
    private fun addPhotoThumbnail(photoUri: Uri, container: LinearLayout, photoType: PhotoType, isEditing: Boolean) {
        if (!isEditing || photoUriToViewMapEdit.containsKey(photoUri)) return

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
                val wasAddedInThisSession = (if (photoType == PhotoType.CLIENT) editClientViewModel.clientPhotosToAddUris.value else editClientViewModel.transactionPhotosToAddUris.value)
                    ?.contains(photoUri) ?: false

                if (wasAddedInThisSession) {
                    editClientViewModel.removePhotoToAdd(photoUri, photoType)
                    Log.d("EditClientActivity", "Usunięto nowo dodane zdjęcie (przed zapisem): $photoUri")
                } else {
                    editClientViewModel.addPhotoToRemove(photoUri)
                    Log.d("EditClientActivity", "Oznaczono do usunięcia istniejące zdjęcie: $photoUri")
                }
            }
            photoUriToViewMapEdit[photoUri] = thumbnailView
            container.addView(thumbnailView)
        } catch (e: Exception) {
            Log.e("EditClientActivity", "Błąd ładowania miniatury $photoUri", e)
        }
    }

    /** Aktualizuje widoczność kontenerów i tytułów sekcji zdjęć. */
    private fun updatePhotoSectionVisibility(photoType: PhotoType, isEditing: Boolean) {
        val loadedPhotos = if (photoType == PhotoType.CLIENT) loadedClientPhotos else loadedTransactionPhotos
        val photosToAddUris = (if (photoType == PhotoType.CLIENT) editClientViewModel.clientPhotosToAddUris.value else editClientViewModel.transactionPhotosToAddUris.value) ?: emptyList()
        val photosToRemoveUris = editClientViewModel.photosToRemoveUris.value ?: emptyList()
        val photosToRemoveUrisSet = photosToRemoveUris.map { it.toString() }.toSet()

        val photosExist = loadedPhotos.any { it.uri !in photosToRemoveUrisSet } || photosToAddUris.isNotEmpty()

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
     * Aktualizuje widoczność i stan edytowalności elementów UI.
     * Poprawiono logikę widoczności pól tekstowych w trybie widoku.
     */
    private fun updateUiMode(isEditing: Boolean) {
        titleTextView.text = getString(if (isEditing) R.string.edit_client_title else R.string.view_client_title)

        editClientDescriptionEditText.isEnabled = isEditing
        editClientAppNumberEditText.isEnabled = isEditing
        editAmoditNumberEditText.isEnabled = isEditing

        saveClientButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteClientButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        editModeClientButton.visibility = if (isEditing) View.GONE else View.VISIBLE

        clientDataSectionTitleEdit.visibility = if (isEditing) View.VISIBLE else View.GONE

        val hasDescription = editClientViewModel.clientDescriptionState.value?.isNotEmpty() ?: false
        val hasAppNumber = editClientViewModel.clientAppNumberState.value?.isNotEmpty() ?: false
        val hasAmoditNumber = editClientViewModel.amoditNumberState.value?.isNotEmpty() ?: false
        val hasAnyData = hasDescription || hasAppNumber || hasAmoditNumber

        if (isEditing) {
            editDescriptionLayout.visibility = View.VISIBLE
            editAppNumberLayout.visibility = View.VISIBLE
            editAmoditNumberLayout.visibility = View.VISIBLE
            clientDataSectionTitleEdit.visibility = View.VISIBLE
            clientDataSectionTitleView.visibility = View.GONE
        } else {
            editDescriptionLayout.visibility = if (hasDescription) View.VISIBLE else View.GONE
            editAppNumberLayout.visibility = if (hasAppNumber) View.VISIBLE else View.GONE
            editAmoditNumberLayout.visibility = if (hasAmoditNumber) View.VISIBLE else View.GONE
            clientDataSectionTitleEdit.visibility = View.GONE
            clientDataSectionTitleView.visibility = if (hasAnyData) View.VISIBLE else View.GONE
        }

        updatePhotoUiIfNeeded(PhotoType.CLIENT)
        updatePhotoUiIfNeeded(PhotoType.TRANSACTION)
    }


    /**
     * Zbiera dane z ViewModelu i wywołuje metodę zapisu zmian.
     */
    private fun saveClientChanges() {
        lifecycleScope.launch {
            val result = editClientViewModel.updateClientAndPhotos(currentClientId)
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
     * Wywołuje metodę usuwania klienta w ViewModelu.
     */
    private fun deleteClient() {
        lifecycleScope.launch {
            val clientStub = com.kaminski.paragownik.data.Client(id = currentClientId, description = null)
            val result = editClientViewModel.deleteClient(clientStub)
            handleEditResult(result, true)
        }
    }

    /**
     * Obsługuje wynik operacji edycji/usuwania zwrócony przez ViewModel.
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
                editClientViewModel.setEditMode(false)
                isViewInitialized = false
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

    /**
     * Otwiera aktywność [FullScreenImageActivity] dla podanej listy URI obrazów i pozycji startowej.
     * @param imageUris Lista URI zdjęć (jako String).
     * @param startPosition Indeks zdjęcia, które ma być wyświetlone jako pierwsze.
     */
    private fun openFullScreenImage(imageUris: ArrayList<String>, startPosition: Int) {
        val intent = Intent(this, FullScreenImageActivity::class.java).apply {
            putStringArrayListExtra(FullScreenImageActivity.IMAGE_URIS, imageUris)
            putExtra(FullScreenImageActivity.START_POSITION, startPosition)
        }
        startActivity(intent)
    }
}