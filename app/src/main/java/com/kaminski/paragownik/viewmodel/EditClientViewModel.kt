package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
import com.kaminski.paragownik.R
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Photo
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.PhotoDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel dla EditClientActivity.
 * Odpowiada za logikę biznesową związaną z edycją i usuwaniem klientów,
 * w tym obsługę wielu zdjęć i usuwanie plików zdjęć.
 */
class EditClientViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val clientDao: ClientDao = database.clientDao()
    private val photoDao: PhotoDao = database.photoDao()
    private val receiptDao: ReceiptDao = database.receiptDao() // Potrzebne do sprawdzania sklepów
    private val storeDao: StoreDao = database.storeDao() // Potrzebne do usuwania sklepów

    /**
     * Enum reprezentujący możliwy wynik operacji edycji lub usuwania klienta.
     */
    enum class EditResult {
        SUCCESS,
        ERROR_NOT_FOUND, // Klient nie znaleziony
        ERROR_DATABASE,
        ERROR_UNKNOWN
    }

    /**
     * Pobiera Flow emitujący parę: [Client?] oraz listę jego zdjęć [List<Photo>?].
     * @param clientId ID klienta do pobrania.
     * @return Flow emitujący Pair.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getClientWithPhotos(clientId: Long): Flow<Pair<Client?, List<Photo>?>> {
        // Łączymy Flow klienta z Flow jego zdjęć
        return clientDao.getClientByIdFlow(clientId)
            .combine(photoDao.getPhotosForClient(clientId)) { client, photos ->
                // Kiedy oba Flow wyemitują wartość, tworzymy Pair
                Pair(client, photos)
            }
            .flowOn(Dispatchers.IO) // Wykonuj operacje DAO w tle
    }

    /**
     * Aktualizuje dane istniejącego klienta oraz synchronizuje jego zdjęcia.
     * @param clientId ID edytowanego klienta.
     * @param clientDescription Nowy opis klienta (opcjonalny).
     * @param clientAppNumber Nowy numer aplikacji klienta (opcjonalny).
     * @param amoditNumber Nowy numer Amodit (opcjonalny).
     * @param clientPhotoUrisToAdd Lista URI zdjęć klienta do dodania.
     * @param transactionPhotoUrisToAdd Lista URI zdjęć transakcji do dodania.
     * @param photoUrisToRemove Lista URI zdjęć do usunięcia.
     * @return [EditResult] wskazujący wynik operacji.
     */
    suspend fun updateClientAndPhotos(
        clientId: Long,
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?,
        clientPhotoUrisToAdd: List<String>,
        transactionPhotoUrisToAdd: List<String>,
        photoUrisToRemove: List<String>
    ): EditResult = withContext(Dispatchers.IO) {
        try {
            // Używamy transakcji, aby zapewnić atomowość operacji na wielu tabelach
            database.withTransaction {
                // --- Krok 1: Pobierz istniejącego klienta ---
                val existingClient = clientDao.getClientById(clientId)
                if (existingClient == null) {
                    Log.e("EditClientViewModel", "Nie znaleziono klienta (ID: $clientId) do edycji.")
                    throw NotFoundException() // Rzuć wyjątek, aby przerwać transakcję
                }

                // --- Krok 2: Aktualizacja Klienta ---
                val updatedClient = existingClient.copy(
                    description = clientDescription?.takeIf { it.isNotBlank() },
                    clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                    amoditNumber = amoditNumber?.takeIf { it.isNotBlank() }
                )
                clientDao.updateClient(updatedClient)
                Log.d("EditClientViewModel", "Transakcja: Zaktualizowano klienta ID: $clientId.")

                // --- Krok 3: Synchronizacja zdjęć (w ramach tej samej transakcji) ---
                // 3a. Usuń zdjęcia oznaczone do usunięcia (tylko wpisy w bazie)
                for (uriToRemove in photoUrisToRemove) {
                    photoDao.deletePhotoByUri(uriToRemove)
                    Log.d("EditClientViewModel", "Transakcja: Usunięto wpis zdjęcia: $uriToRemove dla klienta $clientId")
                }
                // 3b. Dodaj nowe zdjęcia klienta
                for (uriToAdd in clientPhotoUrisToAdd) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd, type = PhotoType.CLIENT)
                    photoDao.insertPhoto(photo)
                    Log.d("EditClientViewModel", "Transakcja: Dodano zdjęcie klienta: $uriToAdd dla klienta $clientId")
                }
                // 3c. Dodaj nowe zdjęcia transakcji
                for (uriToAdd in transactionPhotoUrisToAdd) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd, type = PhotoType.TRANSACTION)
                    photoDao.insertPhoto(photo)
                    Log.d("EditClientViewModel", "Transakcja: Dodano zdjęcie transakcji: $uriToAdd dla klienta $clientId")
                }
            } // Koniec bloku withTransaction

            // --- Krok 4: Usuwanie plików zdjęć POZA transakcją ---
            for (uriToRemove in photoUrisToRemove) {
                deleteImageFile(uriToRemove)
            }

            Log.d("EditClientViewModel", "Klient (ID: $clientId) zaktualizowany, zdjęcia zsynchronizowane.")
            EditResult.SUCCESS // Zwróć sukces, jeśli transakcja się powiodła
        } catch (e: NotFoundException) {
            EditResult.ERROR_NOT_FOUND
        } catch (e: DatabaseException) {
             Log.e("EditClientViewModel", "Błąd bazy danych podczas aktualizacji klienta.", e)
             EditResult.ERROR_DATABASE
        } catch (e: Exception) {
            Log.e("EditClientViewModel", "Nieznany błąd podczas aktualizacji klienta.", e)
            EditResult.ERROR_UNKNOWN
        }
    }

    /**
     * Usuwa podanego klienta (i kaskadowo jego paragony/zdjęcia z bazy), usuwa powiązane pliki zdjęć
     * oraz czyści ewentualnie osierocone sklepy.
     * Logika skopiowana i dostosowana z EditReceiptViewModel.
     * @param client Klient do usunięcia.
     * @return [EditResult] wskazujący wynik operacji.
     */
    suspend fun deleteClient(client: Client): EditResult = withContext(Dispatchers.IO) {
        try {
            // Sprawdź, czy klient istnieje
            val clientToDelete = clientDao.getClientById(client.id)
            if (clientToDelete == null) {
                Log.e("EditClientViewModel", "Nie znaleziono klienta do usunięcia. ID: ${client.id}")
                return@withContext EditResult.ERROR_NOT_FOUND
            }

            // Krok 1: Zbierz ID sklepów powiązanych z klientem PRZED usunięciem
            val associatedStoreIds = receiptDao.getStoreIdsForClient(clientToDelete.id)
            Log.d("EditClientViewModel", "Sklepy powiązane z klientem ${clientToDelete.id} przed usunięciem: $associatedStoreIds")

            // Krok 2: Pobierz listę URI zdjęć PRZED usunięciem klienta
            val photoUrisToDelete = photoDao.getPhotoUrisForClient(clientToDelete.id)
            Log.d("EditClientViewModel", "Znaleziono ${photoUrisToDelete.size} zdjęć do usunięcia dla klienta ${clientToDelete.id}")

            // Krok 3: Używamy transakcji do usunięcia klienta i sprawdzenia sklepów
            database.withTransaction {
                // Usuń klienta (paragony i wpisy zdjęć usuną się kaskadowo dzięki onDelete = CASCADE)
                clientDao.deleteClient(clientToDelete)
                Log.d("EditClientViewModel", "Transakcja: Klient (ID: ${clientToDelete.id}) usunięty z bazy (paragony i zdjęcia kaskadowo).")

                // Sprawdź i wyczyść potencjalnie puste sklepy, które były powiązane z klientem
                for (storeId in associatedStoreIds) {
                    val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                    Log.d("EditClientViewModel", "Transakcja: Sprawdzanie sklepu ID: $storeId. Pozostałe paragony: $storeReceiptsCount")
                    if (storeReceiptsCount == 0) {
                        // Sklep jest pusty, usuń go
                        val storeToDelete = storeDao.getStoreById(storeId)
                        storeToDelete?.let {
                            storeDao.deleteStore(it)
                            Log.d("EditClientViewModel", "Transakcja: Drogeria (ID: $storeId) usunięta automatycznie.")
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
            Log.e("EditClientViewModel", "Błąd podczas usuwania klienta (ID: ${client.id}).", e)
            EditResult.ERROR_DATABASE // Zwróć błąd bazy danych
        }
    }

    /**
     * Usuwa plik zdjęcia z wewnętrznego magazynu aplikacji na podstawie jego URI.
     * Logika skopiowana z EditReceiptViewModel.
     * @param photoUriString URI zdjęcia jako String (powinno być w formacie file://...).
     */
    private fun deleteImageFile(photoUriString: String?) {
        if (photoUriString.isNullOrBlank()) {
            Log.w("EditClientViewModel", "Próba usunięcia pliku, ale URI jest puste.")
            return
        }
        try {
            val fileUri = photoUriString.toUri()
            if (fileUri.scheme == "file" && fileUri.path?.startsWith(getApplication<Application>().filesDir.absolutePath) == true) {
                val fileToDelete = File(fileUri.path!!)
                if (fileToDelete.exists()) {
                    if (fileToDelete.delete()) {
                        Log.d("EditClientViewModel", "Usunięto plik zdjęcia: $photoUriString")
                    } else {
                        Log.w("EditClientViewModel", "Nie udało się usunąć pliku zdjęcia: $photoUriString (metoda delete() zwróciła false)")
                    }
                } else {
                    Log.w("EditClientViewModel", "Plik zdjęcia do usunięcia nie istnieje: $photoUriString")
                }
            } else {
                Log.w("EditClientViewModel", "Próba usunięcia pliku z nieobsługiwanego URI lub spoza magazynu aplikacji: $photoUriString")
            }
        } catch (e: Exception) {
            Log.e("EditClientViewModel", "Błąd podczas próby usunięcia pliku zdjęcia: $photoUriString", e)
        }
    }

    // Prywatne klasy wyjątków dla obsługi błędów w transakcji
    private class NotFoundException : Exception()
    private class DatabaseException(message: String) : Exception(message)
}

