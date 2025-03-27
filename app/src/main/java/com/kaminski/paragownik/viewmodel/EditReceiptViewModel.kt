package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.kaminski.paragownik.R // Potrzebne dla zasobów string
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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel dla EditReceiptActivity.
 * Odpowiada za logikę biznesową związaną z edycją i usuwaniem paragonów/klientów,
 * w tym obsługę URI zdjęcia klienta.
 */
class EditReceiptViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
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
     * Reaguje na zmiany w danych paragonu, klienta i sklepu.
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
     *
     * @param receiptId ID edytowanego paragonu.
     * @param storeNumberString Nowy numer sklepu.
     * @param receiptNumber Nowy numer paragonu.
     * @param receiptDateString Nowa data paragonu (DD-MM-YYYY).
     * @param verificationDateString Nowa data weryfikacji (DD-MM-YYYY, opcjonalna).
     * @param clientDescription Nowy opis klienta.
     * @param clientAppNumber Nowy numer aplikacji klienta.
     * @param amoditNumber Nowy numer Amodit klienta.
     * @param photoUri Nowe URI zdjęcia klienta jako String (może być null).
     * @return [EditResult] Enum wskazujący wynik operacji.
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
        photoUri: String? // Akceptuje URI zdjęcia
    ): EditResult = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // Krok 1: Pobierz istniejące dane
            val existingReceiptWithClient = receiptDao.getReceiptWithClient(receiptId)
            if (existingReceiptWithClient == null || existingReceiptWithClient.client == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono paragonu (ID: $receiptId) lub klienta do edycji.")
                return@withContext EditResult.ERROR_NOT_FOUND
            }
            val existingReceipt = existingReceiptWithClient.receipt
            val existingClient = existingReceiptWithClient.client

            // Krok 2: Walidacja i parsowanie dat
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

            // Krok 3: Walidacja i obsługa numeru sklepu
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
                Log.d("EditReceiptViewModel", "Edycja: Dodano nową drogerię: $storeNumberString, ID: $storeId")
            } else {
                storeId = store.id
            }

            // Krok 4: Walidacja duplikatów paragonów (z wykluczeniem samego siebie)
            val potentialDuplicate = receiptDao.findByNumberDateStore(receiptNumber, receiptDate, storeId)
            if (potentialDuplicate != null && potentialDuplicate.id != receiptId) {
                Log.e("EditReceiptViewModel", "Edycja: Znaleziono inny paragon (ID: ${potentialDuplicate.id}) z tymi samymi danymi.")
                return@withContext EditResult.ERROR_DUPLICATE_RECEIPT
            }

            // Krok 5: Aktualizacja Paragonu
            val updatedReceipt = existingReceipt.copy(
                receiptNumber = receiptNumber,
                receiptDate = receiptDate,
                storeId = storeId,
                verificationDate = verificationDate
            )
            receiptDao.updateReceipt(updatedReceipt)

            // Krok 6: Aktualizacja Klienta (w tym photoUri)
            val updatedClient = existingClient.copy(
                description = clientDescription?.takeIf { it.isNotBlank() },
                clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                amoditNumber = amoditNumber?.takeIf { it.isNotBlank() },
                // Użyj nowego photoUri, jeśli nie jest null/pusty, w przeciwnym razie zostaw stare.
                // Pozwala to na zachowanie zdjęcia, jeśli użytkownik go nie zmienił.
                // Jeśli chcemy umożliwić usunięcie zdjęcia, trzeba by dodać osobną logikę (np. przycisk usuń zdjęcie).
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
     * Usuwa podany paragon z bazy danych i czyści ewentualnie osieroconych klientów/sklepy.
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

            receiptDao.deleteReceipt(receiptToDelete)
            Log.d("EditReceiptViewModel", "Paragon usunięty. ID: ${receipt.id}")

            // Czyszczenie Klienta
            val clientReceiptsCount = receiptDao.getReceiptsForClientCount(clientId)
            if (clientReceiptsCount == 0) {
                val clientToDelete = clientDao.getClientById(clientId)
                clientToDelete?.let {
                    clientDao.deleteClient(it)
                    Log.d("EditReceiptViewModel", "Klient (ID: $clientId) usunięty automatycznie.")
                }
            }

            // Czyszczenie Sklepu
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
     * Usuwa podanego klienta (i kaskadowo jego paragony) oraz czyści ewentualnie osierocone sklepy.
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

    // Prywatna klasa wyjątku dla błędów bazy danych
    private class DatabaseException(message: String) : Exception(message)
}
