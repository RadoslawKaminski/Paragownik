package com.kaminski.paragownik.viewmodel

// import androidx.lifecycle.viewModelScope // Nie jest bezpośrednio używany, ale withContext go wymaga
// import kotlinx.coroutines.flow.combine // Nieużywany
// import kotlinx.coroutines.flow.filterNotNull // Nieużywany
// import kotlinx.coroutines.launch // Nieużywany
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
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
 * Odpowiada za logikę biznesową związaną z:
 * - Pobieraniem danych paragonu i powiązanego klienta do edycji.
 * - Aktualizowaniem danych paragonu i klienta.
 * - Usuwaniem pojedynczego paragonu (z logiką czyszczenia pustych klientów/sklepów).
 * - Usuwaniem klienta wraz ze wszystkimi jego paragonami (z logiką czyszczenia pustych sklepów).
 */
class EditReceiptViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase
    private val receiptDao: ReceiptDao
    private val storeDao: StoreDao
    private val clientDao: ClientDao

    /**
     * Enum reprezentujący możliwy wynik operacji edycji lub usuwania.
     * Używany do komunikacji wyniku (sukcesu lub konkretnego błędu) z Aktywnością (UI).
     */
    enum class EditResult {
        SUCCESS,                    // Operacja zakończona sukcesem
        ERROR_NOT_FOUND,            // Nie znaleziono obiektu (paragonu/klienta) do edycji/usunięcia
        ERROR_DATE_FORMAT,          // Błąd formatu daty podczas edycji
        ERROR_DUPLICATE_RECEIPT,    // Próba zapisu danych, które spowodują duplikat paragonu (innego niż edytowany)
        ERROR_STORE_NUMBER_MISSING, // Brak numeru sklepu podczas edycji
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
     * Pobiera [Flow], który emituje parę: obiekt [ReceiptWithClient?] (paragon z klientem)
     * oraz numer sklepu [String?] dla podanego ID paragonu.
     * Flow reaguje na zmiany w danych paragonu, klienta i sklepu.
     * Używa `flatMapLatest` do dynamicznego przełączania się na obserwację sklepu,
     * gdy zmieni się `storeId` w paragonie.
     * Bezpiecznie obsługuje sytuację, gdy obserwowany paragon zostanie usunięty (emituje parę nulli).
     *
     * @param receiptId ID paragonu do obserwacji.
     * @return [Flow] emitujący parę `(ReceiptWithClient?, String?)`.
     */
    @OptIn(ExperimentalCoroutinesApi::class) // Wymagane dla flatMapLatest
    fun getReceiptWithClientAndStoreNumber(receiptId: Long): Flow<Pair<ReceiptWithClient?, String?>> {
        // 1. Obserwuj Flow dla ReceiptWithClient o podanym ID
        return receiptDao.getReceiptWithClientFlow(receiptId)
            // 2. Użyj flatMapLatest: gdy receiptWithClient się zmieni (lub na początku),
            //    anuluj poprzednią wewnętrzną obserwację (sklepu) i uruchom nową.
            .flatMapLatest { receiptWithClient ->
                // Sprawdź, czy paragon (i klient) został znaleziony.
                if (receiptWithClient == null) {
                    // Jeśli paragon został usunięty (lub nie istnieje), emituj parę nulli.
                    // Używamy flowOf do stworzenia Flow emitującego pojedynczą wartość.
                    kotlinx.coroutines.flow.flowOf(Pair(null, null))
                } else {
                    // Jeśli paragon istnieje, pobierz Flow dla powiązanego sklepu na podstawie storeId z paragonu.
                    storeDao.getStoreByIdFlow(receiptWithClient.receipt.storeId)
                        // 3. Użyj map: gdy dane sklepu się zmienią (lub na początku),
                        //    połącz aktualny receiptWithClient z numerem sklepu (lub null) w parę.
                        .map { store ->
                            Pair(receiptWithClient, store?.storeNumber)
                        }
                }
            }
            // 4. Wykonuj operacje bazodanowe (zapytania DAO) w tle (Dispatcher IO).
            .flowOn(Dispatchers.IO)
    }


    /**
     * Aktualizuje dane istniejącego paragonu oraz powiązanego z nim klienta w bazie danych.
     * Przeprowadza walidację formatu daty, unikalności paragonu (numer, data, sklep),
     * wykluczając sam edytowany paragon z porównania duplikatów.
     * Obsługuje znalezienie lub utworzenie sklepu, jeśli numer został zmieniony na nieistniejący.
     *
     * @param receiptId ID edytowanego paragonu.
     * @param storeNumberString Nowy numer sklepu (nie może być pusty).
     * @param receiptNumber Nowy numer paragonu.
     * @param receiptDateString Nowa data paragonu (jako String w formacie DD-MM-YYYY).
     * @param verificationDateString Nowa data weryfikacji (jako String, może być null/pusta).
     * @param clientDescription Nowy opis klienta (może być null/pusty).
     * @param clientAppNumber Nowy numer aplikacji klienta (może być null/pusty).
     * @param amoditNumber Nowy numer Amodit klienta (może być null/pusty).
     * @param photoUri Nowe URI zdjęcia klienta (obecnie zawsze null, może być null).
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
        photoUri: String? // Obecnie nieużywane do zapisu, ale gotowe na przyszłość
    ): EditResult = withContext(Dispatchers.IO) { // Wykonaj w tle
        // Formatter daty ze ścisłym sprawdzaniem
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // --- Krok 1: Pobierz istniejące dane ---
            // Pobierz aktualny obiekt ReceiptWithClient (jednorazowo)
            val existingReceiptWithClient = receiptDao.getReceiptWithClient(receiptId)
            // Sprawdź, czy paragon i powiązany klient istnieją w bazie
            if (existingReceiptWithClient == null || existingReceiptWithClient.client == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono paragonu (ID: $receiptId) lub powiązanego klienta do edycji.")
                return@withContext EditResult.ERROR_NOT_FOUND // Zwróć błąd, jeśli nie znaleziono
            }
            // Odpakuj istniejący paragon i klienta
            val existingReceipt = existingReceiptWithClient.receipt
            val existingClient = existingReceiptWithClient.client

            // --- Krok 2: Walidacja i parsowanie dat ---
            // Parsuj datę paragonu
            val receiptDate: Date = try {
                dateFormat.parse(receiptDateString) as Date
            } catch (e: ParseException) {
                Log.e("EditReceiptViewModel", "Błąd formatu daty paragonu podczas edycji: $receiptDateString")
                return@withContext EditResult.ERROR_DATE_FORMAT // Zwróć błąd formatu daty
            }
            // Parsuj datę weryfikacji (jeśli podano)
            val verificationDate: Date? = if (!verificationDateString.isNullOrBlank()) {
                try {
                    dateFormat.parse(verificationDateString) as Date
                } catch (e: ParseException) {
                    // Zignoruj błędny format daty weryfikacji, ale zaloguj ostrzeżenie
                    Log.w("EditReceiptViewModel", "Błąd formatu daty weryfikacji podczas edycji (ignorowanie): $verificationDateString")
                    null // Traktuj jako brak daty weryfikacji
                }
            } else {
                null // Jeśli string jest pusty lub null, ustaw datę na null
            }

            // --- Krok 3: Walidacja i obsługa numeru sklepu ---
            // Sprawdź, czy numer sklepu nie jest pusty
            if (storeNumberString.isBlank()) {
                Log.e("EditReceiptViewModel", "Brak numeru drogerii podczas edycji.")
                return@withContext EditResult.ERROR_STORE_NUMBER_MISSING // Zwróć błąd braku numeru sklepu
            }
            // Znajdź sklep o podanym numerze lub utwórz nowy, jeśli nie istnieje
            var store = storeDao.getStoreByNumber(storeNumberString)
            val storeId: Long
            if (store == null) {
                // Sklep nie istnieje, utwórz nowy
                store = Store(storeNumber = storeNumberString)
                storeDao.insertStore(store)
                // Pobierz ponownie, aby uzyskać ID
                store = storeDao.getStoreByNumber(storeNumberString)
                if (store == null) {
                    // Mało prawdopodobny błąd, ale zabezpieczenie
                    throw Exception("Nie udało się utworzyć lub pobrać drogerii podczas edycji: $storeNumberString")
                }
                storeId = store.id
                Log.d("EditReceiptViewModel", "Edycja: Dodano nową drogerię: $storeNumberString, ID: $storeId")
            } else {
                // Sklep istnieje, użyj jego ID
                storeId = store.id
            }

            // --- Krok 4: Walidacja duplikatów paragonów (z wykluczeniem samego siebie) ---
            // Sprawdź, czy istnieje INNY paragon z taką samą kombinacją numeru, daty i sklepu
            val potentialDuplicate = receiptDao.findByNumberDateStore(receiptNumber, receiptDate, storeId)
            // Jeśli znaleziono potencjalny duplikat I jego ID jest RÓŻNE od ID edytowanego paragonu
            if (potentialDuplicate != null && potentialDuplicate.id != receiptId) {
                Log.e("EditReceiptViewModel", "Edycja: Znaleziono inny paragon (ID: ${potentialDuplicate.id}) z tymi samymi danymi co edytowany (ID: $receiptId).")
                return@withContext EditResult.ERROR_DUPLICATE_RECEIPT // Zwróć błąd duplikatu
            }

            // --- Krok 5: Aktualizacja Paragonu ---
            // Utwórz kopię istniejącego paragonu z zaktualizowanymi polami
            val updatedReceipt = existingReceipt.copy(
                receiptNumber = receiptNumber,
                receiptDate = receiptDate,
                storeId = storeId, // Użyj nowego (lub starego) storeId
                verificationDate = verificationDate
                // clientId pozostaje bez zmian
            )
            // Zaktualizuj paragon w bazie danych
            receiptDao.updateReceipt(updatedReceipt)

            // --- Krok 6: Aktualizacja Klienta ---
            // Utwórz kopię istniejącego klienta z zaktualizowanymi polami
            val updatedClient = existingClient.copy(
                description = clientDescription?.takeIf { it.isNotBlank() }, // Zapisz tylko niepuste
                clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                amoditNumber = amoditNumber?.takeIf { it.isNotBlank() },
                // Zachowaj istniejące URI zdjęcia, jeśli nowe jest null (logika zmiany zdjęcia będzie później)
                // Jeśli photoUri przekazane jako argument nie jest null, użyj go, w przeciwnym razie zostaw stare.
                photoUri = photoUri ?: existingClient.photoUri
            )
            // Zaktualizuj klienta w bazie danych
            clientDao.updateClient(updatedClient)

            Log.d("EditReceiptViewModel", "Paragon (ID: $receiptId) i Klient (ID: ${existingClient.id}) zaktualizowane pomyślnie.")
            EditResult.SUCCESS // Zwróć sukces
        } catch (e: Exception) {
            // Złap wszelkie inne wyjątki (np. błędy bazy danych)
            Log.e("EditReceiptViewModel", "Błąd podczas aktualizacji paragonu lub klienta.", e)
            EditResult.ERROR_DATABASE // Lub ERROR_UNKNOWN
        }
    }

    /**
     * Usuwa podany paragon z bazy danych.
     * Po usunięciu paragonu, sprawdza:
     * 1. Czy powiązany klient nie ma już żadnych innych paragonów. Jeśli nie, usuwa klienta.
     * 2. Czy sklep, do którego należał paragon, nie ma już żadnych innych paragonów. Jeśli nie, usuwa sklep.
     *
     * @param receipt Obiekt [Receipt] do usunięcia (musi mieć poprawne ID).
     * @return [EditResult] Enum wskazujący wynik operacji.
     */
    suspend fun deleteReceipt(receipt: Receipt): EditResult = withContext(Dispatchers.IO) {
        try {
            // Pobierz paragon z bazy, aby upewnić się, że istnieje i mamy aktualne dane (clientId, storeId)
            val receiptToDelete = receiptDao.getReceiptById(receipt.id)
            if (receiptToDelete == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono paragonu do usunięcia. ID: ${receipt.id}")
                return@withContext EditResult.ERROR_NOT_FOUND // Zwróć błąd, jeśli nie znaleziono
            }

            // Zapamiętaj ID klienta i sklepu PRZED usunięciem paragonu
            val clientId = receiptToDelete.clientId
            val storeId = receiptToDelete.storeId

            // Usuń paragon z bazy
            receiptDao.deleteReceipt(receiptToDelete)
            Log.d("EditReceiptViewModel", "Paragon usunięty. ID: ${receipt.id}")

            // --- Sprawdzenie i czyszczenie Klienta ---
            // Policz, ile paragonów pozostało dla tego klienta
            val clientReceiptsCount = receiptDao.getReceiptsForClientCount(clientId)
            if (clientReceiptsCount == 0) {
                // Jeśli klient nie ma już paragonów, usuń go
                val clientToDelete = clientDao.getClientById(clientId) // Pobierz pełny obiekt klienta
                clientToDelete?.let { // Sprawdź, czy na pewno istnieje
                    clientDao.deleteClient(it) // Usuń klienta
                    Log.d("EditReceiptViewModel", "Klient (ID: $clientId) usunięty automatycznie (brak paragonów).")
                }
            }

            // --- Sprawdzenie i czyszczenie Sklepu ---
            // Policz, ile paragonów pozostało dla tego sklepu
            val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
            if (storeReceiptsCount == 0) {
                // Jeśli sklep nie ma już paragonów, usuń go
                val storeToDelete = storeDao.getStoreById(storeId) // Pobierz pełny obiekt sklepu
                storeToDelete?.let { // Sprawdź, czy na pewno istnieje
                    storeDao.deleteStore(it) // Usuń sklep
                    Log.d("EditReceiptViewModel", "Drogeria (ID: $storeId) usunięta automatycznie (brak paragonów).")
                }
            }

            EditResult.SUCCESS // Zwróć sukces
        } catch (e: Exception) {
            // Złap wszelkie błędy podczas operacji
            Log.e("EditReceiptViewModel", "Błąd podczas usuwania paragonu (ID: ${receipt.id}).", e)
            EditResult.ERROR_DATABASE
        }
    }

    /**
     * Usuwa podanego klienta oraz wszystkie jego paragony (dzięki relacji `onDelete = CASCADE`).
     * Po usunięciu klienta, identyfikuje wszystkie sklepy, z którymi był on powiązany
     * (na podstawie jego paragonów PRZED usunięciem), a następnie sprawdza każdy z tych sklepów
     * i usuwa te, które stały się puste (nie mają już żadnych paragonów).
     *
     * @param client Obiekt [Client] do usunięcia (wystarczy, że ma poprawne ID).
     * @return [EditResult] Enum wskazujący wynik operacji.
     */
    suspend fun deleteClient(client: Client): EditResult = withContext(Dispatchers.IO) {
        try {
            // Pobierz klienta z bazy, aby upewnić się, że istnieje
            val clientToDelete = clientDao.getClientById(client.id)
            if (clientToDelete == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono klienta do usunięcia. ID: ${client.id}")
                return@withContext EditResult.ERROR_NOT_FOUND // Zwróć błąd, jeśli nie znaleziono
            }

            // --- Krok 1: Zbierz ID sklepów powiązanych z klientem PRZED usunięciem ---
            // Pobierz listę unikalnych ID sklepów, w których klient miał paragony.
            val associatedStoreIds = receiptDao.getStoreIdsForClient(clientToDelete.id)
            Log.d("EditReceiptViewModel", "Sklepy powiązane z klientem ${clientToDelete.id} przed usunięciem: $associatedStoreIds")

            // --- Krok 2: Usuń klienta ---
            // Room automatycznie usunie wszystkie paragony tego klienta dzięki onDelete = CASCADE.
            clientDao.deleteClient(clientToDelete)
            Log.d("EditReceiptViewModel", "Klient (ID: ${clientToDelete.id}) usunięty (kaskada paragonów).")

            // --- Krok 3: Sprawdź i wyczyść potencjalnie puste sklepy ---
            // Iteruj przez ID sklepów, z którymi klient był powiązany
            for (storeId in associatedStoreIds) {
                // Policz, ile paragonów pozostało w tej drogerii PO usunięciu klienta i jego paragonów
                val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                Log.d("EditReceiptViewModel", "Sprawdzanie sklepu ID: $storeId. Liczba pozostałych paragonów: $storeReceiptsCount")
                if (storeReceiptsCount == 0) {
                    // Jeśli drogeria jest teraz pusta, usuń ją
                    val storeToDelete = storeDao.getStoreById(storeId) // Pobierz obiekt sklepu
                    storeToDelete?.let { // Sprawdź null
                        storeDao.deleteStore(it) // Usuń sklep
                        Log.d("EditReceiptViewModel", "Drogeria (ID: $storeId) usunięta automatycznie (brak paragonów po usunięciu klienta).")
                    }
                }
            }

            EditResult.SUCCESS // Zwróć sukces
        } catch (e: Exception) {
            // Złap wszelkie błędy podczas operacji
            Log.e("EditReceiptViewModel", "Błąd podczas usuwania klienta (ID: ${client.id}) i sprawdzania drogerii.", e)
            EditResult.ERROR_DATABASE
        }
    }
}
