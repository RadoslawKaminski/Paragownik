package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Encja (tabela) reprezentująca sklep (drogerię) w bazie danych Room.
 *
 * @property id Unikalny identyfikator sklepu (klucz główny, generowany automatycznie).
 * @property storeNumber Numer identyfikacyjny sklepu (String). W UI ograniczony do 4 cyfr, ale baza przechowuje jako String.
 */
@Entity(tableName = "stores") // Nazwa tabeli w bazie SQLite
data class Store(
    @PrimaryKey(autoGenerate = true) // Klucz główny, auto-inkrementowany
    val id: Long = 0,

    // Numer sklepu przechowywany jako String.
    // Sortowanie numeryczne jest realizowane przez CAST w zapytaniach DAO.
    // Można by dodać indeks, jeśli często wyszukujemy po numerze: @Index(value = ["storeNumber"], unique = true)
    // Unikalność numeru sklepu nie jest wymuszana na poziomie bazy danych.
    val storeNumber: String
)