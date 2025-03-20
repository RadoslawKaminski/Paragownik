package com.kaminski.paragownik.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import com.kaminski.paragownik.data.daos.ClientReceiptCrossRefDao // Dodaj import

@Database(entities = [Store::class, Receipt::class, Client::class, ClientReceiptCrossRef::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun storeDao(): StoreDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun clientDao(): ClientDao
    abstract fun clientReceiptCrossRefDao(): ClientReceiptCrossRefDao // Dodaj ClientReceiptCrossRefDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}