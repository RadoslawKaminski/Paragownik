package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData // Do konwersji Flow na LiveData
import com.kaminski.paragownik.data.AppDatabase
import com.kaminski.paragownik.data.ReceiptWithClient // Klasa relacyjna paragonu z klientem
import com.kaminski.paragownik.data.daos.ReceiptDao

/**
 * ViewModel dla AllReceiptsActivity.
 * Odpowiada za dostarczanie listy wszystkich paragonów (wraz z danymi klientów),
 * posortowanej według numeru sklepu (rosnąco) i daty weryfikacji (brak daty na górze, potem rosnąco).
 */
class AllReceiptsViewModel(application: Application) : AndroidViewModel(application) {

    // Referencja do DAO paragonów
    private val receiptDao: ReceiptDao

    // LiveData emitująca listę wszystkich paragonów z danymi klientów.
    // Lista jest automatycznie aktualizowana dzięki użyciu Flow i asLiveData.
    val allReceipts: LiveData<List<ReceiptWithClient>>

    init {
        // Inicjalizacja bazy danych i DAO w bloku init
        val database = AppDatabase.getDatabase(application)
        receiptDao = database.receiptDao()

        // Pobranie Flow wszystkich posortowanych paragonów z DAO
        // i przekonwertowanie go na LiveData, które może być obserwowane przez UI.
        // Zapytanie `getAllReceiptsSorted()` w DAO dba o odpowiednie sortowanie.
        allReceipts = receiptDao.getAllReceiptsSorted().asLiveData()
    }
}