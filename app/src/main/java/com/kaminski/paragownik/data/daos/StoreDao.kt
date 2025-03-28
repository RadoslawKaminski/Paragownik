package com.kaminski.paragownik.data.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.kaminski.paragownik.data.Store
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) dla operacji na encji [Store] (sklepy/drogerie).
 * Definiuje metody dostępu do tabeli 'stores'.
 */
@Dao
interface StoreDao {

    /**
     * Wstawia nowy sklep do bazy danych.
     * Jeśli sklep o tym samym kluczu głównym już istnieje, operacja może się nie powść (zależy od strategii OnConflict).
     * @param store Obiekt [Store] do wstawienia.
     */
    @Insert
    suspend fun insertStore(store: Store)

    /**
     * Usuwa sklep z bazy danych.
     * Dopasowanie odbywa się na podstawie klucza głównego obiektu [store].
     * UWAGA: Domyślnie Room nie pozwala usunąć sklepu, jeśli istnieją paragony z nim powiązane (Foreign Key constraint).
     * Logika usuwania pustych sklepów jest realizowana w ViewModelu.
     * @param store Obiekt [Store] do usunięcia.
     */
    @Delete
    suspend fun deleteStore(store: Store)

    /**
     * Pobiera wszystkie sklepy z bazy danych jako [Flow].
     * Flow emituje nową listę za każdym razem, gdy dane w tabeli 'stores' ulegną zmianie.
     * Wyniki są sortowane numerycznie rosnąco według numeru sklepu.
     * @return [Flow] emitujący listę obiektów [Store].
     */
    // Zapytanie pobierające wszystkie sklepy, posortowane numerycznie rosnąco według numeru sklepu.
    @Query("SELECT * FROM stores ORDER BY CAST(storeNumber AS INTEGER) ASC")
    fun getAllStores(): Flow<List<Store>>

    /**
     * Pobiera pojedynczy sklep na podstawie jego ID.
     * Operacja jednorazowa (suspend).
     * @param storeId ID sklepu do pobrania.
     * @return Obiekt [Store] lub `null`, jeśli sklep o podanym ID nie został znaleziony.
     */
    @Query("SELECT * FROM stores WHERE id = :storeId")
    suspend fun getStoreById(storeId: Long): Store?

    /**
     * Pobiera pojedynczy sklep na podstawie jego numeru identyfikacyjnego.
     * Operacja jednorazowa (suspend).
     * Zakłada, że numer sklepu jest unikalny (choć baza tego nie wymusza).
     * @param storeNumber Numer sklepu do znalezienia.
     * @return Obiekt [Store] lub `null`, jeśli sklep o podanym numerze nie został znaleziony.
     */
    @Query("SELECT * FROM stores WHERE storeNumber = :storeNumber LIMIT 1") // Dodano LIMIT 1 dla pewności
    suspend fun getStoreByNumber(storeNumber: String): Store?

    /**
     * Pobiera pojedynczy sklep na podstawie jego ID jako [Flow].
     * Przydatne do obserwowania zmian w danych konkretnego sklepu.
     * @param storeId ID sklepu do obserwacji.
     * @return [Flow] emitujący obiekt [Store] lub `null`.
     */
    @Query("SELECT * FROM stores WHERE id = :storeId")
    fun getStoreByIdFlow(storeId: Long): Flow<Store?>
}

