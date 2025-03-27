package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
import com.kaminski.paragownik.AddClientActivity.ReceiptData
import com.kaminski.paragownik.R // Potrzebne dla zasobów string
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
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
 * wraz z jednym lub wieloma paragonami do bazy danych, w tym URI zdjęcia.
 */
class AddClientViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val clientDao: ClientDao = database.clientDao()
    private val storeDao: StoreDao = database.storeDao()

    /**
     * Enum reprezentujący możliwy wynik operacji dodawania klienta i paragonów.
     */
    enum class AddResult {
        SUCCESS,
        ERROR_DATE_FORMAT,
        ERROR_DUPLICATE_RECEIPT,
        ERROR_STORE_NUMBER_MISSING,
        ERROR_DATABASE,
        ERROR_UNKNOWN
    }

    /**
     * Dodaje nowego klienta wraz z listą powiązanych paragonów w jednej transakcji bazodanowej.
     * Zapisuje również przekazane URI zdjęcia klienta.
     *
     * @param clientDescription Opis klienta.
     * @param clientAppNumber Numer aplikacji klienta.
     * @param amoditNumber Numer Amodit klienta.
     * @param photoUri URI zdjęcia klienta jako String (może być null).
     * @param receiptsData Lista danych paragonów do dodania.
     * @param verificationDateString Data weryfikacji dla pierwszego paragonu (jako String).
     * @return [AddResult] Enum wskazujący wynik operacji.
     */
    suspend fun addClientWithReceiptsTransactionally(
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?,
        photoUri: String?, // Akceptuje URI zdjęcia
        receiptsData: List<ReceiptData>,
        verificationDateString: String?
    ): AddResult = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            database.withTransaction {
                // Krok 1: Dodanie Klienta (w tym photoUri)
                val client = Client(
                    description = clientDescription?.takeIf { it.isNotBlank() },
                    clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                    amoditNumber = amoditNumber?.takeIf { it.isNotBlank() },
                    photoUri = photoUri?.takeIf { it.isNotBlank() } // Zapisz URI zdjęcia
                )
                val clientId = clientDao.insertClient(client)
                Log.d("AddClientViewModel", "Transakcja: Dodano klienta ID: $clientId z photoUri: $photoUri")

                if (clientId == -1L) {
                    throw DatabaseException(getApplication<Application>().getString(R.string.error_inserting_client))
                }

                // Krok 2: Przetwarzanie i dodawanie Paragonów
                for (i in receiptsData.indices) {
                    val receiptData = receiptsData[i]

                    if (receiptData.storeNumber.isBlank()) {
                        Log.e("AddClientViewModel", "Transakcja: Brak numeru drogerii dla paragonu: ${receiptData.receiptNumber}")
                        throw StoreNumberMissingException()
                    }

                    val receiptDate: Date = try {
                        dateFormat.parse(receiptData.receiptDate) as Date
                    } catch (e: ParseException) {
                        Log.e("AddClientViewModel", "Transakcja: Błąd formatu daty paragonu: ${receiptData.receiptDate}")
                        throw DateFormatException()
                    }

                    val verificationDate: Date? = if (i == 0 && !verificationDateString.isNullOrBlank()) {
                        try {
                            dateFormat.parse(verificationDateString) as Date
                        } catch (e: ParseException) {
                            Log.w("AddClientViewModel", "Transakcja: Błąd formatu daty weryfikacji (ignorowanie): $verificationDateString")
                            null
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
                        verificationDate = verificationDate,
                        clientId = clientId
                    )
                    receiptDao.insertReceipt(receipt)
                    // Log.d("AddClientViewModel", "Transakcja: Dodano paragon ID: $receiptId dla klienta ID: $clientId") // receiptId nie jest tu zwracane
                }
            }
            AddResult.SUCCESS
        } catch (e: DateFormatException) {
            AddResult.ERROR_DATE_FORMAT
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
    private class DuplicateReceiptException : Exception()
    private class StoreNumberMissingException : Exception()
    private class DatabaseException(message: String) : Exception(message)
}
