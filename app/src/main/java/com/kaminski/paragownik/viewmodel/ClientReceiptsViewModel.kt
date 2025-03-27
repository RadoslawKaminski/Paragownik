package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.asLiveData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import kotlinx.coroutines.flow.Flow // Import Flow

/**
 * ViewModel dla ClientReceiptsActivity.
 * Odpowiada za dostarczanie danych klienta i listy jego paragonów.
 */
class ClientReceiptsViewModel(application: Application) : AndroidViewModel(application) {

    private val clientDao: ClientDao
    private val receiptDao: ReceiptDao

    // MutableLiveData przechowująca aktualnie wybrane ID klienta.
    private val currentClientId = MutableLiveData<Long>()

    init {
        val database = AppDatabase.getDatabase(application)
        clientDao = database.clientDao()
        receiptDao = database.receiptDao()
    }

    /**
     * LiveData emitująca dane klienta, którego ID jest w `currentClientId`.
     * Używa `switchMap` do dynamicznego przełączania obserwacji Flow z DAO.
     */
    val client: LiveData<Client?> = currentClientId.switchMap { clientId ->
        clientDao.getClientByIdFlow(clientId).asLiveData()
    }

    /**
     * LiveData emitująca listę paragonów ([ReceiptWithClient]) dla klienta,
     * którego ID jest aktualnie ustawione w `currentClientId`.
     */
    val receiptsForClient: LiveData<List<ReceiptWithClient>> = currentClientId.switchMap { clientId ->
        // TODO: Dodać sortowanie w DAO lub tutaj (Task II.9)
        receiptDao.getReceiptsWithClientForClient(clientId).asLiveData()
    }

    /**
     * Ustawia ID klienta, dla którego mają być załadowane dane.
     * Wyzwala aktualizację `client` i `receiptsForClient`.
     * @param clientId ID klienta do załadowania.
     */
    fun loadClientData(clientId: Long) {
        if (currentClientId.value != clientId) { // Ustaw tylko jeśli ID się zmieniło
            currentClientId.value = clientId
        }
    }
}
