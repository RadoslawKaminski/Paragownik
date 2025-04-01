
package com.kaminski.paragownik

import android.net.Uri
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.viewmodel.AddClientViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.Calendar

/**
 * Aktywność odpowiedzialna za dodawanie nowego klienta wraz z jednym lub wieloma paragonami i zdjęciami.
 * Używa ViewModelu do przechowywania stanu UI, aby przetrwał zmiany konfiguracji.
 * Używa Glide do wyświetlania miniatur.
 */
class AddClientActivity : AppCompatActivity() {

    // --- Widoki UI ---
    // Pola dla pierwszego paragonu (ich stan jest zarządzany przez pierwszy element w ViewModel.receiptFieldsStates)
    private lateinit var storeNumberEditTextFirstReceipt: EditText
    private lateinit var receiptNumberEditTextFirstReceipt: EditText
    private lateinit var receiptDateEditTextFirstReceipt: EditText
    private lateinit var cashRegisterNumberEditTextFirstReceipt: EditText
    private lateinit var verificationDateEditTextFirstReceipt: EditText
    private lateinit var verificationDateTodayCheckBoxFirstReceipt: CheckBox
    // Pola danych klienta
    private lateinit var clientDescriptionEditText: EditText
    private lateinit var clientAppNumberEditText: EditText
    private lateinit var amoditNumberEditText: EditText
    // Przyciski i kontenery
    private lateinit var addClientButton: Button
    private lateinit var addAdditionalReceiptButton: Button
    private lateinit var receiptsContainer: LinearLayout // Kontener na dynamiczne pola paragonów
    private lateinit var clientPhotosContainer: LinearLayout
    private lateinit var transactionPhotosContainer: LinearLayout
    private lateinit var addClientPhotoButton: Button
    private lateinit var addTransactionPhotoButton: Button

    // --- ViewModels ---
    private lateinit var addClientViewModel: AddClientViewModel
    private lateinit var storeViewModel: StoreViewModel // Potrzebny do pobrania numeru sklepu z ID

    // --- Dane pomocnicze ---
    private var storeIdFromIntent: Long = -1L
    private var currentPhotoTypeToAdd: PhotoType? = null
    // Mapa przechowująca widoki dynamicznych pól paragonów, kluczem jest ID stanu z ViewModelu
    private val receiptStateIdToViewMap = mutableMapOf<String, View>()

    /**
     * Struktura danych przekazywana do [AddClientViewModel.addClientWithReceiptsTransactionally].
     * Pozostaje bez zmian.
     */
    data class ReceiptData(
        val storeNumber: String,
        val receiptNumber: String,
        val receiptDate: String,
        val cashRegisterNumber: String?,
        val verificationDateString: String?
    )

    // Launcher ActivityResult do wybierania obrazu z galerii
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            Log.d("AddClientActivity", "Otrzymano tymczasowe URI: $sourceUri")
            val destinationUri = copyImageToInternalStorage(sourceUri)
            destinationUri?.let { finalUri ->
                when (currentPhotoTypeToAdd) {
                    PhotoType.CLIENT -> {
                        // Sprawdzamy, czy URI już istnieje w ViewModelu
                        if (addClientViewModel.clientPhotoUrisState.value?.contains(finalUri) != true) {
                            addClientViewModel.addClientPhotoUri(finalUri) // Dodaj do ViewModelu
                            // Miniatura zostanie dodana przez obserwatora LiveData
                            Log.d("AddClientActivity", "Dodano URI zdjęcia klienta do ViewModelu: $finalUri")
                        } else {
                            Log.d("AddClientActivity", "Zdjęcie klienta $finalUri już istnieje w ViewModelu.")
                            Toast.makeText(this, R.string.photo_already_added, Toast.LENGTH_SHORT).show()
                        }
                    }
                    PhotoType.TRANSACTION -> {
                        if (addClientViewModel.transactionPhotoUrisState.value?.contains(finalUri) != true) {
                            addClientViewModel.addTransactionPhotoUri(finalUri) // Dodaj do ViewModelu
                            // Miniatura zostanie dodana przez obserwatora LiveData
                            Log.d("AddClientActivity", "Dodano URI zdjęcia transakcji do ViewModelu: $finalUri")
                        } else {
                            Log.d("AddClientActivity", "Zdjęcie transakcji $finalUri już istnieje w ViewModelu.")
                            Toast.makeText(this, R.string.photo_already_added, Toast.LENGTH_SHORT).show()
                        }
                    }
                    null -> Log.w("AddClientActivity", "Nieznany typ zdjęcia do dodania (currentPhotoTypeToAdd is null)")
                }
            }
        } ?: run {
            Log.d("AddClientActivity", "Nie wybrano zdjęcia.")
        }
        currentPhotoTypeToAdd = null
    }

    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_client)

        initializeViews()
        initializeViewModels()
        handleIntentExtras() // Musi być po inicjalizacji ViewModeli

        // Konfiguracja listenerów dla pierwszego paragonu
        setupFirstReceiptFieldsListeners()

        // Konfiguracja głównych listenerów przycisków
        setupButtonClickListeners()

        // Obserwacja zmian w ViewModelu
        observeViewModel()

        // Odtworzenie stanu UI (jeśli ViewModel już go zawiera po obrocie)
        // Odbywa się to wewnątrz observeViewModel()
    }

    /**
     * Inicjalizuje wszystkie referencje do widoków UI z layoutu.
     */
    private fun initializeViews() {
        // Pola pierwszego paragonu
        storeNumberEditTextFirstReceipt = findViewById(R.id.receiptStoreNumberEditText)
        receiptNumberEditTextFirstReceipt = findViewById(R.id.receiptNumberEditText)
        receiptDateEditTextFirstReceipt = findViewById(R.id.receiptDateEditText)
        cashRegisterNumberEditTextFirstReceipt = findViewById(R.id.cashRegisterNumberEditText)
        verificationDateEditTextFirstReceipt = findViewById(R.id.verificationDateEditText)
        verificationDateTodayCheckBoxFirstReceipt = findViewById(R.id.verificationDateTodayCheckBox)
        // Pola klienta
        clientDescriptionEditText = findViewById(R.id.clientDescriptionEditText)
        clientAppNumberEditText = findViewById(R.id.clientAppNumberEditText)
        amoditNumberEditText = findViewById(R.id.amoditNumberEditText)
        // Przyciski i kontenery
        addClientButton = findViewById(R.id.addClientButton)
        addAdditionalReceiptButton = findViewById(R.id.addAdditionalReceiptButton)
        receiptsContainer = findViewById(R.id.receiptsContainer)
        clientPhotosContainer = findViewById(R.id.clientPhotosContainer)
        transactionPhotosContainer = findViewById(R.id.transactionPhotosContainer)
        addClientPhotoButton = findViewById(R.id.addClientPhotoButton)
        addTransactionPhotoButton = findViewById(R.id.addTransactionPhotoButton)
    }

    /**
     * Inicjalizuje ViewModels używane w tej aktywności.
     */
    private fun initializeViewModels() {
        // Używamy ViewModelProvider do uzyskania instancji ViewModelu
        // ViewModel przetrwa zmiany konfiguracji (np. obrót ekranu)
        addClientViewModel = ViewModelProvider(this).get(AddClientViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)
    }

    /**
     * Sprawdza, czy w Intencie przekazano ID sklepu i odpowiednio konfiguruje pole numeru sklepu
     * w stanie pierwszego paragonu w ViewModelu.
     */
    private fun handleIntentExtras() {
        if (intent.hasExtra("STORE_ID")) {
            storeIdFromIntent = intent.getLongExtra("STORE_ID", -1L)
            if (storeIdFromIntent != -1L) {
                storeNumberEditTextFirstReceipt.isEnabled = false // Wyłącz edycję pola w UI
                lifecycleScope.launch {
                    val store = storeViewModel.getStoreById(storeIdFromIntent)
                    store?.let {
                        // Ustaw numer sklepu w stanie pierwszego paragonu w ViewModelu
                        val firstReceiptState = addClientViewModel.receiptFieldsStates.value?.firstOrNull()
                        if (firstReceiptState != null) {
                            addClientViewModel.updateReceiptFieldState(firstReceiptState.id) { state ->
                                state.storeNumber = it.storeNumber
                            }
                            // Aktualizuj pole EditText bezpośrednio (choć obserwator też to zrobi)
                            storeNumberEditTextFirstReceipt.setText(it.storeNumber)
                        }
                    }
                }
            }
        }
    }

    /**
     * Ustawia listenery dla pól pierwszego paragonu (EditTexty, CheckBox).
     * Aktualizują one stan w ViewModelu.
     */
    private fun setupFirstReceiptFieldsListeners() {
        val firstReceiptStateId = addClientViewModel.receiptFieldsStates.value?.firstOrNull()?.id ?: return

        // Formatowanie daty dla pól daty
        setupDateEditText(receiptDateEditTextFirstReceipt, firstReceiptStateId) { state, text -> state.receiptDate = text }
        setupDateEditText(verificationDateEditTextFirstReceipt, firstReceiptStateId) { state, text -> state.verificationDate = text }

        // Listenery TextChanged dla pozostałych pól
        storeNumberEditTextFirstReceipt.addTextChangedListener { text ->
            addClientViewModel.updateReceiptFieldState(firstReceiptStateId) { it.storeNumber = text.toString() }
        }
        receiptNumberEditTextFirstReceipt.addTextChangedListener { text ->
            addClientViewModel.updateReceiptFieldState(firstReceiptStateId) { it.receiptNumber = text.toString() }
        }
        cashRegisterNumberEditTextFirstReceipt.addTextChangedListener { text ->
            addClientViewModel.updateReceiptFieldState(firstReceiptStateId) { it.cashRegisterNumber = text.toString() }
        }

        // Listener dla CheckBoxa "Dzisiaj"
        verificationDateTodayCheckBoxFirstReceipt.setOnCheckedChangeListener { _, isChecked ->
            addClientViewModel.updateReceiptFieldState(firstReceiptStateId) { state ->
                state.isVerificationDateToday = isChecked
                if (isChecked) {
                    val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                    state.verificationDate = currentDate
                    // Aktualizujemy też pole EditText w UI (choć obserwator też to zrobi)
                    verificationDateEditTextFirstReceipt.setText(currentDate)
                    verificationDateEditTextFirstReceipt.isEnabled = false
                } else {
                    verificationDateEditTextFirstReceipt.isEnabled = true
                    // Opcjonalnie: wyczyść pole daty, jeśli odznaczono
                    // state.verificationDate = ""
                    // verificationDateEditTextFirstReceipt.setText("")
                }
            }
            // Zaktualizuj całą listę, aby wymusić odświeżenie obserwatora (jeśli potrzebne)
            // addClientViewModel.receiptFieldsStates.value = addClientViewModel.receiptFieldsStates.value
        }
    }

    /**
     * Ustawia listenery dla głównych przycisków akcji (Dodaj paragon, Zapisz, Dodaj zdjęcia).
     */
    private fun setupButtonClickListeners() {
        addAdditionalReceiptButton.setOnClickListener {
            addClientViewModel.addNewReceiptFieldState() // Dodaj nowy stan do ViewModelu
            // Widok zostanie dodany przez obserwatora LiveData
        }

        addClientButton.setOnClickListener {
            saveClientAndReceipts()
        }

        addClientPhotoButton.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.CLIENT
            pickImageLauncher.launch("image/*")
        }

        addTransactionPhotoButton.setOnClickListener {
            currentPhotoTypeToAdd = PhotoType.TRANSACTION
            pickImageLauncher.launch("image/*")
        }
    }

    /**
     * Obserwuje zmiany w LiveData ViewModelu i aktualizuje UI.
     */
    private fun observeViewModel() {
        // Obserwator dla stanów pól paragonów
        addClientViewModel.receiptFieldsStates.observe(this, Observer { states ->
            Log.d("AddClientActivity", "Obserwator: Zmiana w stanach paragonów, liczba: ${states.size}")
            rebuildReceiptFieldsUI(states ?: emptyList())
        })

        // Obserwator dla URI zdjęć klienta
        addClientViewModel.clientPhotoUrisState.observe(this, Observer { uris ->
            Log.d("AddClientActivity", "Obserwator: Zmiana w URI zdjęć klienta, liczba: ${uris.size}")
            rebuildPhotoThumbnailsUI(clientPhotosContainer, uris ?: emptyList(), PhotoType.CLIENT)
        })

        // Obserwator dla URI zdjęć transakcji
        addClientViewModel.transactionPhotoUrisState.observe(this, Observer { uris ->
            Log.d("AddClientActivity", "Obserwator: Zmiana w URI zdjęć transakcji, liczba: ${uris.size}")
            rebuildPhotoThumbnailsUI(transactionPhotosContainer, uris ?: emptyList(), PhotoType.TRANSACTION)
        })
    }

    /**
     * Odtwarza lub aktualizuje dynamiczne sekcje pól paragonów w UI na podstawie
     * listy stanów z ViewModelu.
     */
    private fun rebuildReceiptFieldsUI(states: List<AddClientViewModel.ReceiptFieldsState>) {
        // Usuń widoki, których stany już nie istnieją w ViewModelu
        val currentStateIds = states.map { it.id }.toSet()
        val viewsToRemove = receiptStateIdToViewMap.filterKeys { it !in currentStateIds }
        viewsToRemove.forEach { (id, view) ->
            receiptsContainer.removeView(view)
            receiptStateIdToViewMap.remove(id)
        }

        // Zaktualizuj pierwszy paragon (jeśli istnieje stan)
        states.firstOrNull()?.let { firstState ->
            // Ustaw wartości pól na podstawie stanu (unikaj resetowania, jeśli użytkownik właśnie wpisuje)
            if (storeNumberEditTextFirstReceipt.text.toString() != firstState.storeNumber) {
                storeNumberEditTextFirstReceipt.setText(firstState.storeNumber)
            }
            if (receiptNumberEditTextFirstReceipt.text.toString() != firstState.receiptNumber) {
                receiptNumberEditTextFirstReceipt.setText(firstState.receiptNumber)
            }
            if (receiptDateEditTextFirstReceipt.text.toString() != firstState.receiptDate) {
                receiptDateEditTextFirstReceipt.setText(firstState.receiptDate)
            }
            if (cashRegisterNumberEditTextFirstReceipt.text.toString() != firstState.cashRegisterNumber) {
                cashRegisterNumberEditTextFirstReceipt.setText(firstState.cashRegisterNumber)
            }
            if (verificationDateEditTextFirstReceipt.text.toString() != firstState.verificationDate) {
                verificationDateEditTextFirstReceipt.setText(firstState.verificationDate)
            }
            if (verificationDateTodayCheckBoxFirstReceipt.isChecked != firstState.isVerificationDateToday) {
                verificationDateTodayCheckBoxFirstReceipt.isChecked = firstState.isVerificationDateToday
            }
            verificationDateEditTextFirstReceipt.isEnabled = !firstState.isVerificationDateToday && storeIdFromIntent == -1L // Dodatkowe sprawdzenie dla intentu
        }

        // Dodaj lub zaktualizuj widoki dla pozostałych stanów
        states.drop(1).forEach { state ->
            val existingView = receiptStateIdToViewMap[state.id]
            if (existingView == null) {
                // Stan jest nowy, dodaj nowy widok
                addNewReceiptFieldsView(state)
            } else {
                // Stan istnieje, zaktualizuj wartości w istniejącym widoku
                updateReceiptFieldsView(existingView, state)
            }
        }
    }

    /**
     * Dodaje nowy widok sekcji paragonu do kontenera i mapy.
     */
    private fun addNewReceiptFieldsView(state: AddClientViewModel.ReceiptFieldsState) {
        val inflater = LayoutInflater.from(this)
        val receiptFieldsView = inflater.inflate(R.layout.additional_receipt_fields, receiptsContainer, false)
        receiptStateIdToViewMap[state.id] = receiptFieldsView // Dodaj do mapy

        // Znajdź widoki w nowo dodanym layoucie
        val storeNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalStoreNumberEditText)
        val receiptNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptNumberEditText)
        val receiptDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptDateEditText)
        val cashRegisterNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalCashRegisterNumberEditText)
        val verificationDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalVerificationDateEditText)
        val verificationDateTodayCheckBox = receiptFieldsView.findViewById<CheckBox>(R.id.additionalVerificationDateTodayCheckBox)
        val removeReceiptButton = receiptFieldsView.findViewById<ImageButton>(R.id.removeReceiptButton)

        // Ustaw początkowe wartości i listenery
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
        storeNumberEditText.addTextChangedListener { text -> addClientViewModel.updateReceiptFieldState(state.id) { it.storeNumber = text.toString() } }
        receiptNumberEditText.addTextChangedListener { text -> addClientViewModel.updateReceiptFieldState(state.id) { it.receiptNumber = text.toString() } }
        cashRegisterNumberEditText.addTextChangedListener { text -> addClientViewModel.updateReceiptFieldState(state.id) { it.cashRegisterNumber = text.toString() } }

        verificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            addClientViewModel.updateReceiptFieldState(state.id) { s ->
                s.isVerificationDateToday = isChecked
                if (isChecked) {
                    val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
                    s.verificationDate = currentDate
                    verificationDateEditText.setText(currentDate) // Aktualizuj UI
                    verificationDateEditText.isEnabled = false
                } else {
                    verificationDateEditText.isEnabled = true
                    // Opcjonalnie: wyczyść pole daty
                    // s.verificationDate = ""
                    // verificationDateEditText.setText("")
                }
            }
            // Zaktualizuj całą listę, aby wymusić odświeżenie obserwatora (jeśli potrzebne)
            // addClientViewModel.receiptFieldsStates.value = addClientViewModel.receiptFieldsStates.value
        }

        removeReceiptButton.setOnClickListener {
            addClientViewModel.removeReceiptFieldState(state.id) // Usuń stan z ViewModelu
            // Widok zostanie usunięty przez obserwatora w rebuildReceiptFieldsUI
        }

        receiptsContainer.addView(receiptFieldsView) // Dodaj widok do layoutu
    }

    /**
     * Aktualizuje wartości pól w istniejącym widoku sekcji paragonu.
     */
    private fun updateReceiptFieldsView(view: View, state: AddClientViewModel.ReceiptFieldsState) {
        val storeNumberEditText = view.findViewById<EditText>(R.id.additionalStoreNumberEditText)
        val receiptNumberEditText = view.findViewById<EditText>(R.id.additionalReceiptNumberEditText)
        val receiptDateEditText = view.findViewById<EditText>(R.id.additionalReceiptDateEditText)
        val cashRegisterNumberEditText = view.findViewById<EditText>(R.id.additionalCashRegisterNumberEditText)
        val verificationDateEditText = view.findViewById<EditText>(R.id.additionalVerificationDateEditText)
        val verificationDateTodayCheckBox = view.findViewById<CheckBox>(R.id.additionalVerificationDateTodayCheckBox)

        // Ustaw wartości (tylko jeśli się różnią, aby uniknąć problemów z kursorem)
        if (storeNumberEditText.text.toString() != state.storeNumber) storeNumberEditText.setText(state.storeNumber)
        if (receiptNumberEditText.text.toString() != state.receiptNumber) receiptNumberEditText.setText(state.receiptNumber)
        if (receiptDateEditText.text.toString() != state.receiptDate) receiptDateEditText.setText(state.receiptDate)
        if (cashRegisterNumberEditText.text.toString() != state.cashRegisterNumber) cashRegisterNumberEditText.setText(state.cashRegisterNumber)
        if (verificationDateEditText.text.toString() != state.verificationDate) verificationDateEditText.setText(state.verificationDate)
        if (verificationDateTodayCheckBox.isChecked != state.isVerificationDateToday) verificationDateTodayCheckBox.isChecked = state.isVerificationDateToday
        verificationDateEditText.isEnabled = !state.isVerificationDateToday
    }


    /**
     * Odtwarza lub aktualizuje miniatury zdjęć w podanym kontenerze.
     */
    private fun rebuildPhotoThumbnailsUI(container: LinearLayout, uris: List<Uri>, photoType: PhotoType) {
        container.removeAllViews() // Wyczyść kontener
        uris.forEach { uri ->
            addPhotoThumbnailView(uri, container, photoType) // Dodaj widok dla każdego URI
        }
    }

    /**
     * Dodaje widok miniatury zdjęcia do kontenera. Logika usuwania jest teraz w ViewModelu.
     */
    private fun addPhotoThumbnailView(photoUri: Uri, container: LinearLayout, photoType: PhotoType) {
        val inflater = LayoutInflater.from(this)
        val thumbnailView = inflater.inflate(R.layout.photo_thumbnail_item, container, false)
        val imageView = thumbnailView.findViewById<ImageView>(R.id.photoThumbnailImageView)
        val deleteButton = thumbnailView.findViewById<ImageButton>(R.id.deletePhotoButton)

        Glide.with(this)
            .load(photoUri)
            .placeholder(R.drawable.ic_photo_placeholder)
            .error(R.drawable.ic_photo_placeholder)
            .centerCrop()
            .into(imageView)

        deleteButton.visibility = View.VISIBLE // Przycisk usuwania jest zawsze widoczny

        deleteButton.setOnClickListener {
            // Wywołaj metodę usuwania w ViewModelu
            when (photoType) {
                PhotoType.CLIENT -> addClientViewModel.removeClientPhotoUri(photoUri)
                PhotoType.TRANSACTION -> addClientViewModel.removeTransactionPhotoUri(photoUri)
            }
            // Widok zostanie usunięty przez obserwatora w rebuildPhotoThumbnailsUI
            Log.d("AddClientActivity", "Zlecono usunięcie URI z ViewModelu: $photoUri")
        }

        container.addView(thumbnailView)
    }


    /**
     * Główna funkcja zbierająca dane z formularza (teraz głównie z ViewModelu)
     * i wywołująca zapis w ViewModelu.
     */
    private fun saveClientAndReceipts() {
        val clientDescription = clientDescriptionEditText.text.toString().trim()
        val clientAppNumber = clientAppNumberEditText.text.toString().trim()
        val amoditNumber = amoditNumberEditText.text.toString().trim()

        // Pobierz stany paragonów z ViewModelu
        val receiptStates = addClientViewModel.receiptFieldsStates.value ?: emptyList()

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

        // Wywołaj metodę zapisu w ViewModelu (przekazując tylko dane klienta)
        lifecycleScope.launch {
            // Metoda w ViewModelu sama pobierze stany paragonów i zdjęcia z LiveData
            val result = addClientViewModel.addClientWithReceiptsTransactionally(
                clientDescription = clientDescription.takeIf { it.isNotEmpty() },
                clientAppNumber = clientAppNumber.takeIf { it.isNotEmpty() },
                amoditNumber = amoditNumber.takeIf { it.isNotEmpty() }
            )
            handleSaveResult(result)
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
     * Obsługuje wynik operacji zapisu zwrócony przez ViewModel, wyświetlając odpowiedni komunikat Toast.
     * W przypadku sukcesu zamyka aktywność.
     */
    private fun handleSaveResult(result: AddClientViewModel.AddResult) {
        val messageResId = when (result) {
            AddClientViewModel.AddResult.SUCCESS -> R.string.save_success_message
            AddClientViewModel.AddResult.ERROR_DATE_FORMAT -> R.string.error_invalid_date_format
            AddClientViewModel.AddResult.ERROR_VERIFICATION_DATE_FORMAT -> R.string.error_invalid_verification_date_format
            AddClientViewModel.AddResult.ERROR_DUPLICATE_RECEIPT -> R.string.error_duplicate_receipt
            AddClientViewModel.AddResult.ERROR_STORE_NUMBER_MISSING -> R.string.error_store_number_missing
            AddClientViewModel.AddResult.ERROR_DATABASE -> R.string.error_database
            AddClientViewModel.AddResult.ERROR_UNKNOWN -> R.string.error_unknown
            // Można dodać obsługę innych błędów, jeśli ViewModel je zwraca
        }
        val message = getString(messageResId)

        Toast.makeText(this@AddClientActivity, message, if (result == AddClientViewModel.AddResult.SUCCESS) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()

        if (result == AddClientViewModel.AddResult.SUCCESS) {
            finish()
        }
    }

    /**
     * Konfiguruje [EditText] do automatycznego formatowania wprowadzanej daty do formatu DD-MM-YYYY
     * i aktualizuje odpowiedni stan w ViewModelu.
     * @param editText Pole EditText do skonfigurowania.
     * @param stateId ID stanu paragonu w ViewModelu, który ma być aktualizowany.
     * @param updateAction Lambda, która przyjmuje stan i nowy tekst, aby zaktualizować odpowiednie pole w stanie.
     */
    private fun setupDateEditText(
        editText: EditText,
        stateId: String,
        updateAction: (AddClientViewModel.ReceiptFieldsState, String) -> Unit
    ) {
        editText.inputType = InputType.TYPE_CLASS_NUMBER // Ustawiamy inputType na numeryczny
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
                if (len >= 1) formatted.append(digitsOnly.substring(0, minOf(len, 2))) // DD
                if (len >= 3) formatted.append("-").append(digitsOnly.substring(2, minOf(len, 4))) // MM
                if (len >= 5) formatted.append("-").append(digitsOnly.substring(4, minOf(len, 8))) // YYYY
                current = formatted.toString()

                // Aktualizuj stan w ViewModelu
                addClientViewModel.updateReceiptFieldState(stateId) { state ->
                    updateAction(state, current)
                }

                // Ustaw sformatowany tekst w EditText
                editText.setText(current)

                // Przywróć pozycję kursora
                try {
                    val lengthDiff = current.length - textLengthBefore
                    var newCursorPos = cursorPosBefore + lengthDiff
                    // Korekta pozycji kursora, jeśli dodano/usunięto myślniki
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

    /**
     * Kopiuje obraz z podanego źródłowego URI (np. z galerii) do wewnętrznego magazynu aplikacji.
     * Zwraca URI skopiowanego pliku lub null w przypadku błędu.
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

            Log.d("AddClientActivity", "Skopiowano zdjęcie do: ${destinationFile.absolutePath}")
            destinationFile.toUri()

        } catch (e: IOException) {
            Log.e("AddClientActivity", "Błąd podczas kopiowania zdjęcia", e)
            Toast.makeText(this, R.string.error_copying_image, Toast.LENGTH_SHORT).show()
            null
        } catch (e: SecurityException) {
            Log.e("AddClientActivity", "Brak uprawnień do odczytu URI źródłowego (SecurityException)", e)
            Toast.makeText(this, R.string.error_permission_read_photo, Toast.LENGTH_SHORT).show()
            null
        }
    }
}