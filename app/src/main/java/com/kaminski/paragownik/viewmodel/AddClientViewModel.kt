package com.kaminski.paragownik.viewmodel

// import com.kaminski.paragownik.AddClientActivity // Nieużywany bezpośrednio, ale ReceiptData jest z niego
// import kotlinx.coroutines.launch // Nieużywany
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
import com.kaminski.paragownik.AddClientActivity.ReceiptData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
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
 * wraz z jednym lub wieloma paragonami do bazy danych.
 * Wykorzystuje transakcje Room do zapewnienia atomowości operacji.
 */
class AddClientViewModel(application: Application) : AndroidViewModel(application) {

    // Referencje do bazy danych i DAO
    private val database: AppDatabase
    private val receiptDao: ReceiptDao
    private val clientDao: ClientDao
    private val storeDao: StoreDao

    /**
     * Enum reprezentujący możliwy wynik operacji dodawania klienta i paragonów.
     * Używany do komunikacji wyniku (sukcesu lub konkretnego błędu) z Aktywnością (UI).
     */
    enum class AddResult {
        SUCCESS,                    // Operacja zakończona sukcesem
        ERROR_DATE_FORMAT,          // Błąd formatu daty w jednym z paragonów
        ERROR_DUPLICATE_RECEIPT,    // Próba dodania paragonu, który już istnieje (ten sam nr, data, sklep)
        ERROR_STORE_NUMBER_MISSING, // Brak numeru sklepu dla jednego z paragonów
        ERROR_DATABASE,             // Ogólny błąd podczas operacji bazodanowej
        ERROR_UNKNOWN               // Nieznany błąd (np. nieprzewidziany wyjątek)
    }

    init {
        // Inicjalizacja bazy danych i DAO przy tworzeniu ViewModelu
        database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()
        clientDao = database.clientDao()
        storeDao = database.storeDao()
    }

    /**
     * Dodaje nowego klienta wraz z listą powiązanych paragonów w jednej transakcji bazodanowej.
     * Przeprowadza walidację formatu daty, unikalności paragonów oraz istnienia sklepu (tworzy go, jeśli nie istnieje).
     * Jeśli jakikolwiek krok wewnątrz transakcji zawiedzie (np. przez rzucenie wyjątku),
     * cała transakcja jest automatycznie wycofywana (rollback).
     *
     * @param clientDescription Opis klienta (może być null lub pusty).
     * @param clientAppNumber Numer aplikacji klienta (może być null lub pusty).
     * @param amoditNumber Numer Amodit klienta (może być null lub pusty).
     * @param photoUri URI zdjęcia klienta (obecnie zawsze null, może być null lub pusty).
     * @param receiptsData Lista obiektów [ReceiptData] zawierających dane paragonów do dodania.
     * @param verificationDateString Data weryfikacji dla pierwszego paragonu (jako String, może być null lub pusta).
     * @return [AddResult] Enum wskazujący wynik operacji (sukces lub typ błędu).
     */
    suspend fun addClientWithReceiptsTransactionally(
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?,
        photoUri: String?,
        receiptsData: List<ReceiptData>,
        verificationDateString: String?
    ): AddResult = withContext(Dispatchers.IO) { // Wykonaj całą operację w tle (IO dispatcher)
        // Formatter daty ze ścisłym sprawdzaniem formatu (DD-MM-YYYY)
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        dateFormat.isLenient = false // Nie akceptuj nieprawidłowych dat (np. 32-13-2024)

        try {
            // Rozpocznij transakcję bazodanową. Wszystkie operacje wewnątrz tego bloku
            // zostaną wykonane atomowo.
            database.withTransaction {
                // --- Krok 1: Dodanie Klienta ---
                // Utwórz obiekt klienta, zapisując tylko niepuste wartości dla numerów i opisu.
                val client = Client(
                    description = clientDescription?.takeIf { it.isNotBlank() },
                    clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                    amoditNumber = amoditNumber?.takeIf { it.isNotBlank() },
                    photoUri = photoUri?.takeIf { it.isNotBlank() } // Na razie photoUri jest zawsze null
                )
                // Wstaw klienta do bazy i pobierz jego nowo wygenerowane ID
                val clientId = clientDao.insertClient(client)
                Log.d("AddClientViewModel", "Transakcja: Dodano klienta ID: $clientId")

                // Sprawdź, czy wstawienie klienta się powiodło (insert zwraca -1 przy błędzie)
                if (clientId == -1L) {
                    // Jeśli nie, rzuć wyjątek, aby wycofać transakcję
                    throw DatabaseException("Błąd wstawiania klienta do bazy danych.")
                }

                // --- Krok 2: Przetwarzanie i dodawanie Paragonów ---
                // Iteruj przez listę danych paragonów przekazanych z Aktywności
                for (i in receiptsData.indices) {
                    val receiptData = receiptsData[i]

                    // Walidacja: Sprawdź, czy numer drogerii nie jest pusty
                    if (receiptData.storeNumber.isBlank()) {
                        Log.e("AddClientViewModel", "Transakcja: Brak numeru drogerii dla paragonu: ${receiptData.receiptNumber}")
                        throw StoreNumberMissingException() // Rzuć wyjątek, aby wycofać transakcję
                    }

                    // Parsowanie daty paragonu ze Stringa na obiekt Date
                    val receiptDate: Date = try {
                        dateFormat.parse(receiptData.receiptDate) as Date // Rzutowanie na Date jest bezpieczne po udanym parsowaniu
                    } catch (e: ParseException) {
                        // Jeśli format daty jest nieprawidłowy, rzuć wyjątek DateFormatException
                        Log.e("AddClientViewModel", "Transakcja: Błąd formatu daty paragonu: ${receiptData.receiptDate}")
                        throw DateFormatException()
                    }

                    // Parsowanie daty weryfikacji (tylko dla pierwszego paragonu i jeśli podano)
                    val verificationDate: Date? = if (i == 0 && !verificationDateString.isNullOrBlank()) {
                        try {
                            dateFormat.parse(verificationDateString) as Date
                        } catch (e: ParseException) {
                            // Jeśli format daty weryfikacji jest zły, zaloguj ostrzeżenie, ale nie przerywaj transakcji.
                            // Data weryfikacji jest opcjonalna, więc błąd formatu nie powinien blokować zapisu.
                            Log.w("AddClientViewModel", "Transakcja: Błąd formatu daty weryfikacji (ignorowanie): $verificationDateString")
                            null // Zignoruj błędną datę
                        }
                    } else {
                        null // Dla pozostałych paragonów lub gdy data weryfikacji nie została podana
                    }

                    // --- Krok 2a: Obsługa Sklepu (Drogerii) ---
                    // Sprawdź, czy sklep o podanym numerze już istnieje w bazie
                    var store = storeDao.getStoreByNumber(receiptData.storeNumber)
                    val storeId: Long
                    if (store == null) {
                        // Jeśli sklep nie istnieje, utwórz nowy obiekt Store
                        store = Store(storeNumber = receiptData.storeNumber)
                        // Wstaw nowy sklep do bazy
                        storeDao.insertStore(store)
                        // Pobierz sklep ponownie, aby uzyskać jego ID (po wstawieniu)
                        // To jest konieczne, bo insertStore nie zwraca ID
                        store = storeDao.getStoreByNumber(receiptData.storeNumber)
                        if (store == null) {
                            // Jeśli nadal nie można pobrać (co nie powinno się zdarzyć), rzuć błąd
                            throw DatabaseException("Nie udało się utworzyć lub pobrać drogerii: ${receiptData.storeNumber}")
                        }
                        storeId = store.id // Pobierz ID nowo utworzonego sklepu
                        Log.d("AddClientViewModel", "Transakcja: Dodano nową drogerię: ${receiptData.storeNumber}, ID: $storeId")
                    } else {
                        // Jeśli sklep istnieje, użyj jego istniejącego ID
                        storeId = store.id
                    }

                    // --- Krok 2b: Walidacja Duplikatów Paragonów ---
                    // Sprawdź, czy paragon o tym samym numerze, dacie i sklepie już istnieje w bazie
                    val existingReceipt = receiptDao.findByNumberDateStore(receiptData.receiptNumber, receiptDate, storeId)
                    if (existingReceipt != null) {
                        // Jeśli duplikat został znaleziony, rzuć wyjątek DuplicateReceiptException
                        Log.e("AddClientViewModel", "Transakcja: Znaleziono duplikat paragonu: Nr ${receiptData.receiptNumber}, Data ${receiptData.receiptDate}, Sklep ID $storeId")
                        throw DuplicateReceiptException()
                    }

                    // --- Krok 2c: Dodanie Paragonu ---
                    // Utwórz obiekt paragonu z zebranymi i przetworzonymi danymi
                    val receipt = Receipt(
                        receiptNumber = receiptData.receiptNumber,
                        receiptDate = receiptDate,
                        storeId = storeId, // Użyj ID znalezionego lub utworzonego sklepu
                        verificationDate = verificationDate, // Użyj sparsowanej daty weryfikacji (lub null)
                        clientId = clientId // Przypisz ID nowo utworzonego klienta
                    )
                    // Wstaw paragon do bazy
                    val receiptId = receiptDao.insertReceipt(receipt)
                    Log.d("AddClientViewModel", "Transakcja: Dodano paragon ID: $receiptId dla klienta ID: $clientId")
                    // Sprawdzenie `receiptId == -1L` nie jest tu konieczne, bo jeśli insertReceipt zawiedzie, rzuci wyjątek SQL, który zostanie złapany niżej.
                }
                // Jeśli pętla po paragonach zakończyła się bez błędów (bez rzucenia wyjątku),
                // transakcja zostanie automatycznie zatwierdzona (commit) po wyjściu z bloku `withTransaction`.
            }
            // Jeśli transakcja została pomyślnie zatwierdzona, zwróć sukces
            AddResult.SUCCESS
        } catch (e: DateFormatException) {
            // Obsługa wyjątku błędu formatu daty paragonu
            AddResult.ERROR_DATE_FORMAT
        } catch (e: DuplicateReceiptException) {
            // Obsługa wyjątku duplikatu paragonu
            AddResult.ERROR_DUPLICATE_RECEIPT
        } catch (e: StoreNumberMissingException) {
            // Obsługa wyjątku braku numeru sklepu
            AddResult.ERROR_STORE_NUMBER_MISSING
        } catch (e: DatabaseException) {
            // Obsługa ogólnych błędów bazy danych zdefiniowanych przez nas
            Log.e("AddClientViewModel", "Transakcja: Błąd bazy danych.", e)
            AddResult.ERROR_DATABASE
        }
        catch (e: Exception) {
            // Obsługa wszystkich innych, nieprzewidzianych błędów (np. błędy SQLite zgłaszane przez Room)
            Log.e("AddClientViewModel", "Transakcja: Nieznany błąd.", e)
            // Można by tu dodać bardziej szczegółowe logowanie lub rozróżnianie błędów Room
            AddResult.ERROR_UNKNOWN // Lub ERROR_DATABASE, zależnie od preferencji
        }
    }

    // Prywatne klasy wyjątków używane do sygnalizowania konkretnych błędów
    // wewnątrz bloku transakcji i sterowania przepływem w bloku catch.
    private class DateFormatException : Exception()
    private class DuplicateReceiptException : Exception()
    private class StoreNumberMissingException : Exception()
    private class DatabaseException(message: String) : Exception(message) // Ogólny błąd DB

}
