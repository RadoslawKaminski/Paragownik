package com.kaminski.paragownik.data.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.ReceiptWithClient
import kotlinx.coroutines.flow.Flow
import java.util.Date // Upewnij się, że Date jest zaimportowane

/**
 * Data Access Object (DAO) dla operacji na encji Paragon (Receipt).
 */
@Dao
interface ReceiptDao {
    /**
     * Wstawia nowy paragon do bazy danych.
     * @param receipt Obiekt paragonu do wstawienia.
     * @return ID nowo wstawionego paragonu.
     */
    @Insert
    suspend fun insertReceipt(receipt: Receipt): Long

    /**
     * Aktualizuje istniejący paragon w bazie danych.
     * @param receipt Obiekt paragonu z zaktualizowanymi danymi.
     */
    @Update
    suspend fun updateReceipt(receipt: Receipt)

    /**
     * Usuwa paragon z bazy danych.
     * @param receipt Obiekt paragonu do usunięcia.
     */
    @Delete
    suspend fun deleteReceipt(receipt: Receipt)

    /**
     * Pobiera paragon na podstawie jego ID.
     * @param receiptId ID paragonu do pobrania.
     * @return Obiekt paragonu lub null, jeśli nie znaleziono.
     */
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptById(receiptId: Long): Receipt?

    /**
     * Pobiera listę paragonów (wraz z danymi klienta) dla określonego sklepu jako Flow.
     * @param storeId ID sklepu, dla którego pobierane są paragony.
     * @return Flow emitujący listę obiektów ReceiptWithClient.
     */
    @Transaction // Zapewnia atomowość pobierania paragonu i klienta
    @Query("SELECT * FROM receipts WHERE storeId = :storeId")
    fun getReceiptsForStore(storeId: Long): Flow<List<ReceiptWithClient>>

    /**
     * Pobiera pojedynczy paragon wraz z danymi klienta jako Flow.
     * @param receiptId ID paragonu do pobrania.
     * @return Flow emitujący obiekt ReceiptWithClient.
     */
    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    fun getReceiptWithClientFlow(receiptId: Long): Flow<ReceiptWithClient> // Zwraca Flow<ReceiptWithClient?> jeśli może nie być klienta? Raczej nie, bo FK

    /**
     * Zlicza liczbę paragonów przypisanych do danego klienta.
     * @param clientId ID klienta.
     * @return Liczba paragonów klienta.
     */
    @Query("SELECT COUNT(*) FROM receipts WHERE clientId = :clientId")
    suspend fun getReceiptsForClientCount(clientId: Long): Int

    /**
     * Zlicza liczbę paragonów przypisanych do danego sklepu.
     * @param storeId ID sklepu.
     * @return Liczba paragonów w sklepie.
     */
    @Query("SELECT COUNT(*) FROM receipts WHERE storeId = :storeId")
    suspend fun getReceiptsForStoreCount(storeId: Long): Int

    /**
     * Pobiera pojedynczy paragon wraz z danymi klienta (operacja suspend).
     * @param receiptId ID paragonu do pobrania.
     * @return Obiekt ReceiptWithClient lub null, jeśli nie znaleziono.
     */
    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptWithClient(receiptId: Long): ReceiptWithClient?

    // --- NOWE ZAPYTANIA ---
    /**
     * Znajduje paragon na podstawie numeru, daty i ID sklepu.
     * Używane do sprawdzania duplikatów.
     * @param receiptNumber Numer paragonu.
     * @param receiptDate Data paragonu.
     * @param storeId ID sklepu.
     * @return Obiekt paragonu lub null, jeśli nie znaleziono.
     */
    @Query("SELECT * FROM receipts WHERE receiptNumber = :receiptNumber AND receiptDate = :receiptDate AND storeId = :storeId LIMIT 1")
    suspend fun findByNumberDateStore(receiptNumber: String, receiptDate: Date, storeId: Long): Receipt?

    /**
     * Pobiera listę unikalnych identyfikatorów sklepów (storeId) powiązanych z danym klientem.
     * Używane przy usuwaniu klienta do sprawdzenia, które drogerie mogą stać się puste.
     * @param clientId ID klienta.
     * @return Lista ID sklepów (Long).
     */
    @Query("SELECT DISTINCT storeId FROM receipts WHERE clientId = :clientId")
    suspend fun getStoreIdsForClient(clientId: Long): List<Long>
    // --- KONIEC NOWYCH ZAPYTAŃ ---
}