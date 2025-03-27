package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Encja (tabela) reprezentująca sklep (drogerię) w bazie danych Room.
 *
 * @property id Unikalny identyfikator sklepu (klucz główny, generowany automatycznie).
 * @property storeNumber Numer identyfikacyjny sklepu (String).
 */
@Entity(tableName = "stores") // Nazwa tabeli w bazie SQLite
data class Store(
    @PrimaryKey(autoGenerate = true) // Klucz główny, auto-inkrementowany
    val id: Long = 0,

    // Numer sklepu przechowywany jako String, zgodnie ze specyfikacją.
    // Można by dodać indeks, jeśli często wyszukujemy po numerze: @Index(value = ["storeNumber"], unique = true)
    val storeNumber: String
)

