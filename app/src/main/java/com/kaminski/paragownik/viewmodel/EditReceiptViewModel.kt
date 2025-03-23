package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapConcat

class EditReceiptViewModel(application: Application) : AndroidViewModel(application) {

    private val receiptDao: ReceiptDao
    private val storeDao: StoreDao

    init {
        val database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()
        storeDao = database.storeDao()
    }

    fun getReceiptWithClient(receiptId: Long): Flow<Pair<ReceiptWithClient?, String?>> {
        return receiptDao.getReceiptWithClientFlow(receiptId)
            .flatMapConcat { receiptWithClient ->
                storeDao.getStoreByIdFlow(receiptWithClient.receipt.storeId)
                    .map { store ->
                        Pair(receiptWithClient, store?.storeNumber) // Zwróć parę: ReceiptWithClient i storeNumber
                    }
            }
            .flowOn(Dispatchers.IO)
    }
}