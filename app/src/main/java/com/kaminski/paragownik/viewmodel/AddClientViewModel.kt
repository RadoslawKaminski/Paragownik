package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
import com.kaminski.paragownik.AddClientActivity.ReceiptData // Zmieniono import, jeśli ReceiptData jest tam zagnieżdżone
import com.kaminski.paragownik.R // Potrzebne dla zasobów string
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Photo // Dodano import Photo
import com.kaminski.paragownik.data.PhotoType // Dodano import PhotoType
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

/**
 * ViewModel dla AddClientActivity.
 * Odpowiada za logikę biznesową związaną z dodawaniem nowego klienta
 * wraz z jednym lub wieloma paragonami i zdjęciami do bazy danych.
 */
class AddClientViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val clientDao: ClientDao = database.clientDao()
    private val storeDao: StoreDao = database.storeDao()
    // Nie potrzebujemy photoDao bezpośrednio tutaj, bo używamy database.photoDao() w transakcji

    /**
     * Enum reprezentujący możliwy wynik operacji dodawania klienta i paragonów.
     */
    enum class AddResult {
        SUCCESS,
        ERROR_DATE_FORMAT, // Ogólny błąd formatu daty paragonu
        ERROR_VERIFICATION_DATE_FORMAT, // Błąd formatu daty weryfikacji
        ERROR_DUPLICATE_RECEIPT,
        ERROR_STORE_NUMBER_MISSING,
        ERROR_DATABASE,
        ERROR_UNKNOWN
    }

    /**
     * Dodaje nowego klienta wraz z listą powiązanych paragonów i zdjęć w jednej transakcji bazodanowej.
     *
     * @param clientDescription Opis klienta.
     * @param clientAppNumber Numer aplikacji klienta.
     * @param amoditNumber Numer Amodit klienta.
     * @param clientPhotoUris Lista URI zdjęć klienta do dodania.
     * @param transactionPhotoUris Lista URI zdjęć transakcji do dodania.
     * @param receiptsData Lista danych paragonów do dodania (zawiera opcjonalną datę weryfikacji).
     * @return [AddResult] Enum wskazujący wynik operacji.
     */
    suspend fun addClientWithReceiptsTransactionally(
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?,
        clientPhotoUris: List<String>, // Nowa lista URI zdjęć klienta
        transactionPhotoUris: List<String>, // Nowa lista URI zdjęć transakcji
        receiptsData: List<ReceiptData>
    ): AddResult = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            database.withTransaction {
                // Krok 1: Dodanie Klienta (już bez photoUri)
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

                // Krok 2a: Dodanie zdjęć klienta
                for (uri in clientPhotoUris) {
                    val photo = Photo(clientId = clientId, uri = uri, type = PhotoType.CLIENT)
                    database.photoDao().insertPhoto(photo) // Używamy photoDao() z instancji bazy
                    Log.d("AddClientViewModel", "Transakcja: Dodano zdjęcie klienta: $uri dla klienta ID: $clientId")
                }

                // Krok 2b: Dodanie zdjęć transakcji
                for (uri in transactionPhotoUris) {
                    val photo = Photo(clientId = clientId, uri = uri, type = PhotoType.TRANSACTION)
                    database.photoDao().insertPhoto(photo)
                    Log.d("AddClientViewModel", "Transakcja: Dodano zdjęcie transakcji: $uri dla klienta ID: $clientId")
                }

                // Krok 3: Przetwarzanie i dodawanie Paragonów (istniejący kod)
                for (receiptData in receiptsData) {
                    val verificationDateStringFromData = receiptData.verificationDateString // Pobierz z ReceiptData

                    if (receiptData.storeNumber.isBlank()) {
                        Log.e("AddClientViewModel", "Transakcja: Brak numeru drogerii dla paragonu: ${receiptData.receiptNumber}")
                        throw StoreNumberMissingException()
                    }

                    // Parsowanie daty paragonu
                    val receiptDate: Date = try {
                        dateFormat.parse(receiptData.receiptDate) as Date
                    } catch (e: ParseException) {
                        Log.e("AddClientViewModel", "Transakcja: Błąd formatu daty paragonu: ${receiptData.receiptDate}")
                        throw DateFormatException()
                    }

                    // Parsowanie daty weryfikacji (teraz dla każdego paragonu)
                    val verificationDate: Date? = if (!verificationDateStringFromData.isNullOrBlank()) {
                        try {
                            dateFormat.parse(verificationDateStringFromData) as Date
                        } catch (e: ParseException) {
                            Log.e("AddClientViewModel", "Transakcja: Błąd formatu daty weryfikacji: $verificationDateStringFromData")
                            throw VerificationDateFormatException() // Rzuć nowy wyjątek
                        }
                    } else {
                        null
                    }

                    // Obsługa Sklepu (znajdź lub utwórz)
                    var store = storeDao.getStoreByNumber(receiptData.storeNumber)
                    val storeId: Long
                    if (store == null) {
                        store = Store(storeNumber = receiptData.storeNumber)
                        storeDao.insertStore(store)
                        store = storeDao.getStoreByNumber(receiptData.storeNumber)
                        if (store == null) {
                            throw DatabaseException(getApplication<Application>().getString(R.string.error_creating_store, receiptData.storeNumber))
                        }
                        storeId = store.id
                        Log.d("AddClientViewModel", "Transakcja: Dodano nową drogerię: ${receiptData.storeNumber}, ID: $storeId")
                    } else {
                        storeId = store.id
                    }

                    // Walidacja Duplikatów Paragonów
                    val existingReceipt = receiptDao.findByNumberDateStore(receiptData.receiptNumber, receiptDate, storeId)
                    if (existingReceipt != null) {
                        Log.e("AddClientViewModel", "Transakcja: Znaleziono duplikat paragonu: Nr ${receiptData.receiptNumber}, Data ${receiptData.receiptDate}, Sklep ID $storeId")
                        throw DuplicateReceiptException()
                    }

                    // Dodanie Paragonu
                    val receipt = Receipt(
                        receiptNumber = receiptData.receiptNumber,
                        receiptDate = receiptDate,
                        storeId = storeId,
                        verificationDate = verificationDate, // Używamy sparsowanej daty weryfikacji
                        clientId = clientId
                    )
                    receiptDao.insertReceipt(receipt)
                }
            }
            AddResult.SUCCESS
        } catch (e: DateFormatException) {
            AddResult.ERROR_DATE_FORMAT
        } catch (e: VerificationDateFormatException) { // Obsługa nowego wyjątku
            AddResult.ERROR_VERIFICATION_DATE_FORMAT
        } catch (e: DuplicateReceiptException) {
            AddResult.ERROR_DUPLICATE_RECEIPT
        } catch (e: StoreNumberMissingException) {
            AddResult.ERROR_STORE_NUMBER_MISSING
        } catch (e: DatabaseException) {
            Log.e("AddClientViewModel", "Transakcja: Błąd bazy danych.", e)
            AddResult.ERROR_DATABASE
        } catch (e: Exception) {
            Log.e("AddClientViewModel", "Transakcja: Nieznany błąd.", e)
            AddResult.ERROR_UNKNOWN
        }
    }

    // Prywatne klasy wyjątków
    private class DateFormatException : Exception()
    private class VerificationDateFormatException : Exception() // Nowy wyjątek
    private class DuplicateReceiptException : Exception()
    private class StoreNumberMissingException : Exception()
    private class DatabaseException(message: String) : Exception(message)
}

