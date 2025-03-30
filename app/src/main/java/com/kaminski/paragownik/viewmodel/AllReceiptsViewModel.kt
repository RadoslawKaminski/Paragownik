package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.data.daos.ReceiptDao

/**
 * ViewModel dla AllReceiptsActivity.
 * Odpowiada za dostarczanie listy wszystkich paragonów (wraz z danymi klientów),
 * posortowanej według określonych kryteriów.
 */
class AllReceiptsViewModel(application: Application) : AndroidViewModel(application) {

    // Referencja do DAO paragonów
    private val receiptDao: ReceiptDao

    // LiveData emitująca listę wszystkich paragonów
    val allReceipts: LiveData<List<ReceiptWithClient>>

    init {
        // Inicjalizacja bazy danych i DAO
        val database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()

        // Pobierz Flow wszystkich posortowanych paragonów i przekonwertuj na LiveData
        allReceipts = receiptDao.getAllReceiptsSorted().asLiveData()
    }
}

