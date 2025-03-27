package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map // Importuj map dla LiveData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.StoreDao

/**
 * ViewModel dla MainActivity i innych miejsc potrzebujących danych o sklepach.
 * Dostarcza listę wszystkich sklepów oraz mapę ID->Numer.
 */
class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val storeDao: StoreDao
    // LiveData z listą wszystkich sklepów.
    val allStores: LiveData<List<Store>>
    // LiveData z mapą [Store.id] -> [Store.storeNumber].
    val allStoresMap: LiveData<Map<Long, String>>

    init {
        val database = AppDatabase.getDatabase(application)
        storeDao = database.storeDao()
        // Pobierz Flow i przekonwertuj na LiveData
        allStores = storeDao.getAllStores().asLiveData() // TODO: Dodać sortowanie w DAO

        // Utwórz mapę z listy sklepów za pomocą transformacji LiveData.map
        allStoresMap = allStores.map { storeList ->
            // Konwertuje listę sklepów na mapę, gdzie kluczem jest ID, a wartością numer sklepu.
            storeList.associateBy({ it.id }, { it.storeNumber })
        }
    }

    /**
     * Pobiera sklep na podstawie jego ID (operacja jednorazowa).
     * @param storeId ID sklepu do pobrania.
     * @return Obiekt [Store] lub `null`.
     */
    suspend fun getStoreById(storeId: Long): Store? {
        return storeDao.getStoreById(storeId)
    }
}
