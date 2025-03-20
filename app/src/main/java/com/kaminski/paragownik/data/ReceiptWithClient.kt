package com.kaminski.paragownik.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ReceiptWithClient(
    @Embedded val receipt: Receipt,
    @Relation(
        parentColumn = "id",          // Poprawione: Klucz główny w Receipt to teraz 'id'
        entityColumn = "id",
        associateBy = Junction(
            value = ClientReceiptCrossRef::class,
            parentColumn = "receiptId", // Poprawione: Kolumna w ClientReceiptCrossRef to 'receiptId'
            entityColumn = "clientId"
        )
    )
    val clients: List<Client>
)