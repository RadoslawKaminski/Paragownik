package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val storeDao: StoreDao
    val allStores: LiveData<List<Store>>

    init {
        val database = AppDatabase.getDatabase(application)
        storeDao = database.storeDao()
        allStores = storeDao.getAllStores().asLiveData()
    }

    fun insertStore(store: Store) {
        viewModelScope.launch(Dispatchers.IO) {
            storeDao.insertStore(store)
        }
    }
    suspend fun getStoreById(storeId: Long): Store? {
        return storeDao.getStoreById(storeId)
    }
}