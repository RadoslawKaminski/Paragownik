package com.kaminski.paragownik

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View // Potrzebny dla View.VISIBLE/GONE
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout // Potrzebny dla LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
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


/**
 * Aktywność odpowiedzialna za przeglądanie i edycję istniejącego paragonu oraz danych klienta.
 * Domyślnie uruchamia się w trybie widoku (pola zablokowane, puste ukryte).
 * Przycisk "Edytuj" przełącza do trybu edycji.
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
    private lateinit var editClientPhotoImageView: ImageView // Miniatura zdjęcia
    private lateinit var editAddChangePhotoButton: ImageButton // Przycisk dodawania/zmiany zdjęcia
    private lateinit var saveReceiptButton: Button
    private lateinit var deleteReceiptButton: Button
    private lateinit var deleteClientButton: Button
    private lateinit var editModeButton: Button // Przycisk "Edytuj"
    private lateinit var largeClientPhotoImageView: ImageView // Duże zdjęcie
    // Layouty do ukrywania/pokazywania
    private lateinit var editVerificationSectionLayout: LinearLayout
    private lateinit var editDescriptionLayout: LinearLayout
    private lateinit var editAppNumberLayout: LinearLayout
    private lateinit var editAmoditNumberLayout: LinearLayout
    private lateinit var editPhotoSectionLayout: LinearLayout

    // --- ViewModel ---
    private lateinit var editReceiptViewModel: EditReceiptViewModel

    // --- Dane pomocnicze ---
    private var receiptId: Long = -1L
    private var currentClientId: Long? = null
    private var loadDataJob: Job? = null
    private var selectedPhotoUri: Uri? = null // URI zdjęcia (trwałe)
    private var isEditMode = false // Stan widoku/edycji

    // Launcher do wybierania zdjęcia z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            Log.d("EditReceiptActivity", "Otrzymano tymczasowe URI: $sourceUri")
            val destinationUri = copyImageToInternalStorage(sourceUri)
            destinationUri?.let { finalUri ->
                // TODO: Opcjonalnie: Usuń stary plik zdjęcia
                selectedPhotoUri = finalUri
                editClientPhotoImageView.setImageURI(finalUri) // Miniatura
                largeClientPhotoImageView.setImageURI(finalUri) // Duże zdjęcie
                Log.d("EditReceiptActivity", "Ustawiono nowe trwałe URI zdjęcia: $finalUri")
                // Upewnij się, że sekcja zdjęcia jest widoczna po dodaniu (w trybie edycji)
                updateUiMode(isEditMode)
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
        loadReceiptData() // Ładowanie danych zainicjuje też updateUiMode
        setupButtonClickListeners()

        updateUiMode(false) // Ustaw tryb widoku na starcie (przed załadowaniem danych)
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
        editClientPhotoImageView = findViewById(R.id.editClientPhotoImageView)
        editAddChangePhotoButton = findViewById(R.id.editAddChangePhotoButton)
        saveReceiptButton = findViewById(R.id.saveReceiptButton)
        deleteReceiptButton = findViewById(R.id.deleteReceiptButton)
        deleteClientButton = findViewById(R.id.deleteClientButton)
        editModeButton = findViewById(R.id.editModeButton) // Przycisk Edytuj
        largeClientPhotoImageView = findViewById(R.id.largeClientPhotoImageView) // Duże ImageView
        // Layouty do ukrywania
        editVerificationSectionLayout = findViewById(R.id.editVerificationSectionLayout)
        editDescriptionLayout = findViewById(R.id.editDescriptionLayout)
        editAppNumberLayout = findViewById(R.id.editAppNumberLayout)
        editAmoditNumberLayout = findViewById(R.id.editAmoditNumberLayout)
        editPhotoSectionLayout = findViewById(R.id.editPhotoSectionLayout)
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
        editAddChangePhotoButton.setOnClickListener { pickImageLauncher.launch("image/*") }

        // Listener dla przycisku "Edytuj"
        editModeButton.setOnClickListener {
            updateUiMode(true) // Włącz tryb edycji
        }
    }

    /**
     * Wczytuje dane paragonu, klienta i sklepu z ViewModelu i wypełnia formularz.
     * Aktualizuje UI do trybu widoku/edycji.
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
                            // Stan enabled zostanie ustawiony w updateUiMode
                        } ?: run {
                            editVerificationDateEditText.text.clear()
                            editVerificationDateTodayCheckBox.isChecked = false
                            // Stan enabled zostanie ustawiony w updateUiMode
                        }

                        // Wypełnianie pól tekstowych klienta
                        editClientDescriptionEditText.setText(client.description ?: "")
                        editClientAppNumberEditText.setText(client.clientAppNumber ?: "")
                        editAmoditNumberEditText.setText(client.amoditNumber ?: "")

                        // Logika ładowania zdjęcia klienta (miniatura i duże)
                        if (!client.photoUri.isNullOrBlank()) {
                            try {
                                val photoUri = client.photoUri.toUri()
                                selectedPhotoUri = photoUri
                                editClientPhotoImageView.setImageURI(photoUri) // Miniatura
                                largeClientPhotoImageView.setImageURI(photoUri) // Duże zdjęcie
                                Log.d("EditReceiptActivity", "Załadowano zdjęcie klienta z trwałego URI: $photoUri")
                            } catch (e: Exception) {
                                Log.e("EditReceiptActivity", "Błąd ładowania zdjęcia z URI: ${client.photoUri}", e)
                                editClientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                                largeClientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                                selectedPhotoUri = null
                            }
                        } else {
                            editClientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                            largeClientPhotoImageView.setImageResource(R.drawable.ic_photo_placeholder)
                            selectedPhotoUri = null
                            Log.d("EditReceiptActivity", "Klient nie ma zapisanego zdjęcia.")
                        }

                        // Zaktualizuj widoczność pól na podstawie załadowanych danych i bieżącego trybu
                        updateUiMode(isEditMode)

                    } else {
                        // Obsługa sytuacji, gdy dane nie zostały znalezione
                        if (isActive) {
                            Log.e("EditReceiptActivity", "Nie znaleziono danych dla receiptId: $receiptId")
                            Toast.makeText(this@EditReceiptActivity, R.string.error_receipt_not_found, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
        }
    }

    /**
     * Aktualizuje widoczność i stan edytowalności elementów UI.
     */
    private fun updateUiMode(isEditing: Boolean) {
        isEditMode = isEditing

        // Włącz/Wyłącz EditTexty
        editReceiptStoreNumberEditText.isEnabled = isEditing
        editReceiptNumberEditText.isEnabled = isEditing
        editReceiptDateEditText.isEnabled = isEditing
        editVerificationDateEditText.isEnabled = isEditing && !editVerificationDateTodayCheckBox.isChecked
        editVerificationDateTodayCheckBox.isEnabled = isEditing
        editClientDescriptionEditText.isEnabled = isEditing
        editClientAppNumberEditText.isEnabled = isEditing
        editAmoditNumberEditText.isEnabled = isEditing

        // Pokaż/Ukryj przyciski akcji
        saveReceiptButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteReceiptButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        deleteClientButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        editAddChangePhotoButton.visibility = if (isEditing) View.VISIBLE else View.GONE
        editModeButton.visibility = if (isEditing) View.GONE else View.VISIBLE

        // Pokaż/Ukryj duży obrazek (tylko w trybie widoku, jeśli jest zdjęcie)
        largeClientPhotoImageView.visibility = if (!isEditing && selectedPhotoUri != null) View.VISIBLE else View.GONE

        // Pokaż/Ukryj opcjonalne sekcje
        editVerificationSectionLayout.visibility = if (isEditing || !editVerificationDateEditText.text.isNullOrBlank()) View.VISIBLE else View.GONE
        editDescriptionLayout.visibility = if (isEditing || !editClientDescriptionEditText.text.isNullOrBlank()) View.VISIBLE else View.GONE
        editAppNumberLayout.visibility = if (isEditing || !editClientAppNumberEditText.text.isNullOrBlank()) View.VISIBLE else View.GONE
        editAmoditNumberLayout.visibility = if (isEditing || !editAmoditNumberEditText.text.isNullOrBlank()) View.VISIBLE else View.GONE
        // Sekcja zdjęcia (miniatura + przycisk) jest widoczna w edycji LUB jeśli jest zdjęcie w trybie widoku
        editPhotoSectionLayout.visibility = if (isEditing || selectedPhotoUri != null) View.VISIBLE else View.GONE
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
                loadDataJob?.cancel()
                finishAffinity()
                startActivity(Intent(this@EditReceiptActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } else {
                // Po udanej EDYCJI, przełącz z powrotem do trybu widoku
                updateUiMode(false)
                // Nie zamykamy już aktywności po zapisie
            }
        }
    }


    /**
     * Konfiguruje działanie CheckBoxa "Dzisiaj" dla daty weryfikacji.
     */
    private fun setupVerificationDateCheckBox() {
        editVerificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            // Ten listener jest aktywny tylko w trybie edycji, bo checkbox jest wyłączony w trybie widoku
            if (isChecked) {
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                editVerificationDateEditText.setText(currentDate)
                editVerificationDateEditText.isEnabled = false // Wyłącz pole daty, gdy "Dzisiaj" jest zaznaczone
            } else {
                editVerificationDateEditText.isEnabled = true // Włącz pole daty, gdy "Dzisiaj" jest odznaczone
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
            Log.d("EditReceiptActivity", "Skopiowano zdjęcie do: ${destinationFile.absolutePath}")
            destinationFile.toUri()

        } catch (e: IOException) {
            Log.e("EditReceiptActivity", "Błąd podczas kopiowania zdjęcia", e)
            Toast.makeText(this, R.string.error_copying_image, Toast.LENGTH_SHORT).show()
            null
        } catch (e: SecurityException) {
            Log.e("EditReceiptActivity", "Brak uprawnień do odczytu URI źródłowego (SecurityException)", e)
            Toast.makeText(this, "Brak uprawnień do odczytu zdjęcia.", Toast.LENGTH_SHORT).show()
            null
        }
    }
}