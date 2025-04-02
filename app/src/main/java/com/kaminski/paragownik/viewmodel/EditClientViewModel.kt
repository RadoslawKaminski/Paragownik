
package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Looper // Import Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData // Import MutableLiveData
// Usunięto: import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.SharingStarted // Import SharingStarted
import kotlinx.coroutines.flow.StateFlow // Import StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn // Import stateIn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel dla EditClientActivity.
 * Odpowiada za logikę biznesową związaną z edycją i usuwaniem klientów,
 * w tym obsługę wielu zdjęć (dodawanie/usuwanie wpisów w bazie i plików)
 * oraz czyszczenie osieroconych sklepów po usunięciu klienta.
 * Przechowuje stan UI edycji za pomocą MutableLiveData.
 */
// Usunięto SavedStateHandle z konstruktora
class EditClientViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val clientDao: ClientDao = database.clientDao()
    private val photoDao: PhotoDao = database.photoDao()
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val storeDao: StoreDao = database.storeDao()

    /**
     * Enum reprezentujący możliwy wynik operacji edycji lub usuwania klienta.
     */
    enum class EditResult {
        SUCCESS, // Operacja zakończona sukcesem
        ERROR_NOT_FOUND, // Klient o podanym ID nie został znaleziony
        ERROR_DATABASE, // Ogólny błąd podczas operacji bazodanowej
        ERROR_UNKNOWN // Nieoczekiwany, inny błąd
    }

    // --- MutableLiveData do zarządzania stanem UI ---
    val isEditMode = MutableLiveData<Boolean>(false)
    val clientDescriptionState = MutableLiveData<String>("")
    val clientAppNumberState = MutableLiveData<String>("")
    val amoditNumberState = MutableLiveData<String>("")
    val clientPhotosToAddUris = MutableLiveData<MutableList<Uri>>(mutableListOf())
    val transactionPhotosToAddUris = MutableLiveData<MutableList<Uri>>(mutableListOf())
    val photosToRemoveUris = MutableLiveData<MutableList<Uri>>(mutableListOf())

    // Flaga inicjalizacji stanu
    private var isDataInitialized = false

    // --- Metody do aktualizacji stanu UI ---
    fun setEditMode(isEditing: Boolean) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            isEditMode.value = isEditing
        } else {
            isEditMode.postValue(isEditing)
        }
    }

    fun setClientDescription(desc: String) { clientDescriptionState.value = desc }
    fun setClientAppNumber(number: String) { clientAppNumberState.value = number }
    fun setAmoditNumber(number: String) { amoditNumberState.value = number }

    fun addPhotoToAdd(uri: Uri, type: PhotoType) {
        val liveData = if (type == PhotoType.CLIENT) clientPhotosToAddUris else transactionPhotosToAddUris
        val currentList = liveData.value ?: mutableListOf()
        if (!currentList.contains(uri)) {
            currentList.add(uri)
            liveData.value = currentList
        }
    }

    fun removePhotoToAdd(uri: Uri, type: PhotoType) {
        val liveData = if (type == PhotoType.CLIENT) clientPhotosToAddUris else transactionPhotosToAddUris
        val currentList = liveData.value ?: mutableListOf()
        if (currentList.remove(uri)) {
            liveData.value = currentList
        }
    }

    fun addPhotoToRemove(uri: Uri) {
        val currentList = photosToRemoveUris.value ?: mutableListOf()
        if (!currentList.contains(uri)) {
            currentList.add(uri)
            photosToRemoveUris.value = currentList
        }
    }

    fun removePhotoToRemove(uri: Uri) {
        val currentList = photosToRemoveUris.value ?: mutableListOf()
        if (currentList.remove(uri)) {
            photosToRemoveUris.value = currentList
        }
    }

    /**
     * Inicjalizuje stan MutableLiveData na podstawie danych z bazy, ale tylko raz.
     * @param client Obiekt klienta z bazy.
     */
    fun initializeStateIfNeeded(client: Client?) {
        if (!isDataInitialized && client != null) {
            clientDescriptionState.value = client.description ?: ""
            clientAppNumberState.value = client.clientAppNumber ?: ""
            amoditNumberState.value = client.amoditNumber ?: ""

            // Resetuj listy zdjęć przy inicjalizacji
            clientPhotosToAddUris.value = mutableListOf()
            transactionPhotosToAddUris.value = mutableListOf()
            photosToRemoveUris.value = mutableListOf()

            isDataInitialized = true
            Log.d("EditClientViewModel", "Stan ViewModelu (LiveData) zainicjalizowany danymi klienta.")
        } else if (isDataInitialized) {
            Log.d("EditClientViewModel", "Stan ViewModelu (LiveData) już zainicjalizowany, pomijanie.")
        } else {
            Log.w("EditClientViewModel", "Próba inicjalizacji stanu z nullowym klientem.")
        }
    }

    /**
     * Pobiera StateFlow emitujący parę: [Client?] oraz listę jego zdjęć [List<Photo>?].
     * Łączy dane z dwóch różnych Flow (dane klienta i jego zdjęcia) w jedną emisję.
     * @param clientId ID klienta do pobrania.
     * @return StateFlow emitujący Pair(Client?, List<Photo>?).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getClientWithPhotosFlow(clientId: Long): StateFlow<Pair<Client?, List<Photo>?>> {
        return clientDao.getClientByIdFlow(clientId)
            .combine(photoDao.getPhotosForClient(clientId)) { client, photos ->
                Pair(client, photos)
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(null, null)) // Konwersja na StateFlow
    }

    /**
     * Aktualizuje dane istniejącego klienta oraz synchronizuje jego zdjęcia.
     * Pobiera dane do zapisu z aktualnego stanu ViewModelu (MutableLiveData).
     * @param clientId ID edytowanego klienta.
     * @return [EditResult] wskazujący wynik operacji.
     */
    suspend fun updateClientAndPhotos(
        clientId: Long
        // Usunięto parametry, bo pobieramy je ze stanu
    ): EditResult = withContext(Dispatchers.IO) {
        // Pobierz aktualne wartości ze stanu LiveData
        val clientDescription = clientDescriptionState.value?.takeIf { it.isNotBlank() }
        val clientAppNumber = clientAppNumberState.value?.takeIf { it.isNotBlank() }
        val amoditNumber = amoditNumberState.value?.takeIf { it.isNotBlank() }
        val clientPhotoUrisToAddList = clientPhotosToAddUris.value ?: mutableListOf()
        val transactionPhotoUrisToAddList = transactionPhotosToAddUris.value ?: mutableListOf()
        val photoUrisToRemoveList = photosToRemoveUris.value ?: mutableListOf()

        try {
            // Używamy transakcji, aby zapewnić atomowość aktualizacji klienta i wpisów zdjęć.
            database.withTransaction {
                // --- Krok 1: Pobierz istniejącego klienta ---
                val existingClient = clientDao.getClientById(clientId)
                    ?: throw NotFoundException() // Rzuć wyjątek, jeśli klient nie istnieje

                // --- Krok 2: Aktualizacja danych Klienta ---
                val updatedClient = existingClient.copy(
                    description = clientDescription,
                    clientAppNumber = clientAppNumber,
                    amoditNumber = amoditNumber
                )
                clientDao.updateClient(updatedClient)
                Log.d("EditClientViewModel", "Transakcja: Zaktualizowano klienta ID: $clientId.")

                // --- Krok 3: Synchronizacja zdjęć (w ramach tej samej transakcji) ---
                // 3a. Usuń wpisy zdjęć z bazy na podstawie URI
                for (uriToRemove in photoUrisToRemoveList) {
                    photoDao.deletePhotoByUri(uriToRemove.toString())
                    Log.d("EditClientViewModel", "Transakcja: Usunięto wpis zdjęcia: $uriToRemove dla klienta $clientId")
                }
                // 3b. Dodaj nowe zdjęcia klienta (typ CLIENT)
                for (uriToAdd in clientPhotoUrisToAddList) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd.toString(), type = PhotoType.CLIENT)
                    photoDao.insertPhoto(photo)
                    Log.d("EditClientViewModel", "Transakcja: Dodano zdjęcie klienta: $uriToAdd dla klienta $clientId")
                }
                // 3c. Dodaj nowe zdjęcia transakcji (typ TRANSACTION)
                for (uriToAdd in transactionPhotoUrisToAddList) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd.toString(), type = PhotoType.TRANSACTION)
                    photoDao.insertPhoto(photo)
                    Log.d("EditClientViewModel", "Transakcja: Dodano zdjęcie transakcji: $uriToAdd dla klienta $clientId")
                }
            } // Koniec transakcji bazodanowej

            // --- Krok 4: Usuwanie plików zdjęć POZA transakcją ---
            for (uriToRemove in photoUrisToRemoveList) {
                deleteImageFile(uriToRemove.toString())
            }

            // Resetuj listy zdjęć do dodania/usunięcia w stanie LiveData po udanym zapisie
            clientPhotosToAddUris.postValue(mutableListOf())
            transactionPhotosToAddUris.postValue(mutableListOf())
            photosToRemoveUris.postValue(mutableListOf())
            // Ustaw tryb widoku
            isEditMode.postValue(false)
            // Zresetuj flagę inicjalizacji
            isDataInitialized = false

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

