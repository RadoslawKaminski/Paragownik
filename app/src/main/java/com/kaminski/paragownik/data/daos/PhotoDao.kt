package com.kaminski.paragownik.data.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.kaminski.paragownik.data.Photo
import com.kaminski.paragownik.data.PhotoType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) dla operacji na encji [Photo].
 * Definiuje metody dostępu do tabeli 'photos' w bazie danych.
 */
@Dao
interface PhotoDao {

    /**
     * Wstawia nowe zdjęcie do bazy.
     * @param photo Obiekt [Photo] do wstawienia.
     * @return ID wstawionego zdjęcia.
     */
    @Insert
    suspend fun insertPhoto(photo: Photo): Long

    /**
     * Pobiera wszystkie zdjęcia dla danego klienta, posortowane wg czasu dodania (rosnąco).
     * @param clientId ID klienta.
     * @return [Flow] emitujący listę obiektów [Photo].
     */
    @Query("SELECT * FROM photos WHERE clientId = :clientId ORDER BY addedTimestamp ASC")
    fun getPhotosForClient(clientId: Long): Flow<List<Photo>>

    /**
     * Pobiera wszystkie zdjęcia danego typu dla klienta, posortowane wg czasu dodania (rosnąco).
     * @param clientId ID klienta.
     * @param type Typ zdjęcia ([PhotoType.CLIENT] lub [PhotoType.TRANSACTION]).
     * @return [Flow] emitujący listę zdjęć danego typu.
     */
    @Query("SELECT * FROM photos WHERE clientId = :clientId AND type = :type ORDER BY addedTimestamp ASC")
    fun getPhotosForClientByType(clientId: Long, type: PhotoType): Flow<List<Photo>>

    /**
     * Pobiera pierwsze (najstarsze) zdjęcie typu CLIENT dla danego klienta.
     * Używane do wyświetlania miniatury klienta.
     * @param clientId ID klienta.
     * @param type Typ zdjęcia (domyślnie [PhotoType.CLIENT]).
     * @return [Flow] emitujący pojedynczy obiekt [Photo] lub `null`, jeśli klient nie ma zdjęć danego typu.
     */
    @Query("SELECT * FROM photos WHERE clientId = :clientId AND type = :type ORDER BY addedTimestamp ASC LIMIT 1")
    fun getFirstPhotoForClientByType(clientId: Long, type: PhotoType = PhotoType.CLIENT): Flow<Photo?>

    /**
     * Pobiera listę URI (jako String) wszystkich zdjęć dla danego klienta.
     * Przydatne do usuwania plików zdjęć z pamięci urządzenia przed usunięciem klienta z bazy.
     * @param clientId ID klienta.
     * @return Lista URI zdjęć jako [String].
     */
    @Query("SELECT uri FROM photos WHERE clientId = :clientId")
    suspend fun getPhotoUrisForClient(clientId: Long): List<String>

    /**
     * Usuwa zdjęcie z bazy danych.
     * @param photo Obiekt [Photo] do usunięcia.
     */
    @Delete
    suspend fun deletePhoto(photo: Photo)

    /**
     * Usuwa zdjęcie na podstawie jego URI.
     * Przydatne, gdy trzeba usunąć konkretne zdjęcie, mając tylko jego URI.
     * @param uri URI zdjęcia do usunięcia (jako String).
     */
    @Query("DELETE FROM photos WHERE uri = :uri")
    suspend fun deletePhotoByUri(uri: String)

    // Usuwanie wszystkich zdjęć klienta za pomocą dedykowanej metody nie jest potrzebne,
    // ponieważ relacja w encji Receipt ma ustawione onDelete = CASCADE,
    // co automatycznie usuwa powiązane zdjęcia przy usunięciu klienta.
}