package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "receipts",
    indices = [Index("storeId")], // Dodaj indeks na storeId
    foreignKeys = [ForeignKey(
        entity = Store::class,
        parentColumns = ["id"],
        childColumns = ["storeId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNumber: String,
    val receiptDate: Date,
    val storeId: Long,
    val verificationDate: Date? = null
)