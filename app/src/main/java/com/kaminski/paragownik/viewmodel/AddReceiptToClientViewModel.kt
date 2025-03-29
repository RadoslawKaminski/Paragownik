package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData // Dodano import
import androidx.lifecycle.MutableLiveData // Dodano import
import androidx.lifecycle.asLiveData // Dodano import
import androidx.lifecycle.switchMap // Dodano import
import androidx.room.withTransaction // Import dla transakcji
import com.kaminski.paragownik.AddClientActivity // Potrzebne dla ReceiptData
import com.kaminski.paragownik.R // Potrzebne dla zasobów string
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.PhotoType // Dodano import
import com.kaminski.paragownik.data.Receipt // Import Receipt
import com.kaminski.paragownik.data.Store // Import Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.PhotoDao // Dodano import
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers // Import Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest // Dodano import
import kotlinx.coroutines.flow.map // Dodano import
import kotlinx.coroutines.withContext // Import withContext
import java.text.ParseException // Import ParseException
import java.text.SimpleDateFormat // Import SimpleDateFormat
import java.util.Date // Import Date
import java.util.Locale // Import Locale

/**
 * ViewModel dla AddReceiptToClientActivity.
 * Odpowiada za pobranie danych klienta (w tym miniatury) i logikę dodawania nowych paragonów do niego.
 */
class AddReceiptToClientViewModel(application: Application) : AndroidViewModel(application) {

    private val clientDao: ClientDao
    private val receiptDao: ReceiptDao
    private val storeDao: StoreDao
    private val photoDao: PhotoDao // <-- DODAJ
    private val database: AppDatabase

    // Możliwe wyniki operacji zapisu paragonów
    enum class SaveReceiptsResult {
        SUCCESS,
        ERROR_DATE_FORMAT, // Ogólny błąd formatu daty paragonu
        ERROR_VERIFICATION_DATE_FORMAT, // Błąd formatu daty weryfikacji
        ERROR_DUPLICATE_RECEIPT,
        ERROR_STORE_NUMBER_MISSING,
        ERROR_DATABASE,
        ERROR_UNKNOWN,
        ERROR_NO_RECEIPTS
    }

    private val currentClientId = MutableLiveData<Long>() // Dodaj, jeśli nie ma
    val clientDataWithThumbnail: LiveData<Pair<Client?, String?>> // Zmieniamy na Pair(Client, ThumbnailUri)


    init {
        database = AppDatabase.getDatabase(application) // Inicjalizacja bazy
        clientDao = database.clientDao()
        receiptDao = database.receiptDao()
        storeDao = database.storeDao()
        photoDao = database.photoDao() // <-- DODAJ

        // Inicjalizacja LiveData klienta z miniaturą
        clientDataWithThumbnail = currentClientId.switchMap { clientId ->
            clientDao.getClientByIdFlow(clientId).flatMapLatest { client ->
                if (client == null) {
                    // Jeśli klient nie istnieje, zwróć null dla obu
                    kotlinx.coroutines.flow.flowOf(Pair(null, null))
                } else {
                    // Pobierz pierwsze zdjęcie klienta
                    photoDao.getFirstPhotoForClientByType(clientId)
                        .map { photo -> client to photo?.uri } // Zwróć parę (Client, thumbnailUri)
                }
            }.asLiveData()
        }
    }

     /** Ustawia ID klienta do obserwacji. */
    fun loadClientData(clientId: Long) {
        if (currentClientId.value != clientId) {
            currentClientId.value = clientId
        }
    }


    /**
     * Dodaje nowe paragony do istniejącego klienta w jednej transakcji.
     * Obsługuje tworzenie sklepów, walidację duplikatów i daty weryfikacji.
     *
     * @param clientId ID klienta, do którego dodajemy paragony.
     * @param receiptsData Lista danych paragonów do dodania (zawiera opcjonalną datę weryfikacji).
     * @return SaveReceiptsResult wskazujący wynik operacji.
     */
    suspend fun saveReceiptsForClient(
        clientId: Long,
        receiptsData: List<AddClientActivity.ReceiptData>
    ): SaveReceiptsResult = withContext(Dispatchers.IO) {
        // Sprawdzenie, czy klient istnieje
        val clientExists = clientDao.getClientById(clientId) // Wywołanie suspend w korutynie IO
        if (clientExists == null) {
            Log.e("AddReceiptVM", "Próba zapisu paragonów dla nieistniejącego klienta ID: $clientId")
            return@withContext SaveReceiptsResult.ERROR_DATABASE
        }

        if (receiptsData.isEmpty()) {
            return@withContext SaveReceiptsResult.ERROR_NO_RECEIPTS
        }

        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // Używamy pola 'database' z klasy ViewModel
            database.withTransaction { // Ta lambda jest suspend
                // Iteruj przez dane paragonów do dodania
                for (receiptData in receiptsData) {

                    // Walidacja numeru sklepu
                    if (receiptData.storeNumber.isBlank()) {
                        Log.e("AddReceiptVM", "Transakcja: Brak numeru drogerii dla paragonu: ${receiptData.receiptNumber}")
                        throw StoreNumberMissingException()
                    }

                    // Parsowanie daty paragonu
                    val receiptDate: Date = try {
                        dateFormat.parse(receiptData.receiptDate) as Date
                    } catch (e: ParseException) {
                        Log.e("AddReceiptVM", "Transakcja: Błąd formatu daty paragonu: ${receiptData.receiptDate}")
                        throw DateFormatException()
                    }

                    // Obsługa Sklepu (znajdź lub utwórz) - wywołania DAO są suspend
                    var store = storeDao.getStoreByNumber(receiptData.storeNumber)
                    val storeId: Long
                    if (store == null) {
                        store = Store(storeNumber = receiptData.storeNumber)
                        storeDao.insertStore(store) // suspend
                        store = storeDao.getStoreByNumber(receiptData.storeNumber) // suspend
                        if (store == null) {
                            throw DatabaseException(getApplication<Application>().getString(R.string.error_creating_store, receiptData.storeNumber))
                        }
                        storeId = store.id
                        Log.d("AddReceiptVM", "Transakcja: Dodano nową drogerię: ${receiptData.storeNumber}, ID: $storeId")
                    } else {
                        storeId = store.id
                    }

                    // Walidacja Duplikatów Paragonów - wywołanie DAO jest suspend
                    val existingReceipt = receiptDao.findByNumberDateStore(receiptData.receiptNumber, receiptDate, storeId)
                    if (existingReceipt != null) {
                        Log.e("AddReceiptVM", "Transakcja: Znaleziono duplikat paragonu: Nr ${receiptData.receiptNumber}, Data ${receiptData.receiptDate}, Sklep ID $storeId")
                        throw DuplicateReceiptException()
                    }

                    val verificationDateStringFromData = receiptData.verificationDateString // Pobierz z ReceiptData

                    // Parsowanie daty weryfikacji
                    val verificationDate: Date? = if (!verificationDateStringFromData.isNullOrBlank()) {
                        try {
                            dateFormat.parse(verificationDateStringFromData) as Date
                        } catch (e: ParseException) {
                            Log.e("AddReceiptVM", "Transakcja: Błąd formatu daty weryfikacji: $verificationDateStringFromData")
                            throw VerificationDateFormatException() // Rzuć nowy wyjątek
                        }
                    } else {
                        null
                    }

                    // Dodanie Paragonu - wywołanie DAO jest suspend
                    val receipt = Receipt(
                        receiptNumber = receiptData.receiptNumber,
                        receiptDate = receiptDate,
                        storeId = storeId,
                        verificationDate = verificationDate, // Używamy sparsowanej daty weryfikacji
                        clientId = clientId // Używamy przekazanego ID klienta
                    )
                    val insertedReceiptId = receiptDao.insertReceipt(receipt) // suspend
                    if (insertedReceiptId == -1L) {
                         throw DatabaseException("Błąd wstawiania paragonu do bazy.")
                    }
                    Log.d("AddReceiptVM", "Transakcja: Dodano paragon ID: $insertedReceiptId dla klienta ID: $clientId")
                }
            } // Koniec bloku withTransaction
            SaveReceiptsResult.SUCCESS
        } catch (e: DateFormatException) {
            SaveReceiptsResult.ERROR_DATE_FORMAT
        } catch (e: VerificationDateFormatException) { // Obsługa nowego wyjątku
            SaveReceiptsResult.ERROR_VERIFICATION_DATE_FORMAT
        } catch (e: DuplicateReceiptException) {
            SaveReceiptsResult.ERROR_DUPLICATE_RECEIPT
        } catch (e: StoreNumberMissingException) {
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
    private class VerificationDateFormatException : Exception() // Nowy wyjątek
    private class DuplicateReceiptException : Exception()
    private class StoreNumberMissingException : Exception()
    private class DatabaseException(message: String) : Exception(message)
}

