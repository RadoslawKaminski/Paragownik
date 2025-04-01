
package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.room.withTransaction
import com.kaminski.paragownik.AddClientActivity // Import dla ReceiptData
import com.kaminski.paragownik.R
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.PhotoDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ViewModel dla AddReceiptToClientActivity.
 * Odpowiada za:
 * - Pobieranie danych klienta (w tym jego miniatury) do wyświetlenia w UI.
 * - Logikę dodawania nowych paragonów do istniejącego klienta w ramach transakcji bazodanowej.
 * - Przechowywanie stanu dynamicznych pól paragonów.
 */
class AddReceiptToClientViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val clientDao: ClientDao = database.clientDao()
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val storeDao: StoreDao = database.storeDao()
    private val photoDao: PhotoDao = database.photoDao()

    /**
     * Enum reprezentujący możliwy wynik operacji zapisu nowych paragonów dla klienta.
     */
    enum class SaveReceiptsResult {
        SUCCESS,
        ERROR_DATE_FORMAT,
        ERROR_VERIFICATION_DATE_FORMAT,
        ERROR_DUPLICATE_RECEIPT,
        ERROR_STORE_NUMBER_MISSING,
        ERROR_DATABASE,
        ERROR_UNKNOWN,
        ERROR_NO_RECEIPTS,
        ERROR_CLIENT_NOT_FOUND // Dodano błąd, gdy klient nie istnieje
    }

    /**
     * Struktura danych przechowująca stan pól dla pojedynczej sekcji paragonu w UI.
     * Używana do zachowania stanu po obrocie ekranu.
     * (Taka sama jak w AddClientViewModel)
     */
    data class ReceiptFieldsState(
        val id: String = UUID.randomUUID().toString(), // Unikalny identyfikator
        var storeNumber: String = "",
        var receiptNumber: String = "",
        var receiptDate: String = "",
        var cashRegisterNumber: String = "",
        var verificationDate: String = "",
        var isVerificationDateToday: Boolean = false
    )

    // MutableLiveData przechowująca ID aktualnie ładowanego klienta.
    private val currentClientId = MutableLiveData<Long>()

    // LiveData przechowująca parę: (Obiekt Client?, URI miniatury jako String?).
    val clientDataWithThumbnail: LiveData<Pair<Client?, String?>>

    // LiveData do przechowywania stanu pól paragonów.
    val receiptFieldsStates = MutableLiveData<MutableList<ReceiptFieldsState>>()

    init {
        // Inicjalizacja LiveData klienta z miniaturą.
        clientDataWithThumbnail = currentClientId.switchMap { clientId ->
            clientDao.getClientByIdFlow(clientId).flatMapLatest { client ->
                if (client == null) {
                    kotlinx.coroutines.flow.flowOf(Pair(null, null))
                } else {
                    photoDao.getFirstPhotoForClientByType(clientId, PhotoType.CLIENT)
                        .map { photo -> client to photo?.uri }
                }
            }.asLiveData()
        }

        // Inicjalizacja LiveData stanu pól paragonów.
        if (receiptFieldsStates.value == null) {
            receiptFieldsStates.value = mutableListOf(ReceiptFieldsState()) // Zaczynamy z jednym pustym stanem
        }
    }

    /**
     * Ustawia ID klienta, którego dane mają być załadowane i obserwowane.
     */
    fun loadClientData(clientId: Long) {
        if (currentClientId.value != clientId) {
            currentClientId.value = clientId
        }
    }

    // --- Metody do modyfikacji stanu UI pól paragonów ---

    /** Dodaje nowy, pusty stan pól paragonu do listy. */
    fun addNewReceiptFieldState() {
        val currentList = receiptFieldsStates.value ?: mutableListOf()
        currentList.add(ReceiptFieldsState())
        receiptFieldsStates.value = currentList
    }

    /** Usuwa stan pól paragonu o podanym ID z listy. */
    fun removeReceiptFieldState(id: String) {
        val currentList = receiptFieldsStates.value ?: return
        currentList.removeAll { it.id == id }
        receiptFieldsStates.value = currentList
    }

    /** Aktualizuje stan konkretnego pola w określonym stanie paragonu. */
    fun updateReceiptFieldState(id: String, updateAction: (ReceiptFieldsState) -> Unit) {
        val currentList = receiptFieldsStates.value ?: return
        val stateToUpdate = currentList.find { it.id == id }
        stateToUpdate?.let {
            updateAction(it)
            // Można rozważyć: receiptFieldsStates.value = currentList
        }
    }

    /**
     * Dodaje listę nowych paragonów do istniejącego klienta w jednej transakcji bazodanowej.
     * Pobiera dane paragonów z `receiptFieldsStates`.
     *
     * @param clientId ID klienta, do którego dodajemy paragony.
     * @return [SaveReceiptsResult] Enum wskazujący wynik operacji.
     */
    suspend fun saveReceiptsForClient(
        clientId: Long
        // Usunięto parametr receiptsData
    ): SaveReceiptsResult = withContext(Dispatchers.IO) {
        // Pobierz aktualny stan paragonów z LiveData
        val currentReceiptStates = receiptFieldsStates.value ?: emptyList()

        // Sprawdzenie, czy klient istnieje
        val clientExists = clientDao.getClientById(clientId)
        if (clientExists == null) {
            Log.e("AddReceiptVM", "Próba zapisu paragonów dla nieistniejącego klienta ID: $clientId")
            return@withContext SaveReceiptsResult.ERROR_CLIENT_NOT_FOUND
        }

        // Sprawdzenie, czy lista stanów nie jest pusta
        if (currentReceiptStates.isEmpty()) {
            return@withContext SaveReceiptsResult.ERROR_NO_RECEIPTS
        }

        // Formatter daty
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // Rozpoczęcie transakcji
            database.withTransaction {
                // Iteracja przez stany paragonów z ViewModelu
                for (receiptState in currentReceiptStates) {
                    // Walidacja wymaganych pól
                    if (receiptState.storeNumber.isBlank() || receiptState.receiptNumber.isBlank() || receiptState.receiptDate.isBlank()) {
                        Log.e("AddReceiptVM", "Transakcja: Brak wymaganych danych w stanie paragonu ID: ${receiptState.id}")
                        throw ValidationException()
                    }

                    val cashRegisterNumber = receiptState.cashRegisterNumber.takeIf { it.isNotBlank() }
                    val verificationDateString = receiptState.verificationDate.takeIf { it.isNotBlank() }

                    // Parsowanie daty paragonu
                    val receiptDate: Date = try {
                        dateFormat.parse(receiptState.receiptDate) as Date
                    } catch (e: ParseException) {
                        Log.e("AddReceiptVM", "Transakcja: Błąd formatu daty paragonu: ${receiptState.receiptDate}")
                        throw DateFormatException()
                    }

                    // Parsowanie daty weryfikacji
                    val verificationDate: Date? = verificationDateString?.let {
                        try {
                            dateFormat.parse(it) as Date
                        } catch (e: ParseException) {
                            Log.e("AddReceiptVM", "Transakcja: Błąd formatu daty weryfikacji: $it")
                            throw VerificationDateFormatException()
                        }
                    }

                    // Obsługa Sklepu
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
                        Log.d("AddReceiptVM", "Transakcja: Dodano nową drogerię: ${receiptState.storeNumber}, ID: $storeId")
                    } else {
                        storeId = store.id
                    }

                    // Walidacja Duplikatów
                    val existingReceipt = receiptDao.findByNumberDateStoreCashRegister(
                        receiptState.receiptNumber,
                        receiptDate,
                        storeId,
                        cashRegisterNumber
                    )
                    if (existingReceipt != null) {
                        Log.e("AddReceiptVM", "Transakcja: Znaleziono duplikat paragonu: Nr ${receiptState.receiptNumber}, Data ${receiptState.receiptDate}, Sklep ID $storeId, Kasa ${cashRegisterNumber ?: "brak"}")
                        throw DuplicateReceiptException()
                    }

                    // Utworzenie i wstawienie paragonu
                    val receipt = Receipt(
                        receiptNumber = receiptState.receiptNumber,
                        receiptDate = receiptDate,
                        storeId = storeId,
                        cashRegisterNumber = cashRegisterNumber,
                        verificationDate = verificationDate,
                        clientId = clientId // Używamy ID klienta przekazanego do metody
                    )
                    val insertedReceiptId = receiptDao.insertReceipt(receipt)
                    if (insertedReceiptId == -1L) {
                        throw DatabaseException("Błąd wstawiania paragonu do bazy.")
                    }
                    Log.d("AddReceiptVM", "Transakcja: Dodano paragon ID: $insertedReceiptId dla klienta ID: $clientId")
                }
            } // Koniec transakcji

            SaveReceiptsResult.SUCCESS
        } catch (e: ValidationException) {
            Log.e("AddReceiptVM", "Transakcja: Błąd walidacji danych ze stanu.", e)
            SaveReceiptsResult.ERROR_UNKNOWN // Lub inny błąd
        } catch (e: DateFormatException) {
            SaveReceiptsResult.ERROR_DATE_FORMAT
        } catch (e: VerificationDateFormatException) {
            SaveReceiptsResult.ERROR_VERIFICATION_DATE_FORMAT
        } catch (e: DuplicateReceiptException) {
            SaveReceiptsResult.ERROR_DUPLICATE_RECEIPT
        } catch (e: StoreNumberMissingException) {
            // Ten wyjątek nie powinien wystąpić, jeśli jest walidacja w pętli
            SaveReceiptsResult.ERROR_STORE_NUMBER_MISSING
        } catch (e: DatabaseException) {
            Log.e("AddReceiptVM", "Transakcja: Błąd bazy danych.", e)
            SaveReceiptsResult.ERROR_DATABASE
        } catch (e: Exception) {
            Log.e("AddReceiptVM", "Transakcja: Nieznany błąd.", e)
            SaveReceiptsResult.ERROR_UNKNOWN
        }
    }

    // Prywatne klasy wyjątków
    private class DateFormatException : Exception()
    private class VerificationDateFormatException : Exception()
    private class DuplicateReceiptException : Exception()
    private class StoreNumberMissingException : Exception()
    private class DatabaseException(message: String) : Exception(message)
    private class ValidationException : Exception()
}

