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

    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val storeDao: StoreDao = database.storeDao()
    private val clientDao: ClientDao = database.clientDao()
    private val photoDao: PhotoDao = database.photoDao() // <-- DODAJ

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
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getReceiptWithClientAndStoreNumber(receiptId: Long): Flow<Triple<ReceiptWithClient?, String?, List<Photo>?>> {
        return receiptDao.getReceiptWithClientFlow(receiptId)
            .flatMapLatest { receiptWithClient ->
                if (receiptWithClient?.client == null) {
                    // Jeśli nie ma paragonu lub klienta, zwróć Triple z nullami
                    kotlinx.coroutines.flow.flowOf(Triple(null, null, null))
                } else {
                    // Połącz Flow sklepu i Flow zdjęć
                    storeDao.getStoreByIdFlow(receiptWithClient.receipt.storeId)
                        .combine(photoDao.getPhotosForClient(receiptWithClient.client.id)) { store, photos ->
                            Triple(receiptWithClient, store?.storeNumber, photos)
                        }
                }
            }
            .flowOn(Dispatchers.IO)
    }


    /**
     * Aktualizuje dane istniejącego paragonu, powiązanego klienta oraz synchronizuje zdjęcia.
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
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // Używamy transakcji, aby zapewnić atomowość operacji na wielu tabelach
            database.withTransaction {
                // Pobierz istniejące dane (wewnątrz transakcji, aby mieć pewność spójności)
                val existingReceiptWithClient = receiptDao.getReceiptWithClient(receiptId)
                if (existingReceiptWithClient == null || existingReceiptWithClient.client == null) {
                    Log.e("EditReceiptViewModel", "Nie znaleziono paragonu (ID: $receiptId) lub klienta do edycji.")
                    throw NotFoundException() // Rzuć wyjątek, aby przerwać transakcję
                }
                val existingReceipt = existingReceiptWithClient.receipt
                val existingClient = existingReceiptWithClient.client
                val clientId = existingClient.id // Pobierz ID klienta

                // Walidacja i parsowanie dat
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
                        Log.w("EditReceiptViewModel", "Błąd formatu daty weryfikacji (ignorowanie): $verificationDateString")
                        null // Traktujemy jako brak daty weryfikacji w przypadku błędu formatu
                    }
                } else {
                    null
                }

                // Walidacja i obsługa numeru sklepu
                if (storeNumberString.isBlank()) {
                    Log.e("EditReceiptViewModel", "Brak numeru drogerii podczas edycji.")
                    throw StoreNumberMissingException()
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
                    throw DuplicateReceiptException()
                }

                // Aktualizacja Paragonu
                val updatedReceipt = existingReceipt.copy(
                    receiptNumber = receiptNumber,
                    receiptDate = receiptDate,
                    storeId = storeId,
                    verificationDate = verificationDate
                )
                receiptDao.updateReceipt(updatedReceipt)

                // Aktualizacja Klienta (bez photoUri)
                val updatedClient = existingClient.copy(
                    description = clientDescription?.takeIf { it.isNotBlank() },
                    clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                    amoditNumber = amoditNumber?.takeIf { it.isNotBlank() }
                )
                clientDao.updateClient(updatedClient)

                // Synchronizacja zdjęć (w ramach tej samej transakcji)

                // 1. Usuń zdjęcia oznaczone do usunięcia (tylko wpisy w bazie, pliki usuwamy poza transakcją)
                for (uriToRemove in photoUrisToRemove) {
                    photoDao.deletePhotoByUri(uriToRemove)
                    Log.d("EditReceiptViewModel", "Transakcja: Usunięto wpis zdjęcia: $uriToRemove dla klienta $clientId")
                }

                // 2. Dodaj nowe zdjęcia klienta
                for (uriToAdd in clientPhotoUrisToAdd) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd, type = PhotoType.CLIENT)
                    photoDao.insertPhoto(photo)
                    Log.d("EditReceiptViewModel", "Transakcja: Dodano zdjęcie klienta: $uriToAdd dla klienta $clientId")
                }

                // 3. Dodaj nowe zdjęcia transakcji
                for (uriToAdd in transactionPhotoUrisToAdd) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd, type = PhotoType.TRANSACTION)
                    photoDao.insertPhoto(photo)
                    Log.d("EditReceiptViewModel", "Transakcja: Dodano zdjęcie transakcji: $uriToAdd dla klienta $clientId")
                }
            } // Koniec bloku withTransaction

            // Usuwanie plików zdjęć POZA transakcją (operacje na plikach nie powinny być w transakcji DB)
            for (uriToRemove in photoUrisToRemove) {
                deleteImageFile(uriToRemove)
                Log.d("EditReceiptViewModel", "Usunięto plik zdjęcia: $uriToRemove")
            }

            Log.d("EditReceiptViewModel", "Paragon (ID: $receiptId) i Klient zaktualizowane, zdjęcia zsynchronizowane.")
            EditResult.SUCCESS
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
            var clientDeleted = false
            var photoUrisToDelete: List<String> = emptyList()

            // Używamy transakcji do usunięcia paragonu i potencjalnie klienta/sklepu
            database.withTransaction {
                // Usuń paragon
                receiptDao.deleteReceipt(receiptToDelete)
                Log.d("EditReceiptViewModel", "Transakcja: Paragon usunięty. ID: ${receipt.id}")

                // Sprawdź, czy klient stał się osierocony
                val clientReceiptsCount = receiptDao.getReceiptsForClientCount(clientId)
                if (clientReceiptsCount == 0) {
                    val clientToDelete = clientDao.getClientById(clientId)
                    clientToDelete?.let { client ->
                        // Pobierz listę URI zdjęć PRZED usunięciem klienta
                        photoUrisToDelete = photoDao.getPhotoUrisForClient(client.id)
                        Log.d("EditReceiptViewModel", "Transakcja: Klient osierocony (ID: ${client.id}). Znaleziono ${photoUrisToDelete.size} zdjęć do usunięcia.")
                        // Usuń klienta (wpisy zdjęć usuną się kaskadowo)
                        clientDao.deleteClient(client)
                        clientDeleted = true
                        Log.d("EditReceiptViewModel", "Transakcja: Klient (ID: ${client.id}) usunięty automatycznie z bazy.")
                    }
                }

                // Sprawdź, czy sklep stał się osierocony (tylko jeśli klient nie został usunięty w tej samej operacji,
                // bo inaczej paragony już zniknęły)
                if (!clientDeleted) {
                    val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                    if (storeReceiptsCount == 0) {
                        val storeToDelete = storeDao.getStoreById(storeId)
                        storeToDelete?.let {
                            storeDao.deleteStore(it)
                            Log.d("EditReceiptViewModel", "Transakcja: Drogeria (ID: $storeId) usunięta automatycznie.")
                        }
                    }
                } else {
                    // Jeśli klient został usunięty, musimy sprawdzić sklep na podstawie jego ID,
                    // bo paragony klienta już nie istnieją
                    val storeReceiptsCountAfterClientDelete = receiptDao.getReceiptsForStoreCount(storeId)
                     if (storeReceiptsCountAfterClientDelete == 0) {
                        val storeToDelete = storeDao.getStoreById(storeId)
                        storeToDelete?.let {
                            storeDao.deleteStore(it)
                            Log.d("EditReceiptViewModel", "Transakcja: Drogeria (ID: $storeId) usunięta automatycznie po usunięciu klienta.")
                        }
                    }
                }
            } // Koniec transakcji

            // Usuń pliki zdjęć POZA transakcją, jeśli klient został usunięty
            if (clientDeleted) {
                for (uri in photoUrisToDelete) {
                    deleteImageFile(uri)
                }
            }

            EditResult.SUCCESS
        } catch (e: Exception) {
            Log.e("EditReceiptViewModel", "Błąd podczas usuwania paragonu (ID: ${receipt.id}).", e)
            EditResult.ERROR_DATABASE
        }
    }

    /**
     * Usuwa podanego klienta (i kaskadowo jego paragony/zdjęcia z bazy), usuwa powiązane pliki zdjęć
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

            // Pobierz listę URI zdjęć PRZED usunięciem klienta
            val photoUrisToDelete = photoDao.getPhotoUrisForClient(clientToDelete.id)
            Log.d("EditReceiptViewModel", "Znaleziono ${photoUrisToDelete.size} zdjęć do usunięcia dla klienta ${clientToDelete.id}")

            // Używamy transakcji do usunięcia klienta i sprawdzenia sklepów
            database.withTransaction {
                // Usuń klienta (paragony i wpisy zdjęć usuną się kaskadowo dzięki onDelete = CASCADE)
                clientDao.deleteClient(clientToDelete)
                Log.d("EditReceiptViewModel", "Transakcja: Klient (ID: ${clientToDelete.id}) usunięty z bazy (paragony i zdjęcia kaskadowo).")

                // Sprawdź i wyczyść potencjalnie puste sklepy
                for (storeId in associatedStoreIds) {
                    val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                    Log.d("EditReceiptViewModel", "Transakcja: Sprawdzanie sklepu ID: $storeId. Pozostałe paragony: $storeReceiptsCount")
                    if (storeReceiptsCount == 0) {
                        val storeToDelete = storeDao.getStoreById(storeId)
                        storeToDelete?.let {
                            storeDao.deleteStore(it)
                            Log.d("EditReceiptViewModel", "Transakcja: Drogeria (ID: $storeId) usunięta automatycznie.")
                        }
                    }
                }
            } // Koniec transakcji

            // Usuń pliki zdjęć z dysku PO usunięciu z bazy
            for (uri in photoUrisToDelete) {
                deleteImageFile(uri)
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

    // Prywatne klasy wyjątków dla obsługi błędów w transakcji
    private class NotFoundException : Exception()
    private class DateFormatException : Exception()
    private class StoreNumberMissingException : Exception()
    private class DuplicateReceiptException : Exception()
    private class DatabaseException(message: String) : Exception(message)
}

