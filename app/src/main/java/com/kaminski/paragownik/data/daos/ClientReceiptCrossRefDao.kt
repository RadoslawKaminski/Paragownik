package com.kaminski.paragownik.data.daos

import androidx.room.Dao
import androidx.room.Insert
import com.kaminski.paragownik.data.ClientReceiptCrossRef

@Dao
interface ClientReceiptCrossRefDao {
    @Insert
    suspend fun insert(crossRef: ClientReceiptCrossRef)
}