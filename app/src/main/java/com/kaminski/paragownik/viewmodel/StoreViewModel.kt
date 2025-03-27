package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData // Import do konwersji Flow na LiveData
import androidx.lifecycle.viewModelScope // Import dla viewModelScope
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Store // Import encji Store
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers // Import Dispatchers
import kotlinx.coroutines.launch // Import launch

/**
 * ViewModel dla [MainActivity].
 * Odpowiada za dostarczanie listy wszystkich sklepów oraz umożliwia
 * wstawianie nowych sklepów i pobieranie sklepu po ID.
 */
class StoreViewModel(application: Application) : AndroidViewModel(application) {

    // Referencja do DAO sklepów
    private val storeDao: StoreDao
    // LiveData przechowująca listę wszystkich sklepów.
    // Jest automatycznie aktualizowana dzięki konwersji Flow z DAO na LiveData.
    val allStores: LiveData<List<Store>>

    init {
        // Inicjalizacja bazy danych i DAO
        val database = AppDatabase.getDatabase(application)
        storeDao = database.storeDao()
        // Pobierz Flow wszystkich sklepów z DAO i przekonwertuj go na LiveData.
        // asLiveData() automatycznie zarządza subskrypcją Flow w oparciu o cykl życia obserwatora.
        allStores = storeDao.getAllStores().asLiveData() // TODO: Dodać sortowanie w DAO (Task II.8)
    }

    /**
     * Wstawia nowy sklep do bazy danych.
     * Operacja wykonywana jest w tle za pomocą korutyny w `viewModelScope`.
     * @param store Obiekt [Store] do wstawienia.
     */
    fun insertStore(store: Store) {
        // Uruchom korutynę w zakresie ViewModelu na wątku IO
        viewModelScope.launch(Dispatchers.IO) {
            storeDao.insertStore(store) // Wywołaj metodę DAO
        }
    }

    /**
     * Pobiera sklep na podstawie jego ID.
     * Jest to operacja jednorazowa (suspend), przeznaczona do wywoływania z korutyny.
     * @param storeId ID sklepu do pobrania.
     * @return Obiekt [Store] lub `null`, jeśli nie znaleziono.
     */
    suspend fun getStoreById(storeId: Long): Store? {
        // Bezpośrednie wywołanie metody suspend z DAO.
        // Należy pamiętać, aby wywoływać tę funkcję z odpowiedniego kontekstu (np. Dispatchers.IO).
        // W AddClientActivity jest wywoływana w lifecycleScope.launch, co jest poprawne.
        return storeDao.getStoreById(storeId)
    }
}

