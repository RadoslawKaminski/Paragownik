package com.kaminski.paragownik

import android.content.Intent
import android.net.Uri // Potrzebne dla URI zdjęcia
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton // Potrzebne dla ImageButton
import android.widget.ImageView // Potrzebne dla ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts // Potrzebne dla launchera
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri // Do konwersji String/File na Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kaminski.paragownik.viewmodel.EditReceiptViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File // Potrzebne do operacji na plikach
import java.io.FileOutputStream // Potrzebne do zapisu pliku
import java.io.IOException // Potrzebne do obsługi błędów IO
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID // Do generowania unikalnych nazw plików


/**
 * Aktywność odpowiedzialna za edycję istniejącego paragonu oraz powiązanych danych klienta,
 * w tym zdjęcia klienta. Kopiuje wybrane zdjęcie do pamięci wewnętrznej.
 * Umożliwia również usunięcie pojedynczego paragonu lub całego klienta.
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
    private lateinit var editClientPhotoImageView: ImageView // Widok miniatury zdjęcia
    private lateinit var editAddChangePhotoButton: ImageButton // Przycisk dodawania/zmiany zdjęcia
    private lateinit var saveReceiptButton: Button
    private lateinit var deleteReceiptButton: Button
    private lateinit var deleteClientButton: Button

    // --- ViewModel ---
    private lateinit var editReceiptViewModel: EditReceiptViewModel

    // --- Dane pomocnicze ---
    private var receiptId: Long = -1L
    private var currentClientId: Long? = null
    private var loadDataJob: Job? = null
    // Przechowuje URI skopiowanego zdjęcia w pamięci wewnętrznej.
    private var selectedPhotoUri: Uri? = null

    // Launcher do wybierania zdjęcia z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            Log.d("EditReceiptActivity", "Otrzymano tymczasowe URI: $sourceUri")
            // Skopiuj obraz i uzyskaj nowe, trwałe URI
            val destinationUri = copyImageToInternalStorage(sourceUri)

            destinationUri?.let { finalUri ->
                // TODO: Opcjonalnie: Usuń stary plik zdjęcia, jeśli istniał (selectedPhotoUri przed zmianą)
                // val oldUri = selectedPhotoUri
                // if (oldUri != null && oldUri != finalUri) { deleteImageFile(oldUri) }

                selectedPhotoUri = finalUri // Zapisz NOWE trwałe URI
                editClientPhotoImageView.setImageURI(finalUri) // Wyświetl miniaturę
                Log.d("EditReceiptActivity", "Ustawiono nowe trwałe URI zdjęcia: $finalUri")
            }
        } ?: run {
            Log.d("EditReceiptActivity", "Nie wybrano nowego zdjęcia.")
        }
    }

    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_receipt)

        initializeViews()
        initializeViewModel()

        receiptId = intent.getLongExtra("RECEIPT_ID", -1L)
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
        editClientPhotoImageView = findViewById(R.id.editClientPhotoImageView) // Inicjalizacja ImageView
        editAddChangePhotoButton = findViewById(R.id.editAddChangePhotoButton) // Inicjalizacja ImageButton
        saveReceiptButton = findViewById(R.id.saveReceiptButton)
        deleteReceiptButton = findViewById(R.id.deleteReceiptButton)
        deleteClientButton = findViewById(R.id.deleteClientButton)
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

        // Listener dla kliknięcia przycisku dodawania/zmiany zdjęcia
        editAddChangePhotoButton.setOnClickListener {
            // Uruchom selektor obrazów
            pickImageLauncher.launch("image/*")
        }
    }

    /**
     * Wczytuje dane paragonu, klienta i sklepu z ViewModelu i wypełnia formularz.
     * Obsługuje również wczytywanie miniatury zdjęcia klienta z wewnętrznego magazynu.
     */
    private fun loadReceiptData() {
        loadDataJob?.cancel()
        loadDataJob = lifecycleScope.launch {
            editReceiptViewModel.getReceiptWithClientAndStoreNumber(receiptId)
                .collectLatest { pair ->
                    val receiptWithClient = pair.first
                    val storeNumber = pair.second

                    if (receiptWithClient != null && receiptWithClient.client != null) {
                        val receipt = receiptWithClient.receipt
                        val client = receiptWithClient.client
                        currentClientId = client.id

                        // Wypełnianie pól tekstowych paragonu
                        editReceiptStoreNumberEditText.setText(storeNumber ?: "")
                        editReceiptNumberEditText.setText(receipt.receiptNumber)
                        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        editReceiptDateEditText.setText(dateFormat.format(receipt.receiptDate))

                        // Obsługa daty weryfikacji
                        receipt.verificationDate?.let { verificationDate ->
                            val formattedVerificationDate = dateFormat.format(verificationDate)
                            editVerificationDateEditText.setText(formattedVerificationDate)
                            val todayDate = dateFormat.format(java.util.Calendar.getInstance().time)
                            editVerificationDateTodayCheckBox.isChecked = formattedVerificationDate == todayDate
                            editVerificationDateEditText.isEnabled = !editVerificationDateTodayCheckBox.isChecked
                        } ?: run {
                            editVerificationDateEditText.text.clear()
                            editVerificationDateTodayCheckBox.isChecked = false
                            editVerificationDateEditText.isEnabled = true
                        }

                        // Wypełnianie pól tekstowych klienta
                        editClientDescriptionEditText.setText(client.description ?: "")
                        editClientAppNumberEditText.setText(client.clientAppNumber ?: "")
                        editAmoditNumberEditText.setText(client.amoditNumber ?: "")

                        // Logika ładowania zdjęcia klienta (teraz z trwałego URI)
                        if (!client.photoUri.isNullOrBlank()) {
                            try {
                                // URI z bazy powinno teraz wskazywać na plik w pamięci wewnętrznej
                                val photoUri = client.photoUri.toUri()
                                selectedPhotoUri = photoUri // Zapisz URI jako aktualne
                                editClientPhotoImageView.setImageURI(photoUri) // Ustaw obraz
                                Log.d("EditReceiptActivity", "Załadowano zdjęcie klienta z trwałego URI: $photoUri")
                            } catch (e: Exception) {
                                // Błąd może wystąpić, jeśli plik został usunięty lub URI jest uszkodzone
                                Log.e("EditReceiptActivity", "Błąd ładowania zdjęcia z URI: ${client.photoUri}", e)
                                editClientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                                selectedPhotoUri = null
                            }
                        } else {
                            // Jeśli brak zdjęcia, ustaw placeholder
                            editClientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                            selectedPhotoUri = null
                            Log.d("EditReceiptActivity", "Klient nie ma zapisanego zdjęcia.")
                        }

                    } else {
                        // Obsługa sytuacji, gdy dane nie zostały znalezione
                        if (isActive) {
                            Log.e("EditReceiptActivity", "Nie znaleziono danych dla receiptId: $receiptId")
                            // Można rozważyć zamknięcie aktywności
                        }
                    }
                }
        }
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
                // Przekaż URI jako String (teraz to trwałe URI)
                photoUri = selectedPhotoUri?.toString()
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
     * Usuwa bieżący paragon.
     */
    private fun deleteReceipt() {
        lifecycleScope.launch {
            val currentReceipt = editReceiptViewModel.getReceiptWithClientAndStoreNumber(receiptId)
                .map { it.first?.receipt }
                .firstOrNull()

            if (currentReceipt == null) {
                Toast.makeText(this@EditReceiptActivity, R.string.error_cannot_get_receipt_data, Toast.LENGTH_LONG).show()
                Log.e("EditReceiptActivity", "Nie udało się pobrać Receipt (id: $receiptId) do usunięcia.")
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
     * Usuwa bieżącego klienta i jego paragony.
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
     * @param result Wynik operacji.
     * @param isDeleteOperation Flaga wskazująca, czy była to operacja usuwania.
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
                loadDataJob?.cancel()
                finishAffinity()
                startActivity(Intent(this@EditReceiptActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } else {
                finish()
            }
        }
    }


    /**
     * Konfiguruje działanie CheckBoxa "Dzisiaj" dla daty weryfikacji.
     */
    private fun setupVerificationDateCheckBox() {
        editVerificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                editVerificationDateEditText.setText(currentDate)
                editVerificationDateEditText.isEnabled = false
            } else {
                editVerificationDateEditText.isEnabled = true
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

            // Utwórz unikalną nazwę pliku dla kopii
            val uniqueFileName = "${UUID.randomUUID()}.jpg"
            val destinationFile = File(imagesDir, uniqueFileName)

            // Otwórz strumień wyjściowy do pliku docelowego
            val outputStream = FileOutputStream(destinationFile)

            // Skopiuj dane
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            Log.d("EditReceiptActivity", "Skopiowano zdjęcie do: ${destinationFile.absolutePath}")
            // Zwróć URI dla nowo utworzonego pliku
            destinationFile.toUri()

        } catch (e: IOException) {
            Log.e("EditReceiptActivity", "Błąd podczas kopiowania zdjęcia", e)
            Toast.makeText(this, R.string.error_copying_image, Toast.LENGTH_SHORT).show()
            null
        } catch (e: SecurityException) {
            // Ten błąd jest mniej prawdopodobny tutaj, ale obsłużmy go
            Log.e("EditReceiptActivity", "Brak uprawnień do odczytu URI źródłowego (SecurityException)", e)
            Toast.makeText(this, "Brak uprawnień do odczytu zdjęcia.", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // TODO: Opcjonalna funkcja do usuwania pliku zdjęcia, jeśli jest potrzebna
    /*
    private fun deleteImageFile(fileUri: Uri) {
        try {
            // Sprawdź, czy to URI pliku z naszego wewnętrznego magazynu
            if (fileUri.scheme == "file" && fileUri.path?.startsWith(filesDir.absolutePath) == true) {
                val fileToDelete = File(fileUri.path!!)
                if (fileToDelete.exists()) {
                    if (fileToDelete.delete()) {
                        Log.d("EditReceiptActivity", "Usunięto stary plik zdjęcia: $fileUri")
                    } else {
                        Log.w("EditReceiptActivity", "Nie udało się usunąć starego pliku zdjęcia: $fileUri")
                    }
                }
            } else {
                Log.w("EditReceiptActivity", "Próba usunięcia pliku z nieobsługiwanego URI: $fileUri")
            }
        } catch (e: Exception) {
            Log.e("EditReceiptActivity", "Błąd podczas usuwania pliku zdjęcia: $fileUri", e)
        }
    }
    */
}