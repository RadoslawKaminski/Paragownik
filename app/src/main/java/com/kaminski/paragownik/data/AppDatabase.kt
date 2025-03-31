package com.kaminski.paragownik.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.PhotoDao
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
@Database(entities = [Store::class, Receipt::class, Client::class, Photo::class], version = 6, exportSchema = false) // Zwiększono wersję do 6
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // Abstrakcyjne metody dostarczające instancje DAO dla każdej encji.
    abstract fun storeDao(): StoreDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun clientDao(): ClientDao
    abstract fun photoDao(): PhotoDao

    // Obiekt towarzyszący (companion object) do implementacji wzorca Singleton
    // oraz przechowywania definicji migracji.
    companion object {
        // Instancja Singleton bazy danych. @Volatile zapewnia widoczność zmian między wątkami.
        @Volatile
        private var INSTANCE: AppDatabase? = null

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

        /**
         * Migracja z wersji 4 do 5.
         * Tworzy tabelę 'photos', przenosi dane 'photoUri' z 'clients' do 'photos',
         * a następnie usuwa kolumnę 'photoUri' z tabeli 'clients'.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i("AppDatabaseMigration", "Rozpoczęcie migracji 4->5")

                // Utwórz nową tabelę 'photos'
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

                // Odczytaj 'id' i 'photoUri' ze starej tabeli 'clients' i wstaw do 'photos'
                Log.i("AppDatabaseMigration", "Migracja 4->5: Rozpoczęcie przenoszenia photoUri do tabeli photos.")
                val cursor = db.query("SELECT id, photoUri FROM clients WHERE photoUri IS NOT NULL AND photoUri != ''")
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex("id")
                    val uriIndex = cursor.getColumnIndex("photoUri")
                    val currentTime = System.currentTimeMillis()

                    if (idIndex >= 0 && uriIndex >= 0) {
                        do {
                            val clientId = cursor.getLong(idIndex)
                            val photoUri = cursor.getString(uriIndex)
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

                // Utwórz tymczasową tabelę 'clients_new' bez kolumny 'photoUri'
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `clients_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `description` TEXT,
                        `clientAppNumber` TEXT,
                        `amoditNumber` TEXT
                    )
                """.trimIndent())
                Log.i("AppDatabaseMigration", "Migracja 4->5: Utworzono tabelę tymczasową 'clients_new'.")

                // Skopiuj dane z 'clients' do 'clients_new' (pomijając 'photoUri')
                db.execSQL("""
                    INSERT INTO `clients_new` (`id`, `description`, `clientAppNumber`, `amoditNumber`)
                    SELECT `id`, `description`, `clientAppNumber`, `amoditNumber` FROM `clients`
                """.trimIndent())
                Log.i("AppDatabaseMigration", "Migracja 4->5: Skopiowano dane do 'clients_new'.")

                // Usuń starą tabelę 'clients'
                db.execSQL("DROP TABLE `clients`")
                Log.i("AppDatabaseMigration", "Migracja 4->5: Usunięto starą tabelę 'clients'.")

                // Zmień nazwę 'clients_new' na 'clients'
                db.execSQL("ALTER TABLE `clients_new` RENAME TO `clients`")
                Log.i("AppDatabaseMigration", "Migracja 4->5: Zmieniono nazwę 'clients_new' na 'clients'.")

                Log.i("AppDatabaseMigration", "Zakończono migrację 4->5")
            }
        }

        /**
         * Migracja z wersji 5 do 6.
         * Dodaje nową, opcjonalną kolumnę 'cashRegisterNumber' do tabeli 'receipts'.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receipts ADD COLUMN cashRegisterNumber TEXT")
                Log.i("AppDatabaseMigration", "Migracja 5->6: Dodano kolumnę 'cashRegisterNumber' do tabeli 'receipts'.")
            }
        }

        /**
         * Zwraca instancję Singleton bazy danych [AppDatabase].
         * Jeśli instancja nie istnieje, tworzy ją w sposób bezpieczny wątkowo.
         * Konfiguruje budowniczego bazy danych, dodając niezbędne migracje.
         * @param context Kontekst aplikacji (najlepiej ApplicationContext, aby uniknąć wycieków pamięci).
         * @return Instancja [AppDatabase].
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    // Dodajemy wszystkie migracje, w tym nową MIGRATION_5_6
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

