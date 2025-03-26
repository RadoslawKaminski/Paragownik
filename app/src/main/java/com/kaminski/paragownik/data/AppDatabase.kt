package com.kaminski.paragownik.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration // Import Migration
import androidx.sqlite.db.SupportSQLiteDatabase // Import SupportSQLiteDatabase
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao

/**
 * Główna klasa bazy danych Room dla aplikacji Paragownik.
 * Definiuje encje, wersję bazy oraz dostarcza dostęp do DAO.
 */
// Zwiększ wersję bazy danych do 3
@Database(entities = [Store::class, Receipt::class, Client::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // Abstrakcyjne metody dostarczające instancje DAO
    abstract fun storeDao(): StoreDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun clientDao(): ClientDao

    companion object {
        // Instancja Singleton bazy danych
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // --- NOWA MIGRACJA ---
        /**
         * Migracja z wersji 2 do 3.
         * Dodaje kolumny clientAppNumber, amoditNumber, photoUri do tabeli clients.
         * Nowe kolumny będą miały wartość NULL dla istniejących wierszy.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Dodaj kolumnę na numer aplikacji klienta (tekst, może być null)
                db.execSQL("ALTER TABLE clients ADD COLUMN clientAppNumber TEXT")
                // Dodaj kolumnę na numer Amodit (tekst, może być null)
                db.execSQL("ALTER TABLE clients ADD COLUMN amoditNumber TEXT")
                // Dodaj kolumnę na URI zdjęcia (tekst, może być null)
                db.execSQL("ALTER TABLE clients ADD COLUMN photoUri TEXT")
            }
        }
        // --- KONIEC NOWEJ MIGRACJI ---

        /**
         * Zwraca instancję Singleton bazy danych.
         * Tworzy bazę danych, jeśli jeszcze nie istnieje.
         * @param context Kontekst aplikacji.
         * @return Instancja AppDatabase.
         */
        fun getDatabase(context: Context): AppDatabase {
            // Zwróć istniejącą instancję lub stwórz nową w bloku synchronized
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database" // Nazwa pliku bazy danych
                )
                    // Dodaj migracje zamiast niszczenia bazy przy zmianie wersji
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance // Przypisz nowo utworzoną instancję
                instance // Zwróć instancję
            }
        }
    }
}