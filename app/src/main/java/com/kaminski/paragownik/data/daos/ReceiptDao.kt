package com.kaminski.paragownik.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kaminski.paragownik.data.Receipt
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Insert
    suspend fun insertReceipt(receipt: Receipt)

    @Query("SELECT * FROM receipts WHERE storeId = :storeId")
    fun getReceiptsForStore(storeId: Long): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE receiptNumber = :receiptNumber")
    suspend fun getReceiptByNumber(receiptNumber: String): Receipt?
}