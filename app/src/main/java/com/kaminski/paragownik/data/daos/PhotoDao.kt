package com.kaminski.paragownik.data.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.kaminski.paragownik.data.Photo
import com.kaminski.paragownik.data.PhotoType
import kotlinx.coroutines.flow.Flow

/**
 * DAO dla operacji na encji Photo.
 */
@Dao
interface PhotoDao {

    /**
     * Wstawia nowe zdjęcie do bazy.
     * @param photo Obiekt Photo do wstawienia.
     * @return ID wstawionego zdjęcia.
     */
    @Insert
    suspend fun insertPhoto(photo: Photo): Long

    /**
     * Pobiera wszystkie zdjęcia dla danego klienta, posortowane wg czasu dodania.
     * @param clientId ID klienta.
     * @return Flow emitujący listę zdjęć.
     */
    @Query("SELECT * FROM photos WHERE clientId = :clientId ORDER BY addedTimestamp ASC")
    fun getPhotosForClient(clientId: Long): Flow<List<Photo>>

    /**
     * Pobiera wszystkie zdjęcia danego typu dla klienta, posortowane wg czasu dodania.
     * @param clientId ID klienta.
     * @param type Typ zdjęcia (CLIENT lub TRANSACTION).
     * @return Flow emitujący listę zdjęć danego typu.
     */
    @Query("SELECT * FROM photos WHERE clientId = :clientId AND type = :type ORDER BY addedTimestamp ASC")
    fun getPhotosForClientByType(clientId: Long, type: PhotoType): Flow<List<Photo>>

    /**
     * Pobiera pierwsze (najstarsze) zdjęcie typu CLIENT dla danego klienta.
     * Używane do wyświetlania miniatury.
     * @param clientId ID klienta.
     * @return Flow emitujący pojedyncze zdjęcie lub null.
     */
    @Query("SELECT * FROM photos WHERE clientId = :clientId AND type = :type ORDER BY addedTimestamp ASC LIMIT 1")
    fun getFirstPhotoForClientByType(clientId: Long, type: PhotoType = PhotoType.CLIENT): Flow<Photo?>

     /**
     * Pobiera listę URI wszystkich zdjęć dla danego klienta.
     * Przydatne przed usunięciem klienta, aby wiedzieć, które pliki usunąć.
     * @param clientId ID klienta.
     * @return Lista URI zdjęć jako String.
     */
    @Query("SELECT uri FROM photos WHERE clientId = :clientId")
    suspend fun getPhotoUrisForClient(clientId: Long): List<String>

    /**
     * Usuwa zdjęcie z bazy danych.
     * @param photo Obiekt Photo do usunięcia.
     */
    @Delete
    suspend fun deletePhoto(photo: Photo)

    /**
     * Usuwa zdjęcie na podstawie jego URI.
     * Przydatne, gdy mamy tylko URI, a nie cały obiekt Photo.
     * @param uri URI zdjęcia do usunięcia.
     */
    @Query("DELETE FROM photos WHERE uri = :uri")
    suspend fun deletePhotoByUri(uri: String)

    // Usuwanie wszystkich zdjęć klienta nie jest potrzebne, bo mamy onDelete = CASCADE
    // @Query("DELETE FROM photos WHERE clientId = :clientId")
    // suspend fun deletePhotosForClient(clientId: Long)
}

