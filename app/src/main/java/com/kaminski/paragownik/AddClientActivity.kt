package com.kaminski.paragownik

import android.net.Uri // Potrzebne dla URI zdjęcia
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View // Dodano import
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
import com.kaminski.paragownik.data.PhotoType // Dodano import
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
import java.util.Calendar // Potrzebne dla Calendar.getInstance()

/**
 * Aktywność odpowiedzialna za dodawanie nowego klienta wraz z jednym lub wieloma paragonami i zdjęciami.
 */
class AddClientActivity : AppCompatActivity() {

    // --- Widoki UI ---
    private lateinit var storeNumberEditTextFirstReceipt: EditText
    private lateinit var receiptNumberEditText: EditText
    private lateinit var receiptDateEditText: EditText
    private lateinit var verificationDateEditText: EditText // Data weryfikacji dla pierwszego paragonu
    private lateinit var verificationDateTodayCheckBox: CheckBox // Checkbox "Dzisiaj" dla pierwszego paragonu
    private lateinit var clientDescriptionEditText: EditText
    private lateinit var clientAppNumberEditText: EditText
    private lateinit var amoditNumberEditText: EditText
    // private lateinit var clientPhotoImageView: ImageView // USUNIĘTO
    // private lateinit var addChangePhotoButton: ImageButton // USUNIĘTO
    private lateinit var addClientButton: Button
    private lateinit var addAdditionalReceiptButton: Button
    private lateinit var receiptsContainer: LinearLayout
    // Nowe widoki dla zdjęć
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
    // private var selectedPhotoUri: Uri? = null // USUNIĘTO

    // Nowe listy dla URI zdjęć
    private val clientPhotoUris = mutableListOf<Uri>()
    private val transactionPhotoUris = mutableListOf<Uri>()
    // Mapa do śledzenia widoku miniatury dla danego URI (potrzebne do usuwania)
    private val photoUriToViewMap = mutableMapOf<Uri, View>()
    // Zmienna do określenia typu dodawanego zdjęcia
    private var currentPhotoTypeToAdd: PhotoType? = null


    /**
     * Struktura pomocnicza do przechowywania referencji do pól EditText
     * dla dynamicznie dodawanych sekcji paragonów.
     */
    private data class ReceiptFields(
        val storeNumberEditText: EditText?, // Null dla pierwszego paragonu w liście
        val receiptNumberEditText: EditText,
        val receiptDateEditText: EditText,
        val verificationDateEditText: EditText?, // Null dla pierwszego paragonu w liście
        val verificationDateTodayCheckBox: CheckBox? // Null dla pierwszego paragonu w liście
    )

    /**
     * Struktura danych przekazywana do [AddClientViewModel.addClientWithReceiptsTransactionally].
     */
    data class ReceiptData(
        val storeNumber: String,
        val receiptNumber: String,
        val receiptDate: String,
        val verificationDateString: String? // Dodane opcjonalne pole
    )

    // Launcher do wybierania zdjęcia z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            Log.d("AddClientActivity", "Otrzymano tymczasowe URI: $sourceUri")
            val destinationUri = copyImageToInternalStorage(sourceUri)
            destinationUri?.let { finalUri ->
                // Dodaj URI do odpowiedniej listy i wyświetl miniaturę
                when (currentPhotoTypeToAdd) {
                    PhotoType.CLIENT -> {
                        if (!clientPhotoUris.contains(finalUri)) { // Unikaj duplikatów
                            clientPhotoUris.add(finalUri)
                            addPhotoThumbnail(finalUri, clientPhotosContainer, PhotoType.CLIENT)
                            Log.d("AddClientActivity", "Dodano zdjęcie klienta: $finalUri")
                        } else {
                            // Pusty else, aby zadowolić kompilator
                            Log.d("AddClientActivity", "Zdjęcie klienta $finalUri już istnieje.")
                            Toast.makeText(this, "To zdjęcie już zostało dodane.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    PhotoType.TRANSACTION -> {
                         if (!transactionPhotoUris.contains(finalUri)) { // Unikaj duplikatów
                            transactionPhotoUris.add(finalUri)
                            addPhotoThumbnail(finalUri, transactionPhotosContainer, PhotoType.TRANSACTION)
                            Log.d("AddClientActivity", "Dodano zdjęcie transakcji: $finalUri")
                        } else {
                            // Pusty else, aby zadowolić kompilator
                            Log.d("AddClientActivity", "Zdjęcie transakcji $finalUri już istnieje.")
                            Toast.makeText(this, "To zdjęcie już zostało dodane.", Toast.LENGTH_SHORT).show()
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
        // Zresetuj typ po zakończeniu operacji
        currentPhotoTypeToAdd = null
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
        setupDateEditText(verificationDateEditText) // Formatowanie dla daty weryfikacji pierwszego paragonu
        // Dodajemy pierwszy paragon do listy, pola weryfikacji są obsługiwane oddzielnie
        receiptFieldsList.add(ReceiptFields(null, receiptNumberEditText, receiptDateEditText, null, null))
        setupListeners()
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI.
     */
    private fun initializeViews() {
        storeNumberEditTextFirstReceipt = findViewById(R.id.receiptStoreNumberEditText)
        receiptNumberEditText = findViewById(R.id.receiptNumberEditText)
        receiptDateEditText = findViewById(R.id.receiptDateEditText)
        verificationDateEditText = findViewById(R.id.verificationDateEditText) // Pierwszy paragon
        verificationDateTodayCheckBox = findViewById(R.id.verificationDateTodayCheckBox) // Pierwszy paragon
        clientDescriptionEditText = findViewById(R.id.clientDescriptionEditText)
        clientAppNumberEditText = findViewById(R.id.clientAppNumberEditText)
        amoditNumberEditText = findViewById(R.id.amoditNumberEditText)
        // clientPhotoImageView = findViewById(R.id.clientPhotoImageView) // USUNIĘTO
        // addChangePhotoButton = findViewById(R.id.addChangePhotoButton) // USUNIĘTO
        addClientButton = findViewById(R.id.addClientButton)
        addAdditionalReceiptButton = findViewById(R.id.addAdditionalReceiptButton)
        receiptsContainer = findViewById(R.id.receiptsContainer)
        // Inicjalizacja nowych widoków zdjęć
        clientPhotosContainer = findViewById(R.id.clientPhotosContainer)
        transactionPhotosContainer = findViewById(R.id.transactionPhotosContainer)
        addClientPhotoButton = findViewById(R.id.addClientPhotoButton)
        addTransactionPhotoButton = findViewById(R.id.addTransactionPhotoButton)
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
        // Listener dla checkboxa "Dzisiaj" PIERWSZEGO paragonu
        verificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
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

        // Listener dla przycisku dodawania zdjęcia KLIENTA
        addClientPhotoButton.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.CLIENT // Ustaw typ przed uruchomieniem launchera
            pickImageLauncher.launch("image/*")
        }

        // Listener dla przycisku dodawania zdjęcia TRANSAKCJI
        addTransactionPhotoButton.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.TRANSACTION // Ustaw typ przed uruchomieniem launchera
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
        val firstVerificationDateString = verificationDateEditText.text.toString().trim() // Pobierz datę weryfikacji dla pierwszego paragonu

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
        } else if (firstVerificationDateString.isNotEmpty() && !isValidDate(firstVerificationDateString)) { // Walidacja daty weryfikacji pierwszego paragonu
            Toast.makeText(this, R.string.error_invalid_verification_date_format, Toast.LENGTH_LONG).show()
            return
        } else {
            receiptsToAdd.add(ReceiptData(firstStoreNumber, firstReceiptNumber, firstReceiptDate, firstVerificationDateString.takeIf { it.isNotEmpty() })) // Dodano datę weryfikacji
        }

        // Przetwarzanie dodatkowych paragonów
        if (!hasEmptyFields) {
            for (receiptFields in receiptFieldsList.drop(1)) { // Pomiń pierwszy paragon, bo już przetworzony
                val storeNumberEditText = receiptFields.storeNumberEditText
                if (storeNumberEditText == null) {
                    Log.e("AddClientActivity", "Błąd krytyczny: storeNumberEditText jest null w pętli dodatkowych paragonów!")
                    Toast.makeText(this, "Wystąpił błąd wewnętrzny.", Toast.LENGTH_LONG).show()
                    return
                }
                val storeNumber = storeNumberEditText.text.toString().trim()
                val receiptNumber = receiptFields.receiptNumberEditText.text.toString().trim()
                val receiptDate = receiptFields.receiptDateEditText.text.toString().trim()
                val verificationDateEditText = receiptFields.verificationDateEditText // Pobierz pole daty weryfikacji
                val verificationDateString = verificationDateEditText?.text?.toString()?.trim() ?: "" // Pobierz datę weryfikacji

                if (storeNumber.isEmpty() || receiptNumber.isEmpty() || receiptDate.isEmpty()) {
                    hasEmptyFields = true
                    break
                } else if (!isValidDate(receiptDate)) {
                    Toast.makeText(this, R.string.error_invalid_additional_receipt_date_format, Toast.LENGTH_LONG).show()
                    return
                } else if (verificationDateString.isNotEmpty() && !isValidDate(verificationDateString)) { // Walidacja daty weryfikacji dodatkowego paragonu
                    Toast.makeText(this, R.string.error_invalid_additional_verification_date_format, Toast.LENGTH_LONG).show()
                    return
                } else {
                    receiptsToAdd.add(ReceiptData(storeNumber, receiptNumber, receiptDate, verificationDateString.takeIf { it.isNotEmpty() })) // Dodano datę weryfikacji
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
                // Przekaż listy URI jako Stringi
                clientPhotoUris = clientPhotoUris.map { it.toString() },
                transactionPhotoUris = transactionPhotoUris.map { it.toString() },
                receiptsData = receiptsToAdd
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
            AddClientViewModel.AddResult.ERROR_DATE_FORMAT -> R.string.error_invalid_date_format // Ogólny błąd formatu daty
            AddClientViewModel.AddResult.ERROR_VERIFICATION_DATE_FORMAT -> R.string.error_invalid_verification_date_format // Błąd formatu daty weryfikacji
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
        val verificationDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalVerificationDateEditText) // Nowe pole
        val verificationDateTodayCheckBox = receiptFieldsView.findViewById<CheckBox>(R.id.additionalVerificationDateTodayCheckBox) // Nowy checkbox

        setupDateEditText(receiptDateEditText)
        setupDateEditText(verificationDateEditText) // Ustaw formatowanie dla daty weryfikacji

        // Listener dla checkboxa "Dzisiaj" w dodatkowych polach
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
            verificationDateEditText, // Dodano
            verificationDateTodayCheckBox // Dodano
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
     * Dodaje widok miniatury zdjęcia do określonego kontenera.
     * @param photoUri URI zdjęcia do wyświetlenia.
     * @param container LinearLayout, do którego zostanie dodana miniatura.
     * @param photoType Typ zdjęcia (potrzebny do logiki usuwania).
     */
    private fun addPhotoThumbnail(photoUri: Uri, container: LinearLayout, photoType: PhotoType) {
        val inflater = LayoutInflater.from(this)
        val thumbnailView = inflater.inflate(R.layout.photo_thumbnail_item, container, false)
        val imageView = thumbnailView.findViewById<ImageView>(R.id.photoThumbnailImageView)
        val deleteButton = thumbnailView.findViewById<ImageButton>(R.id.deletePhotoButton)

        imageView.setImageURI(photoUri)
        deleteButton.visibility = View.VISIBLE // Pokaż przycisk usuwania w AddClientActivity

        deleteButton.setOnClickListener {
            // Usuń widok z kontenera
            container.removeView(thumbnailView)
            // Usuń URI z odpowiedniej listy
            when (photoType) {
                PhotoType.CLIENT -> clientPhotoUris.remove(photoUri)
                PhotoType.TRANSACTION -> transactionPhotoUris.remove(photoUri)
            }
            // Usuń z mapy śledzenia
            photoUriToViewMap.remove(photoUri)
            Log.d("AddClientActivity", "Usunięto miniaturę i URI: $photoUri")
            // Uwaga: Plik na dysku nie jest usuwany na tym etapie, bo klient jeszcze nie został zapisany.
            // Jeśli użytkownik anuluje dodawanie, pliki pozostaną (można dodać logikę czyszczenia).
        }

        container.addView(thumbnailView)
        // Zapisz mapowanie URI na widok
        photoUriToViewMap[photoUri] = thumbnailView
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

