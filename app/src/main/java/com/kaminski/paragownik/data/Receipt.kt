package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date // Import klasy Date

/**
 * Encja (tabela) reprezentująca pojedynczy paragon w bazie danych Room.
 * Zawiera informacje o paragonie oraz klucze obce do powiązanych tabel Store i Client.
 *
 * @property id Unikalny identyfikator paragonu (klucz główny, generowany automatycznie).
 * @property receiptNumber Numer paragonu (String).
 * @property receiptDate Data wystawienia paragonu (Date).
 * @property storeId Klucz obcy wskazujący na sklep ([Store]), do którego należy paragon.
 * @property verificationDate Data weryfikacji paragonu (opcjonalna, może być null).
 * @property clientId Klucz obcy wskazujący na klienta ([Client]), do którego przypisany jest paragon.
 */
@Entity(
    tableName = "receipts", // Nazwa tabeli w bazie SQLite
    // Definicja indeksu na kolumnie clientId dla szybszego wyszukiwania paragonów klienta.
    indices = [Index("clientId"), Index("storeId")], // Dodano indeks dla storeId
    // Definicja klucza obcego do tabeli 'clients'.
    foreignKeys = [
        ForeignKey(
            entity = Client::class,         // Encja nadrzędna (tabela 'clients')
            parentColumns = ["id"],         // Kolumna klucza głównego w tabeli nadrzędnej ('clients')
            childColumns = ["clientId"],    // Kolumna klucza obcego w tej tabeli ('receipts')
            onDelete = ForeignKey.CASCADE   // Akcja przy usunięciu klienta: KASKADOWO usuń powiązane paragony.
        )
        // Można by dodać klucz obcy do Store, ale logika usuwania pustych sklepów jest
        // realizowana ręcznie w ViewModelu, więc CASCADE nie jest tu konieczne.
        // ForeignKey(entity = Store::class, parentColumns = ["id"], childColumns = ["storeId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) // Klucz główny, auto-inkrementowany
    val id: Long = 0,

    val receiptNumber: String, // Numer paragonu (TEXT)
    val receiptDate: Date,     // Data paragonu (INTEGER - przechowywany jako timestamp dzięki konwerterowi)
    val storeId: Long,         // Klucz obcy do tabeli 'stores' (INTEGER)
    val verificationDate: Date? = null, // Data weryfikacji (INTEGER/NULL - timestamp)
    val clientId: Long         // Klucz obcy do tabeli 'clients' (INTEGER)
)

