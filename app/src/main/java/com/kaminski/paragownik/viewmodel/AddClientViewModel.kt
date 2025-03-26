package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.kaminski.paragownik.AddClientActivity // Zaimportuj ReceiptData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel dla AddClientActivity.
 * Odpowiada za logikę dodawania nowego klienta i jego paragonów do bazy danych.
 */
class AddClientViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase // Instancja bazy danych
    private val receiptDao: ReceiptDao
    private val clientDao: ClientDao
    private val storeDao: StoreDao

    /**
     * Enum reprezentujący możliwy wynik operacji dodawania klienta i paragonów.
     * Używany do komunikacji wyniku do UI.
     */
    enum class AddResult {
        SUCCESS,                    // Operacja zakończona sukcesem
        ERROR_DATE_FORMAT,          // Błąd formatu daty w jednym z paragonów
        ERROR_DUPLICATE_RECEIPT,    // Próba dodania paragonu, który już istnieje (ten sam nr, data, sklep)
        ERROR_STORE_NUMBER_MISSING, // Brak numeru sklepu dla jednego z paragonów
        ERROR_DATABASE,             // Ogólny błąd podczas operacji bazodanowej
        ERROR_UNKNOWN               // Nieznany błąd
    }

    init {
        // Inicjalizacja bazy danych i DAO
        database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()
        clientDao = database.clientDao()
        storeDao = database.storeDao()
    }

    // --- NOWA METODA TRANSKACYJNA ---
    /**
     * Dodaje klienta wraz z listą paragonów w jednej transakcji bazodanowej.
     * Przeprowadza walidację formatu daty i unikalności paragonów.
     * Jeśli jakikolwiek krok zawiedzie, cała transakcja jest wycofywana.
     *
     * @param clientDescription Opis klienta (może być null).
     * @param clientAppNumber Numer aplikacji klienta (może być null).
     * @param amoditNumber Numer Amodit klienta (może być null).
     * @param photoUri URI zdjęcia klienta (może być null).
     * @param receiptsData Lista obiektów [AddClientActivity.ReceiptData] zawierających dane paragonów.
     * @param verificationDateString Data weryfikacji dla pierwszego paragonu (może być null).
     * @return [AddResult] Enum wskazujący wynik operacji.
     */
    suspend fun addClientWithReceiptsTransactionally(
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?,
        photoUri: String?,
        receiptsData: List<AddClientActivity.ReceiptData>,
        verificationDateString: String?
    ): AddResult = withContext(Dispatchers.IO) { // Wykonaj w tle (IO dispatcher)
        // Formatter daty ze ścisłym sprawdzaniem formatu
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false

        try {
            // Rozpocznij transakcję bazodanową
            database.withTransaction {
                // 1. Utwórz obiekt klienta, zapisując tylko niepuste wartości dla numerów
                val client = Client(
                    description = clientDescription,
                    clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                    amoditNumber = amoditNumber?.takeIf { it.isNotBlank() },
                    photoUri = photoUri // Na razie photoUri jest zawsze null
                )
                // Wstaw klienta do bazy
                val clientId = clientDao.insertClient(client)
                Log.d("AddClientViewModel", "Transakcja: Dodano klienta ID: $clientId")

                // Sprawdź, czy wstawienie klienta się powiodło
                if (clientId == -1L) {
                    // Jeśli nie, rzuć wyjątek, aby wycofać transakcję
                    throw Exception("Błąd wstawiania klienta do bazy danych.")
                }

                // 2. Przetwórz i dodaj każdy paragon z listy
                for (i in receiptsData.indices) {
                    val receiptData = receiptsData[i]

                    // Walidacja: Sprawdź, czy numer drogerii nie jest pusty
                    if (receiptData.storeNumber.isBlank()) {
                        Log.e("AddClientViewModel", "Transakcja: Brak numeru drogerii dla paragonu: ${receiptData.receiptNumber}")
                        throw StoreNumberMissingException() // Rzuć wyjątek, aby wycofać transakcję
                    }

                    // Parsowanie daty paragonu
                    val receiptDate: Date = try {
                        dateFormat.parse(receiptData.receiptDate) as Date
                    } catch (e: ParseException) {
                        // Jeśli format daty jest nieprawidłowy, rzuć wyjątek
                        Log.e("AddClientViewModel", "Transakcja: Błąd formatu daty paragonu: ${receiptData.receiptDate}")
                        throw DateFormatException()
                    }

                    // Parsowanie daty weryfikacji (tylko dla pierwszego paragonu, jeśli podano)
                    val verificationDate: Date? = if (i == 0 && !verificationDateString.isNullOrBlank()) {
                        try {
                            dateFormat.parse(verificationDateString) as Date
                        } catch (e: ParseException) {
                            // Jeśli format daty weryfikacji jest zły, zaloguj ostrzeżenie, ale nie przerywaj transakcji
                            Log.w("AddClientViewModel", "Transakcja: Błąd formatu daty weryfikacji (ignorowanie): $verificationDateString")
                            null
                        }
                    } else {
                        null // Dla pozostałych paragonów lub gdy nie podano daty weryfikacji
                    }

                    // Znajdź lub utwórz drogerię na podstawie numeru
                    var store = storeDao.getStoreByNumber(receiptData.storeNumber)
                    val storeId: Long
                    if (store == null) {
                        // Jeśli drogeria nie istnieje, utwórz nową
                        store = Store(storeNumber = receiptData.storeNumber)
                        storeDao.insertStore(store) // Wstaw nową drogerię
                        // Pobierz ponownie, aby uzyskać ID nowo wstawionej drogerii
                        store = storeDao.getStoreByNumber(receiptData.storeNumber)
                        if (store == null) {
                            // Jeśli nadal nie można pobrać, rzuć błąd
                            throw Exception("Nie udało się utworzyć lub pobrać drogerii: ${receiptData.storeNumber}")
                        }
                        storeId = store.id
                        Log.d("AddClientViewModel", "Transakcja: Dodano nową drogerię: ${receiptData.storeNumber}, ID: $storeId")
                    } else {
                        // Jeśli drogeria istnieje, użyj jej ID
                        storeId = store.id
                    }

                    // Walidacja duplikatów: Sprawdź, czy paragon o tym samym numerze, dacie i sklepie już istnieje
                    val existingReceipt = receiptDao.findByNumberDateStore(receiptData.receiptNumber, receiptDate, storeId)
                    if (existingReceipt != null) {
                        // Jeśli duplikat znaleziony, rzuć wyjątek
                        Log.e("AddClientViewModel", "Transakcja: Znaleziono duplikat paragonu: Nr ${receiptData.receiptNumber}, Data ${receiptData.receiptDate}, Sklep ID $storeId")
                        throw DuplicateReceiptException()
                    }

                    // Utwórz obiekt paragonu
                    val receipt = Receipt(
                        receiptNumber = receiptData.receiptNumber,
                        receiptDate = receiptDate,
                        storeId = storeId,
                        verificationDate = verificationDate,
                        clientId = clientId // Przypisz ID nowo utworzonego klienta
                    )
                    // Wstaw paragon do bazy
                    val receiptId = receiptDao.insertReceipt(receipt)
                    Log.d("AddClientViewModel", "Transakcja: Dodano paragon ID: $receiptId dla klienta ID: $clientId")
                }
                // Jeśli pętla zakończyła się bez błędów, cała transakcja jest gotowa do zatwierdzenia
            }
            // Jeśli transakcja została pomyślnie zatwierdzona, zwróć sukces
            AddResult.SUCCESS
        } catch (e: DateFormatException) {
            // Obsługa wyjątku błędu formatu daty
            AddResult.ERROR_DATE_FORMAT
        } catch (e: DuplicateReceiptException) {
            // Obsługa wyjątku duplikatu paragonu
            AddResult.ERROR_DUPLICATE_RECEIPT
        } catch (e: StoreNumberMissingException) {
            // Obsługa wyjątku braku numeru sklepu
            AddResult.ERROR_STORE_NUMBER_MISSING
        } catch (e: Exception) {
            // Obsługa innych błędów (np. błędy bazy danych)
            Log.e("AddClientViewModel", "Transakcja: Błąd bazy danych lub nieznany.", e)
            // Można by dodać bardziej szczegółowe logowanie lub rozróżnianie błędów Room
            AddResult.ERROR_DATABASE // Lub ERROR_UNKNOWN
        }
    }

    // Prywatne klasy wyjątków używane do sterowania przepływem w bloku transakcji
    private class DateFormatException : Exception()
    private class DuplicateReceiptException : Exception()
    private class StoreNumberMissingException : Exception()

}