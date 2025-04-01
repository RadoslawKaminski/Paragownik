
package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.room.withTransaction
import com.kaminski.paragownik.AddClientActivity.ReceiptData // Import wewnętrznej klasy z AddClientActivity
import com.kaminski.paragownik.R // Potrzebne dla zasobów string (np. w komunikatach błędów)
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Photo
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ViewModel dla AddClientActivity.
 * Odpowiada za logikę biznesową związaną z dodawaniem nowego klienta
 * wraz z jednym lub wieloma paragonami i zdjęciami do bazy danych.
 * Przechowuje również stan UI, aby przetrwał zmiany konfiguracji.
 */
class AddClientViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val clientDao: ClientDao = database.clientDao()
    private val storeDao: StoreDao = database.storeDao()
    private val photoDao = database.photoDao() // Potrzebne do wstawiania zdjęć

    /**
     * Enum reprezentujący możliwy wynik operacji dodawania klienta i paragonów.
     * Używany do komunikacji wyniku operacji do UI (Activity).
     */
    enum class AddResult {
        SUCCESS, // Operacja zakończona sukcesem
        ERROR_DATE_FORMAT, // Błąd formatu daty paragonu (np. "30-02-2023")
        ERROR_VERIFICATION_DATE_FORMAT, // Błąd formatu daty weryfikacji
        ERROR_DUPLICATE_RECEIPT, // Próba dodania paragonu, który już istnieje (ten sam numer, data, sklep, kasa)
        ERROR_STORE_NUMBER_MISSING, // Brak numeru drogerii w danych paragonu
        ERROR_DATABASE, // Ogólny błąd podczas operacji bazodanowej (np. wstawiania)
        ERROR_UNKNOWN // Nieoczekiwany, inny błąd
    }

    /**
     * Struktura danych przechowująca stan pól dla pojedynczej sekcji paragonu w UI.
     * Używana do zachowania stanu po obrocie ekranu.
     */
    data class ReceiptFieldsState(
        val id: String = UUID.randomUUID().toString(), // Unikalny identyfikator dla stabilności listy
        var storeNumber: String = "",
        var receiptNumber: String = "",
        var receiptDate: String = "",
        var cashRegisterNumber: String = "",
        var verificationDate: String = "",
        var isVerificationDateToday: Boolean = false
    )

    // --- LiveData do przechowywania stanu UI ---

    // Przechowuje stan pól dla wszystkich sekcji paragonów (pierwszej i dodanych dynamicznie).
    val receiptFieldsStates = MutableLiveData<MutableList<ReceiptFieldsState>>()
    // Przechowuje listę URI zdjęć klienta dodanych w tej sesji.
    val clientPhotoUrisState = MutableLiveData<MutableList<Uri>>()
    // Przechowuje listę URI zdjęć transakcji dodanych w tej sesji.
    val transactionPhotoUrisState = MutableLiveData<MutableList<Uri>>()

    init {
        // Inicjalizacja LiveData pustymi listami lub z jednym domyślnym stanem dla pierwszego paragonu.
        if (receiptFieldsStates.value == null) {
            receiptFieldsStates.value = mutableListOf(ReceiptFieldsState()) // Zaczynamy z jednym pustym stanem
        }
        if (clientPhotoUrisState.value == null) {
            clientPhotoUrisState.value = mutableListOf()
        }
        if (transactionPhotoUrisState.value == null) {
            transactionPhotoUrisState.value = mutableListOf()
        }
    }

    // --- Metody do modyfikacji stanu UI ---

    /** Dodaje nowy, pusty stan pól paragonu do listy. */
    fun addNewReceiptFieldState() {
        val currentList = receiptFieldsStates.value ?: mutableListOf()
        currentList.add(ReceiptFieldsState())
        receiptFieldsStates.value = currentList // Zaktualizuj LiveData
    }

    /** Usuwa stan pól paragonu o podanym ID z listy. */
    fun removeReceiptFieldState(id: String) {
        val currentList = receiptFieldsStates.value ?: return
        currentList.removeAll { it.id == id }
        receiptFieldsStates.value = currentList // Zaktualizuj LiveData
    }

    /** Aktualizuje stan konkretnego pola w określonym stanie paragonu. */
    fun updateReceiptFieldState(id: String, updateAction: (ReceiptFieldsState) -> Unit) {
        val currentList = receiptFieldsStates.value ?: return
        val stateToUpdate = currentList.find { it.id == id }
        stateToUpdate?.let {
            updateAction(it)
            // Nie musimy wywoływać receiptFieldsStates.value = currentList,
            // ponieważ modyfikujemy obiekt wewnątrz listy, a obserwatorzy LiveData<List>
            // reagują na zmianę referencji listy, a nie jej zawartości.
            // Jednak dla pewności i potencjalnych przyszłych mechanizmów (np. DiffUtil)
            // można rozważyć aktualizację całej listy: receiptFieldsStates.value = currentList
        }
    }

    /** Dodaje URI zdjęcia klienta do listy stanu. */
    fun addClientPhotoUri(uri: Uri) {
        val currentList = clientPhotoUrisState.value ?: mutableListOf()
        if (!currentList.contains(uri)) {
            currentList.add(uri)
            clientPhotoUrisState.value = currentList // Zaktualizuj LiveData
        }
    }

    /** Usuwa URI zdjęcia klienta z listy stanu. */
    fun removeClientPhotoUri(uri: Uri) {
        val currentList = clientPhotoUrisState.value ?: return
        if (currentList.remove(uri)) {
            clientPhotoUrisState.value = currentList // Zaktualizuj LiveData
        }
    }

    /** Dodaje URI zdjęcia transakcji do listy stanu. */
    fun addTransactionPhotoUri(uri: Uri) {
        val currentList = transactionPhotoUrisState.value ?: mutableListOf()
        if (!currentList.contains(uri)) {
            currentList.add(uri)
            transactionPhotoUrisState.value = currentList // Zaktualizuj LiveData
        }
    }

    /** Usuwa URI zdjęcia transakcji z listy stanu. */
    fun removeTransactionPhotoUri(uri: Uri) {
        val currentList = transactionPhotoUrisState.value ?: return
        if (currentList.remove(uri)) {
            transactionPhotoUrisState.value = currentList // Zaktualizuj LiveData
        }
    }

    /**
     * Dodaje nowego klienta wraz z listą powiązanych paragonów i zdjęć w jednej transakcji bazodanowej.
     * Gwarantuje to, że albo wszystkie dane zostaną zapisane poprawnie, albo żadne zmiany nie zostaną wprowadzone.
     * Operacja wykonywana jest w tle (Dispatchers.IO).
     * Pobiera dane paragonów i zdjęć z aktualnego stanu ViewModelu.
     *
     * @param clientDescription Opis klienta (może być null lub pusty).
     * @param clientAppNumber Numer aplikacji klienta (może być null lub pusty).
     * @param amoditNumber Numer Amodit klienta (może być null lub pusty).
     * @return [AddResult] Enum wskazujący wynik operacji (np. SUCCESS, ERROR_DUPLICATE_RECEIPT).
     */
    suspend fun addClientWithReceiptsTransactionally(
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?
        // Usunięto parametry list zdjęć i paragonów, bo pobieramy je z LiveData
    ): AddResult = withContext(Dispatchers.IO) {
        // Pobierz aktualny stan paragonów i zdjęć z LiveData
        val currentReceiptStates = receiptFieldsStates.value ?: emptyList()
        val currentClientPhotos = clientPhotoUrisState.value ?: emptyList()
        val currentTransactionPhotos = transactionPhotoUrisState.value ?: emptyList()

        // Sprawdzenie, czy jest co najmniej jeden paragon do dodania
        if (currentReceiptStates.isEmpty()) {
            Log.w("AddClientViewModel", "Próba zapisu bez żadnych paragonów.")
            // Można by zwrócić dedykowany błąd, np. ERROR_NO_RECEIPTS
            // Na razie traktujemy to jako błąd walidacji, który powinien być obsłużony w Activity
            return@withContext AddResult.ERROR_UNKNOWN // Lub inny odpowiedni błąd
        }

        // Formatter daty używany do parsowania dat wprowadzonych przez użytkownika.
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // Rozpoczęcie transakcji bazodanowej.
            database.withTransaction {
                // Krok 1: Dodanie Klienta
                val client = Client(
                    description = clientDescription?.takeIf { it.isNotBlank() },
                    clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                    amoditNumber = amoditNumber?.takeIf { it.isNotBlank() }
                )
                val clientId = clientDao.insertClient(client)
                Log.d("AddClientViewModel", "Transakcja: Dodano klienta ID: $clientId")
                if (clientId == -1L) {
                    throw DatabaseException(getApplication<Application>().getString(R.string.error_inserting_client))
                }

                // Krok 2a: Dodanie zdjęć klienta (typu CLIENT)
                for (uri in currentClientPhotos) {
                    val photo = Photo(clientId = clientId, uri = uri.toString(), type = PhotoType.CLIENT)
                    photoDao.insertPhoto(photo)
                    Log.d("AddClientViewModel", "Transakcja: Dodano zdjęcie klienta: $uri dla klienta ID: $clientId")
                }

                // Krok 2b: Dodanie zdjęć transakcji (typu TRANSACTION)
                for (uri in currentTransactionPhotos) {
                    val photo = Photo(clientId = clientId, uri = uri.toString(), type = PhotoType.TRANSACTION)
                    photoDao.insertPhoto(photo)
                    Log.d("AddClientViewModel", "Transakcja: Dodano zdjęcie transakcji: $uri dla klienta ID: $clientId")
                }

                // Krok 3: Przetwarzanie i dodawanie Paragonów na podstawie stanów z ViewModelu
                for (receiptState in currentReceiptStates) {
                    // Walidacja wymaganych pól (powinna być też w UI, ale dla pewności)
                    if (receiptState.storeNumber.isBlank() || receiptState.receiptNumber.isBlank() || receiptState.receiptDate.isBlank()) {
                        Log.e("AddClientViewModel", "Transakcja: Brak wymaganych danych w stanie paragonu ID: ${receiptState.id}")
                        throw ValidationException() // Rzucenie wyjątku przerwie transakcję
                    }

                    val verificationDateString = receiptState.verificationDate.takeIf { it.isNotBlank() }
                    val cashRegisterNumber = receiptState.cashRegisterNumber.takeIf { it.isNotBlank() }

                    // Parsowanie daty paragonu.
                    val receiptDate: Date = try {
                        dateFormat.parse(receiptState.receiptDate) as Date
                    } catch (e: ParseException) {
                        Log.e("AddClientViewModel", "Transakcja: Błąd formatu daty paragonu: ${receiptState.receiptDate}")
                        throw DateFormatException()
                    }

                    // Parsowanie daty weryfikacji (jeśli istnieje).
                    val verificationDate: Date? = verificationDateString?.let {
                        try {
                            dateFormat.parse(it) as Date
                        } catch (e: ParseException) {
                            Log.e("AddClientViewModel", "Transakcja: Błąd formatu daty weryfikacji: $it")
                            throw VerificationDateFormatException()
                        }
                    }

                    // Obsługa Sklepu: Znajdź istniejący sklep lub utwórz nowy.
                    var store = storeDao.getStoreByNumber(receiptState.storeNumber)
                    val storeId: Long
                    if (store == null) {
                        store = Store(storeNumber = receiptState.storeNumber)
                        storeDao.insertStore(store)
                        store = storeDao.getStoreByNumber(receiptState.storeNumber)
                        if (store == null) {
                            throw DatabaseException(getApplication<Application>().getString(R.string.error_creating_store, receiptState.storeNumber))
                        }
                        storeId = store.id
                        Log.d("AddClientViewModel", "Transakcja: Dodano nową drogerię: ${receiptState.storeNumber}, ID: $storeId")
                    } else {
                        storeId = store.id
                    }

                    // Walidacja Duplikatów Paragonów
                    val existingReceipt = receiptDao.findByNumberDateStoreCashRegister(
                        receiptState.receiptNumber,
                        receiptDate,
                        storeId,
                        cashRegisterNumber
                    )
                    if (existingReceipt != null) {
                        Log.e("AddClientViewModel", "Transakcja: Znaleziono duplikat paragonu: Nr ${receiptState.receiptNumber}, Data ${receiptState.receiptDate}, Sklep ID $storeId, Kasa ${cashRegisterNumber ?: "brak"}")
                        throw DuplicateReceiptException()
                    }

                    // Dodanie Paragonu do bazy danych.
                    val receipt = Receipt(
                        receiptNumber = receiptState.receiptNumber,
                        receiptDate = receiptDate,
                        storeId = storeId,
                        cashRegisterNumber = cashRegisterNumber,
                        verificationDate = verificationDate,
                        clientId = clientId
                    )
                    receiptDao.insertReceipt(receipt)
                    Log.d("AddClientViewModel", "Transakcja: Dodano paragon: Nr ${receipt.receiptNumber} dla klienta ID: $clientId")
                }
            }
            // Jeśli transakcja zakończyła się sukcesem, zwracamy SUCCESS.
            AddResult.SUCCESS
        } catch (e: ValidationException) {
            // Błąd walidacji danych ze stanu (np. puste pola)
            // Ten błąd powinien być głównie łapany w UI przed wywołaniem zapisu.
            Log.e("AddClientViewModel", "Transakcja: Błąd walidacji danych ze stanu.", e)
            AddResult.ERROR_UNKNOWN // Lub inny odpowiedni błąd
        } catch (e: DateFormatException) {
            AddResult.ERROR_DATE_FORMAT
        } catch (e: VerificationDateFormatException) {
            AddResult.ERROR_VERIFICATION_DATE_FORMAT
        } catch (e: DuplicateReceiptException) {
            AddResult.ERROR_DUPLICATE_RECEIPT
        } catch (e: StoreNumberMissingException) {
            // Ten wyjątek nie powinien wystąpić, jeśli jest walidacja w pętli
            AddResult.ERROR_STORE_NUMBER_MISSING
        } catch (e: DatabaseException) {
            Log.e("AddClientViewModel", "Transakcja: Błąd bazy danych.", e)
            AddResult.ERROR_DATABASE
        } catch (e: Exception) {
            Log.e("AddClientViewModel", "Transakcja: Nieznany błąd.", e)
            AddResult.ERROR_UNKNOWN
        }
    }

    // Prywatne klasy wyjątków używane do sterowania przepływem w bloku try-catch transakcji.
    private class DateFormatException : Exception()
    private class VerificationDateFormatException : Exception()
    private class DuplicateReceiptException : Exception()
    private class StoreNumberMissingException : Exception() // Nadal potrzebne, jeśli walidacja w pętli zawiedzie
    private class DatabaseException(message: String) : Exception(message)
    private class ValidationException : Exception() // Nowy wyjątek dla błędów walidacji stanu
}

