
package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
// Usunięto: import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.kaminski.paragownik.R
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Photo
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.PhotoDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

/**
 * ViewModel dla EditReceiptActivity.
 * Odpowiada za logikę biznesową związaną z edycją i usuwaniem paragonów/klientów,
 * w tym obsługę wielu zdjęć i usuwanie plików zdjęć.
 * Przechowuje również stan UI edycji za pomocą MutableLiveData.
 */
// Usunięto SavedStateHandle z konstruktora
class EditReceiptViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val storeDao: StoreDao = database.storeDao()
    private val clientDao: ClientDao = database.clientDao()
    private val photoDao: PhotoDao = database.photoDao()

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

    // --- MutableLiveData do zarządzania stanem UI ---

    // Tryb edycji (true) lub widoku (false)
    val isEditMode = MutableLiveData<Boolean>(false) // Wartość początkowa false

    // Stany edytowalnych pól
    val storeNumberState = MutableLiveData<String>("")
    val receiptNumberState = MutableLiveData<String>("")
    val receiptDateState = MutableLiveData<String>("")
    val cashRegisterNumberState = MutableLiveData<String>("")
    val verificationDateState = MutableLiveData<String>("")
    val isVerificationDateTodayState = MutableLiveData<Boolean>(false)
    val clientDescriptionState = MutableLiveData<String>("")
    val clientAppNumberState = MutableLiveData<String>("")
    val amoditNumberState = MutableLiveData<String>("")

    // Stany list zdjęć
    val clientPhotosToAddUris = MutableLiveData<MutableList<Uri>>(mutableListOf())
    val transactionPhotosToAddUris = MutableLiveData<MutableList<Uri>>(mutableListOf())
    val photosToRemoveUris = MutableLiveData<MutableList<Uri>>(mutableListOf())

    // --- Metody do aktualizacji stanu UI ---

    fun setEditMode(isEditing: Boolean) {
        isEditMode.value = isEditing
    }

    // Metody set... aktualizują teraz MutableLiveData
    fun setStoreNumber(number: String) { storeNumberState.value = number }
    fun setReceiptNumber(number: String) { receiptNumberState.value = number }
    fun setReceiptDate(date: String) { receiptDateState.value = date }
    fun setCashRegisterNumber(number: String) { cashRegisterNumberState.value = number }
    fun setVerificationDate(date: String) { verificationDateState.value = date }
    fun setIsVerificationDateToday(isToday: Boolean) { isVerificationDateTodayState.value = isToday }
    fun setClientDescription(desc: String) { clientDescriptionState.value = desc }
    fun setClientAppNumber(number: String) { clientAppNumberState.value = number }
    fun setAmoditNumber(number: String) { amoditNumberState.value = number }

    fun addPhotoToAdd(uri: Uri, type: PhotoType) {
        val liveData = if (type == PhotoType.CLIENT) clientPhotosToAddUris else transactionPhotosToAddUris
        val currentList = liveData.value ?: mutableListOf()
        if (!currentList.contains(uri)) {
            currentList.add(uri)
            liveData.value = currentList // Zaktualizuj LiveData
        }
    }

    fun removePhotoToAdd(uri: Uri, type: PhotoType) {
        val liveData = if (type == PhotoType.CLIENT) clientPhotosToAddUris else transactionPhotosToAddUris
        val currentList = liveData.value ?: mutableListOf()
        if (currentList.remove(uri)) {
            liveData.value = currentList // Zaktualizuj LiveData
        }
    }

    fun addPhotoToRemove(uri: Uri) {
        val currentList = photosToRemoveUris.value ?: mutableListOf()
        if (!currentList.contains(uri)) {
            currentList.add(uri)
            photosToRemoveUris.value = currentList // Zaktualizuj LiveData
        }
    }

    fun removePhotoToRemove(uri: Uri) {
        val currentList = photosToRemoveUris.value ?: mutableListOf()
        if (currentList.remove(uri)) {
            photosToRemoveUris.value = currentList // Zaktualizuj LiveData
        }
    }

    // Flaga inicjalizacji pozostaje jako zwykła zmienna
    private var isDataInitialized = false

    /**
     * Inicjalizuje stan MutableLiveData na podstawie danych z bazy, ale tylko raz.
     * @param receiptWithClient Dane paragonu i klienta.
     * @param storeNumber Numer sklepu.
     */
    fun initializeStateIfNeeded(receiptWithClient: ReceiptWithClient, storeNumber: String?) {
        if (!isDataInitialized) {
            val receipt = receiptWithClient.receipt
            val client = receiptWithClient.client
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

            // Ustaw wartości w MutableLiveData
            storeNumberState.value = storeNumber ?: ""
            receiptNumberState.value = receipt.receiptNumber
            receiptDateState.value = dateFormat.format(receipt.receiptDate)
            cashRegisterNumberState.value = receipt.cashRegisterNumber ?: ""
            val verificationDateStr = receipt.verificationDate?.let { dateFormat.format(it) } ?: ""
            verificationDateState.value = verificationDateStr
            val todayDateStr = dateFormat.format(Calendar.getInstance().time)
            isVerificationDateTodayState.value = verificationDateStr == todayDateStr
            clientDescriptionState.value = client?.description ?: ""
            clientAppNumberState.value = client?.clientAppNumber ?: ""
            amoditNumberState.value = client?.amoditNumber ?: ""

            // Resetujemy listy zdjęć do dodania/usunięcia
            clientPhotosToAddUris.value = mutableListOf()
            transactionPhotosToAddUris.value = mutableListOf()
            photosToRemoveUris.value = mutableListOf()

            isDataInitialized = true
            Log.d("EditReceiptViewModel", "Stan ViewModelu (LiveData) zainicjalizowany danymi z bazy.")
        } else {
            Log.d("EditReceiptViewModel", "Stan ViewModelu (LiveData) już zainicjalizowany, pomijanie.")
        }
    }

    /**
     * Pobiera Flow emitujący trójkę: [ReceiptWithClient?], numer sklepu [String?] oraz listę zdjęć [List<Photo>?].
     * Pozostaje jako Flow, nie konwertujemy na StateFlow tutaj.
     * @param receiptId ID paragonu do pobrania.
     * @return Flow emitujący Triple.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getReceiptDataFlow(receiptId: Long): Flow<Triple<ReceiptWithClient?, String?, List<Photo>?>> {
        return receiptDao.getReceiptWithClientFlow(receiptId)
            .flatMapLatest { receiptWithClient ->
                if (receiptWithClient?.client == null) {
                    kotlinx.coroutines.flow.flowOf(Triple(null, null, null))
                } else {
                    storeDao.getStoreByIdFlow(receiptWithClient.receipt.storeId)
                        .combine(photoDao.getPhotosForClient(receiptWithClient.client.id)) { store, photos ->
                            Triple(receiptWithClient, store?.storeNumber, photos)
                        }
                }
            }
            .flowOn(Dispatchers.IO)
            // Usunięto .stateIn()
    }


    /**
     * Aktualizuje dane istniejącego paragonu, powiązanego klienta oraz synchronizuje zdjęcia.
     * Pobiera dane do zapisu z aktualnego stanu ViewModelu (MutableLiveData).
     * @param receiptId ID edytowanego paragonu.
     * @return [EditResult] wskazujący wynik operacji.
     */
    suspend fun updateReceiptAndClient(
        receiptId: Long
    ): EditResult = withContext(Dispatchers.IO) {
        // Pobierz aktualne wartości ze stanu LiveData
        val storeNumberString = storeNumberState.value ?: ""
        val receiptNumber = receiptNumberState.value ?: ""
        val receiptDateString = receiptDateState.value ?: ""
        val cashRegisterNumber = cashRegisterNumberState.value?.takeIf { it.isNotBlank() }
        val verificationDateString = verificationDateState.value?.takeIf { it.isNotBlank() }
        val clientDescription = clientDescriptionState.value?.takeIf { it.isNotBlank() }
        val clientAppNumber = clientAppNumberState.value?.takeIf { it.isNotBlank() }
        val amoditNumber = amoditNumberState.value?.takeIf { it.isNotBlank() }
        val clientPhotoUrisToAddList = clientPhotosToAddUris.value ?: mutableListOf()
        val transactionPhotoUrisToAddList = transactionPhotosToAddUris.value ?: mutableListOf()
        val photoUrisToRemoveList = photosToRemoveUris.value ?: mutableListOf()

        // Format daty
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // Transakcja bazodanowa
            database.withTransaction {
                // Kroki 1-8 pozostają takie same jak w poprzedniej wersji,
                // używając pobranych wartości z LiveData
                // --- Krok 1: Pobierz istniejące dane PRZED modyfikacją ---
                val existingReceiptWithClient = receiptDao.getReceiptWithClient(receiptId)
                if (existingReceiptWithClient == null || existingReceiptWithClient.client == null) {
                    Log.e("EditReceiptViewModel", "Nie znaleziono paragonu (ID: $receiptId) lub klienta do edycji.")
                    throw NotFoundException()
                }
                val existingReceipt = existingReceiptWithClient.receipt
                val existingClient = existingReceiptWithClient.client
                val clientId = existingClient.id
                val originalStoreId = existingReceipt.storeId

                // --- Krok 2: Walidacja i parsowanie danych wejściowych ze stanu ---
                val receiptDate: Date = try {
                    dateFormat.parse(receiptDateString) as Date
                } catch (e: ParseException) {
                    Log.e("EditReceiptViewModel", "Błąd formatu daty paragonu: $receiptDateString")
                    throw DateFormatException()
                }
                val verificationDate: Date? = verificationDateString?.let {
                    try {
                        dateFormat.parse(it) as Date
                    } catch (e: ParseException) {
                        Log.w("EditReceiptViewModel", "Błąd formatu daty weryfikacji (ignorowanie): $it")
                        null // Traktujemy błąd jako brak daty
                    }
                }

                // --- Krok 3: Walidacja i obsługa numeru sklepu ---
                if (storeNumberString.isBlank()) {
                    Log.e("EditReceiptViewModel", "Brak numeru drogerii podczas edycji.")
                    throw StoreNumberMissingException()
                }
                var store = storeDao.getStoreByNumber(storeNumberString)
                val newStoreId: Long
                if (store == null) {
                    store = Store(storeNumber = storeNumberString)
                    storeDao.insertStore(store)
                    store = storeDao.getStoreByNumber(storeNumberString)
                    if (store == null) {
                        throw DatabaseException(getApplication<Application>().getString(R.string.error_creating_store_edit, storeNumberString))
                    }
                    newStoreId = store.id
                    Log.d("EditReceiptViewModel", "Transakcja: Utworzono nową drogerię (ID: $newStoreId) podczas edycji.")
                } else {
                    newStoreId = store.id
                }

                // --- Krok 4: Walidacja duplikatów paragonów ---
                val potentialDuplicate = receiptDao.findByNumberDateStoreCashRegister(
                    receiptNumber,
                    receiptDate,
                    newStoreId,
                    cashRegisterNumber
                )
                if (potentialDuplicate != null && potentialDuplicate.id != receiptId) {
                    Log.e("EditReceiptViewModel", "Edycja: Znaleziono inny paragon (ID: ${potentialDuplicate.id}) z tymi samymi danymi.")
                    throw DuplicateReceiptException()
                }

                // --- Krok 5: Aktualizacja Paragonu ---
                val updatedReceipt = existingReceipt.copy(
                    receiptNumber = receiptNumber,
                    receiptDate = receiptDate,
                    storeId = newStoreId,
                    cashRegisterNumber = cashRegisterNumber,
                    verificationDate = verificationDate
                )
                receiptDao.updateReceipt(updatedReceipt)
                Log.d("EditReceiptViewModel", "Transakcja: Zaktualizowano paragon ID: $receiptId.")

                // --- Krok 6: Aktualizacja Klienta ---
                val updatedClient = existingClient.copy(
                    description = clientDescription,
                    clientAppNumber = clientAppNumber,
                    amoditNumber = amoditNumber
                )
                clientDao.updateClient(updatedClient)
                Log.d("EditReceiptViewModel", "Transakcja: Zaktualizowano klienta ID: $clientId.")

                // --- Krok 7: Synchronizacja zdjęć (w ramach tej samej transakcji) ---
                // 7a. Usuń zdjęcia oznaczone do usunięcia
                for (uriToRemove in photoUrisToRemoveList) {
                    photoDao.deletePhotoByUri(uriToRemove.toString()) // Konwersja Uri na String
                    Log.d("EditReceiptViewModel", "Transakcja: Usunięto wpis zdjęcia: $uriToRemove dla klienta $clientId")
                }
                // 7b. Dodaj nowe zdjęcia klienta
                for (uriToAdd in clientPhotoUrisToAddList) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd.toString(), type = PhotoType.CLIENT)
                    photoDao.insertPhoto(photo)
                    Log.d("EditReceiptViewModel", "Transakcja: Dodano zdjęcie klienta: $uriToAdd dla klienta $clientId")
                }
                // 7c. Dodaj nowe zdjęcia transakcji
                for (uriToAdd in transactionPhotoUrisToAddList) {
                    val photo = Photo(clientId = clientId, uri = uriToAdd.toString(), type = PhotoType.TRANSACTION)
                    photoDao.insertPhoto(photo)
                    Log.d("EditReceiptViewModel", "Transakcja: Dodano zdjęcie transakcji: $uriToAdd dla klienta $clientId")
                }

                // --- Krok 8: Sprawdź i usuń osieroconą pierwotną drogerię (JEŚLI SIĘ ZMIENIŁA) ---
                if (originalStoreId != newStoreId) {
                    Log.d("EditReceiptViewModel", "Transakcja: ID sklepu zmienione z $originalStoreId na $newStoreId. Sprawdzanie starego sklepu.")
                    val originalStoreReceiptsCount = receiptDao.getReceiptsForStoreCount(originalStoreId)
                    Log.d("EditReceiptViewModel", "Transakcja: Liczba paragonów dla starego sklepu (ID: $originalStoreId): $originalStoreReceiptsCount")
                    if (originalStoreReceiptsCount == 0) {
                        val storeToDelete = storeDao.getStoreById(originalStoreId)
                        storeToDelete?.let {
                            storeDao.deleteStore(it)
                            Log.d("EditReceiptViewModel", "Transakcja: Pierwotna drogeria (ID: $originalStoreId) usunięta automatycznie.")
                        } ?: run {
                            Log.w("EditReceiptViewModel", "Transakcja: Nie znaleziono pierwotnej drogerii (ID: $originalStoreId) do usunięcia.")
                        }
                    }
                }
            } // Koniec bloku withTransaction

            // --- Krok 9: Usuwanie plików zdjęć POZA transakcją ---
            for (uriToRemove in photoUrisToRemoveList) {
                deleteImageFile(uriToRemove.toString()) // Konwersja Uri na String
            }

            // Resetuj listy zdjęć do dodania/usunięcia w stanie LiveData po udanym zapisie
            clientPhotosToAddUris.postValue(mutableListOf()) // Użyj postValue, bo jesteśmy w tle
            transactionPhotosToAddUris.postValue(mutableListOf())
            photosToRemoveUris.postValue(mutableListOf())
            // Ustaw tryb widoku
            setEditMode(false) // To też powinno użyć postValue, jeśli jest ryzyko wywołania z tła
            // Zresetuj flagę inicjalizacji, aby dane zostały ponownie załadowane z bazy po zapisie
            isDataInitialized = false

            Log.d("EditReceiptViewModel", "Paragon (ID: $receiptId) i Klient zaktualizowane.")
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
     * @param receipt Paragon do usunięcia.
     * @return [EditResult] wskazujący wynik operacji.
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

            database.withTransaction {
                receiptDao.deleteReceipt(receiptToDelete)
                Log.d("EditReceiptViewModel", "Transakcja: Paragon usunięty. ID: ${receipt.id}")

                val clientReceiptsCount = receiptDao.getReceiptsForClientCount(clientId)
                Log.d("EditReceiptViewModel", "Transakcja: Liczba pozostałych paragonów dla klienta (ID: $clientId): $clientReceiptsCount")
                if (clientReceiptsCount == 0) {
                    val clientToDelete = clientDao.getClientById(clientId)
                    clientToDelete?.let { client ->
                        photoUrisToDelete = photoDao.getPhotoUrisForClient(client.id)
                        Log.d("EditReceiptViewModel", "Transakcja: Klient osierocony (ID: ${client.id}). Znaleziono ${photoUrisToDelete.size} zdjęć do usunięcia.")
                        clientDao.deleteClient(client)
                        clientDeleted = true
                        Log.d("EditReceiptViewModel", "Transakcja: Klient (ID: ${client.id}) usunięty automatycznie z bazy.")
                    }
                }

                val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                Log.d("EditReceiptViewModel", "Transakcja: Liczba pozostałych paragonów dla sklepu (ID: $storeId): $storeReceiptsCount")
                if (storeReceiptsCount == 0) {
                    val storeToDelete = storeDao.getStoreById(storeId)
                    storeToDelete?.let {
                        storeDao.deleteStore(it)
                        Log.d("EditReceiptViewModel", "Transakcja: Drogeria (ID: $storeId) usunięta automatycznie.")
                    }
                }
            } // Koniec transakcji

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
     * @param client Klient do usunięcia.
     * @return [EditResult] wskazujący wynik operacji.
     */
    suspend fun deleteClient(client: Client): EditResult = withContext(Dispatchers.IO) {
        try {
            val clientToDelete = clientDao.getClientById(client.id)
            if (clientToDelete == null) {
                Log.e("EditReceiptViewModel", "Nie znaleziono klienta do usunięcia. ID: ${client.id}")
                return@withContext EditResult.ERROR_NOT_FOUND
            }

            val associatedStoreIds = receiptDao.getStoreIdsForClient(clientToDelete.id)
            Log.d("EditReceiptViewModel", "Sklepy powiązane z klientem ${clientToDelete.id} przed usunięciem: $associatedStoreIds")

            val photoUrisToDelete = photoDao.getPhotoUrisForClient(clientToDelete.id)
            Log.d("EditReceiptViewModel", "Znaleziono ${photoUrisToDelete.size} zdjęć do usunięcia dla klienta ${clientToDelete.id}")

            database.withTransaction {
                clientDao.deleteClient(clientToDelete)
                Log.d("EditReceiptViewModel", "Transakcja: Klient (ID: ${clientToDelete.id}) usunięty z bazy (paragony i zdjęcia kaskadowo).")

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
            Log.w("EditReceiptViewModel", "Próba usunięcia pliku, ale URI jest puste.")
            return
        }
        try {
            val fileUri = photoUriString.toUri()
            if (fileUri.scheme == "file" && fileUri.path?.startsWith(getApplication<Application>().filesDir.absolutePath) == true) {
                val fileToDelete = File(fileUri.path!!)
                if (fileToDelete.exists()) {
                    if (fileToDelete.delete()) {
                        Log.d("EditReceiptViewModel", "Usunięto plik zdjęcia: $photoUriString")
                    } else {
                        Log.w("EditReceiptViewModel", "Nie udało się usunąć pliku zdjęcia: $photoUriString (metoda delete() zwróciła false)")
                    }
                } else {
                    Log.w("EditReceiptViewModel", "Plik zdjęcia do usunięcia nie istnieje: $photoUriString")
                }
            } else {
                Log.w("EditReceiptViewModel", "Próba usunięcia pliku z nieobsługiwanego URI lub spoza magazynu aplikacji: $photoUriString")
            }
        } catch (e: Exception) {
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

