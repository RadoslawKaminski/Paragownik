
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
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.kaminski.paragownik.viewmodel.EditReceiptViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Aktywność odpowiedzialna za przeglądanie i edycję istniejącego paragonu oraz danych powiązanego klienta.
 * Używa ViewModelu do zarządzania stanem UI i logiką biznesową.
 */
class EditReceiptActivity : AppCompatActivity() {

    // --- Widoki UI ---
    private lateinit var editReceiptStoreNumberEditText: EditText
    private lateinit var editReceiptNumberEditText: EditText
    private lateinit var editReceiptDateEditText: EditText
    private lateinit var editCashRegisterNumberEditText: EditText
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
    private lateinit var addReceiptsToClientButton: Button
    private lateinit var editCashRegisterNumberLayout: LinearLayout
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
    private var navigationContext: String? = null
    private var currentPhotoTypeToAdd: PhotoType? = null
    private val photoUriToViewMapEdit = mutableMapOf<Uri, View>()
    private var loadedClientPhotos: List<Photo> = emptyList()
    private var loadedTransactionPhotos: List<Photo> = emptyList()
    private var isDataInitialized = false
    private var isReceiptDateUpdateFromVM = false
    private var isVerificationDateUpdateFromVM = false


    // Launcher ActivityResult do wybierania obrazu z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            Log.d("EditReceiptActivity", "Otrzymano tymczasowe URI: $sourceUri")
            val destinationUri = copyImageToInternalStorage(sourceUri)
            destinationUri?.let { finalUri ->
                val type = currentPhotoTypeToAdd
                if (type != null) {
                    val isAlreadyLoaded = (if (type == PhotoType.CLIENT) loadedClientPhotos else loadedTransactionPhotos)
                        .any { it.uri == finalUri.toString() }
                    val isAlreadyAdded = (if (type == PhotoType.CLIENT) editReceiptViewModel.clientPhotosToAddUris.value else editReceiptViewModel.transactionPhotosToAddUris.value)
                        ?.contains(finalUri) ?: false
                    val isMarkedForRemoval = editReceiptViewModel.photosToRemoveUris.value?.contains(finalUri) ?: false

                    if (!isAlreadyLoaded && !isAlreadyAdded) {
                        if (isMarkedForRemoval) {
                            editReceiptViewModel.removePhotoToRemove(finalUri)
                        }
                        editReceiptViewModel.addPhotoToAdd(finalUri, type)
                        Log.d("EditReceiptActivity", "Przygotowano do dodania zdjęcie ($type): $finalUri")
                    } else if (isAlreadyLoaded && isMarkedForRemoval) {
                        editReceiptViewModel.removePhotoToRemove(finalUri)
                        Log.d("EditReceiptActivity", "Odznaczono zdjęcie ($type) do usunięcia: $finalUri")
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
        editReceiptViewModel = ViewModelProvider(this).get(EditReceiptViewModel::class.java)

        receiptId = intent.getLongExtra("RECEIPT_ID", -1L)
        navigationContext = intent.getStringExtra("CONTEXT")
        Log.d("EditReceiptActivity", "Otrzymano receiptId: $receiptId, kontekst: $navigationContext")

        if (receiptId == -1L) {
            Toast.makeText(this, R.string.error_invalid_receipt_id, Toast.LENGTH_LONG).show()
            Log.e("EditReceiptActivity", "Nieprawidłowe RECEIPT_ID przekazane w Intencie.")
            finish()
            return
        }

        setupVerificationDateCheckBox()
        setupAdapters() // Zmieniono logikę listenera w tej metodzie
        setupFieldListeners()
        setupButtonClickListeners()
        observeViewModel()
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI z layoutu.
     */
    private fun initializeViews() {
        editReceiptStoreNumberEditText = findViewById(R.id.editReceiptStoreNumberEditText)
        editReceiptNumberEditText = findViewById(R.id.editReceiptNumberEditText)
        editReceiptDateEditText = findViewById(R.id.editReceiptDateEditText)
        editCashRegisterNumberEditText = findViewById(R.id.editCashRegisterNumberEditText)
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
        addReceiptsToClientButton = findViewById(R.id.addReceiptsToClientButton)
        editCashRegisterNumberLayout = findViewById(R.id.editCashRegisterNumberLayout)
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
            // Pobierz aktualną listę URI z adaptera
            val currentPhotoList = clientPhotosAdapter.getCurrentPhotos() // Zakładamy, że adapter ma metodę zwracającą listę Photo
            val allUris = currentPhotoList.map { it.uri }
            val clickedIndex = allUris.indexOf(clickedUri.toString())

            if (clickedIndex != -1) {
                openFullScreenImage(ArrayList(allUris), clickedIndex)
            } else {
                Log.e("EditReceiptActivity", "Nie znaleziono klikniętego URI ($clickedUri) w liście zdjęć klienta.")
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
            // Pobierz aktualną listę URI z adaptera
            val currentPhotoList = transactionPhotosAdapter.getCurrentPhotos() // Zakładamy, że adapter ma metodę zwracającą listę Photo
            val allUris = currentPhotoList.map { it.uri }
            val clickedIndex = allUris.indexOf(clickedUri.toString())

            if (clickedIndex != -1) {
                openFullScreenImage(ArrayList(allUris), clickedIndex)
            } else {
                Log.e("EditReceiptActivity", "Nie znaleziono klikniętego URI ($clickedUri) w liście zdjęć transakcji.")
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show()
            }
        }
        transactionPhotosRecyclerViewView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        transactionPhotosRecyclerViewView.adapter = transactionPhotosAdapter
    }

    /**
     * Ustawia listenery dla pól edycyjnych.
     * Dla pól daty, listener obsługuje formatowanie, ustawianie kursora i aktualizację ViewModelu.
     */
    private fun setupFieldListeners() {
        // Listenery dla pól innych niż data
        editReceiptStoreNumberEditText.addTextChangedListener { if (editReceiptViewModel.isEditMode.value == true) editReceiptViewModel.setStoreNumber(it.toString()) }
        editReceiptNumberEditText.addTextChangedListener { if (editReceiptViewModel.isEditMode.value == true) editReceiptViewModel.setReceiptNumber(it.toString()) }
        editCashRegisterNumberEditText.addTextChangedListener { if (editReceiptViewModel.isEditMode.value == true) editReceiptViewModel.setCashRegisterNumber(it.toString()) }
        editClientDescriptionEditText.addTextChangedListener { if (editReceiptViewModel.isEditMode.value == true) editReceiptViewModel.setClientDescription(it.toString()) }
        editClientAppNumberEditText.addTextChangedListener { if (editReceiptViewModel.isEditMode.value == true) editReceiptViewModel.setClientAppNumber(it.toString()) }
        editAmoditNumberEditText.addTextChangedListener { if (editReceiptViewModel.isEditMode.value == true) editReceiptViewModel.setAmoditNumber(it.toString()) }

        // Zcentralizowany TextWatcher dla daty paragonu
        editReceiptDateEditText.inputType = InputType.TYPE_CLASS_NUMBER // Ustawiamy inputType na numeryczny
        editReceiptDateEditText.addTextChangedListener(createDateTextWatcher(editReceiptDateEditText) { formattedDate ->
            // Aktualizuj ViewModel tylko jeśli tryb edycji i zmiana nie pochodzi z VM
            if (editReceiptViewModel.isEditMode.value == true && !isReceiptDateUpdateFromVM) {
                editReceiptViewModel.setReceiptDate(formattedDate)
            }
        })

        // Zcentralizowany TextWatcher dla daty weryfikacji
        editVerificationDateEditText.inputType = InputType.TYPE_CLASS_NUMBER // Ustawiamy inputType na numeryczny
        editVerificationDateEditText.addTextChangedListener(createDateTextWatcher(editVerificationDateEditText) { formattedDate ->
            // Aktualizuj ViewModel tylko jeśli tryb edycji i zmiana nie pochodzi z VM
            if (editReceiptViewModel.isEditMode.value == true && !isVerificationDateUpdateFromVM) {
                editReceiptViewModel.setVerificationDate(formattedDate)
            }
        })
    }

    /**
     * Tworzy instancję TextWatcher'a do formatowania daty i aktualizacji ViewModelu.
     * @param editText Pole EditText, do którego watcher będzie dołączony.
     * @param updateViewModel Lambda wywoływana po sformatowaniu tekstu, przekazująca sformatowaną datę.
     */
    private fun createDateTextWatcher(editText: EditText, updateViewModel: (String) -> Unit): TextWatcher {
        return object : TextWatcher {
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

                val userInput = s.toString()
                if (userInput == current) {
                    return // Zmiana pochodzi z setText w tym watcherze, ignoruj
                }

                isFormatting = true

                val digitsOnly = userInput.replace("[^\\d]".toRegex(), "")
                val len = digitsOnly.length
                val formatted = StringBuilder()
                if (len >= 1) formatted.append(digitsOnly.substring(0, minOf(len, 2))) // DD
                if (len >= 3) formatted.append("-").append(digitsOnly.substring(2, minOf(len, 4))) // MM
                if (len >= 5) formatted.append("-").append(digitsOnly.substring(4, minOf(len, 8))) // YYYY
                current = formatted.toString()

                // Ustaw sformatowany tekst w EditText
                editText.setText(current)

                // Przywróć pozycję kursora
                try {
                    val lengthDiff = current.length - textLengthBefore
                    var newCursorPos = cursorPosBefore + lengthDiff
                    newCursorPos = maxOf(0, minOf(newCursorPos, current.length))
                    editText.setSelection(newCursorPos)
                } catch (e: Exception) {
                    try { editText.setSelection(current.length) } catch (e2: Exception) { /* Ignoruj */ }
                    Log.e("DateTextWatcher", "Błąd ustawiania kursora w EditReceiptActivity", e)
                }

                // Zdejmij flagę formatowania
                isFormatting = false

                // Zaktualizuj ViewModel sformatowaną datą
                // Sprawdzenie !isXxxUpdateFromVM odbywa się w setupFieldListeners przed wywołaniem updateViewModel
                updateViewModel(current)
            }
        }
    }


    /** Ustawia listenery kliknięć dla przycisków. */
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
            // Przełącz tryb w ViewModelu
            editReceiptViewModel.setEditMode(true)
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

        // Listener dla nowego przycisku
        addReceiptsToClientButton.setOnClickListener {
            currentClientId?.let { clientId ->
                val intent = Intent(this, AddReceiptToClientActivity::class.java)
                intent.putExtra("CLIENT_ID", clientId)
                startActivity(intent)
                Log.d("EditReceiptActivity", "Uruchamianie AddReceiptToClientActivity dla klienta ID: $clientId")
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

    /** Obserwuje zmiany w StateFlow i LiveData ViewModelu. */
    private fun observeViewModel() {
        // Obserwacja danych z bazy (Flow)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                editReceiptViewModel.getReceiptDataFlow(receiptId).collectLatest { triple ->
                    val receiptWithClient = triple.first
                    val storeNumber = triple.second
                    val photos = triple.third

                    if (receiptWithClient == null) {
                        if (isDataInitialized) {
                            Log.e("EditReceiptActivity", "Dane paragonu (ID: $receiptId) stały się null po inicjalizacji.")
                            if (!isFinishing) {
                                Toast.makeText(this@EditReceiptActivity, R.string.error_receipt_not_found, Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        } else {
                            Log.d("EditReceiptActivity", "Otrzymano null receiptWithClient (przed inicjalizacją), ignorowanie.")
                        }
                        return@collectLatest
                    }

                    if (receiptWithClient.client != null) {
                        Log.d("EditReceiptActivity", "Otrzymano aktualne dane paragonu/klienta z Flow.")
                        currentClientId = receiptWithClient.client.id
                        currentStoreId = receiptWithClient.receipt.storeId
                        loadedClientPhotos = photos?.filter { it.type == PhotoType.CLIENT } ?: emptyList()
                        loadedTransactionPhotos = photos?.filter { it.type == PhotoType.TRANSACTION } ?: emptyList()

                        if (!isDataInitialized) {
                            editReceiptViewModel.initializeStateIfNeeded(receiptWithClient, storeNumber)
                            isDataInitialized = true
                            updateUiMode(editReceiptViewModel.isEditMode.value ?: false)
                            Log.d("EditReceiptActivity", "Wymuszono aktualizację UI po inicjalizacji danych.")
                        }

                        updatePhotoUiIfNeeded(PhotoType.CLIENT)
                        updatePhotoUiIfNeeded(PhotoType.TRANSACTION)
                    } else {
                        Log.e("EditReceiptActivity", "Znaleziono paragon (ID: $receiptId), ale powiązany klient jest null.")
                        if (!isFinishing) {
                            Toast.makeText(this@EditReceiptActivity, R.string.error_client_not_found, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        }

        // Obserwacja LiveData stanu UI z ViewModelu
        editReceiptViewModel.isEditMode.observe(this, Observer { isEditing ->
            updateUiMode(isEditing)
        })

        // Obserwatory dla pól tekstowych
        editReceiptViewModel.storeNumberState.observe(this, Observer { value -> if (editReceiptStoreNumberEditText.text.toString() != value) editReceiptStoreNumberEditText.setText(value) })
        editReceiptViewModel.receiptNumberState.observe(this, Observer { value -> if (editReceiptNumberEditText.text.toString() != value) editReceiptNumberEditText.setText(value) })
        editReceiptViewModel.receiptDateState.observe(this, Observer { value ->
            if (editReceiptDateEditText.text.toString() != value) {
                isReceiptDateUpdateFromVM = true
                editReceiptDateEditText.setText(value)
                isReceiptDateUpdateFromVM = false
            }
        })
        editReceiptViewModel.cashRegisterNumberState.observe(this, Observer { value -> if (editCashRegisterNumberEditText.text.toString() != value) editCashRegisterNumberEditText.setText(value) })
        editReceiptViewModel.verificationDateState.observe(this, Observer { value ->
            if (editVerificationDateEditText.text.toString() != value) {
                isVerificationDateUpdateFromVM = true
                editVerificationDateEditText.setText(value)
                isVerificationDateUpdateFromVM = false
            }
        })
        editReceiptViewModel.isVerificationDateTodayState.observe(this, Observer { isChecked ->
            if (editVerificationDateTodayCheckBox.isChecked != isChecked) editVerificationDateTodayCheckBox.isChecked = isChecked
            editVerificationDateEditText.isEnabled = (editReceiptViewModel.isEditMode.value == true) && !isChecked
        })
        editReceiptViewModel.clientDescriptionState.observe(this, Observer { value -> if (editClientDescriptionEditText.text.toString() != value) editClientDescriptionEditText.setText(value) })
        editReceiptViewModel.clientAppNumberState.observe(this, Observer { value -> if (editClientAppNumberEditText.text.toString() != value) editClientAppNumberEditText.setText(value) })
        editReceiptViewModel.amoditNumberState.observe(this, Observer { value -> if (editAmoditNumberEditText.text.toString() != value) editAmoditNumberEditText.setText(value) })

        // Obserwatory dla list zdjęć
        editReceiptViewModel.clientPhotosToAddUris.observe(this, Observer { updatePhotoUiIfNeeded(PhotoType.CLIENT) })
        editReceiptViewModel.transactionPhotosToAddUris.observe(this, Observer { updatePhotoUiIfNeeded(PhotoType.TRANSACTION) })
        editReceiptViewModel.photosToRemoveUris.observe(this, Observer {
            updatePhotoUiIfNeeded(PhotoType.CLIENT)
            updatePhotoUiIfNeeded(PhotoType.TRANSACTION)
        })
    }

    /** Pomocnicza funkcja do aktualizacji UI zdjęć po zmianie list w ViewModelu. */
    private fun updatePhotoUiIfNeeded(photoType: PhotoType) {
        val isEditing = editReceiptViewModel.isEditMode.value ?: false
        val loadedPhotos = if (photoType == PhotoType.CLIENT) loadedClientPhotos else loadedTransactionPhotos
        val container = if (photoType == PhotoType.CLIENT) clientPhotosContainerEdit else transactionPhotosContainerEdit

        if (::clientPhotosContainerEdit.isInitialized && ::transactionPhotosContainerEdit.isInitialized &&
            ::clientPhotosRecyclerViewView.isInitialized && ::transactionPhotosRecyclerViewView.isInitialized) {
            populatePhotoContainer(container, loadedPhotos, photoType, isEditing)
            populatePhotoContainer(null, loadedPhotos, photoType, isEditing)
        } else {
            Log.w("EditReceiptActivity", "Próba aktualizacji UI zdjęć przed inicjalizacją widoków.")
        }
    }


    /**
     * Wypełnia kontener miniaturami zdjęć (tryb edycji) lub RecyclerView (tryb widoku).
     * Odczytuje stan zdjęć do dodania/usunięcia z ViewModelu.
     */
    private fun populatePhotoContainer(container: LinearLayout?, photos: List<Photo>, photoType: PhotoType, isEditing: Boolean) {
        val photosToAddUrisSet = (if (photoType == PhotoType.CLIENT) editReceiptViewModel.clientPhotosToAddUris.value else editReceiptViewModel.transactionPhotosToAddUris.value)?.map { it.toString() }?.toSet() ?: emptySet()
        val photosToRemoveUrisSet = editReceiptViewModel.photosToRemoveUris.value?.map { it.toString() }?.toSet() ?: emptySet()

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
                    Log.e("EditReceiptActivity", "Błąd podczas dodawania miniatury istniejącego zdjęcia: ${photo.uri}", e)
                }
            }
            (if (photoType == PhotoType.CLIENT) editReceiptViewModel.clientPhotosToAddUris.value else editReceiptViewModel.transactionPhotosToAddUris.value)?.forEach { photoUri ->
                try {
                    if (!photoUriToViewMapEdit.containsKey(photoUri)) {
                        addPhotoThumbnail(photoUri, editContainer, photoType, true)
                    }
                } catch (e: Exception) {
                    Log.e("EditReceiptActivity", "Błąd podczas dodawania miniatury nowego zdjęcia: $photoUri", e)
                }
            }
        } else {
            val adapter = if (photoType == PhotoType.CLIENT) clientPhotosAdapter else transactionPhotosAdapter
            val photosToShow = photos.filter { photo ->
                try {
                    photo.uri !in photosToRemoveUrisSet
                } catch (e: Exception) {
                    Log.w("EditReceiptActivity", "Błąd parsowania URI ${photo.uri} podczas filtrowania zdjęć do wyświetlenia.", e)
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
     * Logika usuwania aktualizuje stan w ViewModelu.
     */
    private fun addPhotoThumbnail(photoUri: Uri, container: LinearLayout, photoType: PhotoType, isEditing: Boolean) {
        if (!isEditing) return
        if (photoUriToViewMapEdit.containsKey(photoUri)) {
            return
        }

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
                val wasAddedInThisSession = (if (photoType == PhotoType.CLIENT) editReceiptViewModel.clientPhotosToAddUris.value else editReceiptViewModel.transactionPhotosToAddUris.value)
                    ?.contains(photoUri) ?: false

                if (wasAddedInThisSession) {
                    editReceiptViewModel.removePhotoToAdd(photoUri, photoType)
                    Log.d("EditReceiptActivity", "Usunięto nowo dodane zdjęcie (przed zapisem): $photoUri")
                } else {
                    editReceiptViewModel.addPhotoToRemove(photoUri)
                    Log.d("EditReceiptActivity", "Oznaczono do usunięcia istniejące zdjęcie: $photoUri")
                }
            }
            photoUriToViewMapEdit[photoUri] = thumbnailView
            container.addView(thumbnailView)

        } catch (e: Exception) {
            Log.e("EditReceiptActivity", "Błąd ładowania miniatury $photoUri", e)
        }
    }


    /** Aktualizuje widoczność kontenerów i tytułów sekcji zdjęć. */
    private fun updatePhotoSectionVisibility(photoType: PhotoType, isEditing: Boolean) {
        val loadedPhotos = if (photoType == PhotoType.CLIENT) loadedClientPhotos else loadedTransactionPhotos
        val photosToAddUrisSet = (if (photoType == PhotoType.CLIENT) editReceiptViewModel.clientPhotosToAddUris.value else editReceiptViewModel.transactionPhotosToAddUris.value)?.map { it.toString() }?.toSet() ?: emptySet()
        val photosToRemoveUrisSet = editReceiptViewModel.photosToRemoveUris.value?.map { it.toString() }?.toSet() ?: emptySet()

        val photosExist = loadedPhotos.any { it.uri !in photosToRemoveUrisSet } || photosToAddUrisSet.isNotEmpty()

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
     * Aktualizuje widoczność i stan edytowalności elementów UI na podstawie stanu `isEditMode` z ViewModelu.
     * Uwzględnia widoczność nowego przycisku `addReceiptsToClientButton`.
     */
    private fun updateUiMode(isEditing: Boolean) {
        // Stan pól jest aktualizowany przez obserwatorów, tutaj tylko włączamy/wyłączamy edytowalność
        editReceiptStoreNumberEditText.isEnabled = isEditing
        editReceiptNumberEditText.isEnabled = isEditing
        editReceiptDateEditText.isEnabled = isEditing
        editCashRegisterNumberEditText.isEnabled = isEditing
        editVerificationDateEditText.isEnabled = isEditing && !(editReceiptViewModel.isVerificationDateTodayState.value ?: false)
        editVerificationDateTodayCheckBox.isEnabled = isEditing
        editVerificationDateTodayCheckBox.visibility = if (isEditing) View.VISIBLE else View.GONE
        editClientDescriptionEditText.isEnabled = isEditing
        editClientAppNumberEditText.isEnabled = isEditing
        editAmoditNumberEditText.isEnabled = isEditing

        saveReceiptButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteReceiptButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteClientButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        editModeImageButton.visibility = if (isEditing) View.GONE else View.VISIBLE

        // Widoczność sekcji na podstawie danych i trybu
        val hasCashRegisterNumber = editReceiptViewModel.cashRegisterNumberState.value?.isNotEmpty() ?: false
        editCashRegisterNumberLayout.visibility = if (isEditing || hasCashRegisterNumber) View.VISIBLE else View.GONE

        val hasVerificationDate = editReceiptViewModel.verificationDateState.value?.isNotEmpty() ?: false
        editVerificationSectionLayout.visibility = if (isEditing || hasVerificationDate) View.VISIBLE else View.GONE
        verificationSectionTitleEdit.visibility = if (isEditing && editVerificationSectionLayout.visibility == View.VISIBLE) View.VISIBLE else View.GONE
        verificationSectionTitleView.visibility = if (!isEditing && hasVerificationDate) View.VISIBLE else View.GONE

        val hasDescription = editReceiptViewModel.clientDescriptionState.value?.isNotEmpty() ?: false
        editDescriptionLayout.visibility = if (isEditing || hasDescription) View.VISIBLE else View.GONE

        val hasAppNumber = editReceiptViewModel.clientAppNumberState.value?.isNotEmpty() ?: false
        editAppNumberLayout.visibility = if (isEditing || hasAppNumber) View.VISIBLE else View.GONE

        val hasAmoditNumber = editReceiptViewModel.amoditNumberState.value?.isNotEmpty() ?: false
        editAmoditNumberLayout.visibility = if (isEditing || hasAmoditNumber) View.VISIBLE else View.GONE

        clientDataSectionTitleEdit.visibility = if (isEditing) View.VISIBLE else View.GONE
        clientDataSectionTitleView.visibility = if (!isEditing) View.VISIBLE else View.GONE

        // Aktualizacja UI zdjęć dla obu typów
        updatePhotoUiIfNeeded(PhotoType.CLIENT)
        updatePhotoUiIfNeeded(PhotoType.TRANSACTION)

        // Przyciski nawigacyjne i nowy przycisk
        showClientReceiptsButton.visibility = if (!isEditing && navigationContext == "STORE_LIST") View.VISIBLE else View.GONE
        addReceiptsToClientButton.visibility = if (!isEditing) View.VISIBLE else View.GONE // Nowy przycisk
        showStoreReceiptsButton.visibility = if (!isEditing && navigationContext == "CLIENT_LIST") View.VISIBLE else View.GONE
    }


    /**
     * Zbiera dane z formularza (teraz z ViewModelu) i wywołuje metodę zapisu zmian w ViewModelu.
     * Dodano walidację dat przed wywołaniem zapisu w ViewModelu.
     */
    private fun saveChanges() {
        val storeNumberString = editReceiptViewModel.storeNumberState.value ?: ""
        val receiptNumber = editReceiptViewModel.receiptNumberState.value ?: ""
        val receiptDateString = editReceiptViewModel.receiptDateState.value ?: ""
        val verificationDateString = editReceiptViewModel.verificationDateState.value ?: ""

        if (storeNumberString.isBlank() || receiptNumber.isBlank() || receiptDateString.isBlank()) {
            Toast.makeText(this, R.string.error_fill_required_edit_fields, Toast.LENGTH_LONG).show()
            return
        }

        if (!isValidDate(receiptDateString)) {
            Toast.makeText(this, R.string.error_invalid_receipt_date_format, Toast.LENGTH_LONG).show()
            return
        }

        if (verificationDateString.isNotEmpty() && !isValidDate(verificationDateString)) {
            Toast.makeText(this, R.string.error_invalid_verification_date_format, Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            val result = editReceiptViewModel.updateReceiptAndClient(receiptId)
            handleEditResult(result)
        }
    }

    /**
     * Sprawdza, czy podany ciąg znaków reprezentuje poprawną datę w formacie DD-MM-YYYY.
     */
    private fun isValidDate(dateStr: String): Boolean {
        if (dateStr.length != 10) return false
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false
        return try {
            dateFormat.parse(dateStr)
            true
        } catch (e: ParseException) {
            false
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
     * Wywołuje metodę usuwania paragonu w ViewModelu.
     */
    private fun deleteReceipt() {
        lifecycleScope.launch {
            val currentReceipt = editReceiptViewModel.getReceiptDataFlow(receiptId)
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
     * Wywołuje metodę usuwania klienta w ViewModelu.
     */
    private fun deleteClient() {
        val clientIdToDelete = currentClientId ?: return

        lifecycleScope.launch {
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
                finishAffinity()
                startActivity(Intent(this@EditReceiptActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
        }
    }


    /**
     * Konfiguruje działanie CheckBoxa "Dzisiaj" dla daty weryfikacji.
     * Aktualizuje stan `isVerificationDateTodayState` w ViewModelu.
     */
    private fun setupVerificationDateCheckBox() {
        editVerificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (editReceiptViewModel.isEditMode.value == true) {
                editReceiptViewModel.setIsVerificationDateToday(isChecked)
                if (isChecked) {
                    val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                    editReceiptViewModel.setVerificationDate(currentDate)
                }
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

