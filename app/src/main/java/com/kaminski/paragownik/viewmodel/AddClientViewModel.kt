package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
import com.kaminski.paragownik.AddClientActivity.ReceiptData // Import wewnętrznej klasy z AddClientActivity
import com.kaminski.paragownik.R // Potrzebne dla zasobów string (np. w komunikatach błędów)
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Photo
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel dla AddClientActivity.
 * Odpowiada za logikę biznesową związaną z dodawaniem nowego klienta
 * wraz z jednym lub wieloma paragonami i zdjęciami do bazy danych.
 * Wykorzystuje transakcje bazodanowe do zapewnienia spójności danych.
 */
class AddClientViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val receiptDao: ReceiptDao = database.receiptDao()
    private val clientDao: ClientDao = database.clientDao()
    private val storeDao: StoreDao = database.storeDao()
    // Nie potrzebujemy pola dla photoDao, ponieważ dostęp do niego uzyskujemy
    // przez `database.photoDao()` wewnątrz transakcji.

    /**
     * Enum reprezentujący możliwy wynik operacji dodawania klienta i paragonów.
     * Używany do komunikacji wyniku operacji do UI (Activity).
     */
    enum class AddResult {
        SUCCESS, // Operacja zakończona sukcesem
        ERROR_DATE_FORMAT, // Błąd formatu daty paragonu (np. "30-02-2023")
        ERROR_VERIFICATION_DATE_FORMAT, // Błąd formatu daty weryfikacji
        ERROR_DUPLICATE_RECEIPT, // Próba dodania paragonu, który już istnieje (ten sam numer, data, sklep)
        ERROR_STORE_NUMBER_MISSING, // Brak numeru drogerii w danych paragonu
        ERROR_DATABASE, // Ogólny błąd podczas operacji bazodanowej (np. wstawiania)
        ERROR_UNKNOWN // Nieoczekiwany, inny błąd
    }

    /**
     * Dodaje nowego klienta wraz z listą powiązanych paragonów i zdjęć w jednej transakcji bazodanowej.
     * Gwarantuje to, że albo wszystkie dane zostaną zapisane poprawnie, albo żadne zmiany nie zostaną wprowadzone.
     * Operacja wykonywana jest w tle (Dispatchers.IO).
     *
     * @param clientDescription Opis klienta (może być null lub pusty).
     * @param clientAppNumber Numer aplikacji klienta (może być null lub pusty).
     * @param amoditNumber Numer Amodit klienta (może być null lub pusty).
     * @param clientPhotoUris Lista URI (jako String) zdjęć typu CLIENT do dodania.
     * @param transactionPhotoUris Lista URI (jako String) zdjęć typu TRANSACTION do dodania.
     * @param receiptsData Lista obiektów [ReceiptData] zawierających informacje o paragonach do dodania.
     * @return [AddResult] Enum wskazujący wynik operacji (np. SUCCESS, ERROR_DUPLICATE_RECEIPT).
     */
    suspend fun addClientWithReceiptsTransactionally(
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?,
        clientPhotoUris: List<String>,
        transactionPhotoUris: List<String>,
        receiptsData: List<ReceiptData>
    ): AddResult = withContext(Dispatchers.IO) {
        // Formatter daty używany do parsowania dat wprowadzonych przez użytkownika.
        // isLenient = false oznacza ścisłe sprawdzanie formatu (np. odrzuci "31-02-2023").
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // Rozpoczęcie transakcji bazodanowej. Wszystkie operacje wewnątrz bloku
            // zostaną wykonane atomowo.
            database.withTransaction {
                // Krok 1: Dodanie Klienta
                // Tworzymy obiekt Client, używając takeIf { it.isNotBlank() } do zapisania null,
                // jeśli przekazany String jest pusty lub null.
                val client = Client(
                    description = clientDescription?.takeIf { it.isNotBlank() },
                    clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                    amoditNumber = amoditNumber?.takeIf { it.isNotBlank() }
                )
                // Wstawienie klienta do bazy i pobranie jego nowo wygenerowanego ID.
                val clientId = clientDao.insertClient(client)
                Log.d("AddClientViewModel", "Transakcja: Dodano klienta ID: $clientId")

                // Sprawdzenie, czy wstawienie klienta się powiodło.
                if (clientId == -1L) {
                    throw DatabaseException(getApplication<Application>().getString(R.string.error_inserting_client))
                }

                // Krok 2a: Dodanie zdjęć klienta (typu CLIENT)
                for (uri in clientPhotoUris) {
                    val photo = Photo(clientId = clientId, uri = uri, type = PhotoType.CLIENT)
                    // Używamy photoDao() bezpośrednio z instancji bazy wewnątrz transakcji.
                    database.photoDao().insertPhoto(photo)
                    Log.d(
                        "AddClientViewModel",
                        "Transakcja: Dodano zdjęcie klienta: $uri dla klienta ID: $clientId"
                    )
                }

                // Krok 2b: Dodanie zdjęć transakcji (typu TRANSACTION)
                for (uri in transactionPhotoUris) {
                    val photo = Photo(clientId = clientId, uri = uri, type = PhotoType.TRANSACTION)
                    database.photoDao().insertPhoto(photo)
                    Log.d(
                        "AddClientViewModel",
                        "Transakcja: Dodano zdjęcie transakcji: $uri dla klienta ID: $clientId"
                    )
                }

                // Krok 3: Przetwarzanie i dodawanie Paragonów
                for (receiptData in receiptsData) {
                    val verificationDateStringFromData = receiptData.verificationDateString

                    // Sprawdzenie, czy numer drogerii został podany.
                    if (receiptData.storeNumber.isBlank()) {
                        Log.e(
                            "AddClientViewModel",
                            "Transakcja: Brak numeru drogerii dla paragonu: ${receiptData.receiptNumber}"
                        )
                        throw StoreNumberMissingException()
                    }

                    // Parsowanie daty paragonu. Rzuca DateFormatException w przypadku błędu.
                    val receiptDate: Date = try {
                        dateFormat.parse(receiptData.receiptDate) as Date
                    } catch (e: ParseException) {
                        Log.e(
                            "AddClientViewModel",
                            "Transakcja: Błąd formatu daty paragonu: ${receiptData.receiptDate}"
                        )
                        throw DateFormatException()
                    }

                    // Parsowanie daty weryfikacji (jeśli została podana).
                    // Rzuca VerificationDateFormatException w przypadku błędu.
                    val verificationDate: Date? =
                        if (!verificationDateStringFromData.isNullOrBlank()) {
                            try {
                                dateFormat.parse(verificationDateStringFromData) as Date
                            } catch (e: ParseException) {
                                Log.e(
                                    "AddClientViewModel",
                                    "Transakcja: Błąd formatu daty weryfikacji: $verificationDateStringFromData"
                                )
                                throw VerificationDateFormatException()
                            }
                        } else {
                            null // Jeśli data weryfikacji jest pusta, zapisujemy null.
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
                        // Sprawdzamy, czy na pewno udało się pobrać sklep po wstawieniu.
                        if (store == null) {
                            throw DatabaseException(
                                getApplication<Application>().getString(
                                    R.string.error_creating_store,
                                    receiptData.storeNumber
                                )
                            )
                        }
                        storeId = store.id
                        Log.d(
                            "AddClientViewModel",
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
                            "AddClientViewModel",
                            "Transakcja: Znaleziono duplikat paragonu: Nr ${receiptData.receiptNumber}, Data ${receiptData.receiptDate}, Sklep ID $storeId"
                        )
                        throw DuplicateReceiptException()
                    }

                    // Dodanie Paragonu do bazy danych.
                    val receipt = Receipt(
                        receiptNumber = receiptData.receiptNumber,
                        receiptDate = receiptDate,
                        storeId = storeId,
                        verificationDate = verificationDate, // Używamy sparsowanej lub null daty weryfikacji
                        clientId = clientId // Przypisanie paragonu do nowo utworzonego klienta
                    )
                    receiptDao.insertReceipt(receipt)
                    Log.d(
                        "AddClientViewModel",
                        "Transakcja: Dodano paragon: Nr ${receipt.receiptNumber} dla klienta ID: $clientId"
                    )
                }
            }
            // Jeśli transakcja zakończyła się sukcesem (nie rzucono wyjątku), zwracamy SUCCESS.
            AddResult.SUCCESS
        } catch (e: DateFormatException) {
            // Obsługa błędów specyficznych dla logiki dodawania.
            AddResult.ERROR_DATE_FORMAT
        } catch (e: VerificationDateFormatException) {
            AddResult.ERROR_VERIFICATION_DATE_FORMAT
        } catch (e: DuplicateReceiptException) {
            AddResult.ERROR_DUPLICATE_RECEIPT
        } catch (e: StoreNumberMissingException) {
            AddResult.ERROR_STORE_NUMBER_MISSING
        } catch (e: DatabaseException) {
            // Obsługa błędów bazodanowych rzuconych jawnie w kodzie.
            Log.e("AddClientViewModel", "Transakcja: Błąd bazy danych.", e)
            AddResult.ERROR_DATABASE
        } catch (e: Exception) {
            // Obsługa wszystkich innych, nieprzewidzianych błędów.
            Log.e("AddClientViewModel", "Transakcja: Nieznany błąd.", e)
            AddResult.ERROR_UNKNOWN
        }
    }

    // Prywatne klasy wyjątków używane do sterowania przepływem w bloku try-catch transakcji.
    private class DateFormatException : Exception()
    private class VerificationDateFormatException : Exception()
    private class DuplicateReceiptException : Exception()
    private class StoreNumberMissingException : Exception()
    private class DatabaseException(message: String) : Exception(message)
}