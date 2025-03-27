package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.core.net.toUri // Potrzebny do parsowania URI
import androidx.lifecycle.AndroidViewModel
import com.kaminski.paragownik.R
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File // Potrzebny do operacji na plikach
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel dla EditReceiptActivity.
 * Odpowiada za logikę biznesową związaną z edycją i usuwaniem paragonów/klientów,
 * w tym obsługę URI zdjęcia klienta oraz usuwanie plików zdjęć.
 */
class EditReceiptViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val storeDao: StoreDao = database.storeDao()
    private val clientDao: ClientDao = database.clientDao()

    /**
     * Enum reprezentujący możliwy wynik operacji edycji lub usuwania.
     */
    enum class EditResult {
        SUCCESS,
        ERROR_NOT_FOUND,
        ERROR_DATE_FORMAT,
        ERROR_DUPLICATE_RECEIPT,
        ERROR_STORE_NUMBER_MISSING,
        ERROR_DATABASE,
        ERROR_UNKNOWN
    }

    /**
     * Pobiera Flow emitujący parę: [ReceiptWithClient?] oraz numer sklepu [String?].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getReceiptWithClientAndStoreNumber(receiptId: Long): Flow<Pair<ReceiptWithClient?, String?>> {
        return receiptDao.getReceiptWithClientFlow(receiptId)
            .flatMapLatest { receiptWithClient ->
                if (receiptWithClient == null) {
                    kotlinx.coroutines.flow.flowOf(Pair(null, null))
                } else {
                    storeDao.getStoreByIdFlow(receiptWithClient.receipt.storeId)
                        .map { store ->
                            Pair(receiptWithClient, store?.storeNumber)
                        }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Aktualizuje dane istniejącego paragonu oraz powiązanego z nim klienta, w tym URI zdjęcia.
     */
    suspend fun updateReceiptAndClient(
        receiptId: Long,
        storeNumberString: String,
        receiptNumber: String,
        receiptDateString: String,
        verificationDateString: String?,
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?,
        photoUri: String?
    ): EditResult = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // Pobierz istniejące dane
            val existingReceiptWithClient = receiptDao.getReceiptWithClient(receiptId)
            if (existingReceiptWithClient == null || existingReceiptWithClient.client == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono paragonu (ID: $receiptId) lub klienta do edycji.")
                return@withContext EditResult.ERROR_NOT_FOUND
            }
            val existingReceipt = existingReceiptWithClient.receipt
            val existingClient = existingReceiptWithClient.client

            // Walidacja i parsowanie dat
            val receiptDate: Date = try {
                dateFormat.parse(receiptDateString) as Date
            } catch (e: ParseException) {
                Log.e("EditReceiptViewModel", "Błąd formatu daty paragonu: $receiptDateString")
                return@withContext EditResult.ERROR_DATE_FORMAT
            }
            val verificationDate: Date? = if (!verificationDateString.isNullOrBlank()) {
                try {
                    dateFormat.parse(verificationDateString) as Date
                } catch (e: ParseException) {
                    Log.w("EditReceiptViewModel", "Błąd formatu daty weryfikacji (ignorowanie): $verificationDateString")
                    null
                }
            } else {
                null
            }

            // Walidacja i obsługa numeru sklepu
            if (storeNumberString.isBlank()) {
                Log.e("EditReceiptViewModel", "Brak numeru drogerii podczas edycji.")
                return@withContext EditResult.ERROR_STORE_NUMBER_MISSING
            }
            var store = storeDao.getStoreByNumber(storeNumberString)
            val storeId: Long
            if (store == null) {
                store = Store(storeNumber = storeNumberString)
                storeDao.insertStore(store)
                store = storeDao.getStoreByNumber(storeNumberString)
                if (store == null) {
                    throw DatabaseException(getApplication<Application>().getString(R.string.error_creating_store_edit, storeNumberString))
                }
                storeId = store.id
            } else {
                storeId = store.id
            }

            // Walidacja duplikatów paragonów
            val potentialDuplicate = receiptDao.findByNumberDateStore(receiptNumber, receiptDate, storeId)
            if (potentialDuplicate != null && potentialDuplicate.id != receiptId) {
                Log.e("EditReceiptViewModel", "Edycja: Znaleziono inny paragon (ID: ${potentialDuplicate.id}) z tymi samymi danymi.")
                return@withContext EditResult.ERROR_DUPLICATE_RECEIPT
            }

            // Aktualizacja Paragonu
            val updatedReceipt = existingReceipt.copy(
                receiptNumber = receiptNumber,
                receiptDate = receiptDate,
                storeId = storeId,
                verificationDate = verificationDate
            )
            receiptDao.updateReceipt(updatedReceipt)

            // Aktualizacja Klienta (w tym photoUri)
            val updatedClient = existingClient.copy(
                description = clientDescription?.takeIf { it.isNotBlank() },
                clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                amoditNumber = amoditNumber?.takeIf { it.isNotBlank() },
                // Użyj nowego photoUri, jeśli nie jest null/pusty, w przeciwnym razie zostaw stare.
                photoUri = photoUri?.takeIf { it.isNotBlank() } ?: existingClient.photoUri
            )
            clientDao.updateClient(updatedClient)

            Log.d("EditReceiptViewModel", "Paragon (ID: $receiptId) i Klient (ID: ${existingClient.id}) zaktualizowane.")
            EditResult.SUCCESS
        } catch (e: DatabaseException) {
             Log.e("EditReceiptViewModel", "Błąd bazy danych podczas aktualizacji.", e)
             EditResult.ERROR_DATABASE
        }
        catch (e: Exception) {
            Log.e("EditReceiptViewModel", "Nieznany błąd podczas aktualizacji.", e)
            EditResult.ERROR_UNKNOWN
        }
    }

    /**
     * Usuwa podany paragon z bazy danych i czyści ewentualnie osieroconych klientów/sklepy,
     * w tym usuwa plik zdjęcia klienta, jeśli klient jest usuwany.
     */
    suspend fun deleteReceipt(receipt: Receipt): EditResult = withContext(Dispatchers.IO) {
        try {
            val receiptToDelete = receiptDao.getReceiptById(receipt.id)
            if (receiptToDelete == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono paragonu do usunięcia. ID: ${receipt.id}")
                return@withContext EditResult.ERROR_NOT_FOUND
            }

            val clientId = receiptToDelete.clientId
            val storeId = receiptToDelete.storeId

            // Usuń paragon
            receiptDao.deleteReceipt(receiptToDelete)
            Log.d("EditReceiptViewModel", "Paragon usunięty. ID: ${receipt.id}")

            // Sprawdź, czy klient stał się osierocony
            val clientReceiptsCount = receiptDao.getReceiptsForClientCount(clientId)
            if (clientReceiptsCount == 0) {
                val clientToDelete = clientDao.getClientById(clientId)
                clientToDelete?.let {
                    // Usuń plik zdjęcia PRZED usunięciem klienta z bazy
                    deleteImageFile(it.photoUri)
                    // Usuń klienta
                    clientDao.deleteClient(it)
                    Log.d("EditReceiptViewModel", "Klient (ID: $clientId) usunięty automatycznie.")
                }
            }

            // Sprawdź, czy sklep stał się osierocony
            val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
            if (storeReceiptsCount == 0) {
                val storeToDelete = storeDao.getStoreById(storeId)
                storeToDelete?.let {
                    storeDao.deleteStore(it)
                    Log.d("EditReceiptViewModel", "Drogeria (ID: $storeId) usunięta automatycznie.")
                }
            }

            EditResult.SUCCESS
        } catch (e: Exception) {
            Log.e("EditReceiptViewModel", "Błąd podczas usuwania paragonu (ID: ${receipt.id}).", e)
            EditResult.ERROR_DATABASE
        }
    }

    /**
     * Usuwa podanego klienta (i kaskadowo jego paragony), usuwa powiązany plik zdjęcia
     * oraz czyści ewentualnie osierocone sklepy.
     */
    suspend fun deleteClient(client: Client): EditResult = withContext(Dispatchers.IO) {
        try {
            val clientToDelete = clientDao.getClientById(client.id)
            if (clientToDelete == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono klienta do usunięcia. ID: ${client.id}")
                return@withContext EditResult.ERROR_NOT_FOUND
            }

            // Zbierz ID sklepów powiązanych z klientem PRZED usunięciem
            val associatedStoreIds = receiptDao.getStoreIdsForClient(clientToDelete.id)
            Log.d("EditReceiptViewModel", "Sklepy powiązane z klientem ${clientToDelete.id} przed usunięciem: $associatedStoreIds")

            // Usuń plik zdjęcia PRZED usunięciem klienta z bazy
            deleteImageFile(clientToDelete.photoUri)

            // Usuń klienta (paragony usuną się kaskadowo)
            clientDao.deleteClient(clientToDelete)
            Log.d("EditReceiptViewModel", "Klient (ID: ${clientToDelete.id}) usunięty.")

            // Sprawdź i wyczyść potencjalnie puste sklepy
            for (storeId in associatedStoreIds) {
                val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                Log.d("EditReceiptViewModel", "Sprawdzanie sklepu ID: $storeId. Pozostałe paragony: $storeReceiptsCount")
                if (storeReceiptsCount == 0) {
                    val storeToDelete = storeDao.getStoreById(storeId)
                    storeToDelete?.let {
                        storeDao.deleteStore(it)
                        Log.d("EditReceiptViewModel", "Drogeria (ID: $storeId) usunięta automatycznie.")
                    }
                }
            }

            EditResult.SUCCESS
        } catch (e: Exception) {
            Log.e("EditReceiptViewModel", "Błąd podczas usuwania klienta (ID: ${client.id}).", e)
            EditResult.ERROR_DATABASE
        }
    }

    /**
     * Usuwa plik zdjęcia z wewnętrznego magazynu aplikacji na podstawie jego URI.
     * @param photoUriString URI zdjęcia jako String (powinno być w formacie file://...).
     */
    private fun deleteImageFile(photoUriString: String?) {
        if (photoUriString.isNullOrBlank()) {
            return // Brak URI do usunięcia
        }

        try {
            val fileUri = photoUriString.toUri()
            // Sprawdź, czy to URI pliku ('file' scheme) i czy ścieżka zaczyna się od katalogu plików aplikacji
            if (fileUri.scheme == "file" && fileUri.path?.startsWith(getApplication<Application>().filesDir.absolutePath) == true) {
                // Utwórz obiekt File na podstawie ścieżki z URI
                val fileToDelete = File(fileUri.path!!) // Wykrzyknik jest bezpieczny, bo path nie będzie null dla file:// URI
                if (fileToDelete.exists()) {
                    if (fileToDelete.delete()) {
                        Log.d("EditReceiptViewModel", "Usunięto plik zdjęcia: $photoUriString")
                    } else {
                        Log.w("EditReceiptViewModel", "Nie udało się usunąć pliku zdjęcia: $photoUriString")
                    }
                } else {
                    // Plik mógł zostać już usunięty lub URI jest nieaktualne
                    Log.w("EditReceiptViewModel", "Plik zdjęcia do usunięcia nie istnieje: $photoUriString")
                }
            } else {
                // Logujemy ostrzeżenie, jeśli URI nie jest z oczekiwanego źródła
                Log.w("EditReceiptViewModel", "Próba usunięcia pliku z nieobsługiwanego URI lub spoza magazynu aplikacji: $photoUriString")
            }
        } catch (e: Exception) {
            // Złap wszelkie błędy (np. parsowania URI, SecurityException - choć mało prawdopodobne dla plików wewnętrznych)
            Log.e("EditReceiptViewModel", "Błąd podczas próby usunięcia pliku zdjęcia: $photoUriString", e)
        }
    }

    // Prywatna klasa wyjątku dla błędów bazy danych
    private class DatabaseException(message: String) : Exception(message)
}