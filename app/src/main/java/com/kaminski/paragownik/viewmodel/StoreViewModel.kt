package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.PhotoDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * ViewModel dla MainActivity i innych miejsc potrzebujących danych o sklepach i miniaturach klientów.
 * Dostarcza listę wszystkich sklepów, mapę ID->Numer oraz mapę miniatur klientów.
 */
class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val storeDao: StoreDao
    private val clientDao: ClientDao
    private val photoDao: PhotoDao
    // LiveData z listą wszystkich sklepów.
    val allStores: LiveData<List<Store>>
    // LiveData z mapą [Store.id] -> [Store.storeNumber].
    val allStoresMap: LiveData<Map<Long, String>>
    // LiveData z mapą [Client.id] -> [thumbnailUri?].
    val clientThumbnailsMap: LiveData<Map<Long, String?>>

    init {
        val database = AppDatabase.getDatabase(application)
        storeDao = database.storeDao()
        clientDao = database.clientDao()
        photoDao = database.photoDao()
        allStores = storeDao.getAllStores().asLiveData()

        // Utwórz mapę z listy sklepów za pomocą transformacji LiveData.map
        allStoresMap = allStores.map { storeList ->
            // Konwertuje listę sklepów na mapę, gdzie kluczem jest ID, a wartością numer sklepu.
            storeList.associateBy({ it.id }, { it.storeNumber })
        }

        // Inicjalizacja mapy miniatur klientów
        // Pobieramy wszystkich klientów, a następnie dla każdego znajdujemy pierwsze zdjęcie
        // To może być nieoptymalne dla bardzo dużej liczby klientów.
        // Alternatywą byłoby dedykowane zapytanie SQL zwracające od razu mapę.
        clientThumbnailsMap = clientDao.getAllClients()
            .flatMapLatest { clients ->
                // Dla każdego klienta pobierz jego pierwsze zdjęcie
                val flows: List<Flow<Pair<Long, String?>>> = clients.map { client ->
                    photoDao.getFirstPhotoForClientByType(client.id)
                        .map { photo -> client.id to photo?.uri } // Mapuj na Pair(clientId, uri?)
                }
                // Połącz wyniki wszystkich Flow w jedną mapę
                if (flows.isEmpty()) {
                    kotlinx.coroutines.flow.flowOf(emptyMap()) // Zwróć pustą mapę, jeśli nie ma klientów
                } else {
                    combine(flows) { results ->
                        results.toMap() // Konwertuj tablicę Pair na Map
                    }
                }
            }.asLiveData()
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