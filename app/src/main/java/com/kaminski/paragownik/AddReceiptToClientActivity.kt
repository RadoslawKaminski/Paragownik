package com.kaminski.paragownik

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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.kaminski.paragownik.viewmodel.AddReceiptToClientViewModel
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Aktywność do dodawania nowych paragonów do istniejącego klienta.
 * Wyświetla dane klienta i pozwala na dodanie jednego lub więcej paragonów, w tym daty weryfikacji.
 * Używa Glide do wyświetlania miniatury klienta.
 */
class AddReceiptToClientActivity : AppCompatActivity() {

    // Widoki danych klienta (nieedytowalne)
    private lateinit var clientPhotoImageView: ImageView
    private lateinit var clientDescriptionTextView: TextView
    private lateinit var clientAppNumberTextView: TextView
    private lateinit var clientAmoditNumberTextView: TextView

    // Widoki pól paragonów
    private lateinit var firstStoreNumberEditText: EditText
    private lateinit var firstReceiptNumberEditText: EditText
    private lateinit var firstReceiptDateEditText: EditText
    private lateinit var firstCashRegisterNumberEditText: EditText // Pole na numer kasy
    private lateinit var firstVerificationDateEditText: EditText
    private lateinit var firstVerificationDateTodayCheckBox: CheckBox
    private lateinit var newReceiptsContainer: LinearLayout
    private lateinit var addAnotherReceiptButton: Button
    private lateinit var saveNewReceiptsButton: Button

    // ViewModel
    private lateinit var viewModel: AddReceiptToClientViewModel

    // Dane pomocnicze
    private var clientId: Long = -1L
    private val receiptFieldsList = ArrayList<ReceiptFields>()

    /**
     * Struktura pomocnicza do przechowywania referencji do pól EditText
     * dla dynamicznie dodawanych sekcji paragonów.
     */
    private data class ReceiptFields(
        val storeNumberEditText: EditText,
        val receiptNumberEditText: EditText,
        val receiptDateEditText: EditText,
        val cashRegisterNumberEditText: EditText, // Dodano pole numeru kasy
        val verificationDateEditText: EditText,
        val verificationDateTodayCheckBox: CheckBox
    )

    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_receipt_to_client)

        clientId = intent.getLongExtra("CLIENT_ID", -1L)
        if (clientId == -1L) {
            Log.e("AddReceiptToClient", "Nieprawidłowe CLIENT_ID (-1) otrzymane w Intencie.")
            Toast.makeText(this, R.string.error_invalid_client_id, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.d("AddReceiptToClient", "Otrzymano CLIENT_ID: $clientId")

        initializeViews()

        viewModel = ViewModelProvider(this).get(AddReceiptToClientViewModel::class.java)
        viewModel.loadClientData(clientId)

        setupListeners()

        setupDateEditText(firstReceiptDateEditText)
        setupDateEditText(firstVerificationDateEditText)

        // Dodajemy pola pierwszego paragonu do listy
        receiptFieldsList.add(ReceiptFields(
            firstStoreNumberEditText,
            firstReceiptNumberEditText,
            firstReceiptDateEditText,
            firstCashRegisterNumberEditText, // Dodano pole numeru kasy
            firstVerificationDateEditText,
            firstVerificationDateTodayCheckBox
        ))

        observeClientData()
    }

    /**
     * Inicjalizuje referencje do widoków UI z layoutu.
     */
    private fun initializeViews() {
        clientPhotoImageView = findViewById(R.id.addReceiptClientPhotoImageView)
        clientDescriptionTextView = findViewById(R.id.addReceiptClientDescriptionTextView)
        clientAppNumberTextView = findViewById(R.id.addReceiptClientAppNumberTextView)
        clientAmoditNumberTextView = findViewById(R.id.addReceiptClientAmoditNumberTextView)

        firstStoreNumberEditText = findViewById(R.id.firstStoreNumberEditText)
        firstReceiptNumberEditText = findViewById(R.id.firstReceiptNumberEditText)
        firstReceiptDateEditText = findViewById(R.id.firstReceiptDateEditText)
        firstCashRegisterNumberEditText = findViewById(R.id.firstCashRegisterNumberEditText) // Inicjalizacja pola numeru kasy
        firstVerificationDateEditText = findViewById(R.id.firstVerificationDateEditText)
        firstVerificationDateTodayCheckBox = findViewById(R.id.firstVerificationDateTodayCheckBox)
        newReceiptsContainer = findViewById(R.id.newReceiptsContainer)
        addAnotherReceiptButton = findViewById(R.id.addAnotherReceiptButton)
        saveNewReceiptsButton = findViewById(R.id.saveNewReceiptsButton)
    }

    /**
     * Ustawia listenery dla przycisków i checkboxa "Dzisiaj".
     */
    private fun setupListeners() {
        addAnotherReceiptButton.setOnClickListener {
            addNewReceiptFields()
        }
        saveNewReceiptsButton.setOnClickListener {
            saveNewReceipts()
        }

        firstVerificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                firstVerificationDateEditText.setText(currentDate)
                firstVerificationDateEditText.isEnabled = false
            } else {
                firstVerificationDateEditText.isEnabled = true
            }
        }
    }

    /**
     * Obserwuje dane klienta (Client) i jego miniaturę (thumbnailUri) z ViewModelu.
     * Aktualizuje widoki tekstowe klienta i ładuje miniaturę za pomocą Glide.
     */
    private fun observeClientData() {
        viewModel.clientDataWithThumbnail.observe(this) { pair ->
            val client = pair?.first
            val thumbnailUriString = pair?.second

            if (client == null) {
                Log.e("AddReceiptToClient", "Nie znaleziono klienta o ID: $clientId w observeClientData")
                Toast.makeText(this@AddReceiptToClientActivity, R.string.error_client_not_found, Toast.LENGTH_SHORT).show()
                Glide.with(this).clear(clientPhotoImageView)
                clientPhotoImageView.visibility = View.GONE
                return@observe
            }

            clientDescriptionTextView.text = if (client.description.isNullOrBlank()) {
                getString(R.string.client_item_id_prefix) + client.id.toString()
            } else {
                client.description
            }

            val appNumberText = client.clientAppNumber?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.client_item_app_number_prefix) + " " + it
            }
            clientAppNumberTextView.text = appNumberText
            clientAppNumberTextView.isVisible = appNumberText != null

            val amoditNumberText = client.amoditNumber?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.client_item_amodit_number_prefix) + " " + it
            }
            clientAmoditNumberTextView.text = amoditNumberText
            clientAmoditNumberTextView.isVisible = amoditNumberText != null

            if (!thumbnailUriString.isNullOrBlank()) {
                Glide.with(this)
                    .load(thumbnailUriString.toUri())
                    .placeholder(R.drawable.ic_photo_placeholder)
                    .error(R.drawable.ic_photo_placeholder)
                    .centerCrop()
                    .into(clientPhotoImageView)
                clientPhotoImageView.visibility = View.VISIBLE
            } else {
                Glide.with(this).clear(clientPhotoImageView)
                clientPhotoImageView.visibility = View.GONE
            }
        }
    }


    /**
     * Dodaje dynamicznie nowy zestaw pól dla kolejnego paragonu do layoutu.
     */
    private fun addNewReceiptFields() {
        val inflater = LayoutInflater.from(this)
        val receiptFieldsView = inflater.inflate(R.layout.additional_receipt_fields, newReceiptsContainer, false)

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
        newReceiptsContainer.addView(receiptFieldsView)

        removeReceiptButton.setOnClickListener {
            newReceiptsContainer.removeView(receiptFieldsView)
            receiptFieldsList.remove(newReceiptFields)
        }
    }

    /**
     * Zbiera dane ze wszystkich pól paragonów, waliduje je i wywołuje metodę zapisu w ViewModelu.
     */
    private fun saveNewReceipts() {
        // Używamy tej samej struktury danych co w AddClientActivity
        val receiptsData = mutableListOf<AddClientActivity.ReceiptData>()
        var hasEmptyFields = false
        var invalidDateFound = false // Flaga dla błędów formatu daty

        for (fields in receiptFieldsList) {
            val storeNumber = fields.storeNumberEditText.text.toString().trim()
            val receiptNumber = fields.receiptNumberEditText.text.toString().trim()
            val receiptDate = fields.receiptDateEditText.text.toString().trim()
            val cashRegisterNumber = fields.cashRegisterNumberEditText.text.toString().trim() // Pobranie numeru kasy
            val verificationDateString = fields.verificationDateEditText.text.toString().trim()

            if (storeNumber.isEmpty() || receiptNumber.isEmpty() || receiptDate.isEmpty()) {
                hasEmptyFields = true
                break
            }
            if (!isValidDate(receiptDate)) {
                invalidDateFound = true
                break
            }
            if (verificationDateString.isNotEmpty() && !isValidDate(verificationDateString)) {
                invalidDateFound = true
                break
            }
            receiptsData.add(AddClientActivity.ReceiptData(
                storeNumber,
                receiptNumber,
                receiptDate,
                cashRegisterNumber.takeIf { it.isNotEmpty() }, // Dodanie numeru kasy
                verificationDateString.takeIf { it.isNotEmpty() }
            ))
        }

        if (hasEmptyFields) {
            Toast.makeText(this, R.string.error_fill_required_receipt_fields, Toast.LENGTH_LONG).show()
            return
        }
        if (invalidDateFound) {
            Toast.makeText(this, R.string.error_invalid_date_format, Toast.LENGTH_LONG).show()
            return
        }
        if (receiptsData.isEmpty()) {
             Toast.makeText(this, R.string.error_add_at_least_one_receipt, Toast.LENGTH_SHORT).show()
             return
        }

        Log.d("AddReceiptToClient", "Przygotowano ${receiptsData.size} paragonów do zapisu dla klienta $clientId.")
        lifecycleScope.launch {
             val result = viewModel.saveReceiptsForClient(clientId, receiptsData)
             handleSaveResult(result)
        }
    }

    /**
     * Obsługuje wynik operacji zapisu zwrócony przez ViewModel, wyświetlając odpowiedni komunikat Toast.
     * W przypadku sukcesu zamyka aktywność.
     */
    private fun handleSaveResult(result: AddReceiptToClientViewModel.SaveReceiptsResult) {
         val messageResId = when (result) {
             AddReceiptToClientViewModel.SaveReceiptsResult.SUCCESS -> R.string.save_success_message
             AddReceiptToClientViewModel.SaveReceiptsResult.ERROR_DATE_FORMAT -> R.string.error_invalid_date_format
             AddReceiptToClientViewModel.SaveReceiptsResult.ERROR_VERIFICATION_DATE_FORMAT -> R.string.error_invalid_verification_date_format
             AddReceiptToClientViewModel.SaveReceiptsResult.ERROR_DUPLICATE_RECEIPT -> R.string.error_duplicate_receipt
             AddReceiptToClientViewModel.SaveReceiptsResult.ERROR_STORE_NUMBER_MISSING -> R.string.error_store_number_missing
             AddReceiptToClientViewModel.SaveReceiptsResult.ERROR_DATABASE -> R.string.error_database
             AddReceiptToClientViewModel.SaveReceiptsResult.ERROR_UNKNOWN -> R.string.error_unknown
             AddReceiptToClientViewModel.SaveReceiptsResult.ERROR_NO_RECEIPTS -> R.string.error_add_at_least_one_receipt
         }
         val message = getString(messageResId)

         Toast.makeText(this, message, if (result == AddReceiptToClientViewModel.SaveReceiptsResult.SUCCESS) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()

         if (result == AddReceiptToClientViewModel.SaveReceiptsResult.SUCCESS) {
             finish()
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
}

