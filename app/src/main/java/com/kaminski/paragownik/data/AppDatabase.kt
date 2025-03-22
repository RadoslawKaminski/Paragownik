package com.kaminski.paragownik.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao

@Database(entities = [Store::class, Receipt::class, Client::class], version = 2, exportSchema = false) // Zwiększ wersję bazy danych do 2
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun storeDao(): StoreDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun clientDao(): ClientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration() // Pamiętaj o migracjach w produkcji
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}