package com.kaminski.paragownik

// import android.view.View // Nieużywany
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
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
import java.util.Locale

/**
 * Aktywność odpowiedzialna za dodawanie nowego klienta wraz z jednym lub wieloma paragonami.
 * Umożliwia wprowadzenie danych pierwszego paragonu, danych klienta oraz dynamiczne dodawanie
 * kolejnych paragonów z innych drogerii.
 */
class AddClientActivity : AppCompatActivity() {

    // --- Widoki UI ---
    // Pola EditText dla pierwszego paragonu
    private lateinit var storeNumberEditTextFirstReceipt: EditText // Numer drogerii (może być zablokowany)
    private lateinit var receiptNumberEditText: EditText           // Numer paragonu
    private lateinit var receiptDateEditText: EditText             // Data paragonu (formatowana)
    private lateinit var verificationDateEditText: EditText        // Data weryfikacji (formatowana)
    private lateinit var verificationDateTodayCheckBox: CheckBox   // Checkbox "Dzisiaj" dla daty weryfikacji

    // Pola EditText dla danych klienta
    private lateinit var clientDescriptionEditText: EditText       // Opis klienta
    private lateinit var clientAppNumberEditText: EditText         // Numer aplikacji klienta
    private lateinit var amoditNumberEditText: EditText            // Numer Amodit

    // Przyciski
    private lateinit var addClientButton: Button                   // Przycisk zapisu klienta i paragonów
    private lateinit var addAdditionalReceiptButton: Button        // Przycisk dodawania kolejnego paragonu

    // Kontener na dynamicznie dodawane pola paragonów
    private lateinit var receiptsContainer: LinearLayout

    // --- ViewModels ---
    private lateinit var addClientViewModel: AddClientViewModel // ViewModel do logiki dodawania
    private lateinit var storeViewModel: StoreViewModel         // ViewModel do pobierania danych sklepu

    // --- Dane pomocnicze ---
    // Lista przechowująca referencje do widoków (EditText) dynamicznie dodanych paragonów.
    // Ułatwia zbieranie danych i zarządzanie tymi widokami.
    private val receiptFieldsList = ArrayList<ReceiptFields>()

    // ID sklepu przekazane z MainActivity lub ReceiptListActivity, jeśli użytkownik dodaje
    // paragon w kontekście konkretnego sklepu. -1L oznacza brak przekazanego ID.
    private var storeIdFromIntent: Long = -1L

    /**
     * Struktura pomocnicza do przechowywania referencji do pól EditText
     * dla dynamicznie dodawanych sekcji paragonów.
     * `storeNumberEditText` jest nullable, bo dla pierwszego paragonu używamy dedykowanego pola.
     */
    private data class ReceiptFields(
        val storeNumberEditText: EditText?,
        val receiptNumberEditText: EditText,
        val receiptDateEditText: EditText
    )

    /**
     * Struktura danych przekazywana do [AddClientViewModel.addClientWithReceiptsTransactionally].
     * Zawiera informacje o pojedynczym paragonie do dodania (numer sklepu, numer paragonu, data).
     * Musi być publiczna lub `internal`, aby ViewModel miał do niej dostęp.
     */
    data class ReceiptData(
        val storeNumber: String,
        val receiptNumber: String,
        val receiptDate: String // Data jako String, parsowanie odbywa się w ViewModelu
    )

    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     * Inicjalizuje UI, ViewModels, obsługuje przekazane dane (Intent) i ustawia listenery.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_client) // Ustawienie layoutu dla tej aktywności

        // Inicjalizacja wszystkich widoków UI z layoutu XML
        initializeViews()

        // Inicjalizacja ViewModeli za pomocą ViewModelProvider
        addClientViewModel = ViewModelProvider(this).get(AddClientViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        // Sprawdzenie, czy aktywność została uruchomiona z dodatkowym ID sklepu
        handleIntentExtras()

        // Konfiguracja TextWatcherów do automatycznego formatowania i walidacji pól dat
        setupDateEditText(receiptDateEditText)
        setupDateEditText(verificationDateEditText)

        // Dodanie referencji do pól pierwszego paragonu do listy zarządzającej.
        // Pierwszy element ma null dla storeNumberEditText, bo używamy dedykowanego pola.
        receiptFieldsList.add(ReceiptFields(null, receiptNumberEditText, receiptDateEditText))

        // Konfiguracja listenerów dla przycisków i checkboxa
        setupListeners()
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI z layoutu `activity_add_client.xml`.
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
        addClientButton = findViewById(R.id.addClientButton)
        addAdditionalReceiptButton = findViewById(R.id.addAdditionalReceiptButton)
        receiptsContainer = findViewById(R.id.receiptsContainer)
    }

    /**
     * Sprawdza, czy Intent, który uruchomił tę Aktywność, zawiera dodatkowe dane "STORE_ID".
     * Jeśli tak, pobiera ID, blokuje edycję pola numeru sklepu dla pierwszego paragonu
     * i asynchronicznie wczytuje numer sklepu, aby go wyświetlić.
     */
    private fun handleIntentExtras() {
        // Sprawdź, czy Intent zawiera klucz "STORE_ID"
        if (intent.hasExtra("STORE_ID")) {
            // Pobierz wartość Long, domyślnie -1L jeśli klucza nie ma (choć już sprawdziliśmy hasExtra)
            storeIdFromIntent = intent.getLongExtra("STORE_ID", -1L)
            // Jeśli ID sklepu jest poprawne (różne od -1L)
            if (storeIdFromIntent != -1L) {
                // Zablokuj edycję numeru sklepu dla pierwszego paragonu
                storeNumberEditTextFirstReceipt.isEnabled = false

                // Uruchom korutynę w zakresie cyklu życia Aktywności do pobrania danych sklepu
                lifecycleScope.launch {
                    // Pobierz obiekt Store z ViewModelu na podstawie ID
                    val store = storeViewModel.getStoreById(storeIdFromIntent)
                    // Jeśli sklep został znaleziony (nie jest null)
                    store?.let {
                        // Ustaw numer sklepu w polu EditText
                        storeNumberEditTextFirstReceipt.setText(it.storeNumber)
                    }
                }
            }
        }
    }

    /**
     * Ustawia listenery dla interaktywnych elementów UI:
     * - CheckBox "Dzisiaj" dla daty weryfikacji.
     * - Przycisk dodawania kolejnego paragonu.
     * - Przycisk zapisu klienta i paragonów.
     */
    private fun setupListeners() {
        // Listener dla zmiany stanu checkboxa "Dzisiaj"
        verificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Jeśli zaznaczony:
                // Pobierz aktualną datę w formacie DD-MM-YYYY
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                // Ustaw datę w polu EditText
                verificationDateEditText.setText(currentDate)
                // Wyłącz możliwość edycji pola
                verificationDateEditText.isEnabled = false
            } else {
                // Jeśli odznaczony:
                // Włącz możliwość edycji pola (nie czyścimy go, użytkownik może chcieć edytować)
                verificationDateEditText.isEnabled = true
            }
        }

        // Listener dla kliknięcia przycisku dodawania kolejnego paragonu
        addAdditionalReceiptButton.setOnClickListener {
            // Wywołaj funkcję dodającą nowy zestaw pól dla paragonu
            addNewReceiptFields()
        }

        // Listener dla kliknięcia głównego przycisku zapisu
        addClientButton.setOnClickListener {
            // Wywołaj funkcję zbierającą dane, walidującą i inicjującą zapis
            saveClientAndReceipts()
        }
    }

    /**
     * Główna funkcja zapisu. Wykonywana po kliknięciu przycisku "Zapisz".
     * 1. Zbiera dane klienta z pól EditText.
     * 2. Zbiera datę weryfikacji (tylko dla pierwszego paragonu).
     * 3. Zbiera dane wszystkich paragonów (pierwszego i dynamicznie dodanych).
     * 4. Przeprowadza podstawową walidację (puste pola, format daty) w UI.
     * 5. Jeśli walidacja w UI przejdzie, wywołuje metodę transakcyjną w ViewModelu.
     * 6. Obsługuje wynik zwrócony przez ViewModel (sukces lub różne typy błędów).
     */
    private fun saveClientAndReceipts() {
        // 1. Zbierz dane klienta, usuwając białe znaki z początku i końca
        val clientDescription = clientDescriptionEditText.text.toString().trim()
        val clientAppNumber = clientAppNumberEditText.text.toString().trim()
        val amoditNumber = amoditNumberEditText.text.toString().trim()
        // photoUri będzie dodane w przyszłości, na razie null

        // 2. Zbierz datę weryfikacji (tylko dla pierwszego paragonu)
        val verificationDateString = verificationDateEditText.text.toString().trim()

        // 3. Przygotuj listę do przechowywania danych paragonów do dodania
        val receiptsToAdd = mutableListOf<ReceiptData>()
        var hasEmptyFields = false // Flaga do podstawowej walidacji pustych pól w UI

        // --- Przetwarzanie danych PIERWSZEGO paragonu ---
        val firstStoreNumber = storeNumberEditTextFirstReceipt.text.toString().trim()
        val firstReceiptNumber = receiptNumberEditText.text.toString().trim()
        val firstReceiptDate = receiptDateEditText.text.toString().trim()

        // Podstawowa walidacja pierwszego paragonu w UI
        if (firstStoreNumber.isEmpty() || firstReceiptNumber.isEmpty() || firstReceiptDate.isEmpty()) {
            hasEmptyFields = true // Zaznacz, że znaleziono puste pole
        } else if (!isValidDate(firstReceiptDate)) { // Sprawdź format daty, jeśli pole nie jest puste
            Toast.makeText(this, R.string.error_invalid_receipt_date_format, Toast.LENGTH_LONG).show()
            return // Zatrzymaj proces zapisu, jeśli data jest źle sformatowana
        } else {
            // Jeśli wszystko OK, dodaj dane pierwszego paragonu do listy
            receiptsToAdd.add(ReceiptData(firstStoreNumber, firstReceiptNumber, firstReceiptDate))
        }

        // --- Przetwarzanie danych DODATKOWYCH paragonów (jeśli pierwszy był poprawny) ---
        if (!hasEmptyFields) { // Kontynuuj tylko, jeśli pierwszy paragon nie miał pustych pól
            // Iteruj przez listę referencji do pól dodatkowych paragonów (pomijając pierwszy)
            for (receiptFields in receiptFieldsList.drop(1)) {
                // Pobierz dane z pól EditText
                val storeNumberEditText = receiptFields.storeNumberEditText
                // Sprawdzenie null dla bezpieczeństwa, chociaż w tej logice nie powinien być null
                if (storeNumberEditText == null) {
                    Log.e("AddClientActivity", "Błąd krytyczny: storeNumberEditText jest null w pętli dodatkowych paragonów!")
                    Toast.makeText(this, "Wystąpił błąd wewnętrzny.", Toast.LENGTH_LONG).show()
                    return // Przerwij zapis
                }
                val storeNumber = storeNumberEditText.text.toString().trim()
                val receiptNumber = receiptFields.receiptNumberEditText.text.toString().trim()
                val receiptDate = receiptFields.receiptDateEditText.text.toString().trim()

                // Podstawowa walidacja dodatkowych paragonów w UI
                if (storeNumber.isEmpty() || receiptNumber.isEmpty() || receiptDate.isEmpty()) {
                    hasEmptyFields = true // Zaznacz, że znaleziono puste pole
                    break // Przerwij pętlę, nie ma sensu sprawdzać dalej
                } else if (!isValidDate(receiptDate)) { // Sprawdź format daty
                    Toast.makeText(this, R.string.error_invalid_additional_receipt_date_format, Toast.LENGTH_LONG).show()
                    return // Zatrzymaj proces zapisu
                } else {
                    // Jeśli wszystko OK, dodaj dane dodatkowego paragonu do listy
                    receiptsToAdd.add(ReceiptData(storeNumber, receiptNumber, receiptDate))
                }
            }
        }

        // 4. Końcowa walidacja przed wysłaniem do ViewModelu
        if (hasEmptyFields) {
            // Jeśli którekolwiek z wymaganych pól paragonu było puste
            Toast.makeText(this, R.string.error_fill_required_receipt_fields, Toast.LENGTH_LONG).show()
            return // Zakończ funkcję
        }
        if (receiptsToAdd.isEmpty()) {
            // Teoretycznie nieosiągalne przy obecnej logice (bo pierwszy paragon jest wymagany),
            // ale dodane dla pewności.
            Toast.makeText(this, R.string.error_add_at_least_one_receipt, Toast.LENGTH_SHORT).show()
            return
        }

        // 5. Wywołanie metody ViewModelu w korutynie
        lifecycleScope.launch {
            // Wywołaj metodę transakcyjną w ViewModelu, przekazując zebrane dane.
            // Użyj `takeIf { it.isNotEmpty() }` aby przekazać `null` dla pustych pól opcjonalnych (opis, numery klienta, data weryfikacji).
            val result = addClientViewModel.addClientWithReceiptsTransactionally(
                clientDescription = clientDescription.takeIf { it.isNotEmpty() },
                clientAppNumber = clientAppNumber.takeIf { it.isNotEmpty() },
                amoditNumber = amoditNumber.takeIf { it.isNotEmpty() },
                photoUri = null, // Na razie brak obsługi zdjęcia
                receiptsData = receiptsToAdd, // Lista danych paragonów
                verificationDateString = verificationDateString.takeIf { it.isNotEmpty() } // Data weryfikacji lub null
            )

            // 6. Obsługa wyniku zwróconego przez ViewModel
            handleSaveResult(result)
        }
    }

    /**
     * Sprawdza, czy podany ciąg znaków reprezentuje poprawną datę w formacie DD-MM-YYYY.
     * Używa [SimpleDateFormat] ze ścisłym sprawdzaniem (`isLenient = false`).
     * @param dateStr Ciąg znaków do sprawdzenia.
     * @return `true` jeśli format jest poprawny, `false` w przeciwnym razie.
     */
    private fun isValidDate(dateStr: String): Boolean {
        // Podstawowe sprawdzenie długości (musi być 10 znaków: DD-MM-YYYY)
        if (dateStr.length != 10) return false
        // Utwórz formatter daty
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        // Ustaw ścisłe sprawdzanie formatu (np. nie zaakceptuje 32-13-2024)
        dateFormat.isLenient = false
        return try {
            // Spróbuj sparsować datę
            dateFormat.parse(dateStr)
            // Jeśli parsowanie się udało, format jest poprawny
            true
        } catch (e: ParseException) {
            // Jeśli wystąpił błąd parsowania, format jest nieprawidłowy
            false
        }
    }

    /**
     * Wyświetla odpowiedni komunikat Toast w zależności od wyniku operacji zapisu
     * zwróconego przez [AddClientViewModel].
     * W przypadku sukcesu zamyka Aktywność.
     * @param result Wynik operacji typu [AddClientViewModel.AddResult].
     */
    private fun handleSaveResult(result: AddClientViewModel.AddResult) {
        val message = when (result) {
            AddClientViewModel.AddResult.SUCCESS -> {
                finish() // Zamknij aktywność po pomyślnym dodaniu
                "Klient i paragony dodane pomyślnie" // Zwróć tekst do wyświetlenia
            }
            AddClientViewModel.AddResult.ERROR_DATE_FORMAT -> "Błąd: Nieprawidłowy format daty (DD-MM-YYYY)"
            AddClientViewModel.AddResult.ERROR_DUPLICATE_RECEIPT -> "Błąd: Paragon o podanym numerze, dacie i sklepie już istnieje"
            AddClientViewModel.AddResult.ERROR_STORE_NUMBER_MISSING -> "Błąd: Brak numeru drogerii dla jednego z paragonów"
            AddClientViewModel.AddResult.ERROR_DATABASE -> "Błąd: Wystąpił problem z bazą danych"
            AddClientViewModel.AddResult.ERROR_UNKNOWN -> "Błąd: Wystąpił nieznany błąd"
        }
        // Wyświetl odpowiedni komunikat
        Toast.makeText(this@AddClientActivity, message, if (result == AddClientViewModel.AddResult.SUCCESS) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }

    /**
     * Influje (tworzy) widok z layoutu `additional_receipt_fields.xml`,
     * dodaje go do kontenera `receiptsContainer`, konfiguruje formatowanie daty
     * dla nowego pola daty, dodaje referencje do nowo utworzonych pól do listy `receiptFieldsList`
     * i ustawia listener dla przycisku usuwania tego konkretnego zestawu pól.
     */
    private fun addNewReceiptFields() {
        // Pobierz LayoutInflater systemowy
        val inflater = LayoutInflater.from(this)
        // Utwórz widok na podstawie layoutu XML.
        // `receiptsContainer` jest przekazany jako rodzic, ale `false` oznacza,
        // że widok nie jest jeszcze do niego dołączany (zrobimy to ręcznie).
        val receiptFieldsView = inflater.inflate(R.layout.additional_receipt_fields, receiptsContainer, false)

        // Znajdź widoki EditText i ImageButton wewnątrz nowo utworzonego layoutu
        val storeNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalStoreNumberEditText)
        val receiptNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptNumberEditText)
        val receiptDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptDateEditText)
        val removeReceiptButton = receiptFieldsView.findViewById<ImageButton>(R.id.removeReceiptButton)

        // Skonfiguruj formatowanie daty dla nowego pola daty
        setupDateEditText(receiptDateEditText)

        // Stwórz obiekt przechowujący referencje do nowo utworzonych pól
        val newReceiptFields = ReceiptFields(
            storeNumberEditText, // Przekazujemy referencję do pola numeru sklepu
            receiptNumberEditText,
            receiptDateEditText
        )
        // Dodaj referencje do listy zarządzającej
        receiptFieldsList.add(newReceiptFields)
        // Dodaj fizycznie widok (cały LinearLayout z additional_receipt_fields.xml) do kontenera na ekranie
        receiptsContainer.addView(receiptFieldsView)

        // Ustaw listener dla przycisku usuwania (czerwony minus) tego konkretnego zestawu pól
        removeReceiptButton.setOnClickListener {
            // Usuń widok (LinearLayout) z kontenera `receiptsContainer`
            receiptsContainer.removeView(receiptFieldsView)
            // Usuń również referencje do pól tego widoku z listy zarządzającej
            receiptFieldsList.remove(newReceiptFields)
        }
    }

    /**
     * Konfiguruje [EditText] do automatycznego formatowania daty w formacie DD-MM-YYYY
     * podczas wpisywania i edycji, z poprawioną obsługą kursora.
     * Używa [TextWatcher].
     * @param editText Pole EditText do skonfigurowania.
     */
    private fun setupDateEditText(editText: EditText) {
        // Ustawienie typu wejściowego na numeryczny
        editText.inputType = InputType.TYPE_CLASS_NUMBER

        editText.addTextChangedListener(object : TextWatcher {
            private var current = "" // Aktualny sformatowany tekst
            private var isFormatting: Boolean = false // Flaga zapobiegająca rekurencji
            private var cursorPosBefore: Int = 0 // Pozycja kursora PRZED zmianą
            private var textLengthBefore: Int = 0 // Długość tekstu PRZED zmianą

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isFormatting) return // Ignoruj zmiany wywołane przez setText
                // Zapamiętaj pozycję kursora i długość tekstu PRZED modyfikacją
                cursorPosBefore = editText.selectionStart
                textLengthBefore = s?.length ?: 0
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Ta metoda jest mniej przydatna przy tej strategii kursora
            }

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                val userInput = s.toString()
                if (userInput == current) {
                    isFormatting = false
                    return
                }

                // --- Logika formatowania (bez zmian) ---
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
                // --- Koniec logiki formatowania ---

                // Ustaw sformatowany tekst
                editText.setText(current)

                // --- Nowa Logika Ustawiania Kursora ---
                try {
                    // Oblicz różnicę w długości tekstu spowodowaną formatowaniem
                    val lengthDiff = current.length - textLengthBefore
                    // Oblicz nową pozycję kursora: pozycja przed zmianą + różnica w długości
                    var newCursorPos = cursorPosBefore + lengthDiff

                    // Upewnij się, że nowa pozycja jest w granicach [0, current.length]
                    newCursorPos = maxOf(0, minOf(newCursorPos, current.length))

                    // Ustaw obliczoną pozycję kursora
                    editText.setSelection(newCursorPos)

                } catch (e: Exception) {
                    // W razie błędu, ustaw kursor bezpiecznie na końcu
                    try { editText.setSelection(current.length) } catch (e2: Exception) { /* Ignoruj błąd fallbacku */ }
                    Log.e("DateTextWatcher", "Błąd podczas ustawiania pozycji kursora", e)
                }
                // --- Koniec Nowej Logiki Ustawiania Kursora ---

                isFormatting = false
            }
        })
    }
}
