package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.core.net.toUri // Potrzebny do parsowania URI
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
import com.kaminski.paragownik.R
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Photo // Dodano import
import com.kaminski.paragownik.data.PhotoType // Dodano import
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.PhotoDao // Dodano import
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine // Dodano import
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
 * w tym obsługę wielu zdjęć i usuwanie plików zdjęć.
 */
class EditReceiptViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val storeDao: StoreDao = database.storeDao()
    private val clientDao: ClientDao = database.clientDao()
    private val photoDao: PhotoDao = database.photoDao() // DAO dla zdjęć

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
     * Pobiera Flow emitujący trójkę: [ReceiptWithClient?], numer sklepu [String?] oraz listę zdjęć [List<Photo>?].
     * Łączy dane z paragonu, klienta, sklepu i zdjęć.
     * @param receiptId ID paragonu do pobrania.
     * @return Flow emitujący Triple.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getReceiptWithClientAndStoreNumber(receiptId: Long): Flow<Triple<ReceiptWithClient?, String?, List<Photo>?>> {
        // Rozpoczynamy od pobrania paragonu z klientem
        return receiptDao.getReceiptWithClientFlow(receiptId)
            .flatMapLatest { receiptWithClient ->
                // Jeśli paragon lub klient nie istnieje, zwracamy od razu null'e
                if (receiptWithClient?.client == null) {
                    kotlinx.coroutines.flow.flowOf(Triple(null, null, null))
                } else {
                    // Jeśli istnieją, łączymy Flow sklepu i Flow zdjęć
                    storeDao.getStoreByIdFlow(receiptWithClient.receipt.storeId) // Pobierz Flow sklepu
                        .combine(photoDao.getPhotosForClient(receiptWithClient.client.id)) { store, photos ->
                            // Kiedy oba Flow (sklep i zdjęcia) wyemitują wartość, tworzymy Triple
                            Triple(receiptWithClient, store?.storeNumber, photos)
                        }
                }
            }
            .flowOn(Dispatchers.IO) // Wykonuj operacje DAO w tle
    }


    /**
     * Aktualizuje dane istniejącego paragonu, powiązanego klienta oraz synchronizuje zdjęcia.
     * Dodatkowo sprawdza, czy pierwotna drogeria nie została osierocona i ewentualnie ją usuwa.
     * @param receiptId ID edytowanego paragonu.
     * @param storeNumberString Nowy numer sklepu (jako String).
     * @param receiptNumber Nowy numer paragonu.
     * @param receiptDateString Nowa data paragonu (jako String "dd-MM-yyyy").
     * @param verificationDateString Nowa data weryfikacji (opcjonalna, jako String "dd-MM-yyyy").
     * @param clientDescription Nowy opis klienta (opcjonalny).
     * @param clientAppNumber Nowy numer aplikacji klienta (opcjonalny).
     * @param amoditNumber Nowy numer Amodit (opcjonalny).
     * @param clientPhotoUrisToAdd Lista URI zdjęć klienta do dodania.
     * @param transactionPhotoUrisToAdd Lista URI zdjęć transakcji do dodania.
     * @param photoUrisToRemove Lista URI zdjęć do usunięcia.
     * @return [EditResult] wskazujący wynik operacji.
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
        // Nowe parametry dla zdjęć
        clientPhotoUrisToAdd: List<String>,
        transactionPhotoUrisToAdd: List<String>,
        photoUrisToRemove: List<String>
    ): EditResult = withContext(Dispatchers.IO) {
        // Format daty używany w UI i do parsowania
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false // Ścisłe sprawdzanie formatu

        try {
            // Używamy transakcji, aby zapewnić atomowość operacji na wielu tabelach
            database.withTransaction {
                // --- Krok 1: Pobierz istniejące dane PRZED modyfikacją ---
                val existingReceiptWithClient = receiptDao.getReceiptWithClient(receiptId)
                if (existingReceiptWithClient == null || existingReceiptWithClient.client == null) {
                    Log.e("EditReceiptViewModel", "Nie znaleziono paragonu (ID: $receiptId) lub klienta do edycji.")
                    throw NotFoundException() // Rzuć wyjątek, aby przerwać transakcję
                }
                val existingReceipt = existingReceiptWithClient.receipt
                val existingClient = existingReceiptWithClient.client
                val clientId = existingClient.id // Pobierz ID klienta
                val originalStoreId = existingReceipt.storeId // <-- ZAPISZ ORYGINALNE ID SKLEPU

                // --- Krok 2: Walidacja i parsowanie danych wejściowych ---
                val receiptDate: Date = try {
                    dateFormat.parse(receiptDateString) as Date
                } catch (e: ParseException) {
                    Log.e("EditReceiptViewModel", "Błąd formatu daty paragonu: $receiptDateString")
                    throw DateFormatException()
                }
                val verificationDate: Date? = if (!verificationDateString.isNullOrBlank()) {
                    try {
                        dateFormat.parse(verificationDateString) as Date
                    } catch (e: ParseException) {
                        // Błąd formatu daty weryfikacji traktujemy jako brak daty
                        Log.w("EditReceiptViewModel", "Błąd formatu daty weryfikacji (ignorowanie): $verificationDateString")
                        null
                    }
                } else {
                    null
                }

                // --- Krok 3: Walidacja i obsługa numeru sklepu ---
                if (storeNumberString.isBlank()) {
                    Log.e("EditReceiptViewModel", "Brak numeru drogerii podczas edycji.")
                    throw StoreNumberMissingException()
                }
                var store = storeDao.getStoreByNumber(storeNumberString)
                val newStoreId: Long // ID sklepu PO edycji
                if (store == null) {
                    // Jeśli sklep nie istnieje, utwórz go
                    store = Store(storeNumber = storeNumberString)
                    storeDao.insertStore(store)
                    store = storeDao.getStoreByNumber(storeNumberString) // Pobierz ponownie, aby uzyskać ID
                    if (store == null) {
                        // Błąd krytyczny - nie udało się utworzyć/pobrać sklepu
                        throw DatabaseException(getApplication<Application>().getString(R.string.error_creating_store_edit, storeNumberString))
                    }
                    newStoreId = store.id
                    Log.d("EditReceiptViewModel", "Transakcja: Utworzono nową drogerię (ID: $newStoreId) podczas edycji.")
                } else {
                    newStoreId = store.id
                }

                // --- Krok 4: Walidacja duplikatów paragonów ---
                // Sprawdź, czy inny paragon (o innym ID) nie ma już takiej samej kombinacji numeru, daty i sklepu
                val potentialDuplicate = receiptDao.findByNumberDateStore(receiptNumber, receiptDate, newStoreId)
                if (potentialDuplicate != null && potentialDuplicate.id != receiptId) {
                    Log.e("EditReceiptViewModel", "Edycja: Znaleziono inny paragon (ID: ${potentialDuplicate.id}) z tymi samymi danymi.")
                    throw DuplicateReceiptException()
                }

                // --- Krok 5: Aktualizacja Paragonu ---
                val updatedReceipt = existingReceipt.copy(
                    receiptNumber = receiptNumber,
                    receiptDate = receiptDate,
                    storeId = newStoreId, // Użyj nowego ID sklepu
                    verificationDate = verificationDate
                )
                receiptDao.updateReceipt(updatedReceipt)
                Log.d("EditReceiptViewModel", "Transakcja: Zaktualizowano paragon ID: $receiptId.")

                // --- Krok 6: Aktualizacja Klienta ---
                val updatedClient = existingClient.copy(
                    description = clientDescription?.takeIf { it.isNotBlank() },
                    clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                    amoditNumber = amoditNumber?.takeIf { it.isNotBlank() }
                )
                clientDao.updateClient(updatedClient)
                Log.d("EditReceiptViewModel", "Transakcja: Zaktualizowano klienta ID: $clientId.")

                // --- Krok 7: Synchronizacja zdjęć (w ramach tej samej transakcji) ---
                // 7a. Usuń zdjęcia oznaczone do usunięcia (tylko wpisy w bazie)
                for (uriToRemove in photoUrisToRemove) {
                    photoDao.deletePhotoByUri(uriToRemove)
                    Log.d("EditReceiptViewModel", "Transakcja: Usunięto wpis zdjęcia: $uriToRemove dla klienta $clientId")
                }
                // 7b. Dodaj nowe zdjęcia klienta
                for (uriToAdd in clientPhotoUrisToAdd) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd, type = PhotoType.CLIENT)
                    photoDao.insertPhoto(photo)
                    Log.d("EditReceiptViewModel", "Transakcja: Dodano zdjęcie klienta: $uriToAdd dla klienta $clientId")
                }
                // 7c. Dodaj nowe zdjęcia transakcji
                for (uriToAdd in transactionPhotoUrisToAdd) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd, type = PhotoType.TRANSACTION)
                    photoDao.insertPhoto(photo)
                    Log.d("EditReceiptViewModel", "Transakcja: Dodano zdjęcie transakcji: $uriToAdd dla klienta $clientId")
                }

                // --- Krok 8: Sprawdź i usuń osieroconą pierwotną drogerię (JEŚLI SIĘ ZMIENIŁA) ---
                if (originalStoreId != newStoreId) {
                    Log.d("EditReceiptViewModel", "Transakcja: ID sklepu zmienione z $originalStoreId na $newStoreId. Sprawdzanie starego sklepu.")
                    // Sprawdź liczbę paragonów dla ORYGINALNEGO sklepu
                    val originalStoreReceiptsCount = receiptDao.getReceiptsForStoreCount(originalStoreId)
                    Log.d("EditReceiptViewModel", "Transakcja: Liczba paragonów dla starego sklepu (ID: $originalStoreId): $originalStoreReceiptsCount")
                    if (originalStoreReceiptsCount == 0) {
                        // Jeśli stary sklep jest pusty, usuń go
                        val storeToDelete = storeDao.getStoreById(originalStoreId)
                        storeToDelete?.let {
                            storeDao.deleteStore(it)
                            Log.d("EditReceiptViewModel", "Transakcja: Pierwotna drogeria (ID: $originalStoreId) usunięta automatycznie.")
                        } ?: run {
                            // Sytuacja mało prawdopodobna, ale warto zalogować
                            Log.w("EditReceiptViewModel", "Transakcja: Nie znaleziono pierwotnej drogerii (ID: $originalStoreId) do usunięcia, mimo że powinna być pusta.")
                        }
                    }
                }
                // --- Koniec kroku 8 ---

            } // Koniec bloku withTransaction

            // --- Krok 9: Usuwanie plików zdjęć POZA transakcją ---
            // Operacje na plikach nie powinny być częścią transakcji bazodanowej
            for (uriToRemove in photoUrisToRemove) {
                deleteImageFile(uriToRemove)
            }

            Log.d("EditReceiptViewModel", "Paragon (ID: $receiptId) i Klient zaktualizowane, zdjęcia zsynchronizowane, stare sklepy sprawdzone.")
            EditResult.SUCCESS // Zwróć sukces, jeśli transakcja się powiodła
        } catch (e: NotFoundException) {
            EditResult.ERROR_NOT_FOUND
        } catch (e: DateFormatException) {
            EditResult.ERROR_DATE_FORMAT
        } catch (e: StoreNumberMissingException) {
            EditResult.ERROR_STORE_NUMBER_MISSING
        } catch (e: DuplicateReceiptException) {
            EditResult.ERROR_DUPLICATE_RECEIPT
        } catch (e: DatabaseException) {
             Log.e("EditReceiptViewModel", "Błąd bazy danych podczas aktualizacji.", e)
             EditResult.ERROR_DATABASE
        } catch (e: Exception) {
            Log.e("EditReceiptViewModel", "Nieznany błąd podczas aktualizacji.", e)
            EditResult.ERROR_UNKNOWN
        }
    }


    /**
     * Usuwa podany paragon z bazy danych i czyści ewentualnie osieroconych klientów/sklepy.
     * Usuwa również pliki zdjęć, jeśli klient jest usuwany.
     * @param receipt Paragon do usunięcia.
     * @return [EditResult] wskazujący wynik operacji.
     */
    suspend fun deleteReceipt(receipt: Receipt): EditResult = withContext(Dispatchers.IO) {
        try {
            // Pobierz aktualny stan paragonu z bazy
            val receiptToDelete = receiptDao.getReceiptById(receipt.id)
            if (receiptToDelete == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono paragonu do usunięcia. ID: ${receipt.id}")
                return@withContext EditResult.ERROR_NOT_FOUND
            }

            // Zapamiętaj ID klienta i sklepu przed usunięciem paragonu
            val clientId = receiptToDelete.clientId
            val storeId = receiptToDelete.storeId
            var clientDeleted = false // Flaga wskazująca, czy klient został usunięty
            var photoUrisToDelete: List<String> = emptyList() // Lista URI zdjęć do usunięcia (jeśli klient zostanie usunięty)

            // Używamy transakcji do usunięcia paragonu i potencjalnie klienta/sklepu
            database.withTransaction {
                // Krok 1: Usuń paragon
                receiptDao.deleteReceipt(receiptToDelete)
                Log.d("EditReceiptViewModel", "Transakcja: Paragon usunięty. ID: ${receipt.id}")

                // Krok 2: Sprawdź, czy klient stał się osierocony
                val clientReceiptsCount = receiptDao.getReceiptsForClientCount(clientId)
                Log.d("EditReceiptViewModel", "Transakcja: Liczba pozostałych paragonów dla klienta (ID: $clientId): $clientReceiptsCount")
                if (clientReceiptsCount == 0) {
                    // Klient jest osierocony, usuwamy go
                    val clientToDelete = clientDao.getClientById(clientId)
                    clientToDelete?.let { client ->
                        // Pobierz listę URI zdjęć PRZED usunięciem klienta
                        photoUrisToDelete = photoDao.getPhotoUrisForClient(client.id)
                        Log.d("EditReceiptViewModel", "Transakcja: Klient osierocony (ID: ${client.id}). Znaleziono ${photoUrisToDelete.size} zdjęć do usunięcia.")
                        // Usuń klienta (wpisy zdjęć usuną się kaskadowo dzięki onDelete = CASCADE)
                        clientDao.deleteClient(client)
                        clientDeleted = true // Ustaw flagę
                        Log.d("EditReceiptViewModel", "Transakcja: Klient (ID: ${client.id}) usunięty automatycznie z bazy.")
                    }
                }

                // Krok 3: Sprawdź, czy sklep stał się osierocony
                // Niezależnie od tego, czy klient został usunięty, sprawdzamy sklep
                val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                Log.d("EditReceiptViewModel", "Transakcja: Liczba pozostałych paragonów dla sklepu (ID: $storeId): $storeReceiptsCount")
                if (storeReceiptsCount == 0) {
                    // Sklep jest osierocony, usuwamy go
                    val storeToDelete = storeDao.getStoreById(storeId)
                    storeToDelete?.let {
                        storeDao.deleteStore(it)
                        Log.d("EditReceiptViewModel", "Transakcja: Drogeria (ID: $storeId) usunięta automatycznie.")
                    }
                }
            } // Koniec transakcji

            // Krok 4: Usuń pliki zdjęć POZA transakcją, jeśli klient został usunięty
            if (clientDeleted) {
                for (uri in photoUrisToDelete) {
                    deleteImageFile(uri)
                }
            }

            EditResult.SUCCESS // Zwróć sukces
        } catch (e: Exception) {
            Log.e("EditReceiptViewModel", "Błąd podczas usuwania paragonu (ID: ${receipt.id}).", e)
            EditResult.ERROR_DATABASE // Zwróć błąd bazy danych
        }
    }

    /**
     * Usuwa podanego klienta (i kaskadowo jego paragony/zdjęcia z bazy), usuwa powiązane pliki zdjęć
     * oraz czyści ewentualnie osierocone sklepy.
     * @param client Klient do usunięcia.
     * @return [EditResult] wskazujący wynik operacji.
     */
    suspend fun deleteClient(client: Client): EditResult = withContext(Dispatchers.IO) {
        try {
            // Sprawdź, czy klient istnieje
            val clientToDelete = clientDao.getClientById(client.id)
            if (clientToDelete == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono klienta do usunięcia. ID: ${client.id}")
                return@withContext EditResult.ERROR_NOT_FOUND
            }

            // Krok 1: Zbierz ID sklepów powiązanych z klientem PRZED usunięciem
            val associatedStoreIds = receiptDao.getStoreIdsForClient(clientToDelete.id)
            Log.d("EditReceiptViewModel", "Sklepy powiązane z klientem ${clientToDelete.id} przed usunięciem: $associatedStoreIds")

            // Krok 2: Pobierz listę URI zdjęć PRZED usunięciem klienta
            val photoUrisToDelete = photoDao.getPhotoUrisForClient(clientToDelete.id)
            Log.d("EditReceiptViewModel", "Znaleziono ${photoUrisToDelete.size} zdjęć do usunięcia dla klienta ${clientToDelete.id}")

            // Krok 3: Używamy transakcji do usunięcia klienta i sprawdzenia sklepów
            database.withTransaction {
                // Usuń klienta (paragony i wpisy zdjęć usuną się kaskadowo dzięki onDelete = CASCADE)
                clientDao.deleteClient(clientToDelete)
                Log.d("EditReceiptViewModel", "Transakcja: Klient (ID: ${clientToDelete.id}) usunięty z bazy (paragony i zdjęcia kaskadowo).")

                // Sprawdź i wyczyść potencjalnie puste sklepy, które były powiązane z klientem
                for (storeId in associatedStoreIds) {
                    val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                    Log.d("EditReceiptViewModel", "Transakcja: Sprawdzanie sklepu ID: $storeId. Pozostałe paragony: $storeReceiptsCount")
                    if (storeReceiptsCount == 0) {
                        // Sklep jest pusty, usuń go
                        val storeToDelete = storeDao.getStoreById(storeId)
                        storeToDelete?.let {
                            storeDao.deleteStore(it)
                            Log.d("EditReceiptViewModel", "Transakcja: Drogeria (ID: $storeId) usunięta automatycznie.")
                        }
                    }
                }
            } // Koniec transakcji

            // Krok 4: Usuń pliki zdjęć z dysku PO usunięciu z bazy
            for (uri in photoUrisToDelete) {
                deleteImageFile(uri)
            }

            EditResult.SUCCESS // Zwróć sukces
        } catch (e: Exception) {
            Log.e("EditReceiptViewModel", "Błąd podczas usuwania klienta (ID: ${client.id}).", e)
            EditResult.ERROR_DATABASE // Zwróć błąd bazy danych
        }
    }


    /**
     * Usuwa plik zdjęcia z wewnętrznego magazynu aplikacji na podstawie jego URI.
     * @param photoUriString URI zdjęcia jako String (powinno być w formacie file://...).
     */
    private fun deleteImageFile(photoUriString: String?) {
        if (photoUriString.isNullOrBlank()) {
            Log.w("EditReceiptViewModel", "Próba usunięcia pliku, ale URI jest puste.")
            return // Brak URI do usunięcia
        }

        try {
            val fileUri = photoUriString.toUri()
            // Sprawdź, czy to URI pliku ('file' scheme) i czy ścieżka zaczyna się od katalogu plików aplikacji
            // To zabezpieczenie przed próbą usunięcia plików spoza dedykowanego katalogu
            if (fileUri.scheme == "file" && fileUri.path?.startsWith(getApplication<Application>().filesDir.absolutePath) == true) {
                // Utwórz obiekt File na podstawie ścieżki z URI
                val fileToDelete = File(fileUri.path!!) // Wykrzyknik jest bezpieczny, bo path nie będzie null dla file:// URI
                if (fileToDelete.exists()) {
                    if (fileToDelete.delete()) {
                        Log.d("EditReceiptViewModel", "Usunięto plik zdjęcia: $photoUriString")
                    } else {
                        Log.w("EditReceiptViewModel", "Nie udało się usunąć pliku zdjęcia: $photoUriString (metoda delete() zwróciła false)")
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

    // Prywatne klasy wyjątków dla obsługi błędów w transakcji
    private class NotFoundException : Exception()
    private class DateFormatException : Exception()
    private class StoreNumberMissingException : Exception()
    private class DuplicateReceiptException : Exception()
    private class DatabaseException(message: String) : Exception(message)
}