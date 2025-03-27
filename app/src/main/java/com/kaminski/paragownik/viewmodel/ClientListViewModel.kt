package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.daos.ClientDao

/**
 * ViewModel dla ClientListActivity.
 * Odpowiada za dostarczanie listy wszystkich klientów.
 */
class ClientListViewModel(application: Application) : AndroidViewModel(application) {

    // Referencja do DAO klientów
    private val clientDao: ClientDao
    // LiveData przechowująca listę wszystkich klientów.
    // Jest automatycznie aktualizowana dzięki konwersji Flow z DAO na LiveData.
    val allClients: LiveData<List<Client>>

    init {
        // Inicjalizacja bazy danych i DAO
        val database = AppDatabase.getDatabase(application)
        clientDao = database.clientDao()
        // Pobierz Flow wszystkich klientów z DAO i przekonwertuj go na LiveData.
        // asLiveData() automatycznie zarządza subskrypcją Flow w oparciu o cykl życia obserwatora.
        // TODO: W przyszłości można dodać sortowanie w DAO lub tutaj
        allClients = clientDao.getAllClients().asLiveData()
    }

    // W przyszłości można dodać tu metody do filtrowania lub sortowania listy klientów.
}
