package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
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
 * w tym obsługę wielu zdjęć (dodawanie/usuwanie wpisów w bazie i plików)
 * oraz czyszczenie osieroconych sklepów po usunięciu klienta.
 */
class EditClientViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val clientDao: ClientDao = database.clientDao()
    private val photoDao: PhotoDao = database.photoDao()
    private val receiptDao: ReceiptDao = database.receiptDao() // Potrzebne do sprawdzania sklepów przy usuwaniu klienta
    private val storeDao: StoreDao = database.storeDao() // Potrzebne do usuwania sklepów

    /**
     * Enum reprezentujący możliwy wynik operacji edycji lub usuwania klienta.
     */
    enum class EditResult {
        SUCCESS, // Operacja zakończona sukcesem
        ERROR_NOT_FOUND, // Klient o podanym ID nie został znaleziony
        ERROR_DATABASE, // Ogólny błąd podczas operacji bazodanowej
        ERROR_UNKNOWN // Nieoczekiwany, inny błąd
    }

    /**
     * Pobiera Flow emitujący parę: [Client?] oraz listę jego zdjęć [List<Photo>?].
     * Łączy dane z dwóch różnych Flow (dane klienta i jego zdjęcia) w jedną emisję.
     * @param clientId ID klienta do pobrania.
     * @return Flow emitujący Pair(Client?, List<Photo>?).
     */
    @OptIn(ExperimentalCoroutinesApi::class) // Wymagane przez `combine`
    fun getClientWithPhotos(clientId: Long): Flow<Pair<Client?, List<Photo>?>> {
        // Łączymy Flow klienta z Flow jego zdjęć za pomocą `combine`.
        // Emisja nastąpi, gdy oba źródłowe Flow dostarczą nowe dane.
        return clientDao.getClientByIdFlow(clientId)
            .combine(photoDao.getPhotosForClient(clientId)) { client, photos ->
                // Tworzymy parę z najnowszych danych klienta i jego zdjęć.
                Pair(client, photos)
            }
            .flowOn(Dispatchers.IO) // Zapewnia, że operacje DAO (w combine) wykonują się w tle.
    }

    /**
     * Aktualizuje dane istniejącego klienta oraz synchronizuje jego zdjęcia (dodaje nowe, usuwa zaznaczone).
     * Operacja jest wykonywana w transakcji bazodanowej dla spójności danych klienta i wpisów zdjęć.
     * Usuwanie plików zdjęć odbywa się POZA transakcją.
     *
     * @param clientId ID edytowanego klienta.
     * @param clientDescription Nowy opis klienta (lub null/pusty).
     * @param clientAppNumber Nowy numer aplikacji klienta (lub null/pusty).
     * @param amoditNumber Nowy numer Amodit (lub null/pusty).
     * @param clientPhotoUrisToAdd Lista URI (String) zdjęć typu CLIENT do dodania.
     * @param transactionPhotoUrisToAdd Lista URI (String) zdjęć typu TRANSACTION do dodania.
     * @param photoUrisToRemove Lista URI (String) zdjęć do usunięcia (zarówno wpisów w bazie, jak i plików).
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
            // Używamy transakcji, aby zapewnić atomowość aktualizacji klienta i wpisów zdjęć.
            database.withTransaction {
                // --- Krok 1: Pobierz istniejącego klienta ---
                val existingClient = clientDao.getClientById(clientId)
                    ?: throw NotFoundException() // Rzuć wyjątek, jeśli klient nie istnieje

                // --- Krok 2: Aktualizacja danych Klienta ---
                val updatedClient = existingClient.copy(
                    description = clientDescription?.takeIf { it.isNotBlank() }, // Zapisz null jeśli pusty
                    clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                    amoditNumber = amoditNumber?.takeIf { it.isNotBlank() }
                )
                clientDao.updateClient(updatedClient)
                Log.d("EditClientViewModel", "Transakcja: Zaktualizowano klienta ID: $clientId.")

                // --- Krok 3: Synchronizacja zdjęć (w ramach tej samej transakcji) ---
                // 3a. Usuń wpisy zdjęć z bazy na podstawie URI
                for (uriToRemove in photoUrisToRemove) {
                    photoDao.deletePhotoByUri(uriToRemove)
                    Log.d("EditClientViewModel", "Transakcja: Usunięto wpis zdjęcia: $uriToRemove dla klienta $clientId")
                }
                // 3b. Dodaj nowe zdjęcia klienta (typ CLIENT)
                for (uriToAdd in clientPhotoUrisToAdd) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd, type = PhotoType.CLIENT)
                    photoDao.insertPhoto(photo)
                    Log.d("EditClientViewModel", "Transakcja: Dodano zdjęcie klienta: $uriToAdd dla klienta $clientId")
                }
                // 3c. Dodaj nowe zdjęcia transakcji (typ TRANSACTION)
                for (uriToAdd in transactionPhotoUrisToAdd) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd, type = PhotoType.TRANSACTION)
                    photoDao.insertPhoto(photo)
                    Log.d("EditClientViewModel", "Transakcja: Dodano zdjęcie transakcji: $uriToAdd dla klienta $clientId")
                }
            } // Koniec transakcji bazodanowej

            // --- Krok 4: Usuwanie plików zdjęć POZA transakcją ---
            // Robimy to po udanej transakcji, aby nie usuwać plików, jeśli zapis w bazie się nie powiódł.
            for (uriToRemove in photoUrisToRemove) {
                deleteImageFile(uriToRemove)
            }

            Log.d("EditClientViewModel", "Klient (ID: $clientId) zaktualizowany, zdjęcia zsynchronizowane.")
            EditResult.SUCCESS
        } catch (e: NotFoundException) {
            Log.e("EditClientViewModel", "Nie znaleziono klienta (ID: $clientId) do edycji.")
            EditResult.ERROR_NOT_FOUND
        } catch (e: DatabaseException) { // Obsługa własnego wyjątku DatabaseException
            Log.e("EditClientViewModel", "Błąd bazy danych podczas aktualizacji klienta.", e)
            EditResult.ERROR_DATABASE
        } catch (e: Exception) { // Obsługa innych, nieprzewidzianych błędów
            Log.e("EditClientViewModel", "Nieznany błąd podczas aktualizacji klienta.", e)
            EditResult.ERROR_UNKNOWN
        }
    }

    /**
     * Usuwa podanego klienta. Operacja obejmuje:
     * 1. Pobranie listy sklepów i URI zdjęć powiązanych z klientem (przed usunięciem).
     * 2. Usunięcie klienta z bazy w transakcji (co kaskadowo usuwa jego paragony i wpisy zdjęć).
     * 3. W tej samej transakcji, sprawdzenie czy sklepy powiązane wcześniej z klientem stały się puste i ewentualne ich usunięcie.
     * 4. Usunięcie plików zdjęć z dysku (poza transakcją).
     *
     * @param client Obiekt [Client] do usunięcia.
     * @return [EditResult] wskazujący wynik operacji.
     */
    suspend fun deleteClient(client: Client): EditResult = withContext(Dispatchers.IO) {
        try {
            // Sprawdź, czy klient na pewno istnieje przed próbą usunięcia
            val clientToDelete = clientDao.getClientById(client.id)
            if (clientToDelete == null) {
                Log.e("EditClientViewModel", "Nie znaleziono klienta do usunięcia. ID: ${client.id}")
                return@withContext EditResult.ERROR_NOT_FOUND
            }

            // Krok 1: Zbierz ID sklepów powiązanych z klientem PRZED usunięciem
            val associatedStoreIds = receiptDao.getStoreIdsForClient(clientToDelete.id)
            Log.d("EditClientViewModel", "Sklepy powiązane z klientem ${clientToDelete.id} przed usunięciem: $associatedStoreIds")

            // Krok 2: Pobierz listę URI zdjęć PRZED usunięciem klienta (i kaskadowym usunięciem wpisów Photo)
            val photoUrisToDelete = photoDao.getPhotoUrisForClient(clientToDelete.id)
            Log.d("EditClientViewModel", "Znaleziono ${photoUrisToDelete.size} plików zdjęć do usunięcia dla klienta ${clientToDelete.id}")

            // Krok 3: Używamy transakcji do usunięcia klienta i sprawdzenia/usunięcia sklepów
            database.withTransaction {
                // Usuń klienta. Dzięki `onDelete = CASCADE` w encjach Receipt i Photo,
                // powiązane paragony i wpisy zdjęć zostaną usunięte automatycznie.
                clientDao.deleteClient(clientToDelete)
                Log.d("EditClientViewModel", "Transakcja: Klient (ID: ${clientToDelete.id}) usunięty z bazy (paragony i wpisy zdjęć kaskadowo).")

                // Sprawdź i wyczyść potencjalnie puste sklepy, które były powiązane TYLKO z tym klientem
                for (storeId in associatedStoreIds) {
                    val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                    Log.d("EditClientViewModel", "Transakcja: Sprawdzanie sklepu ID: $storeId. Pozostałe paragony: $storeReceiptsCount")
                    if (storeReceiptsCount == 0) {
                        // Sklep jest pusty, usuń go
                        val storeToDelete = storeDao.getStoreById(storeId)
                        storeToDelete?.let {
                            storeDao.deleteStore(it)
                            Log.d("EditClientViewModel", "Transakcja: Drogeria (ID: $storeId, Numer: ${it.storeNumber}) usunięta automatycznie (brak paragonów).")
                        }
                    }
                }
            } // Koniec transakcji

            // Krok 4: Usuń pliki zdjęć z dysku POZA transakcją
            for (uri in photoUrisToDelete) {
                deleteImageFile(uri)
            }

            Log.i("EditClientViewModel", "Klient (ID: ${client.id}) pomyślnie usunięty wraz ze zdjęciami i czyszczeniem sklepów.")
            EditResult.SUCCESS
        } catch (e: Exception) {
            Log.e("EditClientViewModel", "Błąd podczas usuwania klienta (ID: ${client.id}).", e)
            EditResult.ERROR_DATABASE // Zakładamy, że większość błędów tutaj będzie związana z bazą
        }
    }

    /**
     * Usuwa plik zdjęcia z wewnętrznego magazynu aplikacji na podstawie jego URI.
     * Sprawdza, czy URI jest poprawne i czy plik znajduje się w oczekiwanej lokalizacji.
     * @param photoUriString URI zdjęcia jako String (oczekiwany format: file:///data/user/0/...)
     */
    private fun deleteImageFile(photoUriString: String?) {
        if (photoUriString.isNullOrBlank()) {
            Log.w("EditClientViewModel", "Próba usunięcia pliku, ale URI jest puste lub null.")
            return
        }
        try {
            val fileUri = photoUriString.toUri()
            // Sprawdzenie bezpieczeństwa: upewnij się, że usuwamy plik z katalogu aplikacji
            if (fileUri.scheme == "file" && fileUri.path?.startsWith(getApplication<Application>().filesDir.absolutePath) == true) {
                val fileToDelete = File(fileUri.path!!)
                if (fileToDelete.exists()) {
                    if (fileToDelete.delete()) {
                        Log.d("EditClientViewModel", "Usunięto plik zdjęcia: $photoUriString")
                    } else {
                        // Może się zdarzyć, jeśli np. nie ma uprawnień (choć w tym katalogu powinny być)
                        Log.w("EditClientViewModel", "Nie udało się usunąć pliku zdjęcia: $photoUriString (metoda delete() zwróciła false)")
                    }
                } else {
                    // Sytuacja, gdy wpis w bazie istniał, ale plik już nie (np. ręczne usunięcie)
                    Log.w("EditClientViewModel", "Plik zdjęcia do usunięcia nie istnieje: $photoUriString")
                }
            } else {
                // Zapobiega próbie usunięcia plików spoza bezpiecznego katalogu aplikacji
                Log.w("EditClientViewModel", "Próba usunięcia pliku z nieobsługiwanego URI lub spoza magazynu aplikacji: $photoUriString")
            }
        } catch (e: Exception) {
            Log.e("EditClientViewModel", "Błąd podczas próby usunięcia pliku zdjęcia: $photoUriString", e)
        }
    }

    // Prywatne klasy wyjątków używane do sygnalizowania specyficznych błędów w transakcjach
    private class NotFoundException : Exception() // Rzucany, gdy oczekiwany obiekt nie istnieje w bazie
    private class DatabaseException(message: String) : Exception(message) // Rzucany przy innych błędach DB
}