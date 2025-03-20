package com.kaminski.paragownik.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.ReceiptWithClient
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Insert
    suspend fun insertReceipt(receipt: Receipt): Long

    @Transaction // Dodaj @Transaction
    @Query("SELECT * FROM receipts WHERE storeId = :storeId")
    fun getReceiptsForStore(storeId: Long): Flow<List<ReceiptWithClient>> // Zmień typ zwracany na Flow<List<ReceiptWithClient>>

    @Query("SELECT * FROM receipts WHERE receiptNumber = :receiptNumber")
    suspend fun getReceiptByNumber(receiptNumber: String): Receipt?

    // Możesz dodać więcej metod, np. do aktualizacji, usuwania, wyszukiwania, jeśli będą potrzebne
}