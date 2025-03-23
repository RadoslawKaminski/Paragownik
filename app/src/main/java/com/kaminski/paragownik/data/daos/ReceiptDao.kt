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
}