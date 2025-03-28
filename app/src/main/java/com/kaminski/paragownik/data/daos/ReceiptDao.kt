package com.kaminski.paragownik.data.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction // Import Transaction do zapytań obejmujących relacje
import androidx.room.Update
import com.kaminski.paragownik.data.Receipt // Import encji Receipt
import com.kaminski.paragownik.data.ReceiptWithClient // Import klasy relacyjnej
import kotlinx.coroutines.flow.Flow // Import Flow do obserwacji zmian
import java.util.Date // Upewnij się, że Date jest zaimportowane

/**
 * Data Access Object (DAO) dla operacji na encji [Receipt] (paragony).
 * Definiuje metody dostępu do tabeli 'receipts' oraz powiązanych danych.
 */
@Dao
interface ReceiptDao {

    /**
     * Wstawia nowy paragon do bazy danych.
     * @param receipt Obiekt [Receipt] do wstawienia.
     * @return ID nowo wstawionego paragonu (Long).
     */
    @Insert
    suspend fun insertReceipt(receipt: Receipt): Long

    /**
     * Aktualizuje istniejący paragon w bazie danych.
     * Dopasowanie odbywa się na podstawie klucza głównego obiektu [receipt].
     * @param receipt Obiekt [Receipt] z zaktualizowanymi danymi.
     */
    @Update
    suspend fun updateReceipt(receipt: Receipt)

    /**
     * Usuwa paragon z bazy danych.
     * Dopasowanie odbywa się na podstawie klucza głównego obiektu [receipt].
     * @param receipt Obiekt [Receipt] do usunięcia.
     */
    @Delete
    suspend fun deleteReceipt(receipt: Receipt)

    /**
     * Pobiera paragon na podstawie jego ID.
     * Operacja jednorazowa (suspend).
     * @param receiptId ID paragonu do pobrania.
     * @return Obiekt [Receipt] lub `null`, jeśli nie znaleziono.
     */
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptById(receiptId: Long): Receipt?

    /**
     * Pobiera listę paragonów (wraz z danymi klienta) dla określonego sklepu jako [Flow].
     * Używa relacji [ReceiptWithClient] do pobrania powiązanych danych klienta.
     * Wyniki są sortowane według daty weryfikacji (NULLe na górze, potem rosnąco).
     * `@Transaction` zapewnia atomowość operacji pobierania danych z wielu tabel.
     * @param storeId ID sklepu, dla którego pobierane są paragony.
     * @return [Flow] emitujący listę obiektów [ReceiptWithClient].
     */
    @Transaction // Ważne przy pobieraniu relacji
    // Zapytanie pobierające paragony dla danego sklepu, posortowane wg daty weryfikacji.
    // NULLe (brak daty) są na górze (DESC), pozostałe daty rosnąco (ASC).
    @Query("SELECT * FROM receipts WHERE storeId = :storeId ORDER BY verificationDate IS NULL DESC, verificationDate ASC")
    fun getReceiptsForStore(storeId: Long): Flow<List<ReceiptWithClient>>

    /**
     * Pobiera pojedynczy paragon wraz z danymi powiązanego klienta jako [Flow].
     * Używa relacji [ReceiptWithClient].
     * `@Transaction` zapewnia atomowość.
     * @param receiptId ID paragonu do pobrania.
     * @return [Flow] emitujący obiekt [ReceiptWithClient]. Flow będzie emitował nową wartość
     *         przy zmianie danych paragonu lub powiązanego klienta.
     */
    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    fun getReceiptWithClientFlow(receiptId: Long): Flow<ReceiptWithClient?>

    /**
     * Zlicza liczbę paragonów przypisanych do danego klienta.
     * Używane do sprawdzania, czy klient stał się pusty po usunięciu paragonu.
     * @param clientId ID klienta.
     * @return Liczba paragonów (Int) przypisanych do klienta.
     */
    @Query("SELECT COUNT(*) FROM receipts WHERE clientId = :clientId")
    suspend fun getReceiptsForClientCount(clientId: Long): Int

    /**
     * Zlicza liczbę paragonów przypisanych do danego sklepu.
     * Używane do sprawdzania, czy sklep stał się pusty po usunięciu paragonu/klienta.
     * @param storeId ID sklepu.
     * @return Liczba paragonów (Int) w danym sklepie.
     */
    @Query("SELECT COUNT(*) FROM receipts WHERE storeId = :storeId")
    suspend fun getReceiptsForStoreCount(storeId: Long): Int

    /**
     * Pobiera pojedynczy paragon wraz z danymi klienta (operacja jednorazowa suspend).
     * Używa relacji [ReceiptWithClient].
     * `@Transaction` zapewnia atomowość.
     * @param receiptId ID paragonu do pobrania.
     * @return Obiekt [ReceiptWithClient] lub `null`, jeśli paragon o podanym ID nie został znaleziony.
     */
    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptWithClient(receiptId: Long): ReceiptWithClient?

    /**
     * Znajduje paragon na podstawie unikalnej kombinacji numeru, daty i ID sklepu.
     * Używane do sprawdzania duplikatów przed wstawieniem lub aktualizacją paragonu.
     * @param receiptNumber Numer paragonu.
     * @param receiptDate Data paragonu.
     * @param storeId ID sklepu.
     * @return Obiekt [Receipt] jeśli znaleziono pasujący paragon, lub `null` w przeciwnym razie.
     */
    @Query("SELECT * FROM receipts WHERE receiptNumber = :receiptNumber AND receiptDate = :receiptDate AND storeId = :storeId LIMIT 1")
    suspend fun findByNumberDateStore(receiptNumber: String, receiptDate: Date, storeId: Long): Receipt?

    /**
     * Pobiera listę unikalnych identyfikatorów sklepów (storeId), z którymi powiązane są paragony
     * danego klienta.
     * Używane w logice usuwania klienta, aby sprawdzić, które sklepy mogą stać się puste.
     * @param clientId ID klienta.
     * @return Lista unikalnych ID sklepów (List<Long>).
     */
    @Query("SELECT DISTINCT storeId FROM receipts WHERE clientId = :clientId")
    suspend fun getStoreIdsForClient(clientId: Long): List<Long>

    /**
     * Pobiera listę paragonów (wraz z danymi klienta) dla określonego klienta jako [Flow].
     * Używa relacji [ReceiptWithClient]. Wyniki są sortowane numerycznie rosnąco według numeru sklepu.
     * @param clientId ID klienta, dla którego pobierane są paragony.
     * @return [Flow] emitujący listę obiektów [ReceiptWithClient].
     */
    // Zapytanie pobierające paragony dla danego klienta, łącząc z tabelą sklepów,
    // aby posortować wyniki numerycznie rosnąco według numeru sklepu.
    @Transaction
    @Query("SELECT receipts.* FROM receipts INNER JOIN stores ON receipts.storeId = stores.id WHERE receipts.clientId = :clientId ORDER BY CAST(stores.storeNumber AS INTEGER) ASC")
    fun getReceiptsWithClientForClient(clientId: Long): Flow<List<ReceiptWithClient>>
}