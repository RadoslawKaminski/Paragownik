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
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.ClientReceiptCrossRefDao
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

    init {
        val database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()
        clientDao = database.clientDao()
        clientReceiptCrossRefDao = database.clientReceiptCrossRefDao()
    }

    suspend fun addClientAndReceipt(
        storeId: Long,
        receiptNumber: String,
        receiptDateString: String,
        verificationDateString: String?,
        clientDescription: String?
    ): Boolean = suspendCancellableCoroutine { continuation ->
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Utwórz Klienta
            val client = Client(description = clientDescription)
            val clientId = clientDao.insertClient(client)

            // 2. Utwórz Paragon
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            dateFormat.isLenient = false

            val verificationDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) // Format dla daty weryfikacji
            verificationDateFormat.isLenient = false

            try {
                val receiptDate: Date = dateFormat.parse(receiptDateString) as Date
                val verificationDate: Date? = try { // Parsowanie daty weryfikacji - może się nie udać (opcjonalne pole)
                    if (!verificationDateString.isNullOrEmpty()) verificationDateFormat.parse(verificationDateString) as Date else null
                } catch (e: Exception) {
                    null // Jeśli parsowanie daty weryfikacji się nie powiedzie, ustaw null
                }

                val receipt = Receipt(
                    receiptNumber = receiptNumber,
                    receiptDate = receiptDate,
                    storeId = storeId,
                    verificationDate = verificationDate // Ustaw datę weryfikacji (może być null)
                )
                val receiptId = receiptDao.insertReceipt(receipt)

                // 3. Utwórz relację ClientReceiptCrossRef - TERAZ WSTAWIAMY DO TABELI POŚREDNICZĄCEJ
                val crossRef = ClientReceiptCrossRef(clientId = clientId, receiptId = receiptId)
                clientReceiptCrossRefDao.insert(crossRef) // Jawnie wstaw rekord do client_receipt_cross_ref

                Log.d("AddClientViewModel", "Client and Receipt added successfully. Client ID: $clientId, Receipt ID: $receiptId, Verification Date: $verificationDate, Client Description: $clientDescription")
                continuation.resume(true)

            } catch (e: Exception) {
                Log.e("AddClientViewModel", "Error parsing receipt date: $receiptDateString", e)
                continuation.resume(false)
            }
        }
    }
}