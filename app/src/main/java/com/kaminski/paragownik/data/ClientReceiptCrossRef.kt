package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "client_receipt_cross_ref",
    primaryKeys = ["clientId", "receiptId"], // Zmień na receiptId
    indices = [Index("receiptId")], // Dodaj indeks na receiptId
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"], // Zmień na id (klucz główny Receipt)
            childColumns = ["receiptId"], // Zmień na receiptId
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ClientReceiptCrossRef(
    val clientId: Long,
    val receiptId: Long // Zmień na receiptId
)