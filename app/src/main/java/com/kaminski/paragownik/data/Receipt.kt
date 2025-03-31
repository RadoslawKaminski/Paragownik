package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Encja (tabela) reprezentująca pojedynczy paragon w bazie danych Room.
 * Zawiera informacje o paragonie oraz klucze obce do powiązanych tabel Store i Client.
 *
 * @property id Unikalny identyfikator paragonu (klucz główny, generowany automatycznie).
 * @property receiptNumber Numer paragonu (String).
 * @property receiptDate Data wystawienia paragonu (Date).
 * @property storeId Klucz obcy wskazujący na sklep ([Store]), do którego należy paragon.
 * @property cashRegisterNumber Numer kasy, z której pochodzi paragon (opcjonalny, String).
 * @property verificationDate Data weryfikacji paragonu (opcjonalna, może być null).
 * @property clientId Klucz obcy wskazujący na klienta ([Client]), do którego przypisany jest paragon.
 */
@Entity(
    tableName = "receipts",
    indices = [Index("clientId"), Index("storeId")],
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE // Kaskadowe usuwanie paragonów przy usunięciu klienta
        )
    ]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val receiptNumber: String,
    val receiptDate: Date,
    val storeId: Long,
    val cashRegisterNumber: String? = null, // Dodano pole numeru kasy
    val verificationDate: Date? = null,
    val clientId: Long
)

