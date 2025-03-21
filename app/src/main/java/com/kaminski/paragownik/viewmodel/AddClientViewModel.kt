package com.kaminski.paragownik.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.ClientReceiptCrossRef
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.ClientReceiptCrossRefDao
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
    private val clientReceiptCrossRefDao: ClientReceiptCrossRefDao
    private val storeDao: StoreDao // Dodaj StoreDao

    init {
        val database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()
        clientDao = database.clientDao()
        clientReceiptCrossRefDao = database.clientReceiptCrossRefDao()
        storeDao = database.storeDao() // Zainicjalizuj StoreDao
    }

    suspend fun addClientAndReceipt(
        storeId: Long, // storeId dla pierwszego paragonu (z MainActivity)
        receiptNumber: String,
        receiptDateString: String,
        verificationDateString: String?,
        clientDescription: String?,
        storeNumberForReceipt: String? = null // Nowy parametr dla numeru drogerii (String) dla dodatkowych paragonów
    ): Boolean = suspendCancellableCoroutine { continuation ->
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Utwórz Klienta
            val client = Client(description = clientDescription)
            val clientId = clientDao.insertClient(client)

            // 2. Utwórz Paragon
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

                // Ustal poprawne storeId dla paragonu
                var receiptStoreId = storeId // Domyślnie storeId z MainActivity (dla pierwszego paragonu)
                if (storeNumberForReceipt != null) { // Jeśli podano storeNumberForReceipt (dla dodatkowych paragonów)
                    val store = storeDao.getStoreByNumber(storeNumberForReceipt) // Znajdź Store po storeNumber
                    if (store != null) {
                        receiptStoreId = store.id // Użyj ID znalezionej Drogerii
                    } else {
                        Log.e("AddClientViewModel", "Nie znaleziono drogerii dla numeru drogerii: $storeNumberForReceipt")
                        continuation.resume(false) // Nie znaleziono drogerii, niepowodzenie
                        return@launch
                    }
                }

                val receipt = Receipt(
                    receiptNumber = receiptNumber,
                    receiptDate = receiptDate,
                    storeId = receiptStoreId, // Użyj ustalonego storeId
                    verificationDate = verificationDate
                )
                val receiptId = receiptDao.insertReceipt(receipt)

                // 3. Utwórz relację ClientReceiptCrossRef
                val crossRef = ClientReceiptCrossRef(clientId = clientId, receiptId = receiptId)
                clientReceiptCrossRefDao.insert(crossRef)

                Log.d("AddClientViewModel", "Klient i Paragon dodane pomyślnie. ID Klienta: $clientId, ID Paragonu: $receiptId, Data Weryfikacji: $verificationDate, Opis Klienta: $clientDescription, ID Drogerii: $receiptStoreId")
                continuation.resume(true)

            } catch (e: Exception) {
                Log.e("AddClientViewModel", "Błąd parsowania daty paragonu: $receiptDateString", e)
                continuation.resume(false)
            }
        }
    }
}