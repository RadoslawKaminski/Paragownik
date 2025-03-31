package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Photo
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.PhotoDao
import com.kaminski.paragownik.data.daos.ReceiptDao

/**
 * ViewModel dla ClientReceiptsActivity.
 * Odpowiada za dostarczanie danych klienta, listy jego paragonów oraz listy jego zdjęć.
 */
class ClientReceiptsViewModel(application: Application) : AndroidViewModel(application) {

    private val clientDao: ClientDao
    private val receiptDao: ReceiptDao
    private val photoDao: PhotoDao

    // MutableLiveData przechowująca aktualnie wybrane ID klienta.
    // Zmiana tej wartości wyzwala aktualizację pozostałych LiveData.
    private val currentClientId = MutableLiveData<Long>()

    /**
     * LiveData emitująca listę wszystkich zdjęć ([Photo]) dla klienta,
     * którego ID jest aktualnie ustawione w `currentClientId`.
     * Reaguje na zmiany w `currentClientId`.
     */
    val clientPhotos: LiveData<List<Photo>>

    init {
        val database = AppDatabase.getDatabase(application)
        clientDao = database.clientDao()
        receiptDao = database.receiptDao()
        photoDao = database.photoDao()

        // Inicjalizacja LiveData obserwującej zdjęcia klienta.
        // `switchMap` zapewnia, że obserwujemy Flow zdjęć dla aktualnego `currentClientId`.
        clientPhotos = currentClientId.switchMap { clientId ->
            photoDao.getPhotosForClient(clientId).asLiveData()
        }
    }

    /**
     * LiveData emitująca dane klienta ([Client]), którego ID jest w `currentClientId`.
     * Używa `switchMap` do dynamicznego przełączania obserwacji Flow z DAO.
     */
    val client: LiveData<Client?> = currentClientId.switchMap { clientId ->
        clientDao.getClientByIdFlow(clientId).asLiveData()
    }

    /**
     * LiveData emitująca listę paragonów ([ReceiptWithClient]) dla klienta,
     * którego ID jest aktualnie ustawione w `currentClientId`.
     * Reaguje na zmiany w `currentClientId`.
     */
    val receiptsForClient: LiveData<List<ReceiptWithClient>> =
        currentClientId.switchMap { clientId ->
            // Pobiera paragony wraz z danymi klienta dla danego ID klienta.
            // Zapytanie w DAO dba o sortowanie (np. wg numeru sklepu).
            receiptDao.getReceiptsWithClientForClient(clientId).asLiveData()
        }

    /**
     * Ustawia ID klienta, dla którego mają być załadowane dane.
     * Wyzwala aktualizację `client`, `receiptsForClient` i `clientPhotos`, jeśli ID się zmieniło.
     * @param clientId ID klienta do załadowania.
     */
    fun loadClientData(clientId: Long) {
        // Aktualizuj ID tylko jeśli jest inne od obecnego, aby uniknąć zbędnych operacji.
        if (currentClientId.value != clientId) {
            currentClientId.value = clientId
        }
    }
}