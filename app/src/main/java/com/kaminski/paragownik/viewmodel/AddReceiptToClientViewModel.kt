package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.kaminski.paragownik.AddClientActivity // Potrzebne dla ReceiptData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel dla AddReceiptToClientActivity.
 * Odpowiada za pobranie danych klienta i logikę dodawania nowych paragonów do niego.
 */
class AddReceiptToClientViewModel(application: Application) : AndroidViewModel(application) {

    private val clientDao: ClientDao
    private val receiptDao: ReceiptDao
    private val storeDao: StoreDao

    // Możliwe wyniki operacji zapisu paragonów
    enum class SaveReceiptsResult {
        SUCCESS,
        ERROR_DATE_FORMAT,
        ERROR_DUPLICATE_RECEIPT,
        ERROR_STORE_NUMBER_MISSING,
        ERROR_DATABASE,
        ERROR_UNKNOWN,
        ERROR_NO_RECEIPTS // Dodano przypadek braku paragonów
    }

    init {
        val database = AppDatabase.getDatabase(application)
        clientDao = database.clientDao()
        receiptDao = database.receiptDao()
        storeDao = database.storeDao()
    }

    /**
     * Pobiera dane klienta jako Flow.
     * @param clientId ID klienta.
     * @return Flow emitujący obiekt Client lub null.
     */
    fun getClientByIdFlow(clientId: Long): Flow<Client?> {
        return clientDao.getClientByIdFlow(clientId)
    }

    /**
     * TODO: Zaimplementować logikę dodawania paragonów do istniejącego klienta.
     * Powinna być transakcyjna.
     * Powinna obsługiwać tworzenie nowych sklepów.
     * Powinna walidować duplikaty paragonów.
     *
     * @param clientId ID klienta, do którego dodajemy paragony.
     * @param receiptsData Lista danych paragonów do dodania.
     * @return SaveReceiptsResult wskazujący wynik operacji.
     */
    suspend fun saveReceiptsForClient(
        clientId: Long,
        receiptsData: List<AddClientActivity.ReceiptData> // Używamy tej samej struktury danych
    ): SaveReceiptsResult {
        // --- Implementacja w kolejnym kroku ---
        Log.d("AddReceiptVM", "saveReceiptsForClient wywołane dla klienta $clientId z ${receiptsData.size} paragonami.")
        // Symulacja sukcesu dla testów UI
        kotlinx.coroutines.delay(500) // Symulacja operacji
        if (receiptsData.isEmpty()) return SaveReceiptsResult.ERROR_NO_RECEIPTS
        return SaveReceiptsResult.SUCCESS
        // --- Koniec implementacji w kolejnym kroku ---
    }
}
