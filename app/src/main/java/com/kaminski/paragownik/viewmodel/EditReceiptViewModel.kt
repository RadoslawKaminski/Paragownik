package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine // Użyj combine do połączenia Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest // Użyj flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel dla EditReceiptActivity.
 * Odpowiada za logikę edycji paragonu, klienta oraz usuwania paragonu lub klienta.
 */
class EditReceiptViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase // Instancja bazy danych
    private val receiptDao: ReceiptDao
    private val storeDao: StoreDao
    private val clientDao: ClientDao

    /**
     * Enum reprezentujący możliwy wynik operacji edycji lub usuwania.
     */
    enum class EditResult {
        SUCCESS,                    // Operacja zakończona sukcesem
        ERROR_NOT_FOUND,            // Nie znaleziono obiektu do edycji/usunięcia
        ERROR_DATE_FORMAT,          // Błąd formatu daty
        ERROR_DUPLICATE_RECEIPT,    // Próba zapisu danych, które spowodują duplikat paragonu
        ERROR_STORE_NUMBER_MISSING, // Brak numeru sklepu
        ERROR_DATABASE,             // Ogólny błąd podczas operacji bazodanowej
        ERROR_UNKNOWN               // Nieznany błąd
    }

    init {
        // Inicjalizacja bazy danych i DAO
        database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()
        storeDao = database.storeDao()
        clientDao = database.clientDao()
    }

    /**
     * Pobiera Flow, który emituje parę: obiekt [ReceiptWithClient] (zawierający pełne dane klienta)
     * oraz numer sklepu ([String]) dla podanego ID paragonu.
     * Flow reaguje na zmiany w danych paragonu, klienta i sklepu.
     * Bezpiecznie obsługuje sytuację, gdy obserwowany paragon zostanie usunięty.
     *
     * @param receiptId ID paragonu do obserwacji.
     * @return Flow emitujący parę `(ReceiptWithClient?, String?)`.
     */
    fun getReceiptWithClientAndStoreNumber(receiptId: Long): Flow<Pair<ReceiptWithClient?, String?>> {
        return receiptDao.getReceiptWithClientFlow(receiptId)
            .flatMapLatest { receiptWithClient ->
                // --- POPRAWKA: Sprawdź, czy receiptWithClient nie jest null ---
                if (receiptWithClient == null) {
                    // Jeśli paragon został usunięty, emituj parę nulli
                    kotlinx.coroutines.flow.flowOf(Pair(null, null))
                } else {
                    // Jeśli paragon istnieje, pobierz Flow dla powiązanego sklepu
                    storeDao.getStoreByIdFlow(receiptWithClient.receipt.storeId)
                        .map { store ->
                            // Połącz dane paragonu/klienta z numerem sklepu i zwróć jako parę
                            Pair(receiptWithClient, store?.storeNumber)
                        }
                }
                // --- KONIEC POPRAWKI ---
            }
            .flowOn(Dispatchers.IO) // Wykonuj operacje bazodanowe w tle
    }


    /**
     * Aktualizuje dane paragonu oraz powiązanego z nim klienta.
     * Przeprowadza walidację formatu daty i unikalności paragonu (numer, data, sklep),
     * wykluczając sprawdzany paragon z porównania duplikatów.
     *
     * @param receiptId ID edytowanego paragonu.
     * @param storeNumberString Nowy numer sklepu.
     * @param receiptNumber Nowy numer paragonu.
     * @param receiptDateString Nowa data paragonu (w formacie DD-MM-YYYY).
     * @param verificationDateString Nowa data weryfikacji (w formacie DD-MM-YYYY, może być null/pusta).
     * @param clientDescription Nowy opis klienta (może być null/pusty).
     * @param clientAppNumber Nowy numer aplikacji klienta (może być null/pusty).
     * @param amoditNumber Nowy numer Amodit klienta (może być null/pusty).
     * @param photoUri Nowe URI zdjęcia klienta (może być null).
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
        photoUri: String?
    ): EditResult = withContext(Dispatchers.IO) {
        // Formatter daty ze ścisłym sprawdzaniem
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // 1. Pobierz istniejący paragon i klienta (muszą istnieć do edycji)
            val existingReceiptWithClient = receiptDao.getReceiptWithClient(receiptId)
            // Sprawdź, czy paragon i powiązany klient istnieją
            if (existingReceiptWithClient == null || existingReceiptWithClient.client == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono paragonu lub klienta o ID paragonu: $receiptId do edycji.")
                return@withContext EditResult.ERROR_NOT_FOUND
            }
            val existingReceipt = existingReceiptWithClient.receipt
            val existingClient = existingReceiptWithClient.client

            // 2. Walidacja i parsowanie dat
            val receiptDate: Date = try {
                dateFormat.parse(receiptDateString) as Date
            } catch (e: ParseException) {
                Log.e("EditReceiptViewModel", "Błąd formatu daty paragonu podczas edycji: $receiptDateString")
                return@withContext EditResult.ERROR_DATE_FORMAT
            }
            val verificationDate: Date? = if (!verificationDateString.isNullOrBlank()) {
                try {
                    dateFormat.parse(verificationDateString) as Date
                } catch (e: ParseException) {
                    Log.w("EditReceiptViewModel", "Błąd formatu daty weryfikacji podczas edycji (ignorowanie): $verificationDateString")
                    null // Ignoruj błędną datę, nie przerywaj zapisu
                }
            } else {
                null // Jeśli puste, ustaw null
            }

            // 3. Walidacja i obsługa numeru sklepu
            if (storeNumberString.isBlank()) {
                Log.e("EditReceiptViewModel", "Brak numeru drogerii podczas edycji.")
                return@withContext EditResult.ERROR_STORE_NUMBER_MISSING
            }
            // Znajdź lub utwórz sklep
            var store = storeDao.getStoreByNumber(storeNumberString)
            val storeId: Long
            if (store == null) {
                store = Store(storeNumber = storeNumberString)
                storeDao.insertStore(store)
                store = storeDao.getStoreByNumber(storeNumberString)
                if (store == null) {
                    throw Exception("Nie udało się utworzyć lub pobrać drogerii podczas edycji: $storeNumberString")
                }
                storeId = store.id
                Log.d("EditReceiptViewModel", "Edycja: Dodano nową drogerię: $storeNumberString, ID: $storeId")
            } else {
                storeId = store.id
            }

            // 4. Walidacja duplikatów paragonów (z wykluczeniem samego siebie)
            // Sprawdź, czy istnieje INNY paragon z taką samą kombinacją numeru, daty i sklepu
            val potentialDuplicate = receiptDao.findByNumberDateStore(receiptNumber, receiptDate, storeId)
            if (potentialDuplicate != null && potentialDuplicate.id != receiptId) { // Kluczowe sprawdzenie ID
                Log.e("EditReceiptViewModel", "Edycja: Znaleziono inny paragon ($potentialDuplicate.id) z tymi samymi danymi co edytowany ($receiptId).")
                return@withContext EditResult.ERROR_DUPLICATE_RECEIPT
            }

            // 5. Przygotuj zaktualizowany obiekt paragonu
            val updatedReceipt = existingReceipt.copy(
                receiptNumber = receiptNumber,
                receiptDate = receiptDate,
                storeId = storeId,
                verificationDate = verificationDate
            )
            // Zaktualizuj paragon w bazie
            receiptDao.updateReceipt(updatedReceipt)

            // 6. Przygotuj zaktualizowany obiekt klienta
            val updatedClient = existingClient.copy(
                description = clientDescription,
                clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() }, // Zapisz tylko niepuste
                amoditNumber = amoditNumber?.takeIf { it.isNotBlank() },   // Zapisz tylko niepuste
                // Zachowaj istniejące URI zdjęcia, jeśli nowe jest null (logika zmiany zdjęcia będzie później)
                photoUri = photoUri ?: existingClient.photoUri
            )
            // Zaktualizuj klienta w bazie
            clientDao.updateClient(updatedClient)

            Log.d("EditReceiptViewModel", "Paragon i Klient zaktualizowane pomyślnie. ID Paragonu: $receiptId")
            EditResult.SUCCESS // Zwróć sukces
        } catch (e: Exception) {
            Log.e("EditReceiptViewModel", "Błąd podczas aktualizacji paragonu lub klienta.", e)
            EditResult.ERROR_DATABASE // Lub ERROR_UNKNOWN
        }
    }

    /**
     * Usuwa podany paragon z bazy danych.
     * Po usunięciu sprawdza, czy powiązany klient lub sklep nie stały się puste
     * i usuwa je, jeśli tak się stało.
     *
     * @param receipt Obiekt paragonu do usunięcia.
     * @return [EditResult] Enum wskazujący wynik operacji.
     */
    suspend fun deleteReceipt(receipt: Receipt): EditResult = withContext(Dispatchers.IO) {
        try {
            // Pobierz paragon z bazy, aby upewnić się, że istnieje i mamy aktualne dane
            val receiptToDelete = receiptDao.getReceiptById(receipt.id)
            if (receiptToDelete == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono paragonu do usunięcia. ID: ${receipt.id}")
                return@withContext EditResult.ERROR_NOT_FOUND
            }

            // Zapamiętaj ID klienta i sklepu przed usunięciem paragonu
            val clientId = receiptToDelete.clientId
            val storeId = receiptToDelete.storeId

            // Usuń paragon
            receiptDao.deleteReceipt(receiptToDelete)
            Log.d("EditReceiptViewModel", "Paragon usunięty. ID: ${receipt.id}")

            // Sprawdź, czy klient ma jeszcze jakieś paragony
            val clientReceiptsCount = receiptDao.getReceiptsForClientCount(clientId)
            if (clientReceiptsCount == 0) {
                // Jeśli nie, pobierz i usuń klienta
                val clientToDelete = clientDao.getClientById(clientId)
                clientToDelete?.let {
                    clientDao.deleteClient(it)
                    Log.d("EditReceiptViewModel", "Klient usunięty automatycznie (brak paragonów). ID: $clientId")
                }
            }

            // Sprawdź, czy sklep ma jeszcze jakieś paragony
            val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
            if (storeReceiptsCount == 0) {
                // Jeśli nie, pobierz i usuń sklep
                val storeToDelete = storeDao.getStoreById(storeId)
                storeToDelete?.let {
                    storeDao.deleteStore(it)
                    Log.d("EditReceiptViewModel", "Drogeria usunięta automatycznie (brak paragonów). ID: $storeId")
                }
            }

            EditResult.SUCCESS // Zwróć sukces
        } catch (e: Exception) {
            Log.e("EditReceiptViewModel", "Błąd podczas usuwania paragonu.", e)
            EditResult.ERROR_DATABASE
        }
    }

    /**
     * Usuwa podanego klienta oraz wszystkie jego paragony (dzięki relacji kaskadowej).
     * Po usunięciu klienta, identyfikuje wszystkie sklepy, z którymi był on powiązany,
     * a następnie sprawdza i usuwa te sklepy, które stały się puste.
     *
     * @param client Obiekt klienta do usunięcia (wystarczy, że ma poprawne ID).
     * @return [EditResult] Enum wskazujący wynik operacji.
     */
    suspend fun deleteClient(client: Client): EditResult = withContext(Dispatchers.IO) {
        try {
            // Pobierz klienta z bazy, aby upewnić się, że istnieje
            val clientToDelete = clientDao.getClientById(client.id)
            if (clientToDelete == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono klienta do usunięcia. ID: ${client.id}")
                return@withContext EditResult.ERROR_NOT_FOUND
            }

            // 1. Pobierz listę unikalnych ID sklepów powiązanych z tym klientem PRZED jego usunięciem
            val associatedStoreIds = receiptDao.getStoreIdsForClient(clientToDelete.id)
            Log.d("EditReceiptViewModel", "Sklepy powiązane z klientem ${clientToDelete.id} przed usunięciem: $associatedStoreIds")

            // 2. Usuń klienta - Room automatycznie usunie powiązane paragony (onDelete = CASCADE)
            clientDao.deleteClient(clientToDelete)
            Log.d("EditReceiptViewModel", "Klient usunięty (kaskada paragonów). ID: ${clientToDelete.id}")

            // 3. Sprawdź każdą drogerię, z którą klient był powiązany
            for (storeId in associatedStoreIds) {
                // Policz, ile paragonów pozostało w tej drogerii PO usunięciu klienta
                val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                Log.d("EditReceiptViewModel", "Sprawdzanie sklepu ID: $storeId. Liczba pozostałych paragonów: $storeReceiptsCount")
                if (storeReceiptsCount == 0) {
                    // Jeśli drogeria jest teraz pusta, usuń ją
                    val storeToDelete = storeDao.getStoreById(storeId)
                    storeToDelete?.let {
                        storeDao.deleteStore(it)
                        Log.d("EditReceiptViewModel", "Drogeria usunięta automatycznie (brak paragonów po usunięciu klienta). ID: $storeId")
                    }
                }
            }

            EditResult.SUCCESS // Zwróć sukces
        } catch (e: Exception) {
            Log.e("EditReceiptViewModel", "Błąd podczas usuwania klienta i sprawdzania drogerii.", e)
            EditResult.ERROR_DATABASE
        }
    }
}