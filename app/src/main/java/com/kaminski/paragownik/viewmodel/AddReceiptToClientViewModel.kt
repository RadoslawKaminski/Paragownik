package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.room.withTransaction
import com.kaminski.paragownik.AddClientActivity
import com.kaminski.paragownik.R
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.PhotoDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel dla AddReceiptToClientActivity.
 * Odpowiada za:
 * - Pobieranie danych klienta (w tym jego miniatury) do wyświetlenia w UI.
 * - Logikę dodawania nowych paragonów do istniejącego klienta w ramach transakcji bazodanowej.
 */
class AddReceiptToClientViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val clientDao: ClientDao = database.clientDao()
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val storeDao: StoreDao = database.storeDao()
    private val photoDao: PhotoDao = database.photoDao() // DAO do operacji na zdjęciach

    /**
     * Enum reprezentujący możliwy wynik operacji zapisu nowych paragonów dla klienta.
     * Używany do komunikacji wyniku operacji do UI (Activity).
     */
    enum class SaveReceiptsResult {
        SUCCESS, // Operacja zakończona sukcesem
        ERROR_DATE_FORMAT, // Błąd formatu daty paragonu
        ERROR_VERIFICATION_DATE_FORMAT, // Błąd formatu daty weryfikacji
        ERROR_DUPLICATE_RECEIPT, // Próba dodania paragonu, który już istnieje
        ERROR_STORE_NUMBER_MISSING, // Brak numeru drogerii w danych paragonu
        ERROR_DATABASE, // Ogólny błąd podczas operacji bazodanowej
        ERROR_UNKNOWN, // Nieoczekiwany, inny błąd
        ERROR_NO_RECEIPTS // Próba zapisu pustej listy paragonów
    }

    // MutableLiveData przechowująca ID aktualnie ładowanego klienta.
    // Zmiana wartości tego LiveData wyzwala aktualizację `clientDataWithThumbnail`.
    private val currentClientId = MutableLiveData<Long>()

    // LiveData przechowująca parę: (Obiekt Client?, URI miniatury jako String?).
    // Jest to wynik obserwacji zmian w `currentClientId`. Kiedy `currentClientId` się zmienia,
    // `switchMap` anuluje poprzednią obserwację i rozpoczyna nową:
    // 1. Pobiera Flow<Client?> dla nowego ID klienta.
    // 2. Używa `flatMapLatest` do połączenia tego Flow z Flow<Photo?> (miniatura).
    // 3. Mapuje wynik na Pair(Client?, String?).
    // 4. Konwertuje wynikowy Flow na LiveData.
    val clientDataWithThumbnail: LiveData<Pair<Client?, String?>>

    init {
        // Inicjalizacja LiveData klienta z miniaturą w bloku init.
        clientDataWithThumbnail = currentClientId.switchMap { clientId ->
            // Obserwuj zmiany danych klienta o podanym ID.
            clientDao.getClientByIdFlow(clientId).flatMapLatest { client ->
                if (client == null) {
                    // Jeśli klient nie istnieje (np. został usunięty), emituj parę nulli.
                    kotlinx.coroutines.flow.flowOf(Pair(null, null))
                } else {
                    // Jeśli klient istnieje, pobierz Flow dla jego pierwszego zdjęcia typu CLIENT.
                    photoDao.getFirstPhotoForClientByType(clientId, PhotoType.CLIENT)
                        .map { photo -> client to photo?.uri } // Zmapuj na parę (Client, URI?)
                }
            }.asLiveData() // Konwertuj wynikowy Flow na LiveData.
        }
    }

    /**
     * Ustawia ID klienta, którego dane mają być załadowane i obserwowane.
     * Wywołuje aktualizację `clientDataWithThumbnail`, jeśli ID się zmieniło.
     * @param clientId ID klienta do załadowania.
     */
    fun loadClientData(clientId: Long) {
        // Aktualizuj wartość tylko jeśli nowe ID różni się od obecnego,
        // aby uniknąć niepotrzebnego przeładowania danych.
        if (currentClientId.value != clientId) {
            currentClientId.value = clientId
        }
    }


    /**
     * Dodaje listę nowych paragonów do istniejącego klienta w jednej transakcji bazodanowej.
     * Zapewnia atomowość operacji: albo wszystkie paragony zostaną dodane, albo żadne.
     * Obsługuje tworzenie nowych sklepów, jeśli nie istnieją, walidację duplikatów paragonów
     * oraz parsowanie i walidację dat (paragonu i weryfikacji).
     * Operacja wykonywana jest w tle (Dispatchers.IO).
     *
     * @param clientId ID klienta, do którego dodajemy paragony.
     * @param receiptsData Lista obiektów [AddClientActivity.ReceiptData] zawierających informacje o paragonach do dodania.
     * @return [SaveReceiptsResult] Enum wskazujący wynik operacji (np. SUCCESS, ERROR_DUPLICATE_RECEIPT).
     */
    suspend fun saveReceiptsForClient(
        clientId: Long,
        receiptsData: List<AddClientActivity.ReceiptData>
    ): SaveReceiptsResult = withContext(Dispatchers.IO) {
        // Sprawdzenie, czy klient, do którego próbujemy dodać paragony, w ogóle istnieje.
        val clientExists = clientDao.getClientById(clientId)
        if (clientExists == null) {
            Log.e(
                "AddReceiptVM",
                "Próba zapisu paragonów dla nieistniejącego klienta ID: $clientId"
            )
            return@withContext SaveReceiptsResult.ERROR_DATABASE // Zwracamy błąd, jeśli klient nie istnieje
        }

        // Sprawdzenie, czy lista paragonów do dodania nie jest pusta.
        if (receiptsData.isEmpty()) {
            return@withContext SaveReceiptsResult.ERROR_NO_RECEIPTS
        }

        // Formatter daty używany do parsowania dat wprowadzonych przez użytkownika.
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false // Ścisłe sprawdzanie formatu daty

        try {
            // Rozpoczęcie transakcji bazodanowej.
            database.withTransaction {
                // Iteracja przez listę danych paragonów przekazanych z UI.
                for (receiptData in receiptsData) {

                    // Walidacja: Sprawdzenie, czy numer sklepu został podany.
                    if (receiptData.storeNumber.isBlank()) {
                        Log.e(
                            "AddReceiptVM",
                            "Transakcja: Brak numeru drogerii dla paragonu: ${receiptData.receiptNumber}"
                        )
                        throw StoreNumberMissingException() // Rzucenie wyjątku przerwie transakcję
                    }

                    // Parsowanie daty paragonu. Rzuca DateFormatException w przypadku błędu.
                    val receiptDate: Date = try {
                        dateFormat.parse(receiptData.receiptDate) as Date
                    } catch (e: ParseException) {
                        Log.e(
                            "AddReceiptVM",
                            "Transakcja: Błąd formatu daty paragonu: ${receiptData.receiptDate}"
                        )
                        throw DateFormatException()
                    }

                    // Obsługa Sklepu: Znajdź istniejący sklep lub utwórz nowy.
                    var store = storeDao.getStoreByNumber(receiptData.storeNumber)
                    val storeId: Long
                    if (store == null) {
                        // Sklep nie istnieje, tworzymy nowy.
                        store = Store(storeNumber = receiptData.storeNumber)
                        storeDao.insertStore(store)
                        // Pobieramy nowo utworzony sklep, aby uzyskać jego ID.
                        store = storeDao.getStoreByNumber(receiptData.storeNumber)
                        if (store == null) {
                            // Jeśli nadal nie można pobrać sklepu, rzucamy błąd.
                            throw DatabaseException(
                                getApplication<Application>().getString(
                                    R.string.error_creating_store,
                                    receiptData.storeNumber
                                )
                            )
                        }
                        storeId = store.id
                        Log.d(
                            "AddReceiptVM",
                            "Transakcja: Dodano nową drogerię: ${receiptData.storeNumber}, ID: $storeId"
                        )
                    } else {
                        // Sklep istnieje, używamy jego ID.
                        storeId = store.id
                    }

                    // Walidacja Duplikatów Paragonów: Sprawdzenie, czy paragon o tej samej
                    // kombinacji numeru, daty i sklepu już istnieje w bazie.
                    val existingReceipt = receiptDao.findByNumberDateStore(
                        receiptData.receiptNumber,
                        receiptDate,
                        storeId
                    )
                    if (existingReceipt != null) {
                        Log.e(
                            "AddReceiptVM",
                            "Transakcja: Znaleziono duplikat paragonu: Nr ${receiptData.receiptNumber}, Data ${receiptData.receiptDate}, Sklep ID $storeId"
                        )
                        throw DuplicateReceiptException()
                    }

                    // Pobranie daty weryfikacji z danych wejściowych.
                    val verificationDateStringFromData = receiptData.verificationDateString

                    // Parsowanie daty weryfikacji (jeśli została podana).
                    // Rzuca VerificationDateFormatException w przypadku błędu.
                    val verificationDate: Date? =
                        if (!verificationDateStringFromData.isNullOrBlank()) {
                            try {
                                dateFormat.parse(verificationDateStringFromData) as Date
                            } catch (e: ParseException) {
                                Log.e(
                                    "AddReceiptVM",
                                    "Transakcja: Błąd formatu daty weryfikacji: $verificationDateStringFromData"
                                )
                                throw VerificationDateFormatException()
                            }
                        } else {
                            null // Jeśli data weryfikacji jest pusta, zapisujemy null.
                        }

                    // Utworzenie obiektu Receipt i wstawienie go do bazy.
                    val receipt = Receipt(
                        receiptNumber = receiptData.receiptNumber,
                        receiptDate = receiptDate,
                        storeId = storeId,
                        verificationDate = verificationDate, // Używamy sparsowanej lub null daty weryfikacji
                        clientId = clientId // Przypisanie paragonu do istniejącego klienta
                    )
                    val insertedReceiptId = receiptDao.insertReceipt(receipt)
                    // Sprawdzenie, czy wstawienie paragonu się powiodło.
                    if (insertedReceiptId == -1L) {
                        throw DatabaseException("Błąd wstawiania paragonu do bazy.")
                    }
                    Log.d(
                        "AddReceiptVM",
                        "Transakcja: Dodano paragon ID: $insertedReceiptId dla klienta ID: $clientId"
                    )
                }
            } // Koniec bloku withTransaction

            // Jeśli transakcja zakończyła się sukcesem (nie rzucono wyjątku), zwracamy SUCCESS.
            SaveReceiptsResult.SUCCESS
        } catch (e: DateFormatException) {
            // Obsługa specyficznych wyjątków rzuconych wewnątrz transakcji.
            SaveReceiptsResult.ERROR_DATE_FORMAT
        } catch (e: VerificationDateFormatException) {
            SaveReceiptsResult.ERROR_VERIFICATION_DATE_FORMAT
        } catch (e: DuplicateReceiptException) {
            SaveReceiptsResult.ERROR_DUPLICATE_RECEIPT
        } catch (e: StoreNumberMissingException) {
            SaveReceiptsResult.ERROR_STORE_NUMBER_MISSING
        } catch (e: DatabaseException) {
            // Obsługa błędów bazodanowych rzuconych jawnie w kodzie.
            Log.e("AddReceiptVM", "Transakcja: Błąd bazy danych.", e)
            SaveReceiptsResult.ERROR_DATABASE
        } catch (e: Exception) {
            // Obsługa wszystkich innych, nieprzewidzianych błędów.
            Log.e("AddReceiptVM", "Transakcja: Nieznany błąd.", e)
            SaveReceiptsResult.ERROR_UNKNOWN
        }
    }

    // Prywatne klasy wyjątków używane do sterowania przepływem w bloku try-catch transakcji.
    private class DateFormatException : Exception()
    private class VerificationDateFormatException : Exception()
    private class DuplicateReceiptException : Exception()
    private class StoreNumberMissingException : Exception()
    private class DatabaseException(message: String) : Exception(message)
}