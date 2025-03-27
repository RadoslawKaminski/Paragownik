@file:Suppress("KDocUnresolvedReference")

package com.kaminski.paragownik.data

import android.content.Context
import android.util.Log // Dodano import dla Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao

/**
 * Główna klasa bazy danych Room dla aplikacji Paragownik.
 * Definiuje encje wchodzące w skład bazy, wersję schematu bazy danych
 * oraz dostarcza dostęp do obiektów DAO (Data Access Objects).
 *
 * @property entities Lista klas encji (tabel) w bazie danych.
 * @property version Numer wersji schematu bazy danych. Należy go zwiększać przy każdej zmianie schematu.
 * @property exportSchema Czy eksportować schemat bazy do pliku JSON (przydatne do testów i dokumentacji).
 */
// Zwiększona wersja bazy danych do 4 po dodaniu indeksu do tabeli Receipt
@Database(entities = [Store::class, Receipt::class, Client::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class) // Rejestracja konwerterów typów (np. dla Date)
abstract class AppDatabase : RoomDatabase() {

    // Abstrakcyjne metody dostarczające instancje DAO dla każdej encji.
    // Room automatycznie wygeneruje implementacje tych metod.
    abstract fun storeDao(): StoreDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun clientDao(): ClientDao

    // Obiekt towarzyszący (companion object) do implementacji wzorca Singleton
    // oraz przechowywania definicji migracji.
    companion object {
        // Instancja Singleton bazy danych. @Volatile zapewnia widoczność zmian między wątkami.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // --- DEFINICJA MIGRACJI Z WERSJI 2 DO 3 ---
        /**
         * Obiekt migracji z wersji 2 do 3 schematu bazy danych.
         * Wykonywany, gdy aplikacja jest aktualizowana na urządzeniu z bazą w wersji 2.
         * Dodaje nowe kolumny (`clientAppNumber`, `amoditNumber`, `photoUri`) do tabeli `clients`.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Wykonaj polecenia SQL ALTER TABLE, aby dodać nowe kolumny.
                // Nowe kolumny będą miały wartość NULL dla istniejących wierszy.
                db.execSQL("ALTER TABLE clients ADD COLUMN clientAppNumber TEXT") // Numer aplikacji klienta (tekst, nullable)
                db.execSQL("ALTER TABLE clients ADD COLUMN amoditNumber TEXT")    // Numer Amodit (tekst, nullable)
                db.execSQL("ALTER TABLE clients ADD COLUMN photoUri TEXT")         // URI zdjęcia (tekst, nullable)
            }
        }
        // --- KONIEC DEFINICJI MIGRACJI 2 -> 3 ---

        // --- DEFINICJA MIGRACJI Z WERSJI 3 DO 4 ---
        /**
         * Migracja z wersji 3 do 4.
         * Dodaje indeks do kolumny 'storeId' w tabeli 'receipts'
         * w celu potencjalnego przyspieszenia zapytań filtrujących po sklepie.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Dodaj indeks dla kolumny storeId w tabeli receipts
                // IF NOT EXISTS zapobiega błędowi, jeśli indeks jakimś cudem już istnieje
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_storeId ON receipts (storeId)")
                Log.i("AppDatabaseMigration", "Migracja 3->4: Dodano indeks index_receipts_storeId.") // Opcjonalny log
            }
        }
        // --- KONIEC DEFINICJI MIGRACJI 3 -> 4 ---

        /**
         * Zwraca instancję Singleton bazy danych [AppDatabase].
         * Jeśli instancja nie istnieje, tworzy ją w sposób bezpieczny wątkowo.
         * Konfiguruje budowniczego bazy danych, dodając niezbędne migracje.
         * @param context Kontekst aplikacji (najlepiej ApplicationContext, aby uniknąć wycieków pamięci).
         * @return Instancja [AppDatabase].
         */
        fun getDatabase(context: Context): AppDatabase {
            // Zwróć istniejącą instancję, jeśli już istnieje.
            return INSTANCE ?: synchronized(this) {
                // Jeśli instancja nie istnieje, wejdź do bloku synchronizowanego,
                // aby tylko jeden wątek mógł ją utworzyć.
                val instance = Room.databaseBuilder(
                    context.applicationContext, // Użyj ApplicationContext
                    AppDatabase::class.java,    // Klasa bazy danych
                    "app_database"              // Nazwa pliku bazy danych SQLite
                )
                    // Dodaj zdefiniowane migracje do budowniczego.
                    // Room użyje odpowiedniej migracji w zależności od starej i nowej wersji bazy.
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4) // Dodano MIGRATION_3_4
                    // Zbuduj instancję bazy danych.
                    .build()
                // Przypisz nowo utworzoną instancję do INSTANCE.
                INSTANCE = instance
                // Zwróć instancję.
                instance
            }
        }
    }
}
