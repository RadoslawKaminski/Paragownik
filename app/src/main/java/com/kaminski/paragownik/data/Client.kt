package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Encja (tabela) reprezentująca klienta w bazie danych Room.
 * Przechowuje informacje identyfikujące i opisujące klienta.
 *
 * @property id Unikalny identyfikator klienta (klucz główny, generowany automatycznie).
 * @property description Krótki opis klienta, np. cechy wyglądu (opcjonalne, może być null).
 * @property clientAppNumber Numer aplikacji klienta (opcjonalne, może być null). Wprowadzony w v3.
 * @property amoditNumber Numer Amodit (opcjonalne, może być null). Wprowadzony w v3.
 * @property photoUri Ścieżka URI do zdjęcia klienta przechowywanego lokalnie (opcjonalne, może być null). Wprowadzony w v3.
 */
@Entity(tableName = "clients") // Nazwa tabeli w bazie danych SQLite
data class Client(
    @PrimaryKey(autoGenerate = true) // Klucz główny, którego wartość jest generowana automatycznie
    val id: Long = 0,

    val description: String?, // Kolumna na opis (TEXT, nullable)

    // --- NOWE POLA (dodane w wersji 3 bazy danych) ---
    val clientAppNumber: String? = null, // Kolumna na numer aplikacji (TEXT, nullable)
    val amoditNumber: String? = null,    // Kolumna na numer Amodit (TEXT, nullable)
    val photoUri: String? = null         // Kolumna na URI zdjęcia (TEXT, nullable)
    // --- KONIEC NOWYCH PÓL ---
)

