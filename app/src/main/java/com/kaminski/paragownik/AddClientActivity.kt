package com.kaminski.paragownik

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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.viewmodel.AddClientViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.Calendar

/**
 * Aktywność odpowiedzialna za dodawanie nowego klienta wraz z jednym lub wieloma paragonami i zdjęciami.
 * Używa Glide do wyświetlania miniatur.
 */
class AddClientActivity : AppCompatActivity() {

    // --- Widoki UI ---
    private lateinit var storeNumberEditTextFirstReceipt: EditText
    private lateinit var receiptNumberEditText: EditText
    private lateinit var receiptDateEditText: EditText
    private lateinit var cashRegisterNumberEditText: EditText // Pole na numer kasy dla pierwszego paragonu
    private lateinit var verificationDateEditText: EditText // Data weryfikacji dla pierwszego paragonu
    private lateinit var verificationDateTodayCheckBox: CheckBox // Checkbox "Dzisiaj" dla pierwszego paragonu
    private lateinit var clientDescriptionEditText: EditText
    private lateinit var clientAppNumberEditText: EditText
    private lateinit var amoditNumberEditText: EditText
    private lateinit var addClientButton: Button
    private lateinit var addAdditionalReceiptButton: Button
    private lateinit var receiptsContainer: LinearLayout
    private lateinit var clientPhotosContainer: LinearLayout
    private lateinit var transactionPhotosContainer: LinearLayout
    private lateinit var addClientPhotoButton: Button
    private lateinit var addTransactionPhotoButton: Button


    // --- ViewModels ---
    private lateinit var addClientViewModel: AddClientViewModel
    private lateinit var storeViewModel: StoreViewModel

    // --- Dane pomocnicze ---
    private val receiptFieldsList = ArrayList<ReceiptFields>()
    private var storeIdFromIntent: Long = -1L
    private val clientPhotoUris = mutableListOf<Uri>()
    private val transactionPhotoUris = mutableListOf<Uri>()
    private val photoUriToViewMap = mutableMapOf<Uri, View>()
    private var currentPhotoTypeToAdd: PhotoType? = null


    /**
     * Struktura pomocnicza do przechowywania referencji do pól EditText
     * dla dynamicznie dodawanych sekcji paragonów.
     */
    private data class ReceiptFields(
        val storeNumberEditText: EditText?,
        val receiptNumberEditText: EditText,
        val receiptDateEditText: EditText,
        val cashRegisterNumberEditText: EditText?, // Dodano pole dla numeru kasy
        val verificationDateEditText: EditText?,
        val verificationDateTodayCheckBox: CheckBox?
    )

    /**
     * Struktura danych przekazywana do [AddClientViewModel.addClientWithReceiptsTransactionally].
     */
    data class ReceiptData(
        val storeNumber: String,
        val receiptNumber: String,
        val receiptDate: String,
        val cashRegisterNumber: String?, // Dodano pole dla numeru kasy
        val verificationDateString: String? // Opcjonalna data weryfikacji jako String
    )

    // Launcher ActivityResult do wybierania obrazu z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            Log.d("AddClientActivity", "Otrzymano tymczasowe URI: $sourceUri")
            // Kopiuje wybrany obraz do pamięci wewnętrznej aplikacji
            val destinationUri = copyImageToInternalStorage(sourceUri)
            destinationUri?.let { finalUri ->
                // Dodaje URI do odpowiedniej listy i wyświetla miniaturę
                when (currentPhotoTypeToAdd) {
                    PhotoType.CLIENT -> {
                        if (!clientPhotoUris.contains(finalUri)) { // Unikaj duplikatów
                            clientPhotoUris.add(finalUri)
                            addPhotoThumbnail(finalUri, clientPhotosContainer, PhotoType.CLIENT)
                            Log.d("AddClientActivity", "Dodano zdjęcie klienta: $finalUri")
                        } else {
                            Log.d("AddClientActivity", "Zdjęcie klienta $finalUri już istnieje.")
                            Toast.makeText(this, R.string.photo_already_added, Toast.LENGTH_SHORT).show()
                        }
                    }
                    PhotoType.TRANSACTION -> {
                         if (!transactionPhotoUris.contains(finalUri)) { // Unikaj duplikatów
                            transactionPhotoUris.add(finalUri)
                            addPhotoThumbnail(finalUri, transactionPhotosContainer, PhotoType.TRANSACTION)
                            Log.d("AddClientActivity", "Dodano zdjęcie transakcji: $finalUri")
                        } else {
                            Log.d("AddClientActivity", "Zdjęcie transakcji $finalUri już istnieje.")
                            Toast.makeText(this, R.string.photo_already_added, Toast.LENGTH_SHORT).show()
                        }
                    }
                    null -> {
                        Log.w("AddClientActivity", "Nieznany typ zdjęcia do dodania (currentPhotoTypeToAdd is null)")
                    }
                }
            }
        } ?: run {
            Log.d("AddClientActivity", "Nie wybrano zdjęcia.")
        }
        // Resetuje typ po zakończeniu operacji
        currentPhotoTypeToAdd = null
    }


    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_client)

        initializeViews()
        initializeViewModels()
        handleIntentExtras()
        setupDateEditText(receiptDateEditText)
        setupDateEditText(verificationDateEditText)
        // Dodajemy pola pierwszego paragonu do listy
        receiptFieldsList.add(ReceiptFields(null, receiptNumberEditText, receiptDateEditText, cashRegisterNumberEditText, verificationDateEditText, verificationDateTodayCheckBox))
        setupListeners()
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI z layoutu.
     */
    private fun initializeViews() {
        storeNumberEditTextFirstReceipt = findViewById(R.id.receiptStoreNumberEditText)
        receiptNumberEditText = findViewById(R.id.receiptNumberEditText)
        receiptDateEditText = findViewById(R.id.receiptDateEditText)
        cashRegisterNumberEditText = findViewById(R.id.cashRegisterNumberEditText) // Inicjalizacja pola numeru kasy
        verificationDateEditText = findViewById(R.id.verificationDateEditText)
        verificationDateTodayCheckBox = findViewById(R.id.verificationDateTodayCheckBox)
        clientDescriptionEditText = findViewById(R.id.clientDescriptionEditText)
        clientAppNumberEditText = findViewById(R.id.clientAppNumberEditText)
        amoditNumberEditText = findViewById(R.id.amoditNumberEditText)
        addClientButton = findViewById(R.id.addClientButton)
        addAdditionalReceiptButton = findViewById(R.id.addAdditionalReceiptButton)
        receiptsContainer = findViewById(R.id.receiptsContainer)
        clientPhotosContainer = findViewById(R.id.clientPhotosContainer)
        transactionPhotosContainer = findViewById(R.id.transactionPhotosContainer)
        addClientPhotoButton = findViewById(R.id.addClientPhotoButton)
        addTransactionPhotoButton = findViewById(R.id.addTransactionPhotoButton)
    }

    /**
     * Inicjalizuje ViewModels używane w tej aktywności.
     */
    private fun initializeViewModels() {
        addClientViewModel = ViewModelProvider(this).get(AddClientViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)
    }

    /**
     * Sprawdza, czy w Intencie przekazano ID sklepu i odpowiednio konfiguruje pole numeru sklepu.
     */
    private fun handleIntentExtras() {
        if (intent.hasExtra("STORE_ID")) {
            storeIdFromIntent = intent.getLongExtra("STORE_ID", -1L)
            if (storeIdFromIntent != -1L) {
                storeNumberEditTextFirstReceipt.isEnabled = false
                lifecycleScope.launch {
                    val store = storeViewModel.getStoreById(storeIdFromIntent)
                    store?.let {
                        storeNumberEditTextFirstReceipt.setText(it.storeNumber)
                    }
                }
            }
        }
    }

    /**
     * Ustawia listenery dla interaktywnych elementów UI (przyciski, checkbox).
     */
    private fun setupListeners() {
        verificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                verificationDateEditText.setText(currentDate)
                verificationDateEditText.isEnabled = false
            } else {
                verificationDateEditText.isEnabled = true
            }
        }

        addAdditionalReceiptButton.setOnClickListener {
            addNewReceiptFields()
        }

        addClientButton.setOnClickListener {
            saveClientAndReceipts()
        }

        addClientPhotoButton.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.CLIENT
            pickImageLauncher.launch("image/*")
        }

        addTransactionPhotoButton.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.TRANSACTION
            pickImageLauncher.launch("image/*")
        }
    }


    /**
     * Główna funkcja zbierająca dane z formularza, walidująca je i wywołująca zapis w ViewModelu.
     */
    private fun saveClientAndReceipts() {
        val clientDescription = clientDescriptionEditText.text.toString().trim()
        val clientAppNumber = clientAppNumberEditText.text.toString().trim()
        val amoditNumber = amoditNumberEditText.text.toString().trim()
        val firstVerificationDateString = verificationDateEditText.text.toString().trim()
        val firstCashRegisterNumber = cashRegisterNumberEditText.text.toString().trim() // Pobranie numeru kasy

        val receiptsToAdd = mutableListOf<ReceiptData>()
        var hasEmptyFields = false

        val firstStoreNumber = storeNumberEditTextFirstReceipt.text.toString().trim()
        val firstReceiptNumber = receiptNumberEditText.text.toString().trim()
        val firstReceiptDate = receiptDateEditText.text.toString().trim()

        if (firstStoreNumber.isEmpty() || firstReceiptNumber.isEmpty() || firstReceiptDate.isEmpty()) {
            hasEmptyFields = true
        } else if (!isValidDate(firstReceiptDate)) {
            Toast.makeText(this, R.string.error_invalid_receipt_date_format, Toast.LENGTH_LONG).show()
            return
        } else if (firstVerificationDateString.isNotEmpty() && !isValidDate(firstVerificationDateString)) {
            Toast.makeText(this, R.string.error_invalid_verification_date_format, Toast.LENGTH_LONG).show()
            return
        } else {
            receiptsToAdd.add(ReceiptData(
                firstStoreNumber,
                firstReceiptNumber,
                firstReceiptDate,
                firstCashRegisterNumber.takeIf { it.isNotEmpty() }, // Dodanie numeru kasy
                firstVerificationDateString.takeIf { it.isNotEmpty() }
            ))
        }

        if (!hasEmptyFields) {
            for (receiptFields in receiptFieldsList.drop(1)) { // Pomiń pierwszy element, bo już go mamy
                val storeNumberEditText = receiptFields.storeNumberEditText
                if (storeNumberEditText == null) {
                    Log.e("AddClientActivity", "Błąd krytyczny: storeNumberEditText jest null w pętli dodatkowych paragonów!")
                    Toast.makeText(this, "Wystąpił błąd wewnętrzny.", Toast.LENGTH_LONG).show()
                    return
                }
                val storeNumber = storeNumberEditText.text.toString().trim()
                val receiptNumber = receiptFields.receiptNumberEditText.text.toString().trim()
                val receiptDate = receiptFields.receiptDateEditText.text.toString().trim()
                val cashRegisterNumberEditText = receiptFields.cashRegisterNumberEditText // Pobranie pola numeru kasy
                val cashRegisterNumber = cashRegisterNumberEditText?.text?.toString()?.trim() ?: "" // Pobranie wartości
                val verificationDateEditText = receiptFields.verificationDateEditText
                val verificationDateString = verificationDateEditText?.text?.toString()?.trim() ?: ""

                if (storeNumber.isEmpty() || receiptNumber.isEmpty() || receiptDate.isEmpty()) {
                    hasEmptyFields = true
                    break
                } else if (!isValidDate(receiptDate)) {
                    Toast.makeText(this, R.string.error_invalid_additional_receipt_date_format, Toast.LENGTH_LONG).show()
                    return
                } else if (verificationDateString.isNotEmpty() && !isValidDate(verificationDateString)) {
                    Toast.makeText(this, R.string.error_invalid_additional_verification_date_format, Toast.LENGTH_LONG).show()
                    return
                } else {
                    receiptsToAdd.add(ReceiptData(
                        storeNumber,
                        receiptNumber,
                        receiptDate,
                        cashRegisterNumber.takeIf { it.isNotEmpty() }, // Dodanie numeru kasy
                        verificationDateString.takeIf { it.isNotEmpty() }
                    ))
                }
            }
        }

        if (hasEmptyFields) {
            Toast.makeText(this, R.string.error_fill_required_receipt_fields, Toast.LENGTH_LONG).show()
            return
        }
        if (receiptsToAdd.isEmpty()) {
            Toast.makeText(this, R.string.error_add_at_least_one_receipt, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = addClientViewModel.addClientWithReceiptsTransactionally(
                clientDescription = clientDescription.takeIf { it.isNotEmpty() },
                clientAppNumber = clientAppNumber.takeIf { it.isNotEmpty() },
                amoditNumber = amoditNumber.takeIf { it.isNotEmpty() },
                clientPhotoUris = clientPhotoUris.map { it.toString() },
                transactionPhotoUris = transactionPhotoUris.map { it.toString() },
                receiptsData = receiptsToAdd
            )
            handleSaveResult(result)
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
     * Obsługuje wynik operacji zapisu zwrócony przez ViewModel, wyświetlając odpowiedni komunikat Toast.
     * W przypadku sukcesu zamyka aktywność.
     */
    private fun handleSaveResult(result: AddClientViewModel.AddResult) {
        val messageResId = when (result) {
            AddClientViewModel.AddResult.SUCCESS -> R.string.save_success_message
            AddClientViewModel.AddResult.ERROR_DATE_FORMAT -> R.string.error_invalid_date_format
            AddClientViewModel.AddResult.ERROR_VERIFICATION_DATE_FORMAT -> R.string.error_invalid_verification_date_format
            AddClientViewModel.AddResult.ERROR_DUPLICATE_RECEIPT -> R.string.error_duplicate_receipt
            AddClientViewModel.AddResult.ERROR_STORE_NUMBER_MISSING -> R.string.error_store_number_missing
            AddClientViewModel.AddResult.ERROR_DATABASE -> R.string.error_database
            AddClientViewModel.AddResult.ERROR_UNKNOWN -> R.string.error_unknown
        }
        val message = getString(messageResId)

        Toast.makeText(this@AddClientActivity, message, if (result == AddClientViewModel.AddResult.SUCCESS) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()

        if (result == AddClientViewModel.AddResult.SUCCESS) {
            finish()
        }
    }


    /**
     * Dodaje dynamicznie nowy zestaw pól dla kolejnego paragonu do layoutu.
     */
    private fun addNewReceiptFields() {
        val inflater = LayoutInflater.from(this)
        val receiptFieldsView = inflater.inflate(R.layout.additional_receipt_fields, receiptsContainer, false)

        val storeNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalStoreNumberEditText)
        val receiptNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptNumberEditText)
        val receiptDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptDateEditText)
        val cashRegisterNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalCashRegisterNumberEditText) // Pobranie pola numeru kasy
        val removeReceiptButton = receiptFieldsView.findViewById<ImageButton>(R.id.removeReceiptButton)
        val verificationDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalVerificationDateEditText)
        val verificationDateTodayCheckBox = receiptFieldsView.findViewById<CheckBox>(R.id.additionalVerificationDateTodayCheckBox)

        setupDateEditText(receiptDateEditText)
        setupDateEditText(verificationDateEditText)

        verificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                verificationDateEditText.setText(currentDate)
                verificationDateEditText.isEnabled = false
            } else {
                verificationDateEditText.isEnabled = true
            }
        }

        val newReceiptFields = ReceiptFields(
            storeNumberEditText,
            receiptNumberEditText,
            receiptDateEditText,
            cashRegisterNumberEditText, // Dodanie pola do struktury
            verificationDateEditText,
            verificationDateTodayCheckBox
        )
        receiptFieldsList.add(newReceiptFields)
        receiptsContainer.addView(receiptFieldsView)

        removeReceiptButton.setOnClickListener {
            receiptsContainer.removeView(receiptFieldsView)
            receiptFieldsList.remove(newReceiptFields)
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
                if (len >= 1) {
                    formatted.append(digitsOnly.substring(0, minOf(len, 2))) // DD
                }
                if (len >= 3) {
                    formatted.append("-").append(digitsOnly.substring(2, minOf(len, 4))) // MM
                }
                if (len >= 5) {
                    formatted.append("-").append(digitsOnly.substring(4, minOf(len, 8))) // YYYY
                }
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
     * Dodaje widok miniatury zdjęcia do określonego kontenera [LinearLayout], używając Glide.
     * @param photoUri URI zdjęcia do wyświetlenia.
     * @param container LinearLayout, do którego zostanie dodana miniatura.
     * @param photoType Typ zdjęcia (potrzebny do logiki usuwania z listy).
     */
    private fun addPhotoThumbnail(photoUri: Uri, container: LinearLayout, photoType: PhotoType) {
        val inflater = LayoutInflater.from(this)
        val thumbnailView = inflater.inflate(R.layout.photo_thumbnail_item, container, false)
        val imageView = thumbnailView.findViewById<ImageView>(R.id.photoThumbnailImageView)
        val deleteButton = thumbnailView.findViewById<ImageButton>(R.id.deletePhotoButton)

        Glide.with(this)
            .load(photoUri)
            .placeholder(R.drawable.ic_photo_placeholder)
            .error(R.drawable.ic_photo_placeholder)
            .centerCrop()
            .into(imageView)

        deleteButton.visibility = View.VISIBLE

        deleteButton.setOnClickListener {
            container.removeView(thumbnailView)
            when (photoType) {
                PhotoType.CLIENT -> clientPhotoUris.remove(photoUri)
                PhotoType.TRANSACTION -> transactionPhotoUris.remove(photoUri)
            }
            photoUriToViewMap.remove(photoUri)
            Log.d("AddClientActivity", "Usunięto miniaturę i URI: $photoUri")
        }

        container.addView(thumbnailView)
        photoUriToViewMap[photoUri] = thumbnailView
    }


    /**
     * Kopiuje obraz z podanego źródłowego URI (np. z galerii) do wewnętrznego magazynu aplikacji.
     * Zwraca URI skopiowanego pliku lub null w przypadku błędu.
     * @param sourceUri URI obrazu źródłowego.
     * @return URI skopiowanego pliku w magazynie wewnętrznym lub null w przypadku błędu.
     */
    private fun copyImageToInternalStorage(sourceUri: Uri): Uri? {
        return try {
            val inputStream = contentResolver.openInputStream(sourceUri) ?: return null
            val imagesDir = File(filesDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            val uniqueFileName = "${UUID.randomUUID()}.jpg"
            val destinationFile = File(imagesDir, uniqueFileName)
            val outputStream = FileOutputStream(destinationFile)

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            Log.d("AddClientActivity", "Skopiowano zdjęcie do: ${destinationFile.absolutePath}")
            destinationFile.toUri()

        } catch (e: IOException) {
            Log.e("AddClientActivity", "Błąd podczas kopiowania zdjęcia", e)
            Toast.makeText(this, R.string.error_copying_image, Toast.LENGTH_SHORT).show()
            null
        } catch (e: SecurityException) {
            Log.e("AddClientActivity", "Brak uprawnień do odczytu URI źródłowego (SecurityException)", e)
            Toast.makeText(this, R.string.error_permission_read_photo, Toast.LENGTH_SHORT).show()
            null
        }
    }
}

