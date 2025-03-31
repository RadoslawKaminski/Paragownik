package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Encja (tabela) reprezentująca klienta w bazie danych Room.
 * Przechowuje informacje identyfikujące i opisujące klienta.
 *
 * @property id Unikalny identyfikator klienta (klucz główny, generowany automatycznie).
 * @property description Krótki opis klienta, np. cechy wyglądu (opcjonalne, może być null).
 * @property clientAppNumber Numer aplikacji klienta (opcjonalne, może być null).
 * @property amoditNumber Numer Amodit (opcjonalne, może być null).
 */
@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val description: String?,

    val clientAppNumber: String? = null,
    val amoditNumber: String? = null
)



