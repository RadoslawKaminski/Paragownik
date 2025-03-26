package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Encja reprezentująca klienta (potencjalnego sprawcę).
 */
@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // Unikalny identyfikator klienta
    val description: String?, // Krótki opis klienta, np. cechy wyglądu (opcjonalne)

    // --- NOWE POLA ---
    val clientAppNumber: String? = null, // Numer aplikacji klienta (opcjonalne)
    val amoditNumber: String? = null,    // Numer Amodit (opcjonalne)
    val photoUri: String? = null         // Ścieżka URI do zdjęcia klienta (opcjonalne)
    // --- KONIEC NOWYCH PÓL ---
)