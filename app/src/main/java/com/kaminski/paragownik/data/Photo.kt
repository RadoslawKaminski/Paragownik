package com.kaminski.paragownik.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Encja reprezentująca pojedyncze zdjęcie powiązane z klientem.
 *
 * @property id Unikalny identyfikator zdjęcia.
 * @property clientId Klucz obcy do tabeli klientów.
 * @property uri URI pliku zdjęcia w pamięci wewnętrznej (np. "file:///...").
 * @property type Typ zdjęcia (CLIENT lub TRANSACTION).
 * @property addedTimestamp Znacznik czasu dodania zdjęcia (do sortowania).
 */
@Entity(
    tableName = "photos",
    indices = [Index("clientId"), Index("type")],
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE // Kaskadowe usuwanie zdjęć przy usunięciu klienta
        )
    ]
)
data class Photo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clientId: Long,
    val uri: String,
    val type: PhotoType,
    val addedTimestamp: Long = System.currentTimeMillis()
)



