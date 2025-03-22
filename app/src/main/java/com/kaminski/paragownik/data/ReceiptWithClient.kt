package com.kaminski.paragownik.data

import androidx.room.Embedded
import androidx.room.Relation

data class ReceiptWithClient(
    @Embedded val receipt: Receipt,
    @Relation(
        parentColumn = "clientId", // Teraz parentColumn to clientId w Receipt
        entityColumn = "id" ,      // entityColumn to id w Client
        entity = Client::class // Określ, że relacja jest do encji Client
    )
    val client: Client? // Zmieniamy List<Client> na Client?, bo paragon ma tylko jednego klienta
)