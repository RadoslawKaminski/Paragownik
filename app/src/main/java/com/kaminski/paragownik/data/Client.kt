package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // Room automatycznie wygeneruje ID
    val description: String? // Krótki opis klienta, może być null
)