package com.kaminski.paragownik.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kaminski.paragownik.data.Store
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Insert
    suspend fun insertStore(store: Store)

    @Query("SELECT * FROM stores")
    fun getAllStores(): Flow<List<Store>>

    @Query("SELECT * FROM stores WHERE id = :storeId")
    suspend fun getStoreById(storeId: Long): Store?

    @Query("SELECT * FROM stores WHERE storeNumber = :storeNumber")
    suspend fun getStoreByNumber(storeNumber: String): Store?

    @Query("SELECT * FROM stores WHERE id = :storeId")
    fun getStoreByIdFlow(storeId: Long): Flow<Store?>
}