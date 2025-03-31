package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData // Do konwersji Flow na LiveData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.ClientWithThumbnail // Klasa pomocnicza klienta z URI miniatury
import com.kaminski.paragownik.data.daos.ClientDao

/**
 * ViewModel dla ClientListActivity.
 * Odpowiada za dostarczanie listy wszystkich klientów wraz z URI ich miniatur (pierwszego zdjęcia typu CLIENT).
 */
class ClientListViewModel(application: Application) : AndroidViewModel(application) {

    // Referencja do DAO klientów
    private val clientDao: ClientDao

    // LiveData przechowująca listę wszystkich klientów z URI ich miniatur.
    // Lista jest automatycznie aktualizowana dzięki użyciu Flow i asLiveData.
    // Typem listy jest ClientWithThumbnail, który zawiera obiekt Client i pole thumbnailUri.
    val allClients: LiveData<List<ClientWithThumbnail>>

    init {
        // Inicjalizacja bazy danych i DAO w bloku init
        val database = AppDatabase.getDatabase(application)
        clientDao = database.clientDao()

        // Pobranie Flow klientów z miniaturami z DAO (zapytanie `getAllClientsWithThumbnails`)
        // i przekonwertowanie go na LiveData, które może być obserwowane przez UI.
        allClients = clientDao.getAllClientsWithThumbnails().asLiveData()
    }

    // W przyszłości można dodać tu metody do filtrowania lub sortowania listy klientów,
    // np. na podstawie obecności numeru aplikacji, statusu "ujęty" itp.
}