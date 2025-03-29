package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client // Nadal potrzebne dla ClientDao
import com.kaminski.paragownik.data.ClientWithThumbnail // Dodano import
import com.kaminski.paragownik.data.daos.ClientDao

/**
 * ViewModel dla ClientListActivity.
 * Odpowiada za dostarczanie listy wszystkich klientów wraz z miniaturami.
 */
class ClientListViewModel(application: Application) : AndroidViewModel(application) {

    // Referencja do DAO klientów
    private val clientDao: ClientDao
    // LiveData przechowująca listę wszystkich klientów z miniaturami.
    val allClients: LiveData<List<ClientWithThumbnail>> // Zmieniono typ

    init {
        // Inicjalizacja bazy danych i DAO
        val database = AppDatabase.getDatabase(application)
        clientDao = database.clientDao()
        // Pobierz Flow klientów z miniaturami i przekonwertuj na LiveData.
        allClients = clientDao.getAllClientsWithThumbnails().asLiveData() // Użyto nowego zapytania
    }

    // W przyszłości można dodać tu metody do filtrowania lub sortowania listy klientów.
}

