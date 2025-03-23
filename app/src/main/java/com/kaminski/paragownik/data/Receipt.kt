package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "receipts",
    indices = [Index("clientId")],
    foreignKeys = [ForeignKey(
        entity = Client::class,
        parentColumns = ["id"],
        childColumns = ["clientId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNumber: String,
    val receiptDate: Date,
    val storeId: Long,
    val verificationDate: Date? = null,
    val clientId: Long
)