package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.daos.ReceiptDao

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {

    private val receiptDao: ReceiptDao
    private var currentStoreId: Long = -1L // Przechowuje aktualne storeId

    // LiveData do obserwowania listy paragonów dla aktualnego storeId
    private var _receiptsForStore: LiveData<List<Receipt>>? = null
    val receiptsForStore: LiveData<List<Receipt>>
        get() = _receiptsForStore ?: throw IllegalStateException("Receipts not loaded for store yet")

    init {
        val database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()
    }

    fun loadReceiptsForStore(storeId: Long) {
        currentStoreId = storeId
        _receiptsForStore = receiptDao.getReceiptsForStore(storeId).asLiveData()
    }

    // Funkcja do wstawiania paragonu (może być potrzebna później)
    /*
    fun insertReceipt(receipt: Receipt) {
        viewModelScope.launch(Dispatchers.IO) {
            receiptDao.insertReceipt(receipt)
        }
    }
    */
}