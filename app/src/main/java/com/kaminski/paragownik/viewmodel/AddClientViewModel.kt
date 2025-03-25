package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class AddClientViewModel(application: Application) : AndroidViewModel(application) {

    private val receiptDao: ReceiptDao
    private val clientDao: ClientDao
    private val storeDao: StoreDao

    init {
        val database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()
        clientDao = database.clientDao()
        storeDao = database.storeDao()
    }

    suspend fun insertClient(clientDescription: String?): Long =
        suspendCancellableCoroutine { continuation ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val client = Client(description = clientDescription)
                    val clientId = clientDao.insertClient(client)
                    Log.d(
                        "AddClientViewModel",
                        "Dodano klienta. ID Klienta: $clientId, Opis Klienta: $clientDescription"
                    )
                    continuation.resume(clientId)
                } catch (e: Exception) {
                    Log.e("AddClientViewModel", "Błąd dodawania klienta.", e)
                    continuation.resume(-1L) // Zwróć -1L w przypadku błędu
                }
            }
        }


    suspend fun insertReceipt(
        storeId: Long, // storeId dla pierwszego paragonu (z ReceiptListActivity, może być -1L jeśli z ekranu głównego - NIEUŻYWANE)
        receiptNumber: String,
        receiptDateString: String,
        verificationDateString: String?,
        storeNumberForReceipt: String? = null, // Numer drogerii (String) dla wszystkich paragonów - UŻYWANE ZAWSZE
        clientId: Long // ID Klienta, do którego przypisujemy paragon
    ): Boolean = suspendCancellableCoroutine { continuation ->
        viewModelScope.launch(Dispatchers.IO) {
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            dateFormat.isLenient = false

            val verificationDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            verificationDateFormat.isLenient = false

            try {
                val receiptDate: Date = dateFormat.parse(receiptDateString) as Date
                val verificationDate: Date? = try {
                    if (!verificationDateString.isNullOrEmpty()) verificationDateFormat.parse(
                        verificationDateString
                    ) as Date else null
                } catch (e: Exception) {
                    null
                }

                // Ustal poprawne storeId dla paragonu - ZAWSZE UŻYWAJ storeNumberForReceipt
                var receiptStoreId: Long? = null
                if (storeNumberForReceipt != null) {
                    var store = storeDao.getStoreByNumber(storeNumberForReceipt)
                    if (store == null) {
                        // Drogeria nie istnieje, dodaj nową
                        store = Store(storeNumber = storeNumberForReceipt)
                        storeDao.insertStore(store)
                        store =
                            storeDao.getStoreByNumber(storeNumberForReceipt) // Pobierz nowo dodaną drogerię
                        Log.d(
                            "AddClientViewModel",
                            "Dodano nową drogerię o numerze: ${storeNumberForReceipt}"
                        )
                    }
                    receiptStoreId = store?.id // Użyj ID znalezionej lub nowo dodanej Drogerii
                } else {
                    Log.e("AddClientViewModel", "Brak numeru drogerii dla paragonu.")
                    continuation.resume(false)
                    return@launch
                }

                if (receiptStoreId == null) {
                    Log.e("AddClientViewModel", "Nie udało się ustalić storeId dla paragonu.")
                    continuation.resume(false)
                    return@launch
                }


                val receipt = Receipt(
                    receiptNumber = receiptNumber,
                    receiptDate = receiptDate,
                    storeId = receiptStoreId, // Użyj ustalonego storeId
                    verificationDate = verificationDate,
                    clientId = clientId // Przypisz clientId do paragonu
                )
                val receiptId = receiptDao.insertReceipt(receipt)


                Log.d(
                    "AddClientViewModel",
                    "Paragon dodany pomyślnie. ID Paragonu: $receiptId, Data Weryfikacji: $verificationDate, Numer Drogerii: $storeNumberForReceipt, ID Drogerii: $receiptStoreId, ID Klienta: $clientId"
                )
                continuation.resume(true)

            } catch (e: Exception) {
                Log.e("AddClientViewModel", "Błąd parsowania daty paragonu: $receiptDateString", e)
                continuation.resume(false)
            }
        }
    }
}