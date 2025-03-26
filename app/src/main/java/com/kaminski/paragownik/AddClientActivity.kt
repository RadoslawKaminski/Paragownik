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
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kaminski.paragownik.viewmodel.AddClientViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale

/**
 * Aktywność odpowiedzialna za dodawanie nowego klienta wraz z jednym lub wieloma paragonami.
 */
class AddClientActivity : AppCompatActivity() {

    // --- Widoki UI ---
    private lateinit var storeNumberEditTextFirstReceipt: EditText
    private lateinit var receiptNumberEditText: EditText
    private lateinit var receiptDateEditText: EditText
    private lateinit var verificationDateEditText: EditText
    private lateinit var verificationDateTodayCheckBox: CheckBox
    private lateinit var clientDescriptionEditText: EditText
    private lateinit var clientAppNumberEditText: EditText // Nowe pole
    private lateinit var amoditNumberEditText: EditText    // Nowe pole
    private lateinit var addClientButton: Button
    private lateinit var addAdditionalReceiptButton: Button
    private lateinit var receiptsContainer: LinearLayout // Kontener na dynamiczne pola paragonów

    // --- ViewModels ---
    private lateinit var addClientViewModel: AddClientViewModel
    private lateinit var storeViewModel: StoreViewModel // Do pobierania numeru sklepu, jeśli ID przekazano

    // --- Dane pomocnicze ---
    // Lista przechowująca referencje do pól dynamicznie dodanych paragonów
    private val receiptFieldsList = ArrayList<ReceiptFields>()
    // ID sklepu przekazane z poprzedniej aktywności (jeśli dotyczy)
    private var storeIdFromIntent: Long = -1L

    /**
     * Struktura pomocnicza do przechowywania referencji do pól EditText
     * dla dynamicznie dodawanych sekcji paragonów.
     */
    private data class ReceiptFields(
        val storeNumberEditText: EditText?, // Null dla pierwszego paragonu (używamy dedykowanego pola)
        val receiptNumberEditText: EditText,
        val receiptDateEditText: EditText
    )

    /**
     * Struktura danych przekazywana do ViewModelu, zawierająca informacje
     * o pojedynczym paragonie do dodania.
     * Musi być publiczna lub wewnętrzna (internal), aby ViewModel miał do niej dostęp.
     */
    data class ReceiptData(
        val storeNumber: String,
        val receiptNumber: String,
        val receiptDate: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_client)

        // Inicjalizacja wszystkich widoków UI
        initializeViews()

        // Inicjalizacja ViewModeli
        addClientViewModel = ViewModelProvider(this).get(AddClientViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        // Sprawdzenie, czy aktywność została uruchomiona z kontekstem sklepu
        handleIntentExtras()

        // Konfiguracja formatowania i walidacji dla pól dat
        setupDateEditText(receiptDateEditText)
        setupDateEditText(verificationDateEditText)

        // Dodanie pól pierwszego paragonu do listy zarządzającej
        // Pierwszy element ma null dla storeNumberEditText, bo używamy dedykowanego pola
        receiptFieldsList.add(ReceiptFields(null, receiptNumberEditText, receiptDateEditText))

        // Konfiguracja listenerów dla interaktywnych elementów UI
        setupListeners()
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI z layoutu.
     */
    private fun initializeViews() {
        storeNumberEditTextFirstReceipt = findViewById(R.id.receiptStoreNumberEditText)
        receiptNumberEditText = findViewById(R.id.receiptNumberEditText)
        receiptDateEditText = findViewById(R.id.receiptDateEditText)
        verificationDateEditText = findViewById(R.id.verificationDateEditText)
        verificationDateTodayCheckBox = findViewById(R.id.verificationDateTodayCheckBox)
        clientDescriptionEditText = findViewById(R.id.clientDescriptionEditText)
        clientAppNumberEditText = findViewById(R.id.clientAppNumberEditText) // Inicjalizacja nowego pola
        amoditNumberEditText = findViewById(R.id.amoditNumberEditText)       // Inicjalizacja nowego pola
        addClientButton = findViewById(R.id.addClientButton)
        addAdditionalReceiptButton = findViewById(R.id.addAdditionalReceiptButton)
        receiptsContainer = findViewById(R.id.receiptsContainer)
    }

    /**
     * Sprawdza, czy Intent zawiera dodatkowe dane (np. ID sklepu) i odpowiednio reaguje.
     */
    private fun handleIntentExtras() {
        if (intent.hasExtra("STORE_ID")) {
            storeIdFromIntent = intent.getLongExtra("STORE_ID", -1L)
            // Jeśli ID sklepu jest przekazane, zablokuj edycję numeru sklepu dla pierwszego paragonu
            storeNumberEditTextFirstReceipt.isEnabled = false

            // Asynchronicznie pobierz numer sklepu i ustaw go w polu EditText
            lifecycleScope.launch {
                val store = storeViewModel.getStoreById(storeIdFromIntent)
                store?.let {
                    storeNumberEditTextFirstReceipt.setText(it.storeNumber)
                }
            }
        }
    }

    /**
     * Ustawia listenery dla przycisków i checkboxa.
     */
    private fun setupListeners() {
        // Listener dla checkboxa "Dzisiaj" (data weryfikacji)
        verificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Ustaw aktualną datę i wyłącz pole edycji
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                verificationDateEditText.setText(currentDate)
                verificationDateEditText.isEnabled = false
            } else {
                // Włącz pole edycji (nie czyścimy go, użytkownik może chcieć edytować)
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
    }

    /**
     * Główna funkcja zapisu. Zbiera dane z formularza, waliduje je
     * i wywołuje metodę transakcyjną w ViewModelu.
     */
    private fun saveClientAndReceipts() {
        // 1. Zbierz dane klienta z pól EditText
        val clientDescription = clientDescriptionEditText.text.toString().trim()
        val clientAppNumber = clientAppNumberEditText.text.toString().trim()
        val amoditNumber = amoditNumberEditText.text.toString().trim()
        // photoUri będzie dodane w przyszłości

        // 2. Zbierz datę weryfikacji (tylko dla pierwszego paragonu)
        val verificationDateString = verificationDateEditText.text.toString().trim()

        // 3. Zbierz dane wszystkich paragonów (pierwszego i dodatkowych)
        val receiptsToAdd = mutableListOf<ReceiptData>()
        var hasEmptyFields = false // Flaga do podstawowej walidacji pustych pól

        // Przetwarzanie danych pierwszego paragonu
        val firstStoreNumber = storeNumberEditTextFirstReceipt.text.toString().trim()
        val firstReceiptNumber = receiptNumberEditText.text.toString().trim()
        val firstReceiptDate = receiptDateEditText.text.toString().trim()

        // Podstawowa walidacja pierwszego paragonu
        if (firstStoreNumber.isEmpty() || firstReceiptNumber.isEmpty() || firstReceiptDate.isEmpty() || !isValidDate(firstReceiptDate)) {
            hasEmptyFields = true
            if (!isValidDate(firstReceiptDate) && firstReceiptDate.isNotEmpty()) {
                Toast.makeText(this, "Nieprawidłowy format daty pierwszego paragonu (DD-MM-YYYY)", Toast.LENGTH_LONG).show()
                return // Zatrzymaj, jeśli data jest źle sformatowana
            }
        } else {
            receiptsToAdd.add(ReceiptData(firstStoreNumber, firstReceiptNumber, firstReceiptDate))
        }

        // Przetwarzanie danych dodatkowych paragonów (jeśli pierwszy był poprawny)
        if (!hasEmptyFields) {
            for (receiptFields in receiptFieldsList.drop(1)) { // Pomiń pierwszy element listy (już przetworzony)
                val storeNumber = receiptFields.storeNumberEditText?.text.toString().trim()
                val receiptNumber = receiptFields.receiptNumberEditText.text.toString().trim()
                val receiptDate = receiptFields.receiptDateEditText.text.toString().trim()

                // Podstawowa walidacja dodatkowych paragonów
                if (storeNumber.isEmpty() || receiptNumber.isEmpty() || receiptDate.isEmpty() || !isValidDate(receiptDate)) {
                    hasEmptyFields = true
                    if (!isValidDate(receiptDate) && receiptDate.isNotEmpty()) {
                        Toast.makeText(this, "Nieprawidłowy format daty w dodatkowym paragonie (DD-MM-YYYY)", Toast.LENGTH_LONG).show()
                        return // Zatrzymaj, jeśli data jest źle sformatowana
                    }
                    break // Przerwij pętlę, jeśli znaleziono puste pole lub zły format daty
                }
                receiptsToAdd.add(ReceiptData(storeNumber, receiptNumber, receiptDate))
            }
        }

        // 4. Końcowa walidacja przed wysłaniem do ViewModelu
        if (hasEmptyFields) {
            Toast.makeText(this, "Wypełnij wszystkie wymagane pola paragonów (numer, data, sklep) poprawnym formatem daty", Toast.LENGTH_LONG).show()
            return // Zakończ, jeśli są błędy walidacji
        }
        if (receiptsToAdd.isEmpty()) {
            // Teoretycznie nieosiągalne przy obecnej logice, ale dla pewności
            Toast.makeText(this, "Dodaj przynajmniej jeden paragon", Toast.LENGTH_SHORT).show()
            return
        }

        // 5. Wywołanie metody ViewModelu w korutynie
        lifecycleScope.launch {
            // Wywołaj metodę transakcyjną, przekazując zebrane dane
            // Użyj takeIf { it.isNotEmpty() } aby przekazać null dla pustych pól opcjonalnych
            val result = addClientViewModel.addClientWithReceiptsTransactionally(
                clientDescription = clientDescription.takeIf { it.isNotEmpty() },
                clientAppNumber = clientAppNumber.takeIf { it.isNotEmpty() },
                amoditNumber = amoditNumber.takeIf { it.isNotEmpty() },
                photoUri = null, // Na razie brak obsługi zdjęcia
                receiptsData = receiptsToAdd,
                verificationDateString = verificationDateString.takeIf { it.isNotEmpty() }
            )

            // 6. Obsługa wyniku zwróconego przez ViewModel
            handleSaveResult(result)
        }
    }

    /**
     * Sprawdza, czy podany ciąg znaków reprezentuje poprawną datę w formacie DD-MM-YYYY.
     */
    private fun isValidDate(dateStr: String): Boolean {
        if (dateStr.length != 10) return false // Podstawowa długość
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false // Ścisłe sprawdzanie
        return try {
            dateFormat.parse(dateStr) // Spróbuj sparsować
            true
        } catch (e: ParseException) {
            false // Błąd parsowania oznacza nieprawidłowy format
        }
    }

    /**
     * Wyświetla odpowiedni komunikat Toast w zależności od wyniku operacji zapisu.
     * @param result Wynik operacji zwrócony przez ViewModel ([AddClientViewModel.AddResult]).
     */
    private fun handleSaveResult(result: AddClientViewModel.AddResult) {
        when (result) {
            AddClientViewModel.AddResult.SUCCESS -> {
                Toast.makeText(this@AddClientActivity, "Klient i paragony dodane pomyślnie", Toast.LENGTH_SHORT).show()
                finish() // Zamknij aktywność po sukcesie
            }
            AddClientViewModel.AddResult.ERROR_DATE_FORMAT -> {
                Toast.makeText(this@AddClientActivity, "Błąd: Nieprawidłowy format daty (DD-MM-YYYY)", Toast.LENGTH_LONG).show()
            }
            AddClientViewModel.AddResult.ERROR_DUPLICATE_RECEIPT -> {
                Toast.makeText(this@AddClientActivity, "Błąd: Paragon o podanym numerze, dacie i sklepie już istnieje", Toast.LENGTH_LONG).show()
            }
            AddClientViewModel.AddResult.ERROR_STORE_NUMBER_MISSING -> {
                Toast.makeText(this@AddClientActivity, "Błąd: Brak numeru drogerii dla jednego z paragonów", Toast.LENGTH_LONG).show()
            }
            AddClientViewModel.AddResult.ERROR_DATABASE -> {
                Toast.makeText(this@AddClientActivity, "Błąd: Wystąpił problem z bazą danych", Toast.LENGTH_LONG).show()
            }
            AddClientViewModel.AddResult.ERROR_UNKNOWN -> {
                Toast.makeText(this@AddClientActivity, "Błąd: Wystąpił nieznany błąd", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Influje layout `additional_receipt_fields.xml`, dodaje go do kontenera
     * i konfiguruje obsługę usuwania dla nowo dodanego zestawu pól.
     */
    private fun addNewReceiptFields() {
        val inflater = LayoutInflater.from(this)
        // Utwórz widok na podstawie layoutu XML, nie dołączając go jeszcze do rodzica (trzeci argument false)
        val receiptFieldsView = inflater.inflate(R.layout.additional_receipt_fields, receiptsContainer, false)

        // Znajdź widoki wewnątrz nowo utworzonego layoutu
        val storeNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalStoreNumberEditText)
        val receiptNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptNumberEditText)
        val receiptDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptDateEditText)
        val removeReceiptButton = receiptFieldsView.findViewById<ImageButton>(R.id.removeReceiptButton)

        // Skonfiguruj formatowanie daty dla nowego pola
        setupDateEditText(receiptDateEditText)

        // Stwórz obiekt przechowujący referencje do nowo utworzonych pól
        val newReceiptFields = ReceiptFields(
            storeNumberEditText,
            receiptNumberEditText,
            receiptDateEditText
        )
        // Dodaj referencje do listy zarządzającej
        receiptFieldsList.add(newReceiptFields)
        // Dodaj fizycznie widok do kontenera na ekranie
        receiptsContainer.addView(receiptFieldsView)

        // Ustaw listener dla przycisku usuwania tego konkretnego zestawu pól
        removeReceiptButton.setOnClickListener {
            // Usuń widok z layoutu
            receiptsContainer.removeView(receiptFieldsView)
            // Usuń referencje z listy zarządzającej
            receiptFieldsList.remove(newReceiptFields)
        }
    }

    /**
     * Konfiguruje [EditText] do automatycznego formatowania wpisywanej daty
     * do formatu DD-MM-YYYY oraz podstawowej walidacji.
     * @param editText Pole EditText do skonfigurowania.
     */
    private fun setupDateEditText(editText: EditText) {
        // Ustawienie typu wejściowego - pozwala na cyfry, ale TextWatcher zajmie się resztą
        editText.inputType = InputType.TYPE_CLASS_NUMBER // Użyjemy klawiatury numerycznej

        editText.addTextChangedListener(object : TextWatcher {
            private var current = ""
            private val ddmmyyyy = "DDMMYYYY"
            private val cal = java.util.Calendar.getInstance()
            private var isFormatting: Boolean = false // Flaga zapobiegająca rekurencji

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return // Jeśli już formatujemy, wyjdź
                isFormatting = true // Ustaw flagę

                val userInput = s.toString()
                if (userInput == current) {
                    isFormatting = false
                    return // Bez zmian, wyjdź
                }

                // Usuń wszystkie znaki niebędące cyframi
                val digitsOnly = userInput.replace("[^\\d]".toRegex(), "")
                val len = digitsOnly.length

                // Formatowanie DD-MM-YYYY
                val sb = StringBuilder()
                var cursorPos = editText.selectionStart // Zapamiętaj pozycję kursora

                if (len >= 1) {
                    sb.append(digitsOnly.substring(0, minOf(len, 2))) // DD
                    if (len > 2 || (userInput.length > sb.length && userInput[sb.length] == '-')) {
                        if (sb.length == 2) sb.append('-')
                        if (len >= 3) {
                            sb.append(digitsOnly.substring(2, minOf(len, 4))) // MM
                            if (len > 4 || (userInput.length > sb.length && userInput[sb.length] == '-')) {
                                if (sb.length == 5) sb.append('-')
                                if (len >= 5) {
                                    sb.append(digitsOnly.substring(4, minOf(len, 8))) // YYYY
                                }
                            }
                        }
                    }
                }

                current = sb.toString()
                editText.setText(current)

                // Przywróć pozycję kursora, dbając o granice
                // Proste ustawienie na końcu jest często wystarczające przy dodawaniu
                try {
                    editText.setSelection(minOf(cursorPos + 1 , current.length)) // Spróbuj przesunąć kursor
                } catch (e: Exception) {
                    editText.setSelection(current.length) // W razie problemu ustaw na końcu
                }


                isFormatting = false // Zakończ formatowanie
            }
        })
    }
}