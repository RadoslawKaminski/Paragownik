package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stores")
data class Store(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // Room automatycznie wygeneruje ID
    val storeNumber: String // Numer drogerii jako String, bo tak jest w specyfikacji
)