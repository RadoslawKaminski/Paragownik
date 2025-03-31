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
import java.util.Calendar

/**
 * Aktywność odpowiedzialna za przeglądanie i edycję istniejącego paragonu oraz danych powiązanego klienta,
 * w tym zarządzanie wieloma zdjęciami klienta i transakcji. Używa Glide do ładowania obrazów.
 * Umożliwia otwarcie zdjęcia na pełnym ekranie.
 */
class EditReceiptActivity : AppCompatActivity() {

    // --- Widoki UI ---
    private lateinit var editReceiptStoreNumberEditText: EditText
    private lateinit var editReceiptNumberEditText: EditText
    private lateinit var editReceiptDateEditText: EditText
    private lateinit var editCashRegisterNumberEditText: EditText // Pole numeru kasy
    private lateinit var editVerificationDateEditText: EditText
    private lateinit var editVerificationDateTodayCheckBox: CheckBox
    private lateinit var editClientDescriptionEditText: EditText
    private lateinit var editClientAppNumberEditText: EditText
    private lateinit var editAmoditNumberEditText: EditText
    private lateinit var saveReceiptButton: Button
    private lateinit var deleteReceiptButton: Button
    private lateinit var deleteClientButton: Button
    private lateinit var editModeImageButton: ImageButton
    private lateinit var showClientReceiptsButton: Button
    private lateinit var showStoreReceiptsButton: Button
    private lateinit var editCashRegisterNumberLayout: LinearLayout // Layout dla numeru kasy
    private lateinit var editVerificationSectionLayout: LinearLayout
    private lateinit var verificationSectionTitleEdit: TextView
    private lateinit var verificationSectionTitleView: TextView
    private lateinit var editDescriptionLayout: LinearLayout
    private lateinit var editAppNumberLayout: LinearLayout
    private lateinit var editAmoditNumberLayout: LinearLayout
    private lateinit var clientDataSectionTitleEdit: TextView
    private lateinit var clientDataSectionTitleView: TextView

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
    private lateinit var editReceiptViewModel: EditReceiptViewModel

    // --- Dane pomocnicze ---
    private var receiptId: Long = -1L
    private var currentClientId: Long? = null
    private var currentStoreId: Long = -1L
    private var loadDataJob: Job? = null
    private var isEditMode = false
    private var navigationContext: String? = null

    private val currentClientPhotos = mutableListOf<Photo>()
    private val currentTransactionPhotos = mutableListOf<Photo>()
    private val photosToAdd = mutableMapOf<PhotoType, MutableList<Uri>>()
    private val photosToRemove = mutableListOf<Uri>()
    private val photoUriToViewMapEdit = mutableMapOf<Uri, View>()
    private var currentPhotoTypeToAdd: PhotoType? = null


    // Launcher ActivityResult do wybierania obrazu z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            Log.d("EditReceiptActivity", "Otrzymano tymczasowe URI: $sourceUri")
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
                        Log.d("EditReceiptActivity", "Przygotowano do dodania zdjęcie ($type): $finalUri")
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
        currentPhotoTypeToAdd = null
    }


    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_receipt)

        initializeViews()
        initializeViewModel()

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

        loadReceiptData()
        setupButtonClickListeners()
        updateUiMode(false)
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI z layoutu.
     */
    private fun initializeViews() {
        editReceiptStoreNumberEditText = findViewById(R.id.editReceiptStoreNumberEditText)
        editReceiptNumberEditText = findViewById(R.id.editReceiptNumberEditText)
        editReceiptDateEditText = findViewById(R.id.editReceiptDateEditText)
        editCashRegisterNumberEditText = findViewById(R.id.editCashRegisterNumberEditText) // Inicjalizacja pola numeru kasy
        editVerificationDateEditText = findViewById(R.id.editVerificationDateEditText)
        editVerificationDateTodayCheckBox = findViewById(R.id.editVerificationDateTodayCheckBox)
        editClientDescriptionEditText = findViewById(R.id.editClientDescriptionEditText)
        editClientAppNumberEditText = findViewById(R.id.editClientAppNumberEditText)
        editAmoditNumberEditText = findViewById(R.id.editAmoditNumberEditText)
        saveReceiptButton = findViewById(R.id.saveReceiptButton)
        deleteReceiptButton = findViewById(R.id.deleteReceiptButton)
        deleteClientButton = findViewById(R.id.deleteClientButton)
        editModeImageButton = findViewById(R.id.editModeImageButton)
        showClientReceiptsButton = findViewById(R.id.showClientReceiptsButton)
        showStoreReceiptsButton = findViewById(R.id.showStoreReceiptsButton)
        editCashRegisterNumberLayout = findViewById(R.id.editCashRegisterNumberLayout) // Inicjalizacja layoutu numeru kasy
        editVerificationSectionLayout = findViewById(R.id.editVerificationSectionLayout)
        verificationSectionTitleEdit = findViewById(R.id.verificationSectionTitleEdit)
        verificationSectionTitleView = findViewById(R.id.verificationSectionTitleView)
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
     * Inicjalizuje ViewModel.
     */
    private fun initializeViewModel() {
        editReceiptViewModel = ViewModelProvider(this).get(EditReceiptViewModel::class.java)
    }

    /**
     * Ustawia listenery kliknięć dla przycisków (Zapisz, Usuń, Dodaj zdjęcie, Edytuj, Nawigacja).
     */
    private fun setupButtonClickListeners() {
        saveReceiptButton.setOnClickListener { saveChanges() }
        deleteReceiptButton.setOnClickListener { showDeleteReceiptDialog() }
        deleteClientButton.setOnClickListener { showDeleteClientDialog() }

        addClientPhotoButtonEdit.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.CLIENT
            pickImageLauncher.launch("image/*")
        }

        addTransactionPhotoButtonEdit.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.TRANSACTION
            pickImageLauncher.launch("image/*")
        }

        editModeImageButton.setOnClickListener {
            updateUiMode(true)
        }

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
     * Wczytuje dane paragonu, klienta, sklepu i zdjęć z ViewModelu.
     * Wypełnia formularz oraz kontenery zdjęć.
     */
    private fun loadReceiptData() {
        loadDataJob?.cancel()
        loadDataJob = lifecycleScope.launch {
            editReceiptViewModel.getReceiptWithClientAndStoreNumber(receiptId)
                .collectLatest { triple ->
                    if (!isActive) return@collectLatest

                    val receiptWithClient = triple.first
                    val storeNumber = triple.second
                    val photos = triple.third

                    clearPhotoData()

                    if (receiptWithClient != null && receiptWithClient.client != null) {
                        val receipt = receiptWithClient.receipt
                        val client = receiptWithClient.client
                        currentClientId = client.id
                        currentStoreId = receipt.storeId

                        editReceiptStoreNumberEditText.setText(storeNumber ?: "")
                        editReceiptNumberEditText.setText(receipt.receiptNumber)
                        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        editReceiptDateEditText.setText(dateFormat.format(receipt.receiptDate))
                        editCashRegisterNumberEditText.setText(receipt.cashRegisterNumber ?: "") // Wypełnienie pola numeru kasy

                        receipt.verificationDate?.let { verificationDate ->
                            val formattedVerificationDate = dateFormat.format(verificationDate)
                            editVerificationDateEditText.setText(formattedVerificationDate)
                            val todayDate = dateFormat.format(Calendar.getInstance().time)
                            editVerificationDateTodayCheckBox.isChecked = formattedVerificationDate == todayDate
                        } ?: run {
                            editVerificationDateEditText.text.clear()
                            editVerificationDateTodayCheckBox.isChecked = false
                        }

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
                             Log.e("EditReceiptActivity", "Nie znaleziono danych dla receiptId: $receiptId (prawdopodobnie usunięto)")
                             // Dodano Toast i finish() w przypadku braku danych
                             Toast.makeText(this@EditReceiptActivity, R.string.error_receipt_not_found, Toast.LENGTH_SHORT).show()
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

            for (photo in photos) {
                 try {
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
            val adapter = if (photoType == PhotoType.CLIENT) clientPhotosAdapter else transactionPhotosAdapter
            val photosToShow = photos.filter { photo ->
                try {
                    !photosToRemove.contains(photo.uri.toUri())
                } catch (e: Exception) {
                    Log.w("EditReceiptActivity", "Błąd parsowania URI ${photo.uri} podczas filtrowania zdjęć do wyświetlenia.", e)
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
                        Log.d("EditReceiptActivity", "Oznaczono do usunięcia istniejące zdjęcie: $photoUri")
                    }
                } else {
                    Log.d("EditReceiptActivity", "Usunięto nowo dodane zdjęcie (przed zapisem): $photoUri")
                }
                photoUriToViewMapEdit.remove(photoUri)
                updatePhotoSectionVisibility(photoType, true)
            }
            photoUriToViewMapEdit[photoUri] = thumbnailView
            container.addView(thumbnailView)

        } catch (e: Exception) {
            Log.e("EditReceiptActivity", "Błąd ładowania miniatury $photoUri", e)
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

        editReceiptStoreNumberEditText.isEnabled = isEditing
        editReceiptNumberEditText.isEnabled = isEditing
        editReceiptDateEditText.isEnabled = isEditing
        editCashRegisterNumberEditText.isEnabled = isEditing // Włączenie/wyłączenie pola numeru kasy
        editVerificationDateEditText.isEnabled = isEditing && !editVerificationDateTodayCheckBox.isChecked
        editVerificationDateTodayCheckBox.isEnabled = isEditing
        editVerificationDateTodayCheckBox.visibility = if (isEditing) View.VISIBLE else View.GONE
        editClientDescriptionEditText.isEnabled = isEditing
        editClientAppNumberEditText.isEnabled = isEditing
        editAmoditNumberEditText.isEnabled = isEditing

        saveReceiptButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteReceiptButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteClientButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        editModeImageButton.visibility = if (isEditing) View.GONE else View.VISIBLE

        val hasCashRegisterNumber = !editCashRegisterNumberEditText.text.isNullOrBlank()
        editCashRegisterNumberLayout.visibility = if (isEditing || hasCashRegisterNumber) View.VISIBLE else View.GONE // Widoczność layoutu numeru kasy

        val hasVerificationDate = !editVerificationDateEditText.text.isNullOrBlank()
        editVerificationSectionLayout.visibility = if (isEditing || hasVerificationDate) View.VISIBLE else View.GONE
        verificationSectionTitleEdit.visibility = if (isEditing && editVerificationSectionLayout.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        verificationSectionTitleView.visibility = if (!isEditing && hasVerificationDate) View.VISIBLE else View.GONE

        val hasDescription = !editClientDescriptionEditText.text.isNullOrBlank()
        editDescriptionLayout.visibility = if (isEditing || hasDescription) View.VISIBLE else View.GONE

        val hasAppNumber = !editClientAppNumberEditText.text.isNullOrBlank()
        editAppNumberLayout.visibility = if (isEditing || hasAppNumber) View.VISIBLE else View.GONE

        val hasAmoditNumber = !editAmoditNumberEditText.text.isNullOrBlank()
        editAmoditNumberLayout.visibility = if (isEditing || hasAmoditNumber) View.VISIBLE else View.GONE

        clientDataSectionTitleEdit.visibility = if (isEditing) View.VISIBLE else View.GONE
        clientDataSectionTitleView.visibility = if (!isEditing) View.VISIBLE else View.GONE

        updatePhotoSectionVisibility(PhotoType.CLIENT, isEditing)
        updatePhotoSectionVisibility(PhotoType.TRANSACTION, isEditing)

        showClientReceiptsButton.visibility = if (!isEditing && navigationContext == "STORE_LIST") View.VISIBLE else View.GONE
        showStoreReceiptsButton.visibility = if (!isEditing && navigationContext == "CLIENT_LIST") View.VISIBLE else View.GONE

        populatePhotoContainer(clientPhotosContainerEdit, currentClientPhotos, PhotoType.CLIENT, isEditing)
        populatePhotoContainer(null, currentClientPhotos, PhotoType.CLIENT, isEditing)
        populatePhotoContainer(transactionPhotosContainerEdit, currentTransactionPhotos, PhotoType.TRANSACTION, isEditing)
        populatePhotoContainer(null, currentTransactionPhotos, PhotoType.TRANSACTION, isEditing)
    }


    /**
     * Zbiera dane z formularza i wywołuje metodę zapisu zmian w ViewModelu.
     */
    private fun saveChanges() {
        val storeNumberString = editReceiptStoreNumberEditText.text.toString().trim()
        val receiptNumber = editReceiptNumberEditText.text.toString().trim()
        val receiptDateString = editReceiptDateEditText.text.toString().trim()
        val cashRegisterNumber = editCashRegisterNumberEditText.text.toString().trim() // Pobranie numeru kasy
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
                cashRegisterNumber = cashRegisterNumber.takeIf { it.isNotEmpty() }, // Przekazanie numeru kasy
                verificationDateString = verificationDateString.takeIf { it.isNotEmpty() },
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
     * Wywołuje metodę usuwania paragonu w ViewModelu. Anuluje najpierw obserwację danych.
     */
    private fun deleteReceipt() {
        loadDataJob?.cancel()
        Log.d("EditReceiptActivity", "Anulowano loadDataJob przed usunięciem paragonu.")

        lifecycleScope.launch {
            val currentReceipt = editReceiptViewModel.getReceiptWithClientAndStoreNumber(receiptId)
                .map { it.first?.receipt }
                .firstOrNull()

            if (currentReceipt == null) {
                Toast.makeText(this@EditReceiptActivity, R.string.error_cannot_get_receipt_data, Toast.LENGTH_LONG).show()
                Log.e("EditReceiptActivity", "Nie udało się pobrać Receipt (id: $receiptId) do usunięcia.")
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
     * Wywołuje metodę usuwania klienta w ViewModelu. Anuluje najpierw obserwację danych.
     */
    private fun deleteClient() {
        loadDataJob?.cancel()
        Log.d("EditReceiptActivity", "Anulowano loadDataJob przed usunięciem klienta.")

        val clientIdToDelete = currentClientId ?: return

        lifecycleScope.launch {
            val clientStub = com.kaminski.paragownik.data.Client(id = clientIdToDelete, description = null)
            val result = editReceiptViewModel.deleteClient(clientStub)
            handleEditResult(result, true)
        }
    }


    /**
     * Obsługuje wynik operacji edycji/usuwania zwrócony przez ViewModel.
     * Wyświetla Toast i odpowiednio zarządza stanem aktywności.
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
                finishAffinity()
                startActivity(Intent(this@EditReceiptActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } else {
                photosToAdd.clear()
                photosToRemove.clear()
                loadReceiptData()
                updateUiMode(false)
            }
        }
    }


    /**
     * Konfiguruje działanie CheckBoxa "Dzisiaj" dla daty weryfikacji.
     * Zmiana stanu checkboxa wpływa na pole daty tylko w trybie edycji.
     */
    private fun setupVerificationDateCheckBox() {
        editVerificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isEditMode) {
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
     * Konfiguruje [EditText] do automatycznego formatowania wprowadzanej daty do formatu DD-MM-YYYY.
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

    /**
     * Otwiera aktywność [FullScreenImageActivity] dla podanego URI obrazu.
     */
    private fun openFullScreenImage(imageUri: Uri) {
        val intent = Intent(this, FullScreenImageActivity::class.java)
        intent.putExtra("IMAGE_URI", imageUri.toString())
        startActivity(intent)
    }
}

