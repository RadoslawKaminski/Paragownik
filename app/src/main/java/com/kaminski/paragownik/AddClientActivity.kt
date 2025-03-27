package com.kaminski.paragownik

import android.net.Uri // Potrzebne dla URI zdjęcia
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton // Potrzebne dla ImageButton
import android.widget.ImageView // Potrzebne dla ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts // Potrzebne dla launchera
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri // Do konwersji File na Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kaminski.paragownik.viewmodel.AddClientViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel
import kotlinx.coroutines.launch
import java.io.File // Potrzebne do operacji na plikach
import java.io.FileOutputStream // Potrzebne do zapisu pliku
import java.io.IOException // Potrzebne do obsługi błędów IO
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID // Do generowania unikalnych nazw plików

/**
 * Aktywność odpowiedzialna za dodawanie nowego klienta wraz z jednym lub wieloma paragonami.
 * Umożliwia wprowadzenie danych pierwszego paragonu, danych klienta (w tym zdjęcia)
 * oraz dynamiczne dodawanie kolejnych paragonów z innych drogerii.
 */
class AddClientActivity : AppCompatActivity() {

    // --- Widoki UI ---
    private lateinit var storeNumberEditTextFirstReceipt: EditText
    private lateinit var receiptNumberEditText: EditText
    private lateinit var receiptDateEditText: EditText
    private lateinit var verificationDateEditText: EditText
    private lateinit var verificationDateTodayCheckBox: CheckBox
    private lateinit var clientDescriptionEditText: EditText
    private lateinit var clientAppNumberEditText: EditText
    private lateinit var amoditNumberEditText: EditText
    private lateinit var clientPhotoImageView: ImageView // Widok miniatury zdjęcia
    private lateinit var addChangePhotoButton: ImageButton // Przycisk dodawania/zmiany zdjęcia
    private lateinit var addClientButton: Button
    private lateinit var addAdditionalReceiptButton: Button
    private lateinit var receiptsContainer: LinearLayout

    // --- ViewModels ---
    private lateinit var addClientViewModel: AddClientViewModel
    private lateinit var storeViewModel: StoreViewModel

    // --- Dane pomocnicze ---
    private val receiptFieldsList = ArrayList<ReceiptFields>()
    private var storeIdFromIntent: Long = -1L
    // Przechowuje URI skopiowanego zdjęcia w pamięci wewnętrznej.
    private var selectedPhotoUri: Uri? = null

    /**
     * Struktura pomocnicza do przechowywania referencji do pól EditText
     * dla dynamicznie dodawanych sekcji paragonów.
     */
    private data class ReceiptFields(
        val storeNumberEditText: EditText?,
        val receiptNumberEditText: EditText,
        val receiptDateEditText: EditText
    )

    /**
     * Struktura danych przekazywana do [AddClientViewModel.addClientWithReceiptsTransactionally].
     */
    data class ReceiptData(
        val storeNumber: String,
        val receiptNumber: String,
        val receiptDate: String
    )

    // Launcher do wybierania zdjęcia z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri -> // Zmieniono nazwę zmiennej dla jasności
            Log.d("AddClientActivity", "Otrzymano tymczasowe URI: $sourceUri")
            // Skopiuj obraz do pamięci wewnętrznej i uzyskaj nowe, trwałe URI
            val destinationUri = copyImageToInternalStorage(sourceUri)

            destinationUri?.let { finalUri ->
                selectedPhotoUri = finalUri // Zapisz TRWAŁE URI
                clientPhotoImageView.setImageURI(finalUri) // Wyświetl miniaturę z trwałego URI
                Log.d("AddClientActivity", "Ustawiono trwałe URI zdjęcia: $finalUri")
            }
            // Jeśli kopiowanie się nie powiodło, selectedPhotoUri pozostanie null lub poprzednią wartością
        } ?: run {
            Log.d("AddClientActivity", "Nie wybrano zdjęcia.")
        }
    }

    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_client)

        initializeViews()
        initializeViewModels()
        handleIntentExtras()
        setupDateEditText(receiptDateEditText)
        setupDateEditText(verificationDateEditText)
        receiptFieldsList.add(ReceiptFields(null, receiptNumberEditText, receiptDateEditText))
        setupListeners()
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI.
     */
    private fun initializeViews() {
        storeNumberEditTextFirstReceipt = findViewById(R.id.receiptStoreNumberEditText)
        receiptNumberEditText = findViewById(R.id.receiptNumberEditText)
        receiptDateEditText = findViewById(R.id.receiptDateEditText)
        verificationDateEditText = findViewById(R.id.verificationDateEditText)
        verificationDateTodayCheckBox = findViewById(R.id.verificationDateTodayCheckBox)
        clientDescriptionEditText = findViewById(R.id.clientDescriptionEditText)
        clientAppNumberEditText = findViewById(R.id.clientAppNumberEditText)
        amoditNumberEditText = findViewById(R.id.amoditNumberEditText)
        clientPhotoImageView = findViewById(R.id.clientPhotoImageView) // Inicjalizacja ImageView
        addChangePhotoButton = findViewById(R.id.addChangePhotoButton) // Inicjalizacja ImageButton
        addClientButton = findViewById(R.id.addClientButton)
        addAdditionalReceiptButton = findViewById(R.id.addAdditionalReceiptButton)
        receiptsContainer = findViewById(R.id.receiptsContainer)
    }

    /**
     * Inicjalizuje ViewModels.
     */
    private fun initializeViewModels() {
        addClientViewModel = ViewModelProvider(this).get(AddClientViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)
    }

    /**
     * Sprawdza i obsługuje ID sklepu przekazane w Intencie.
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
     * Ustawia listenery dla interaktywnych elementów UI.
     */
    private fun setupListeners() {
        // Listener dla checkboxa "Dzisiaj"
        verificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                verificationDateEditText.setText(currentDate)
                verificationDateEditText.isEnabled = false
            } else {
                verificationDateEditText.isEnabled = true
            }
        }

        // Listener dla przycisku dodawania kolejnego paragonu
        addAdditionalReceiptButton.setOnClickListener {
            addNewReceiptFields()
        }

        // Listener dla głównego przycisku zapisu
        addClientButton.setOnClickListener {
            saveClientAndReceipts()
        }

        // Listener dla kliknięcia przycisku dodawania/zmiany zdjęcia
        addChangePhotoButton.setOnClickListener {
            // Uruchom systemowy selektor plików/galerii do wyboru obrazu
            pickImageLauncher.launch("image/*")
        }
    }

    /**
     * Główna funkcja zapisu klienta i paragonów.
     */
    private fun saveClientAndReceipts() {
        val clientDescription = clientDescriptionEditText.text.toString().trim()
        val clientAppNumber = clientAppNumberEditText.text.toString().trim()
        val amoditNumber = amoditNumberEditText.text.toString().trim()
        val verificationDateString = verificationDateEditText.text.toString().trim()

        val receiptsToAdd = mutableListOf<ReceiptData>()
        var hasEmptyFields = false

        // Przetwarzanie pierwszego paragonu
        val firstStoreNumber = storeNumberEditTextFirstReceipt.text.toString().trim()
        val firstReceiptNumber = receiptNumberEditText.text.toString().trim()
        val firstReceiptDate = receiptDateEditText.text.toString().trim()

        if (firstStoreNumber.isEmpty() || firstReceiptNumber.isEmpty() || firstReceiptDate.isEmpty()) {
            hasEmptyFields = true
        } else if (!isValidDate(firstReceiptDate)) {
            Toast.makeText(this, R.string.error_invalid_receipt_date_format, Toast.LENGTH_LONG).show()
            return
        } else {
            receiptsToAdd.add(ReceiptData(firstStoreNumber, firstReceiptNumber, firstReceiptDate))
        }

        // Przetwarzanie dodatkowych paragonów
        if (!hasEmptyFields) {
            for (receiptFields in receiptFieldsList.drop(1)) {
                val storeNumberEditText = receiptFields.storeNumberEditText
                if (storeNumberEditText == null) {
                    Log.e("AddClientActivity", "Błąd krytyczny: storeNumberEditText jest null w pętli dodatkowych paragonów!")
                    Toast.makeText(this, "Wystąpił błąd wewnętrzny.", Toast.LENGTH_LONG).show()
                    return
                }
                val storeNumber = storeNumberEditText.text.toString().trim()
                val receiptNumber = receiptFields.receiptNumberEditText.text.toString().trim()
                val receiptDate = receiptFields.receiptDateEditText.text.toString().trim()

                if (storeNumber.isEmpty() || receiptNumber.isEmpty() || receiptDate.isEmpty()) {
                    hasEmptyFields = true
                    break
                } else if (!isValidDate(receiptDate)) {
                    Toast.makeText(this, R.string.error_invalid_additional_receipt_date_format, Toast.LENGTH_LONG).show()
                    return
                } else {
                    receiptsToAdd.add(ReceiptData(storeNumber, receiptNumber, receiptDate))
                }
            }
        }

        // Końcowa walidacja
        if (hasEmptyFields) {
            Toast.makeText(this, R.string.error_fill_required_receipt_fields, Toast.LENGTH_LONG).show()
            return
        }
        if (receiptsToAdd.isEmpty()) {
            Toast.makeText(this, R.string.error_add_at_least_one_receipt, Toast.LENGTH_SHORT).show()
            return
        }

        // Wywołanie metody ViewModelu
        lifecycleScope.launch {
            val result = addClientViewModel.addClientWithReceiptsTransactionally(
                clientDescription = clientDescription.takeIf { it.isNotEmpty() },
                clientAppNumber = clientAppNumber.takeIf { it.isNotEmpty() },
                amoditNumber = amoditNumber.takeIf { it.isNotEmpty() },
                // Przekaż URI jako String, jeśli zostało wybrane (teraz to trwałe URI)
                photoUri = selectedPhotoUri?.toString(),
                receiptsData = receiptsToAdd,
                verificationDateString = verificationDateString.takeIf { it.isNotEmpty() }
            )
            handleSaveResult(result)
        }
    }

    /**
     * Sprawdza poprawność formatu daty (DD-MM-YYYY).
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
     * Obsługuje wynik operacji zapisu zwrócony przez ViewModel.
     */
    private fun handleSaveResult(result: AddClientViewModel.AddResult) {
        val messageResId = when (result) {
            AddClientViewModel.AddResult.SUCCESS -> R.string.save_success_message
            AddClientViewModel.AddResult.ERROR_DATE_FORMAT -> R.string.error_invalid_date_format
            AddClientViewModel.AddResult.ERROR_DUPLICATE_RECEIPT -> R.string.error_duplicate_receipt
            AddClientViewModel.AddResult.ERROR_STORE_NUMBER_MISSING -> R.string.error_store_number_missing
            AddClientViewModel.AddResult.ERROR_DATABASE -> R.string.error_database
            AddClientViewModel.AddResult.ERROR_UNKNOWN -> R.string.error_unknown
        }
        val message = getString(messageResId)

        Toast.makeText(this@AddClientActivity, message, if (result == AddClientViewModel.AddResult.SUCCESS) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()

        if (result == AddClientViewModel.AddResult.SUCCESS) {
            finish() // Zamknij aktywność po sukcesie
        }
    }


    /**
     * Dodaje dynamicznie nowy zestaw pól dla kolejnego paragonu.
     */
    private fun addNewReceiptFields() {
        val inflater = LayoutInflater.from(this)
        val receiptFieldsView = inflater.inflate(R.layout.additional_receipt_fields, receiptsContainer, false)

        val storeNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalStoreNumberEditText)
        val receiptNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptNumberEditText)
        val receiptDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptDateEditText)
        val removeReceiptButton = receiptFieldsView.findViewById<ImageButton>(R.id.removeReceiptButton)

        setupDateEditText(receiptDateEditText)

        val newReceiptFields = ReceiptFields(
            storeNumberEditText,
            receiptNumberEditText,
            receiptDateEditText
        )
        receiptFieldsList.add(newReceiptFields)
        receiptsContainer.addView(receiptFieldsView)

        removeReceiptButton.setOnClickListener {
            receiptsContainer.removeView(receiptFieldsView)
            receiptFieldsList.remove(newReceiptFields)
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
     * Kopiuje obraz z podanego źródłowego URI do wewnętrznego magazynu aplikacji.
     * @param sourceUri URI obrazu źródłowego (np. z galerii).
     * @return URI skopiowanego pliku w magazynie wewnętrznym lub null w przypadku błędu.
     */
    private fun copyImageToInternalStorage(sourceUri: Uri): Uri? {
        return try {
            // Otwórz strumień wejściowy dla źródłowego URI
            val inputStream = contentResolver.openInputStream(sourceUri) ?: return null

            // Utwórz katalog 'images' w wewnętrznym magazynie plików aplikacji, jeśli nie istnieje
            val imagesDir = File(filesDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            // Utwórz unikalną nazwę pliku dla kopii (np. używając UUID)
            val uniqueFileName = "${UUID.randomUUID()}.jpg"
            val destinationFile = File(imagesDir, uniqueFileName)

            // Otwórz strumień wyjściowy do pliku docelowego
            val outputStream = FileOutputStream(destinationFile)

            // Skopiuj dane ze strumienia wejściowego do wyjściowego
            // Użycie 'use' zapewnia automatyczne zamknięcie strumieni
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            Log.d("AddClientActivity", "Skopiowano zdjęcie do: ${destinationFile.absolutePath}")
            // Zwróć URI dla nowo utworzonego pliku
            destinationFile.toUri() // Używamy toUri() dla plików wewnętrznych

        } catch (e: IOException) {
            Log.e("AddClientActivity", "Błąd podczas kopiowania zdjęcia", e)
            Toast.makeText(this, R.string.error_copying_image, Toast.LENGTH_SHORT).show()
            null // Zwróć null w przypadku błędu
        } catch (e: SecurityException) {
            // Ten błąd nie powinien tu wystąpić, bo mamy tymczasowe uprawnienie, ale dla pewności
            Log.e("AddClientActivity", "Brak uprawnień do odczytu URI źródłowego (SecurityException)", e)
            Toast.makeText(this, "Brak uprawnień do odczytu zdjęcia.", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
