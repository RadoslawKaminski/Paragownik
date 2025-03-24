package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.ReceiptWithClient
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class EditReceiptViewModel(application: Application) : AndroidViewModel(application) {

    private val receiptDao: ReceiptDao
    private val storeDao: StoreDao
    private val clientDao: ClientDao

    init {
        val database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()
        storeDao = database.storeDao()
        clientDao = database.clientDao()
    }

    fun getReceiptWithClient(receiptId: Long): Flow<Pair<ReceiptWithClient?, String?>> {
        return receiptDao.getReceiptWithClientFlow(receiptId)
            .flatMapConcat { receiptWithClient ->
                storeDao.getStoreByIdFlow(receiptWithClient.receipt.storeId)
                    .map { store ->
                        Pair(receiptWithClient, store?.storeNumber) // Zwróć parę: ReceiptWithClient i storeNumber
                    }
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun updateReceiptAndClient( // Poprawiona funkcja updateReceiptAndClient
        receiptId: Long,
        storeNumberString: String,
        receiptNumber: String,
        receiptDateString: String,
        verificationDateString: String?,
        clientDescription: String?
    ): Boolean = suspendCancellableCoroutine { continuation ->
        viewModelScope.launch(Dispatchers.IO) {
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            dateFormat.isLenient = false

            val verificationDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            verificationDateFormat.isLenient = false

            try {
                val receiptDate: Date = dateFormat.parse(receiptDateString) as Date
                val verificationDate: Date? = try {
                    if (!verificationDateString.isNullOrEmpty()) verificationDateFormat.parse(verificationDateString) as Date else null
                } catch (e: Exception) {
                    null
                }

                // Pobierz istniejący paragon PO ID
                val existingReceipt = receiptDao.getReceiptById(receiptId) // Użyj getReceiptById do pobrania paragonu po ID

                if (existingReceipt == null) {
                    Log.e("EditReceiptViewModel", "Nie znaleziono paragonu o ID: $receiptId") // Zmieniono komunikat logu
                    continuation.resume(false)
                    return@launch
                }

                // Pobierz istniejącego klienta (zakładamy, że klient istnieje, bo edytujemy istniejący paragon)
                val existingClient = existingReceipt.clientId?.let { clientDao.getClientById(it) }


                // Aktualizuj Drogerię (jeśli numer drogerii się zmienił)
                var updatedStoreId: Long? = null
                if (storeNumberString != null) {
                    var store = storeDao.getStoreByNumber(storeNumberString)
                    if (store == null) {
                        // Drogeria nie istnieje, dodaj nową
                        store = Store(storeNumber = storeNumberString)
                        storeDao.insertStore(store)
                        store = storeDao.getStoreByNumber(storeNumberString) // Pobierz nowo dodaną drogerię
                        Log.d("EditReceiptViewModel", "Dodano nową drogerię o numerze: ${storeNumberString}")
                    }
                    updatedStoreId = store?.id // Użyj ID znalezionej lub nowo dodanej Drogerii
                } else {
                    Log.e("EditReceiptViewModel", "Brak numeru drogerii dla paragonu.")
                    continuation.resume(false)
                    return@launch
                }

                if (updatedStoreId == null) {
                    Log.e("EditReceiptViewModel", "Nie udało się ustalić storeId dla paragonu.")
                    continuation.resume(false)
                    return@launch
                }


                // Aktualizuj paragon
                val updatedReceipt = existingReceipt.copy( // Użyj copy, aby zaktualizować istniejący paragon
                    receiptNumber = receiptNumber,
                    receiptDate = receiptDate,
                    storeId = updatedStoreId,
                    verificationDate = verificationDate
                )
                receiptDao.updateReceipt(updatedReceipt) // Użyj updateReceipt DAO

                // Aktualizuj klienta (opis klienta)
                existingClient?.let { client -> // Upewnij się, że klient istnieje
                    val updatedClient = client.copy(description = clientDescription) // Użyj copy, aby zaktualizować klienta
                    clientDao.updateClient(updatedClient) // Użyj updateClient DAO
                    Log.d("EditReceiptViewModel", "Zaktualizowano klienta. ID Klienta: ${updatedClient.id}, Opis Klienta: ${updatedClient.description}")
                } ?: run {
                    Log.e("EditReceiptViewModel", "Nie znaleziono klienta do aktualizacji.")
                }


                Log.d("EditReceiptViewModel", "Paragon i Klient zaktualizowane pomyślnie. ID Paragonu: $receiptId, Numer Drogerii: $storeNumberString, Numer Paragonu: $receiptNumber, Data Paragonu: $receiptDateString, Data Weryfikacji: $verificationDateString, Opis Klienta: $clientDescription")
                continuation.resume(true)

            } catch (e: Exception) {
                Log.e("EditReceiptViewModel", "Błąd podczas aktualizacji paragonu lub klienta.", e)
                continuation.resume(false)
            }
        }
    }

    suspend fun deleteReceipt(receipt: Receipt): Boolean = suspendCancellableCoroutine { continuation -> // Poprawiona funkcja deleteReceipt
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val receiptToDelete = receiptDao.getReceiptById(receipt.id) // Pobierz paragon do usunięcia (dla pewności)

                receiptToDelete?.let {
                    receiptDao.deleteReceipt(it) // Usuń paragon

                    // Sprawdź, czy klient ma jeszcze jakieś paragony
                    val clientId = it.clientId
                    if (clientId != null) {
                        val clientReceiptsCount = receiptDao.getReceiptsForClientCount(clientId) // Dodaj funkcję DAO
                        if (clientReceiptsCount == 0) {
                            // Usuń klienta, jeśli nie ma już paragonów
                            val clientToDelete = clientDao.getClientById(clientId)
                            clientToDelete?.let { client ->
                                clientDao.deleteClient(client) // Usuń klienta
                                Log.d("EditReceiptViewModel", "Klient usunięty automatycznie (brak paragonów). ID Klienta: ${client.id}")
                            }
                        }
                    }

                    // Sprawdź, czy drogeria ma jeszcze jakieś paragony
                    val storeId = it.storeId
                    val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId) // Dodaj funkcję DAO
                    if (storeReceiptsCount == 0) {
                        // Usuń drogerię, jeśli nie ma już paragonów
                        val storeToDelete = storeDao.getStoreById(storeId)
                        storeToDelete?.let { store ->
                            storeDao.deleteStore(store) // Dodaj funkcję DAO
                            Log.d("EditReceiptViewModel", "Drogeria usunięta automatycznie (brak paragonów). ID Drogerii: ${store.id}")
                        }
                    }

                    Log.d("EditReceiptViewModel", "Paragon usunięty pomyślnie. ID Paragonu: ${receipt.id}")
                    continuation.resume(true)
                } ?: {
                    Log.e("EditReceiptViewModel", "Nie znaleziono paragonu do usunięcia. ID Paragonu: ${receipt.id}")
                    continuation.resume(false)
                }


            } catch (e: Exception) {
                Log.e("EditReceiptViewModel", "Błąd podczas usuwania paragonu.", e)
                continuation.resume(false)
            }
        }
    }

    suspend fun deleteClient(client: Client): Boolean = suspendCancellableCoroutine { continuation -> // Poprawiona funkcja deleteClient
        viewModelScope.launch(Dispatchers.IO, CoroutineStart.UNDISPATCHED) { // ZMIANA - CoroutineStart.UNDISPATCHED
            Log.d("EditReceiptViewModel", "deleteClient: Rozpoczęto usuwanie klienta. ID Klienta: ${client.id}")
            try {
                val clientToDelete = clientDao.getClientById(client.id) // Pobierz klienta do usunięcia (dla pewności)
                clientToDelete?.let {
                    Log.d("EditReceiptViewModel", "deleteClient: Klient pobrany z bazy danych. ID Klienta: ${client.id}")

                    // Pobierz storeId paragonu klienta PRZED usunięciem klienta (potrzebne do sprawdzenia drogerii)
                    var storeIdToDeleteCheck: Long? = null

                    // Użyj runBlocking, aby jednorazowo pobrać ReceiptWithClient i storeId
                    storeIdToDeleteCheck = receiptDao.getReceiptWithClient(receiptIdForDeletion)?.receipt?.storeId


                    Log.d("EditReceiptViewModel", "deleteClient: storeId paragonu pobrane. storeId: $storeIdToDeleteCheck")


                    Log.d("EditReceiptViewModel", "deleteClient: Usuwanie klienta z bazy danych. ID Klienta: ${client.id}")
                    clientDao.deleteClient(it) // Usuń klienta - kaskada powinna usunąć paragony
                    Log.d("EditReceiptViewModel", "deleteClient: Klient usunięty z bazy danych. ID Klienta: ${client.id}")


                    Log.d("EditReceiptViewModel", "deleteClient: Sprawdzanie drogerii do automatycznego usunięcia.")
                    // Sprawdź, czy drogeria ma jeszcze jakieś paragony (po usunięciu klienta i jego paragonów)
                    storeIdToDeleteCheck?.let { storeId ->
                        val storeReceiptsCount = receiptDao.getReceiptsForStoreCount(storeId)
                        Log.d("EditReceiptViewModel", "deleteClient: Liczba paragonów drogerii po usunięciu klienta: $storeReceiptsCount, ID Drogerii: $storeId")
                        if (storeReceiptsCount == 0) {
                            // Usuń drogerię, jeśli nie ma już paragonów
                            val storeToDelete = storeDao.getStoreById(storeId)
                            storeToDelete?.let { store ->
                                Log.d("EditReceiptViewModel", "deleteClient: Usuwanie drogerii z bazy danych. ID Drogerii: ${store.id}")
                                storeDao.deleteStore(store)
                                Log.d("EditReceiptViewModel", "deleteClient: Drogeria usunięta z bazy danych. ID Drogerii: ${store.id}")
                            }
                        }
                    }

                    Log.d("EditReceiptViewModel", "deleteClient: Usuwanie klienta zakończone sukcesem. ID Klienta: ${client.id}")
                    continuation.resume(true)

                } ?: {
                    Log.e("EditReceiptViewModel", "deleteClient: Nie znaleziono klienta do usunięcia. ID Klienta: ${client.id}")
                    continuation.resume(false)
                }


            } catch (e: Exception) {
                Log.e("EditReceiptViewModel", "deleteClient: Błąd podczas usuwania klienta i paragonów.", e)
                continuation.resume(false)
            }
        }
    }

    companion object {
        var receiptIdForDeletion: Long = -1L
    }
}