package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.asLiveData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.data.daos.ReceiptDao

/**
 * ViewModel dla ReceiptListActivity.
 * Odpowiada za dostarczanie listy paragonów (wraz z danymi klientów)
 * dla określonego sklepu.
 */
class ReceiptViewModel(application: Application) : AndroidViewModel(application) {

    // Referencja do DAO paragonów
    private val receiptDao: ReceiptDao
    // MutableLiveData przechowująca aktualnie wybrane ID sklepu.
    private val currentStoreId = MutableLiveData<Long>()

    init {
        // Inicjalizacja bazy danych i DAO
        val database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()
    }

    /**
     * LiveData emitująca listę paragonów ([ReceiptWithClient]) dla sklepu,
     * którego ID jest aktualnie ustawione w `currentStoreId`.
     * Używa `switchMap` do automatycznego przełączania obserwacji Flow z DAO,
     * gdy zmieni się `currentStoreId`.
     */
    val receiptsForStore: LiveData<List<ReceiptWithClient>> = currentStoreId.switchMap { storeId ->
        // Gdy currentStoreId się zmieni, switchMap anuluje poprzednią obserwację
        // i rozpoczyna nową, pobierając Flow z DAO dla nowego storeId
        // i konwertując go na LiveData za pomocą asLiveData().
        receiptDao.getReceiptsForStore(storeId).asLiveData()
    }

    /**
     * Ustawia ID sklepu, dla którego mają być załadowane paragony.
     * Zmiana wartości `currentStoreId` automatycznie wyzwoli aktualizację
     * `receiptsForStore` dzięki `switchMap`.
     * @param storeId ID sklepu do załadowania.
     */
    fun loadReceiptsForStore(storeId: Long) {
        // Ustaw nową wartość w MutableLiveData.
        // Jeśli wartość jest taka sama jak poprzednia, switchMap nic nie zrobi.
        // Jeśli wartość jest inna, switchMap pobierze nowy Flow i zacznie go obserwować.
        currentStoreId.value = storeId
    }

}
