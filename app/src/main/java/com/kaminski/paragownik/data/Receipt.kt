package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "receipts",
    indices = [Index("clientId")] // Indeks na clientId zamiast storeId
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNumber: String,
    val receiptDate: Date,
    val storeId: Long, // Pozostawiamy storeId, relacja do drogerii zostaje
    val verificationDate: Date? = null,
    val clientId: Long // Dodana kolumna clientId
)