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

class EditReceiptActivity : AppCompatActivity() {

    // Pola UI
    private lateinit var editReceiptStoreNumberEditText: EditText
    private lateinit var editReceiptNumberEditText: EditText
    private lateinit var editReceiptDateEditText: EditText
    private lateinit var editVerificationDateEditText: EditText
    private lateinit var editVerificationDateTodayCheckBox: CheckBox
    private lateinit var editClientDescriptionEditText: EditText
    // --- NOWE POLA UI KLIENTA ---
    private lateinit var editClientAppNumberEditText: EditText
    private lateinit var editAmoditNumberEditText: EditText
    // --- KONIEC NOWYCH PÓL UI KLIENTA ---
    private lateinit var saveReceiptButton: Button
    private lateinit var deleteReceiptButton: Button
    private lateinit var deleteClientButton: Button

    // ViewModel
    private lateinit var editReceiptViewModel: EditReceiptViewModel

    // ID edytowanego paragonu
    private var receiptId: Long = -1L
    // Przechowuje aktualne dane klienta (potrzebne przy usuwaniu)
    private var currentClientId: Long? = null

    private var loadDataJob: Job? = null // Zmienna do przechowywania Joba

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_receipt)

        // Inicjalizacja widoków
        editReceiptStoreNumberEditText = findViewById(R.id.editReceiptStoreNumberEditText)
        editReceiptNumberEditText = findViewById(R.id.editReceiptNumberEditText)
        editReceiptDateEditText = findViewById(R.id.editReceiptDateEditText)
        editVerificationDateEditText = findViewById(R.id.editVerificationDateEditText)
        editVerificationDateTodayCheckBox = findViewById(R.id.editVerificationDateTodayCheckBox)
        editClientDescriptionEditText = findViewById(R.id.editClientDescriptionEditText)
        // --- INICJALIZACJA NOWYCH PÓL ---
        editClientAppNumberEditText = findViewById(R.id.editClientAppNumberEditText)
        editAmoditNumberEditText = findViewById(R.id.editAmoditNumberEditText)
        // --- KONIEC INICJALIZACJI NOWYCH PÓL ---
        saveReceiptButton = findViewById(R.id.saveReceiptButton)
        deleteReceiptButton = findViewById(R.id.deleteReceiptButton)
        deleteClientButton = findViewById(R.id.deleteClientButton)

        // Inicjalizacja ViewModelu
        editReceiptViewModel = ViewModelProvider(this).get(EditReceiptViewModel::class.java)

        // Pobierz ID paragonu z Intentu
        receiptId = intent.getLongExtra("RECEIPT_ID", -1L)

        // Sprawdź, czy ID paragonu jest poprawne
        if (receiptId == -1L) {
            Toast.makeText(this, "Błąd: Nieprawidłowe ID paragonu.", Toast.LENGTH_LONG).show()
            Log.e("EditReceiptActivity", "Nieprawidłowe RECEIPT_ID przekazane w Intencie.")
            finish() // Zakończ aktywność, jeśli ID jest nieprawidłowe
            return
        }

        // Ustaw formatowanie dla pól dat
        setupDateEditText(editReceiptDateEditText)
        setupDateEditText(editVerificationDateEditText)
        // Skonfiguruj checkbox "Dzisiaj"
        setupVerificationDateCheckBox()

        // Załaduj dane paragonu i klienta
        loadReceiptData()

        // Ustaw listenery dla przycisków
        saveReceiptButton.setOnClickListener {
            saveChanges()
        }
        deleteReceiptButton.setOnClickListener {
            showDeleteReceiptDialog()
        }
        deleteClientButton.setOnClickListener {
            showDeleteClientDialog()
        }
    }

    /**
     * Wczytuje dane paragonu i powiązanego klienta z ViewModelu i wypełnia pola formularza.
     */
    /**
     * Wczytuje dane paragonu i powiązanego klienta z ViewModelu i wypełnia pola formularza.
     * Anuluje poprzednie zadanie ładowania, jeśli istnieje.
     * Loguje błąd tylko wtedy, gdy dane nie zostaną znalezione, a korutyna jest nadal aktywna.
     */
    private fun loadReceiptData() {
        // Anuluj poprzednie zadanie ładowania, jeśli było aktywne
        loadDataJob?.cancel()
        // Uruchom nowe zadanie w lifecycleScope i zapisz referencję do Joba
        loadDataJob = lifecycleScope.launch {
            editReceiptViewModel.getReceiptWithClientAndStoreNumber(receiptId)
                .collectLatest { pair ->
                    val receiptWithClient = pair.first
                    // val storeNumber = pair.second // Pobierzemy storeNumber w bloku if

                    // Sprawdź, czy otrzymano poprawne dane paragonu i klienta
                    if (receiptWithClient != null && receiptWithClient.client != null) {
                        // Dane są poprawne, wypełnij pola formularza
                        val receipt = receiptWithClient.receipt
                        val client = receiptWithClient.client
                        val storeNumber = pair.second // Pobierz numer sklepu

                        // Zapisz ID klienta na potrzeby późniejszego usuwania
                        currentClientId = client.id

                        // Wypełnij pola formularza
                        editReceiptStoreNumberEditText.setText(storeNumber ?: "")
                        editReceiptNumberEditText.setText(receipt.receiptNumber)

                        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        editReceiptDateEditText.setText(dateFormat.format(receipt.receiptDate))

                        // Obsługa daty weryfikacji (może być null)
                        receipt.verificationDate?.let { verificationDate ->
                            val formattedVerificationDate = dateFormat.format(verificationDate)
                            editVerificationDateEditText.setText(formattedVerificationDate)
                            val todayDate = dateFormat.format(java.util.Calendar.getInstance().time)
                            if (formattedVerificationDate == todayDate) {
                                editVerificationDateTodayCheckBox.isChecked = true
                                editVerificationDateEditText.isEnabled = false
                            } else {
                                editVerificationDateTodayCheckBox.isChecked = false
                                editVerificationDateEditText.isEnabled = true
                            }
                        } ?: run {
                            editVerificationDateEditText.text.clear()
                            editVerificationDateTodayCheckBox.isChecked = false
                            editVerificationDateEditText.isEnabled = true
                        }

                        editClientDescriptionEditText.setText(client.description ?: "")
                        editClientAppNumberEditText.setText(client.clientAppNumber ?: "")
                        editAmoditNumberEditText.setText(client.amoditNumber ?: "")
                        // Logika ładowania zdjęcia (photoUri) zostanie dodana później

                    } else {
                        // Dane są null (np. paragon został usunięty)
                        // Loguj błąd TYLKO jeśli korutyna jest nadal aktywna
                        // (zapobiega logowaniu podczas zamykania aktywności po usunięciu)
                        if (isActive) { // Sprawdź, czy korutyna nie została anulowana
                            Log.e("EditReceiptActivity", "Nie znaleziono ReceiptWithClient lub Client dla receiptId: $receiptId (lub został usunięty)")
                        }
                    }
                }
        }
    }

    /**
     * Zbiera dane z formularza i wywołuje metodę ViewModelu do zapisania zmian.
     */
    private fun saveChanges() {
        // Zbierz dane z pól formularza
        val storeNumberString = editReceiptStoreNumberEditText.text.toString().trim()
        val receiptNumber = editReceiptNumberEditText.text.toString().trim()
        val receiptDateString = editReceiptDateEditText.text.toString().trim()
        val verificationDateString = editVerificationDateEditText.text.toString().trim()
        val clientDescription = editClientDescriptionEditText.text.toString().trim()
        // --- POBRANIE DANYCH Z NOWYCH PÓL ---
        val clientAppNumber = editClientAppNumberEditText.text.toString().trim()
        val amoditNumber = editAmoditNumberEditText.text.toString().trim()
        // photoUri będzie dodane później
        // --- KONIEC POBRANIA DANYCH Z NOWYCH PÓL ---

        // Podstawowa walidacja pustych pól (ViewModel zrobi resztę)
        if (storeNumberString.isEmpty() || receiptNumber.isEmpty() || receiptDateString.isEmpty()) {
            Toast.makeText(this, "Wypełnij numer sklepu, numer paragonu i datę paragonu.", Toast.LENGTH_LONG).show()
            return
        }


        // Wywołaj metodę ViewModelu w korutynie
        lifecycleScope.launch {
            val result = editReceiptViewModel.updateReceiptAndClient(
                receiptId = receiptId,
                storeNumberString = storeNumberString,
                receiptNumber = receiptNumber,
                receiptDateString = receiptDateString,
                verificationDateString = verificationDateString.takeIf { it.isNotEmpty() },
                clientDescription = clientDescription.takeIf { it.isNotEmpty() },
                // Przekazanie nowych pól
                clientAppNumber = clientAppNumber.takeIf { it.isNotEmpty() },
                amoditNumber = amoditNumber.takeIf { it.isNotEmpty() },
                photoUri = null // Na razie null
            )

            // Obsługa wyniku operacji zapisu
            when (result) {
                EditReceiptViewModel.EditResult.SUCCESS -> {
                    Toast.makeText(this@EditReceiptActivity, "Zmiany zapisane pomyślnie", Toast.LENGTH_SHORT).show()
                    finish() // Powrót do poprzedniej aktywności po sukcesie
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
     * Wyświetla dialog potwierdzający usunięcie paragonu.
     */
    private fun showDeleteReceiptDialog() {
        AlertDialog.Builder(this)
            .setTitle("Potwierdzenie usunięcia")
            .setMessage("Czy na pewno chcesz usunąć ten paragon?\n\n(Jeśli to ostatni paragon klienta lub drogerii, zostaną oni również usunięci).")
            .setPositiveButton("Usuń") { _, _ ->
                deleteReceipt() // Wywołaj funkcję usuwania po potwierdzeniu
            }
            .setNegativeButton("Anuluj", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    /**
     * Wywołuje metodę ViewModelu do usunięcia bieżącego paragonu.
     */
    private fun deleteReceipt() {
        lifecycleScope.launch {
            // Pobierz aktualny obiekt paragonu (potrzebny do metody deleteReceipt w ViewModel)
            // Używamy firstOrNull() do jednorazowego pobrania wartości z Flow
            val currentReceipt = editReceiptViewModel.getReceiptWithClientAndStoreNumber(receiptId)
                .map { it.first?.receipt } // Wyciągnij tylko paragon
                .firstOrNull() // Pobierz pierwszą nie-null wartość lub null

            if (currentReceipt == null) {
                Toast.makeText(this@EditReceiptActivity, "Błąd: Nie można pobrać danych paragonu do usunięcia.", Toast.LENGTH_LONG).show()
                return@launch
            }

            // Wywołaj metodę usuwania w ViewModelu
            val result = editReceiptViewModel.deleteReceipt(currentReceipt)

            // Obsługa wyniku operacji usuwania
            when (result) {
                EditReceiptViewModel.EditResult.SUCCESS -> {
                    Toast.makeText(this@EditReceiptActivity, "Paragon usunięty", Toast.LENGTH_SHORT).show()
                    // Powrót do MainActivity po usunięciu paragonu
                    finishAffinity() // Zamknij bieżącą i poprzednie aktywności (np. ReceiptListActivity)
                    startActivity(Intent(this@EditReceiptActivity, MainActivity::class.java).apply {
                        // Flagi czyszczące stos aktywności, aby MainActivity była nowym korzeniem
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
                EditReceiptViewModel.EditResult.ERROR_NOT_FOUND -> {
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
     * Wyświetla dialog potwierdzający usunięcie klienta i wszystkich jego paragonów.
     */
    private fun showDeleteClientDialog() {
        // Sprawdź, czy mamy ID klienta
        if (currentClientId == null) {
            Toast.makeText(this, "Błąd: Nie można zidentyfikować klienta do usunięcia.", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Potwierdzenie usunięcia klienta")
            .setMessage("Czy na pewno chcesz usunąć tego klienta?\n\nUWAGA: Usunięcie klienta spowoduje USUNIĘCIE WSZYSTKICH jego paragonów! Drogerie, które staną się puste, również zostaną usunięte.")
            .setPositiveButton("Usuń Klienta") { _, _ ->
                deleteClient() // Wywołaj funkcję usuwania klienta po potwierdzeniu
            }
            .setNegativeButton("Anuluj", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    /**
     * Wywołuje metodę ViewModelu do usunięcia klienta (i kaskadowo jego paragonów).
     */
    private fun deleteClient() {
        val clientIdToDelete = currentClientId ?: return // Ponowne sprawdzenie dla bezpieczeństwa

        lifecycleScope.launch {
            // Tworzymy tymczasowy obiekt Client tylko z ID, bo ViewModel i tak pobierze pełny obiekt
            val clientStub = com.kaminski.paragownik.data.Client(id = clientIdToDelete, description = null)
            val result = editReceiptViewModel.deleteClient(clientStub)

            // Obsługa wyniku operacji usuwania klienta
            when (result) {
                EditReceiptViewModel.EditResult.SUCCESS -> {
                    Toast.makeText(this@EditReceiptActivity, "Klient i jego paragony usunięte", Toast.LENGTH_SHORT).show()
                    // Anuluj obserwację danych
                    loadDataJob?.cancel()
                    // Powrót do MainActivity po usunięciu klienta
                    finishAffinity() // Zamknij bieżącą i poprzednie aktywności
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
     * Konfiguruje CheckBox "Dzisiaj" dla daty weryfikacji.
     */
    private fun setupVerificationDateCheckBox() {
        editVerificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Ustaw aktualną datę i wyłącz EditText
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                editVerificationDateEditText.setText(currentDate)
                editVerificationDateEditText.isEnabled = false
            } else {
                editVerificationDateEditText.text.clear() // Wyczyść pole
                editVerificationDateEditText.isEnabled = true
            }
        }
    }

    /**
     * Konfiguruje EditText do automatycznego formatowania daty w formacie DD-MM-YYYY
     * podczas wpisywania i edycji. Uproszczona wersja.
     * @param editText Pole EditText do skonfigurowania.
     */
    private fun setupDateEditText(editText: EditText) {
        // Nadal używamy klawiatury numerycznej
        editText.inputType = InputType.TYPE_CLASS_NUMBER

        editText.addTextChangedListener(object : TextWatcher {
            private var current = ""
            private var isFormatting: Boolean = false // Flaga zapobiegająca rekurencji

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return // Jeśli już formatujemy lub s jest null, wyjdź
                isFormatting = true // Rozpocznij formatowanie

                val userInput = s.toString()
                if (userInput == current) {
                    isFormatting = false
                    return // Bez zmian, wyjdź
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
                    formatted.append("-").append(digitsOnly.substring(2, minOf(len, 4))) // MM
                }
                if (len >= 5) {
                    formatted.append("-").append(digitsOnly.substring(4, minOf(len, 8))) // YYYY
                }

                current = formatted.toString()
                editText.setText(current)

                // Ustaw kursor na końcu sformatowanego tekstu
                // To najprostsze podejście, które działa dobrze w większości przypadków
                // przy dodawaniu i usuwaniu od końca.
                try {
                    editText.setSelection(current.length)
                } catch (e: Exception) {
                    // Ignoruj błędy ustawiania kursora, jeśli wystąpią
                }

                isFormatting = false // Zakończ formatowanie
            }
        })
    }
}