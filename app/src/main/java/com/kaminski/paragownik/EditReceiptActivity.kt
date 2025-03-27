package com.kaminski.paragownik

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kaminski.paragownik.viewmodel.EditReceiptViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.text.ParseException // Potrzebne do obsługi błędów parsowania daty w TextWatcherze

/**
 * Aktywność odpowiedzialna za edycję istniejącego paragonu oraz powiązanych danych klienta.
 * Umożliwia również usunięcie pojedynczego paragonu lub całego klienta (wraz ze wszystkimi jego paragonami).
 */
class EditReceiptActivity : AppCompatActivity() {

    // --- Widoki UI ---
    // Pola edycyjne dla danych paragonu
    private lateinit var editReceiptStoreNumberEditText: EditText
    private lateinit var editReceiptNumberEditText: EditText
    private lateinit var editReceiptDateEditText: EditText
    private lateinit var editVerificationDateEditText: EditText
    private lateinit var editVerificationDateTodayCheckBox: CheckBox

    // Pola edycyjne dla danych klienta
    private lateinit var editClientDescriptionEditText: EditText
    private lateinit var editClientAppNumberEditText: EditText
    private lateinit var editAmoditNumberEditText: EditText

    // Przyciski akcji
    private lateinit var saveReceiptButton: Button
    private lateinit var deleteReceiptButton: Button
    private lateinit var deleteClientButton: Button

    // --- ViewModel ---
    private lateinit var editReceiptViewModel: EditReceiptViewModel

    // --- Dane pomocnicze ---
    // ID edytowanego paragonu, pobierane z Intentu
    private var receiptId: Long = -1L
    // Przechowuje ID aktualnie edytowanego klienta (potrzebne przy usuwaniu klienta)
    private var currentClientId: Long? = null
    // Referencja do korutyny ładującej dane, aby można ją było anulować (np. przy usuwaniu)
    private var loadDataJob: Job? = null

    /**
     * Metoda wywoływana przy tworzeniu Aktywności.
     * Inicjalizuje UI, ViewModel, pobiera ID paragonu z Intentu, ładuje dane
     * i ustawia listenery dla przycisków.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_receipt) // Ustawienie layoutu

        // Inicjalizacja wszystkich widoków UI
        initializeViews()

        // Inicjalizacja ViewModelu
        editReceiptViewModel = ViewModelProvider(this).get(EditReceiptViewModel::class.java)

        // Pobierz ID paragonu przekazane z poprzedniej aktywności (ReceiptListActivity)
        receiptId = intent.getLongExtra("RECEIPT_ID", -1L)

        // Sprawdź, czy ID paragonu jest poprawne. Jeśli nie, zakończ aktywność.
        if (receiptId == -1L) {
            Toast.makeText(this, "Błąd: Nieprawidłowe ID paragonu.", Toast.LENGTH_LONG).show()
            Log.e("EditReceiptActivity", "Nieprawidłowe RECEIPT_ID przekazane w Intencie.")
            finish() // Zamknij aktywność
            return   // Zakończ wykonywanie onCreate
        }

        // Ustaw formatowanie dla pól dat (DD-MM-YYYY)
        setupDateEditText(editReceiptDateEditText)
        setupDateEditText(editVerificationDateEditText)
        // Skonfiguruj działanie checkboxa "Dzisiaj" dla daty weryfikacji
        setupVerificationDateCheckBox()

        // Rozpocznij ładowanie danych paragonu i klienta z bazy danych
        loadReceiptData()

        // Ustaw listenery dla przycisków "Zapisz", "Usuń paragon", "Usuń klienta"
        setupButtonClickListeners()
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI z layoutu `activity_edit_receipt.xml`.
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
        saveReceiptButton = findViewById(R.id.saveReceiptButton)
        deleteReceiptButton = findViewById(R.id.deleteReceiptButton)
        deleteClientButton = findViewById(R.id.deleteClientButton)
    }

    /**
     * Ustawia listenery kliknięć dla przycisków zapisu i usuwania.
     */
    private fun setupButtonClickListeners() {
        saveReceiptButton.setOnClickListener {
            saveChanges() // Wywołaj funkcję zapisu zmian
        }
        deleteReceiptButton.setOnClickListener {
            showDeleteReceiptDialog() // Pokaż dialog potwierdzenia usunięcia paragonu
        }
        deleteClientButton.setOnClickListener {
            showDeleteClientDialog() // Pokaż dialog potwierdzenia usunięcia klienta
        }
    }


    /**
     * Wczytuje dane paragonu (wraz z klientem i numerem sklepu) z ViewModelu.
     * Używa `collectLatest` do obserwacji Flow, co zapewnia, że zawsze przetwarzane są najnowsze dane
     * i anuluje poprzednie przetwarzanie, jeśli nowe dane pojawią się szybko.
     * Anuluje poprzednie zadanie ładowania (`loadDataJob`), jeśli istniało.
     * Wypełnia pola formularza pobranymi danymi.
     * Loguje błąd tylko wtedy, gdy dane nie zostaną znalezione, a korutyna jest nadal aktywna
     * (zapobiega logowaniu podczas normalnego zamykania aktywności po usunięciu).
     */
    private fun loadReceiptData() {
        // Anuluj poprzednie zadanie ładowania danych, jeśli było aktywne
        loadDataJob?.cancel()
        // Uruchom nową korutynę w zakresie cyklu życia Aktywności i zapisz referencję do Joba
        loadDataJob = lifecycleScope.launch {
            // Obserwuj Flow zwracany przez ViewModel
            editReceiptViewModel.getReceiptWithClientAndStoreNumber(receiptId)
                .collectLatest { pair -> // collectLatest: przetwarza tylko najnowsze dane
                    // Rozpakuj parę (ReceiptWithClient?, String?)
                    val receiptWithClient = pair.first
                    val storeNumber = pair.second

                    // Sprawdź, czy otrzymano poprawne dane (paragon i klient nie są null)
                    if (receiptWithClient != null && receiptWithClient.client != null) {
                        // Dane są poprawne, można wypełnić formularz
                        val receipt = receiptWithClient.receipt
                        val client = receiptWithClient.client

                        // Zapisz ID klienta - będzie potrzebne do ewentualnego usunięcia klienta
                        currentClientId = client.id

                        // Wypełnij pola formularza danymi paragonu i klienta
                        editReceiptStoreNumberEditText.setText(storeNumber ?: "") // Użyj numeru sklepu z pary
                        editReceiptNumberEditText.setText(receipt.receiptNumber)

                        // Formatowanie daty paragonu
                        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        editReceiptDateEditText.setText(dateFormat.format(receipt.receiptDate))

                        // Obsługa daty weryfikacji (może być null)
                        receipt.verificationDate?.let { verificationDate ->
                            // Jeśli data weryfikacji istnieje
                            val formattedVerificationDate = dateFormat.format(verificationDate)
                            editVerificationDateEditText.setText(formattedVerificationDate)
                            // Sprawdź, czy data weryfikacji to dzisiaj
                            val todayDate = dateFormat.format(java.util.Calendar.getInstance().time)
                            if (formattedVerificationDate == todayDate) {
                                // Jeśli tak, zaznacz checkbox i zablokuj pole
                                editVerificationDateTodayCheckBox.isChecked = true
                                editVerificationDateEditText.isEnabled = false
                            } else {
                                // Jeśli nie, odznacz checkbox i odblokuj pole
                                editVerificationDateTodayCheckBox.isChecked = false
                                editVerificationDateEditText.isEnabled = true
                            }
                        } ?: run {
                            // Jeśli data weryfikacji jest null
                            editVerificationDateEditText.text.clear() // Wyczyść pole
                            editVerificationDateTodayCheckBox.isChecked = false // Odznacz checkbox
                            editVerificationDateEditText.isEnabled = true // Odblokuj pole
                        }

                        // Wypełnij pola danych klienta (użyj pustego stringa, jeśli wartość jest null)
                        editClientDescriptionEditText.setText(client.description ?: "")
                        editClientAppNumberEditText.setText(client.clientAppNumber ?: "")
                        editAmoditNumberEditText.setText(client.amoditNumber ?: "")
                        // Logika ładowania zdjęcia (photoUri) zostanie dodana później

                    } else {
                        // Dane są null (receiptWithClient lub client jest null) - prawdopodobnie paragon został usunięty
                        // Loguj błąd TYLKO jeśli korutyna jest nadal aktywna (nie została anulowana np. przez onDestroy lub usunięcie)
                        if (isActive) {
                            Log.e("EditReceiptActivity", "Nie znaleziono ReceiptWithClient lub Client dla receiptId: $receiptId (lub został usunięty w międzyczasie)")
                            // Można by tu dodać np. zamknięcie aktywności, jeśli dane zniknęły
                            // finish()
                            // Toast.makeText(this@EditReceiptActivity, "Paragon został usunięty.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
    }


    /**
     * Zbiera dane z formularza edycji i wywołuje metodę [EditReceiptViewModel.updateReceiptAndClient]
     * w celu zapisania zmian w bazie danych.
     * Przeprowadza podstawową walidację pustych pól w UI.
     * Obsługuje wynik operacji zapisu zwrócony przez ViewModel.
     */
    private fun saveChanges() {
        // Zbierz dane z pól formularza, usuwając białe znaki z początku i końca
        val storeNumberString = editReceiptStoreNumberEditText.text.toString().trim()
        val receiptNumber = editReceiptNumberEditText.text.toString().trim()
        val receiptDateString = editReceiptDateEditText.text.toString().trim()
        val verificationDateString = editVerificationDateEditText.text.toString().trim()
        val clientDescription = editClientDescriptionEditText.text.toString().trim()
        val clientAppNumber = editClientAppNumberEditText.text.toString().trim()
        val amoditNumber = editAmoditNumberEditText.text.toString().trim()
        // photoUri będzie dodane później

        // Podstawowa walidacja pustych pól wymaganych dla paragonu w UI
        if (storeNumberString.isEmpty() || receiptNumber.isEmpty() || receiptDateString.isEmpty()) {
            Toast.makeText(this, "Wypełnij numer sklepu, numer paragonu i datę paragonu.", Toast.LENGTH_LONG).show()
            return // Zakończ, jeśli brakuje podstawowych danych
        }

        // Wywołaj metodę ViewModelu w korutynie
        lifecycleScope.launch {
            // Wywołaj metodę aktualizującą dane w ViewModelu
            val result = editReceiptViewModel.updateReceiptAndClient(
                receiptId = receiptId, // ID edytowanego paragonu
                storeNumberString = storeNumberString,
                receiptNumber = receiptNumber,
                receiptDateString = receiptDateString,
                // Przekaż datę weryfikacji jako null, jeśli jest pusta
                verificationDateString = verificationDateString.takeIf { it.isNotEmpty() },
                // Przekaż dane klienta jako null, jeśli są puste
                clientDescription = clientDescription.takeIf { it.isNotEmpty() },
                clientAppNumber = clientAppNumber.takeIf { it.isNotEmpty() },
                amoditNumber = amoditNumber.takeIf { it.isNotEmpty() },
                photoUri = null // Na razie null
            )

            // Obsługa wyniku operacji zapisu zwróconego przez ViewModel
            when (result) {
                EditReceiptViewModel.EditResult.SUCCESS -> {
                    Toast.makeText(this@EditReceiptActivity, "Zmiany zapisane pomyślnie", Toast.LENGTH_SHORT).show()
                    finish() // Powrót do poprzedniej aktywności (ReceiptListActivity) po sukcesie
                }
                EditReceiptViewModel.EditResult.ERROR_NOT_FOUND -> {
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Nie znaleziono paragonu do aktualizacji.", Toast.LENGTH_LONG).show()
                }
                EditReceiptViewModel.EditResult.ERROR_DATE_FORMAT -> {
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Nieprawidłowy format daty (DD-MM-YYYY)", Toast.LENGTH_LONG).show()
                }
                EditReceiptViewModel.EditResult.ERROR_DUPLICATE_RECEIPT -> {
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Inny paragon o podanym numerze, dacie i sklepie już istnieje", Toast.LENGTH_LONG).show()
                }
                EditReceiptViewModel.EditResult.ERROR_STORE_NUMBER_MISSING -> {
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Numer drogerii nie może być pusty", Toast.LENGTH_LONG).show()
                }
                EditReceiptViewModel.EditResult.ERROR_DATABASE -> {
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Wystąpił problem z bazą danych podczas zapisu", Toast.LENGTH_LONG).show()
                }
                EditReceiptViewModel.EditResult.ERROR_UNKNOWN -> {
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Wystąpił nieznany błąd podczas zapisu", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Wyświetla standardowy [AlertDialog] z pytaniem, czy użytkownik na pewno chce usunąć
     * bieżący paragon. Informuje również o potencjalnym usunięciu klienta/drogerii.
     */
    private fun showDeleteReceiptDialog() {
        AlertDialog.Builder(this)
            .setTitle("Potwierdzenie usunięcia")
            .setMessage("Czy na pewno chcesz usunąć ten paragon?\n\n(Jeśli to ostatni paragon klienta lub drogerii, zostaną oni również usunięci).")
            .setPositiveButton("Usuń") { _, _ ->
                // Po kliknięciu "Usuń" wywołaj funkcję usuwającą paragon
                deleteReceipt()
            }
            .setNegativeButton("Anuluj", null) // "Anuluj" nic nie robi, dialog się zamyka
            .setIcon(android.R.drawable.ic_dialog_alert) // Standardowa ikona ostrzeżenia
            .show() // Pokaż dialog
    }

    /**
     * Wywołuje metodę [EditReceiptViewModel.deleteReceipt] w celu usunięcia bieżącego paragonu.
     * Najpierw pobiera aktualny obiekt paragonu z bazy (potrzebny jako argument dla metody ViewModelu).
     * Po pomyślnym usunięciu nawiguje z powrotem do [MainActivity], czyszcząc stos aktywności.
     */
    private fun deleteReceipt() {
        lifecycleScope.launch {
            // Pobierz aktualny obiekt paragonu z bazy danych.
            // Używamy `firstOrNull()` do jednorazowego pobrania najnowszej wartości z Flow.
            // Mapujemy wynik, aby uzyskać tylko obiekt `Receipt` lub `null`.
            val currentReceipt = editReceiptViewModel.getReceiptWithClientAndStoreNumber(receiptId)
                .map { it.first?.receipt } // Wyciągnij tylko paragon z pary (może być null)
                .firstOrNull()             // Pobierz pierwszą (i jedyną) emitowaną wartość lub null

            // Sprawdź, czy udało się pobrać paragon
            if (currentReceipt == null) {
                // Jeśli nie (np. został usunięty w międzyczasie), pokaż błąd i zakończ
                Toast.makeText(this@EditReceiptActivity, "Błąd: Nie można pobrać danych paragonu do usunięcia.", Toast.LENGTH_LONG).show()
                Log.e("EditReceiptActivity", "Nie udało się pobrać Receipt (id: $receiptId) do usunięcia.")
                return@launch // Zakończ korutynę
            }

            // Wywołaj metodę usuwania w ViewModelu, przekazując pobrany obiekt paragonu
            val result = editReceiptViewModel.deleteReceipt(currentReceipt)

            // Obsługa wyniku operacji usuwania
            when (result) {
                EditReceiptViewModel.EditResult.SUCCESS -> {
                    Toast.makeText(this@EditReceiptActivity, "Paragon usunięty", Toast.LENGTH_SHORT).show()
                    // Po pomyślnym usunięciu paragonu, wróć do MainActivity, czyszcząc stos
                    finishAffinity() // Zamknij bieżącą aktywność i wszystkie poprzednie w zadaniu
                    // Uruchom MainActivity jako nowe zadanie
                    startActivity(Intent(this@EditReceiptActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
                EditReceiptViewModel.EditResult.ERROR_NOT_FOUND -> {
                    // Ten błąd nie powinien wystąpić, bo sprawdziliśmy istnienie paragonu wcześniej
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Nie znaleziono paragonu do usunięcia.", Toast.LENGTH_LONG).show()
                }
                EditReceiptViewModel.EditResult.ERROR_DATABASE -> {
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Wystąpił problem z bazą danych podczas usuwania paragonu", Toast.LENGTH_LONG).show()
                }
                else -> { // Inne błędy (np. UNKNOWN)
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Wystąpił nieznany błąd podczas usuwania paragonu", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Wyświetla [AlertDialog] z ostrzeżeniem o konsekwencjach usunięcia klienta
     * (usunięcie wszystkich jego paragonów i potencjalnie pustych drogerii).
     * Pyta o potwierdzenie przed wykonaniem operacji.
     */
    private fun showDeleteClientDialog() {
        // Sprawdź, czy mamy zapisane ID klienta (powinno być ustawione w loadReceiptData)
        if (currentClientId == null) {
            Toast.makeText(this, "Błąd: Nie można zidentyfikować klienta do usunięcia.", Toast.LENGTH_LONG).show()
            Log.e("EditReceiptActivity", "Próba usunięcia klienta, ale currentClientId jest null.")
            return // Zakończ, jeśli nie znamy ID klienta
        }

        // Pokaż dialog potwierdzenia
        AlertDialog.Builder(this)
            .setTitle("Potwierdzenie usunięcia klienta")
            .setMessage("Czy na pewno chcesz usunąć tego klienta?\n\nUWAGA: Usunięcie klienta spowoduje USUNIĘCIE WSZYSTKICH jego paragonów! Drogerie, które staną się puste, również zostaną usunięte.")
            .setPositiveButton("Usuń Klienta") { _, _ ->
                // Po kliknięciu "Usuń Klienta" wywołaj funkcję usuwającą klienta
                deleteClient()
            }
            .setNegativeButton("Anuluj", null) // Anuluj nic nie robi
            .setIcon(android.R.drawable.ic_dialog_alert) // Ikona ostrzeżenia
            .show() // Pokaż dialog
    }

    /**
     * Wywołuje metodę [EditReceiptViewModel.deleteClient] w celu usunięcia klienta
     * (i kaskadowo jego paragonów).
     * Anuluje obserwację danych (`loadDataJob`), aby uniknąć błędów po usunięciu.
     * Po pomyślnym usunięciu nawiguje z powrotem do [MainActivity], czyszcząc stos aktywności.
     */
    private fun deleteClient() {
        // Pobierz ID klienta do usunięcia (ponowne sprawdzenie dla bezpieczeństwa)
        val clientIdToDelete = currentClientId ?: return

        lifecycleScope.launch {
            // Utwórz tymczasowy obiekt Client tylko z ID. ViewModel i tak pobierze pełny obiekt z bazy.
            // Opis i inne pola nie są potrzebne do operacji delete.
            val clientStub = com.kaminski.paragownik.data.Client(id = clientIdToDelete, description = null)
            // Wywołaj metodę usuwania w ViewModelu
            val result = editReceiptViewModel.deleteClient(clientStub)

            // Obsługa wyniku operacji usuwania klienta
            when (result) {
                EditReceiptViewModel.EditResult.SUCCESS -> {
                    Toast.makeText(this@EditReceiptActivity, "Klient i jego paragony usunięte", Toast.LENGTH_SHORT).show()
                    // Anuluj korutynę obserwującą dane (loadDataJob), aby uniknąć prób
                    // dostępu do usuniętych danych po zamknięciu aktywności.
                    loadDataJob?.cancel()
                    // Po pomyślnym usunięciu klienta, wróć do MainActivity, czyszcząc stos
                    finishAffinity() // Zamknij bieżącą i poprzednie aktywności
                    // Uruchom MainActivity jako nowe zadanie
                    startActivity(Intent(this@EditReceiptActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
                EditReceiptViewModel.EditResult.ERROR_NOT_FOUND -> {
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Nie znaleziono klienta do usunięcia.", Toast.LENGTH_LONG).show()
                }
                EditReceiptViewModel.EditResult.ERROR_DATABASE -> {
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Wystąpił problem z bazą danych podczas usuwania klienta", Toast.LENGTH_LONG).show()
                }
                else -> { // Inne błędy
                    Toast.makeText(this@EditReceiptActivity, "Błąd: Wystąpił nieznany błąd podczas usuwania klienta", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Konfiguruje działanie CheckBoxa "Dzisiaj" dla pola daty weryfikacji.
     * Po zaznaczeniu ustawia aktualną datę i blokuje pole EditText.
     * Po odznaczeniu czyści pole EditText i odblokowuje je.
     */
    private fun setupVerificationDateCheckBox() {
        editVerificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Jeśli zaznaczony:
                // Pobierz aktualną datę
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                // Ustaw datę w polu
                editVerificationDateEditText.setText(currentDate)
                // Zablokuj pole
                editVerificationDateEditText.isEnabled = false
            } else {
                // Jeśli odznaczony:
                // Wyczyść pole daty weryfikacji
                editVerificationDateEditText.text.clear()
                // Odblokuj pole
                editVerificationDateEditText.isEnabled = true
            }
        }
    }

    /**
     * Konfiguruje [EditText] do automatycznego formatowania daty w formacie DD-MM-YYYY
     * podczas wpisywania i edycji. Używa [TextWatcher].
     * Jest to ta sama funkcja co w [AddClientActivity].
     * @param editText Pole EditText do skonfigurowania.
     */
    private fun setupDateEditText(editText: EditText) {
        // Ustawienie typu wejściowego na numeryczny, aby sugerować odpowiednią klawiaturę
        editText.inputType = InputType.TYPE_CLASS_NUMBER

        editText.addTextChangedListener(object : TextWatcher {
            private var current = "" // Przechowuje aktualny sformatowany tekst
            private var isFormatting: Boolean = false // Flaga zapobiegająca rekurencji

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // Jeśli już formatujemy lub tekst jest null, wyjdź
                if (isFormatting || s == null) return
                // Rozpocznij formatowanie
                isFormatting = true

                val userInput = s.toString()
                // Jeśli tekst się nie zmienił, wyjdź
                if (userInput == current) {
                    isFormatting = false
                    return
                }

                // Usuń wszystkie znaki niebędące cyframi
                val digitsOnly = userInput.replace("[^\\d]".toRegex(), "")
                val len = digitsOnly.length

                // Buduj sformatowany string DD-MM-YYYY
                val formatted = StringBuilder()
                if (len >= 1) {
                    formatted.append(digitsOnly.substring(0, minOf(len, 2))) // DD
                }
                if (len >= 3) {
                    // Dodaj myślnik i MM
                    formatted.append("-").append(digitsOnly.substring(2, minOf(len, 4)))
                }
                if (len >= 5) {
                    // Dodaj myślnik i YYYY
                    formatted.append("-").append(digitsOnly.substring(4, minOf(len, 8)))
                }

                // Zaktualizuj sformatowany tekst
                current = formatted.toString()
                // Ustaw tekst w EditText (co znów wywoła afterTextChanged)
                editText.setText(current)

                // Ustaw kursor na końcu sformatowanego tekstu.
                // To najprostsze podejście, działa dobrze przy dodawaniu/usuwaniu od końca.
                try {
                    editText.setSelection(current.length)
                } catch (e: Exception) {
                    // Ignoruj błędy ustawiania kursora (np. IndexOutOfBounds)
                }

                // Zakończ formatowanie
                isFormatting = false
            }
        })
    }
}

