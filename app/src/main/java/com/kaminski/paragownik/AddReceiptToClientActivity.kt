
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
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
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
 * Wyświetla dane klienta i pozwala na dodanie jednego lub więcej paragonów.
 * Używa ViewModelu do przechowywania stanu dynamicznych pól paragonów.
 */
class AddReceiptToClientActivity : AppCompatActivity() {

    // Widoki danych klienta (nieedytowalne)
    private lateinit var clientPhotoImageView: ImageView
    private lateinit var clientDescriptionTextView: TextView
    private lateinit var clientAppNumberTextView: TextView
    private lateinit var clientAmoditNumberTextView: TextView

    // Widoki pól pierwszego paragonu
    private lateinit var firstStoreNumberEditText: EditText
    private lateinit var firstReceiptNumberEditText: EditText
    private lateinit var firstReceiptDateEditText: EditText
    private lateinit var firstCashRegisterNumberEditText: EditText
    private lateinit var firstVerificationDateEditText: EditText
    private lateinit var firstVerificationDateTodayCheckBox: CheckBox
    // Kontener i przyciski
    private lateinit var newReceiptsContainer: LinearLayout // Kontener na dynamiczne pola
    private lateinit var addAnotherReceiptButton: Button
    private lateinit var saveNewReceiptsButton: Button

    // ViewModel
    private lateinit var viewModel: AddReceiptToClientViewModel

    // Dane pomocnicze
    private var clientId: Long = -1L
    // Mapa przechowująca widoki dynamicznych pól paragonów, kluczem jest ID stanu z ViewModelu
    private val receiptStateIdToViewMap = mutableMapOf<String, View>()

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
        initializeViewModel() // Inicjalizacja ViewModelu

        viewModel.loadClientData(clientId) // Rozpocznij ładowanie danych klienta

        setupFirstReceiptFieldsListeners() // Ustaw listenery dla pierwszego paragonu
        setupButtonClickListeners() // Ustaw listenery dla przycisków

        observeViewModelData() // Rozpocznij obserwację danych z ViewModelu
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
        firstCashRegisterNumberEditText = findViewById(R.id.firstCashRegisterNumberEditText)
        firstVerificationDateEditText = findViewById(R.id.firstVerificationDateEditText)
        firstVerificationDateTodayCheckBox = findViewById(R.id.firstVerificationDateTodayCheckBox)
        newReceiptsContainer = findViewById(R.id.newReceiptsContainer)
        addAnotherReceiptButton = findViewById(R.id.addAnotherReceiptButton)
        saveNewReceiptsButton = findViewById(R.id.saveNewReceiptsButton)
    }

    /** Inicjalizuje ViewModel. */
    private fun initializeViewModel() {
        viewModel = ViewModelProvider(this).get(AddReceiptToClientViewModel::class.java)
    }

    /**
     * Ustawia listenery dla pól pierwszego paragonu, aktualizujące stan w ViewModelu.
     */
    private fun setupFirstReceiptFieldsListeners() {
        val firstReceiptStateId = viewModel.receiptFieldsStates.value?.firstOrNull()?.id ?: return

        // Formatowanie daty
        setupDateEditText(firstReceiptDateEditText, firstReceiptStateId) { state, text -> state.receiptDate = text }
        setupDateEditText(firstVerificationDateEditText, firstReceiptStateId) { state, text -> state.verificationDate = text }

        // Listenery TextChanged
        firstStoreNumberEditText.addTextChangedListener { text ->
            viewModel.updateReceiptFieldState(firstReceiptStateId) { it.storeNumber = text.toString() }
        }
        firstReceiptNumberEditText.addTextChangedListener { text ->
            viewModel.updateReceiptFieldState(firstReceiptStateId) { it.receiptNumber = text.toString() }
        }
        firstCashRegisterNumberEditText.addTextChangedListener { text ->
            viewModel.updateReceiptFieldState(firstReceiptStateId) { it.cashRegisterNumber = text.toString() }
        }

        // Listener CheckBoxa "Dzisiaj"
        firstVerificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateReceiptFieldState(firstReceiptStateId) { state ->
                state.isVerificationDateToday = isChecked
                if (isChecked) {
                    val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                    state.verificationDate = currentDate
                    firstVerificationDateEditText.setText(currentDate) // Aktualizuj UI
                    firstVerificationDateEditText.isEnabled = false
                } else {
                    firstVerificationDateEditText.isEnabled = true
                    // Opcjonalnie: wyczyść pole daty
                    // state.verificationDate = ""
                    // firstVerificationDateEditText.setText("")
                }
            }
            // viewModel.receiptFieldsStates.value = viewModel.receiptFieldsStates.value // Wymuś odświeżenie
        }
    }

    /**
     * Ustawia listenery dla przycisków "Dodaj paragon" i "Zapisz paragony".
     */
    private fun setupButtonClickListeners() {
        addAnotherReceiptButton.setOnClickListener {
            viewModel.addNewReceiptFieldState() // Dodaj nowy stan w ViewModelu
            // Widok zostanie dodany przez obserwatora
        }
        saveNewReceiptsButton.setOnClickListener {
            saveNewReceipts()
        }
    }

    /**
     * Obserwuje dane klienta i stany pól paragonów z ViewModelu.
     */
    private fun observeViewModelData() {
        // Obserwator danych klienta
        viewModel.clientDataWithThumbnail.observe(this, Observer { pair ->
            val client = pair?.first
            val thumbnailUriString = pair?.second

            if (client == null) {
                Log.e("AddReceiptToClient", "Nie znaleziono klienta o ID: $clientId w observeClientData")
                Toast.makeText(this@AddReceiptToClientActivity, R.string.error_client_not_found, Toast.LENGTH_SHORT).show()
                Glide.with(this).clear(clientPhotoImageView)
                clientPhotoImageView.visibility = View.GONE
                // Można rozważyć finish() jeśli klient zniknął
                return@Observer
            }

            // Aktualizacja widoków danych klienta
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

            // Ładowanie miniatury
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
        })

        // Obserwator stanów pól paragonów
        viewModel.receiptFieldsStates.observe(this, Observer { states ->
            Log.d("AddReceiptToClient", "Obserwator: Zmiana w stanach paragonów, liczba: ${states.size}")
            rebuildReceiptFieldsUI(states ?: emptyList())
        })
    }

    /**
     * Odtwarza lub aktualizuje dynamiczne sekcje pól paragonów w UI.
     */
    private fun rebuildReceiptFieldsUI(states: List<AddReceiptToClientViewModel.ReceiptFieldsState>) {
        // Usuń widoki, których stany już nie istnieją
        val currentStateIds = states.map { it.id }.toSet()
        val viewsToRemove = receiptStateIdToViewMap.filterKeys { it !in currentStateIds }
        viewsToRemove.forEach { (id, view) ->
            newReceiptsContainer.removeView(view)
            receiptStateIdToViewMap.remove(id)
        }

        // Zaktualizuj pierwszy paragon
        states.firstOrNull()?.let { firstState ->
            if (firstStoreNumberEditText.text.toString() != firstState.storeNumber) firstStoreNumberEditText.setText(firstState.storeNumber)
            if (firstReceiptNumberEditText.text.toString() != firstState.receiptNumber) firstReceiptNumberEditText.setText(firstState.receiptNumber)
            if (firstReceiptDateEditText.text.toString() != firstState.receiptDate) firstReceiptDateEditText.setText(firstState.receiptDate)
            if (firstCashRegisterNumberEditText.text.toString() != firstState.cashRegisterNumber) firstCashRegisterNumberEditText.setText(firstState.cashRegisterNumber)
            if (firstVerificationDateEditText.text.toString() != firstState.verificationDate) firstVerificationDateEditText.setText(firstState.verificationDate)
            if (firstVerificationDateTodayCheckBox.isChecked != firstState.isVerificationDateToday) firstVerificationDateTodayCheckBox.isChecked = firstState.isVerificationDateToday
            firstVerificationDateEditText.isEnabled = !firstState.isVerificationDateToday
        }

        // Dodaj lub zaktualizuj widoki dla pozostałych stanów
        states.drop(1).forEach { state ->
            val existingView = receiptStateIdToViewMap[state.id]
            if (existingView == null) {
                addNewReceiptFieldsView(state)
            } else {
                updateReceiptFieldsView(existingView, state)
            }
        }
    }

    /**
     * Dodaje nowy widok sekcji paragonu do kontenera.
     */
    private fun addNewReceiptFieldsView(state: AddReceiptToClientViewModel.ReceiptFieldsState) {
        val inflater = LayoutInflater.from(this)
        val receiptFieldsView = inflater.inflate(R.layout.additional_receipt_fields, newReceiptsContainer, false)
        receiptStateIdToViewMap[state.id] = receiptFieldsView

        // Znajdź widoki
        val storeNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalStoreNumberEditText)
        val receiptNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptNumberEditText)
        val receiptDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptDateEditText)
        val cashRegisterNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalCashRegisterNumberEditText)
        val verificationDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalVerificationDateEditText)
        val verificationDateTodayCheckBox = receiptFieldsView.findViewById<CheckBox>(R.id.additionalVerificationDateTodayCheckBox)
        val removeReceiptButton = receiptFieldsView.findViewById<ImageButton>(R.id.removeReceiptButton)

        // Ustaw wartości i listenery
        storeNumberEditText.setText(state.storeNumber)
        receiptNumberEditText.setText(state.receiptNumber)
        receiptDateEditText.setText(state.receiptDate)
        cashRegisterNumberEditText.setText(state.cashRegisterNumber)
        verificationDateEditText.setText(state.verificationDate)
        verificationDateTodayCheckBox.isChecked = state.isVerificationDateToday
        verificationDateEditText.isEnabled = !state.isVerificationDateToday

        // Listenery aktualizujące ViewModel
        setupDateEditText(receiptDateEditText, state.id) { s, text -> s.receiptDate = text }
        setupDateEditText(verificationDateEditText, state.id) { s, text -> s.verificationDate = text }
        storeNumberEditText.addTextChangedListener { text -> viewModel.updateReceiptFieldState(state.id) { it.storeNumber = text.toString() } }
        receiptNumberEditText.addTextChangedListener { text -> viewModel.updateReceiptFieldState(state.id) { it.receiptNumber = text.toString() } }
        cashRegisterNumberEditText.addTextChangedListener { text -> viewModel.updateReceiptFieldState(state.id) { it.cashRegisterNumber = text.toString() } }

        verificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateReceiptFieldState(state.id) { s ->
                s.isVerificationDateToday = isChecked
                if (isChecked) {
                    val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                    s.verificationDate = currentDate
                    verificationDateEditText.setText(currentDate)
                    verificationDateEditText.isEnabled = false
                } else {
                    verificationDateEditText.isEnabled = true
                    // Opcjonalnie: wyczyść pole daty
                    // s.verificationDate = ""
                    // verificationDateEditText.setText("")
                }
            }
            // viewModel.receiptFieldsStates.value = viewModel.receiptFieldsStates.value // Wymuś odświeżenie
        }

        removeReceiptButton.setOnClickListener {
            viewModel.removeReceiptFieldState(state.id) // Usuń stan z ViewModelu
            // Widok zostanie usunięty przez obserwatora
        }

        newReceiptsContainer.addView(receiptFieldsView)
    }

    /**
     * Aktualizuje wartości pól w istniejącym widoku sekcji paragonu.
     */
    private fun updateReceiptFieldsView(view: View, state: AddReceiptToClientViewModel.ReceiptFieldsState) {
        val storeNumberEditText = view.findViewById<EditText>(R.id.additionalStoreNumberEditText)
        val receiptNumberEditText = view.findViewById<EditText>(R.id.additionalReceiptNumberEditText)
        val receiptDateEditText = view.findViewById<EditText>(R.id.additionalReceiptDateEditText)
        val cashRegisterNumberEditText = view.findViewById<EditText>(R.id.additionalCashRegisterNumberEditText)
        val verificationDateEditText = view.findViewById<EditText>(R.id.additionalVerificationDateEditText)
        val verificationDateTodayCheckBox = view.findViewById<CheckBox>(R.id.additionalVerificationDateTodayCheckBox)

        // Ustaw wartości (tylko jeśli się różnią)
        if (storeNumberEditText.text.toString() != state.storeNumber) storeNumberEditText.setText(state.storeNumber)
        if (receiptNumberEditText.text.toString() != state.receiptNumber) receiptNumberEditText.setText(state.receiptNumber)
        if (receiptDateEditText.text.toString() != state.receiptDate) receiptDateEditText.setText(state.receiptDate)
        if (cashRegisterNumberEditText.text.toString() != state.cashRegisterNumber) cashRegisterNumberEditText.setText(state.cashRegisterNumber)
        if (verificationDateEditText.text.toString() != state.verificationDate) verificationDateEditText.setText(state.verificationDate)
        if (verificationDateTodayCheckBox.isChecked != state.isVerificationDateToday) verificationDateTodayCheckBox.isChecked = state.isVerificationDateToday
        verificationDateEditText.isEnabled = !state.isVerificationDateToday
    }

    /**
     * Zbiera dane ze wszystkich pól paragonów (teraz z ViewModelu),
     * waliduje je i wywołuje metodę zapisu w ViewModelu.
     */
    private fun saveNewReceipts() {
        // Pobierz stany paragonów z ViewModelu
        val receiptStates = viewModel.receiptFieldsStates.value ?: emptyList()

        // Walidacja pól paragonów (po stronie UI)
        var hasEmptyFields = false
        var invalidDateFound = false
        var invalidVerificationDateFound = false

        for (state in receiptStates) {
            if (state.storeNumber.isBlank() || state.receiptNumber.isBlank() || state.receiptDate.isBlank()) {
                hasEmptyFields = true
                break
            }
            if (!isValidDate(state.receiptDate)) {
                invalidDateFound = true
                break
            }
            if (state.verificationDate.isNotEmpty() && !isValidDate(state.verificationDate)) {
                invalidVerificationDateFound = true
                break
            }
        }

        if (hasEmptyFields) {
            Toast.makeText(this, R.string.error_fill_required_receipt_fields, Toast.LENGTH_LONG).show()
            return
        }
        if (invalidDateFound) {
            Toast.makeText(this, R.string.error_invalid_receipt_date_format, Toast.LENGTH_LONG).show()
            return
        }
        if (invalidVerificationDateFound) {
            Toast.makeText(this, R.string.error_invalid_verification_date_format, Toast.LENGTH_LONG).show()
            return
        }
        if (receiptStates.isEmpty()) {
             Toast.makeText(this, R.string.error_add_at_least_one_receipt, Toast.LENGTH_SHORT).show()
             return
        }

        Log.d("AddReceiptToClient", "Przygotowano ${receiptStates.size} paragonów do zapisu dla klienta $clientId.")
        // Wywołaj metodę zapisu w ViewModelu, przekazując tylko clientId
        lifecycleScope.launch {
             val result = viewModel.saveReceiptsForClient(clientId)
             handleSaveResult(result)
        }
    }

    /**
     * Obsługuje wynik operacji zapisu zwrócony przez ViewModel.
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
             AddReceiptToClientViewModel.SaveReceiptsResult.ERROR_CLIENT_NOT_FOUND -> R.string.error_client_not_found // Obsługa nowego błędu
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
     * Konfiguruje [EditText] do automatycznego formatowania daty i aktualizacji stanu w ViewModelu.
     */
    private fun setupDateEditText(
        editText: EditText,
        stateId: String,
        updateAction: (AddReceiptToClientViewModel.ReceiptFieldsState, String) -> Unit
    ) {
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

                // Aktualizuj stan w ViewModelu
                viewModel.updateReceiptFieldState(stateId) { state ->
                    updateAction(state, current)
                }

                // Ustaw sformatowany tekst w EditText
                editText.setText(current)

                // Przywróć pozycję kursora
                try {
                    val lengthDiff = current.length - textLengthBefore
                    var newCursorPos = cursorPosBefore + lengthDiff
                    if (lengthDiff != 0 && (cursorPosBefore == 2 || cursorPosBefore == 5)) {
                        if (lengthDiff > 0) newCursorPos++ else newCursorPos--
                    }
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