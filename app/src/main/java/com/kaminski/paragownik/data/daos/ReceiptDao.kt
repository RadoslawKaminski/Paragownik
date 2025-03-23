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

@Dao
interface ReceiptDao {
    @Insert
    suspend fun insertReceipt(receipt: Receipt): Long

    @Update
    suspend fun updateReceipt(receipt: Receipt)

    @Delete // Dodana funkcja deleteReceipt
    suspend fun deleteReceipt(receipt: Receipt)

    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptById(receiptId: Long): Receipt?

    @Transaction // Dodaj @Transaction
    @Query("SELECT * FROM receipts WHERE storeId = :storeId")
    fun getReceiptsForStore(storeId: Long): Flow<List<ReceiptWithClient>>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    fun getReceiptWithClientFlow(receiptId: Long): Flow<ReceiptWithClient>

    @Query("SELECT COUNT(*) FROM receipts WHERE clientId = :clientId") // Dodana funkcja
    suspend fun getReceiptsForClientCount(clientId: Long): Int

    @Query("SELECT COUNT(*) FROM receipts WHERE storeId = :storeId") // Dodana funkcja
    suspend fun getReceiptsForStoreCount(storeId: Long): Int

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptWithClient(receiptId: Long): ReceiptWithClient? // Dodana funkcja suspend
}