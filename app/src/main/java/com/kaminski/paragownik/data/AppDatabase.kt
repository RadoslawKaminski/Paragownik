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
import com.kaminski.paragownik.data.daos.PhotoDao // Dodano import PhotoDao
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
// Zwiększona wersja bazy danych do 5 po dodaniu tabeli Photo i usunięciu photoUri z Client
@Database(entities = [Store::class, Receipt::class, Client::class, Photo::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class) // Rejestracja konwerterów typów (Date, PhotoType)
abstract class AppDatabase : RoomDatabase() {

    // Abstrakcyjne metody dostarczające instancje DAO dla każdej encji.
    // Room automatycznie wygeneruje implementacje tych metod.
    abstract fun storeDao(): StoreDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun clientDao(): ClientDao
    abstract fun photoDao(): PhotoDao // <-- DODANO DAO DLA ZDJĘĆ

    // Obiekt towarzyszący (companion object) do implementacji wzorca Singleton
    // oraz przechowywania definicji migracji.
    companion object {
        // Instancja Singleton bazy danych. @Volatile zapewnia widoczność zmian między wątkami.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // --- DEFINICJA MIGRACJI Z WERSJI 2 DO 3 ---
        /**
         * Obiekt migracji z wersji 2 do 3 schematu bazy danych.
         * Dodaje nowe kolumny (`clientAppNumber`, `amoditNumber`, `photoUri`) do tabeli `clients`.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clients ADD COLUMN clientAppNumber TEXT")
                db.execSQL("ALTER TABLE clients ADD COLUMN amoditNumber TEXT")
                db.execSQL("ALTER TABLE clients ADD COLUMN photoUri TEXT")
            }
        }
        // --- KONIEC DEFINICJI MIGRACJI 2 -> 3 ---

        // --- DEFINICJA MIGRACJI Z WERSJI 3 DO 4 ---
        /**
         * Migracja z wersji 3 do 4.
         * Dodaje indeks do kolumny 'storeId' w tabeli 'receipts'.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_storeId ON receipts (storeId)")
                Log.i("AppDatabaseMigration", "Migracja 3->4: Dodano indeks index_receipts_storeId.")
            }
        }
        // --- KONIEC DEFINICJI MIGRACJI 3 -> 4 ---

        // --- DEFINICJA MIGRACJI Z WERSJI 4 DO 5 (Z PRZENOSZENIEM DANYCH) ---
        /**
         * Migracja z wersji 4 do 5.
         * 1. Tworzy nową tabelę 'photos'.
         * 2. Odczytuje 'id' i 'photoUri' ze starej tabeli 'clients'.
         * 3. Dla każdego klienta z niepustym 'photoUri', wstawia wpis do tabeli 'photos'.
         * 4. Tworzy tymczasową tabelę 'clients_new' bez kolumny 'photoUri'.
         * 5. Kopiuje dane z 'clients' do 'clients_new'.
         * 6. Usuwa starą tabelę 'clients'.
         * 7. Zmienia nazwę 'clients_new' na 'clients'.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i("AppDatabaseMigration", "Rozpoczęcie migracji 4->5")

                // 1. Utwórz nową tabelę 'photos'
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `photos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `clientId` INTEGER NOT NULL,
                        `uri` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `addedTimestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                Log.i("AppDatabaseMigration", "Migracja 4->5: Utworzono tabelę 'photos'.")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_clientId` ON `photos` (`clientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_type` ON `photos` (`type`)")
                Log.i("AppDatabaseMigration", "Migracja 4->5: Dodano indeksy do tabeli 'photos'.")

                // 2. Odczytaj 'id' i 'photoUri' ze starej tabeli 'clients' i wstaw do 'photos'
                Log.i("AppDatabaseMigration", "Migracja 4->5: Rozpoczęcie przenoszenia photoUri do tabeli photos.")
                val cursor = db.query("SELECT id, photoUri FROM clients WHERE photoUri IS NOT NULL AND photoUri != ''")
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex("id")
                    val uriIndex = cursor.getColumnIndex("photoUri")
                    val currentTime = System.currentTimeMillis() // Użyjemy tego samego timestampu dla wszystkich migrowanych zdjęć

                    // Sprawdź, czy kolumny istnieją (zabezpieczenie)
                    if (idIndex >= 0 && uriIndex >= 0) {
                        do {
                            val clientId = cursor.getLong(idIndex)
                            val photoUri = cursor.getString(uriIndex)
                            // Wstawiamy do nowej tabeli photos
                            db.execSQL("""
                                INSERT INTO photos (clientId, uri, type, addedTimestamp)
                                VALUES (?, ?, ?, ?)
                            """.trimIndent(), arrayOf(clientId, photoUri, PhotoType.CLIENT.name, currentTime))
                            Log.d("AppDatabaseMigration", "Migracja 4->5: Przeniesiono zdjęcie dla klienta ID: $clientId, URI: $photoUri")
                        } while (cursor.moveToNext())
                    } else {
                        Log.e("AppDatabaseMigration", "Migracja 4->5: Nie znaleziono kolumn 'id' lub 'photoUri' w starej tabeli 'clients'.")
                    }
                }
                cursor.close()
                Log.i("AppDatabaseMigration", "Migracja 4->5: Zakończono przenoszenie photoUri.")

                // 4. Utwórz tymczasową tabelę 'clients_new' bez kolumny 'photoUri'
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `clients_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `description` TEXT,
                        `clientAppNumber` TEXT,
                        `amoditNumber` TEXT
                    )
                """.trimIndent())
                Log.i("AppDatabaseMigration", "Migracja 4->5: Utworzono tabelę tymczasową 'clients_new'.")

                // 5. Skopiuj dane z 'clients' do 'clients_new' (pomijając 'photoUri')
                db.execSQL("""
                    INSERT INTO `clients_new` (`id`, `description`, `clientAppNumber`, `amoditNumber`)
                    SELECT `id`, `description`, `clientAppNumber`, `amoditNumber` FROM `clients`
                """.trimIndent())
                Log.i("AppDatabaseMigration", "Migracja 4->5: Skopiowano dane do 'clients_new'.")

                // 6. Usuń starą tabelę 'clients'
                db.execSQL("DROP TABLE `clients`")
                Log.i("AppDatabaseMigration", "Migracja 4->5: Usunięto starą tabelę 'clients'.")

                // 7. Zmień nazwę 'clients_new' na 'clients'
                db.execSQL("ALTER TABLE `clients_new` RENAME TO `clients`")
                Log.i("AppDatabaseMigration", "Migracja 4->5: Zmieniono nazwę 'clients_new' na 'clients'.")

                Log.i("AppDatabaseMigration", "Zakończono migrację 4->5")
            }
        }
        // --- KONIEC DEFINICJI MIGRACJI 4 -> 5 ---

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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5) // Dodano MIGRATION_4_5
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