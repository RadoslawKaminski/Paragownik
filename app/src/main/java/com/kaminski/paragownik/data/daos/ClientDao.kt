package com.kaminski.paragownik.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.kaminski.paragownik.data.Client // Import encji Client
import kotlinx.coroutines.flow.Flow // Import Flow do obserwacji zmian

/**
 * Data Access Object (DAO) dla operacji na encji [Client].
 * Definiuje metody dostępu do tabeli 'clients' w bazie danych.
 * Metody oznaczone jako `suspend` są przeznaczone do wywoływania w korutynach.
 */
@Dao
interface ClientDao {

    /**
     * Wstawia nowego klienta do bazy danych.
     * @param client Obiekt [Client] do wstawienia.
     * @return ID nowo wstawionego klienta (Long).
     */
    @Insert
    suspend fun insertClient(client: Client): Long

    /**
     * Aktualizuje istniejącego klienta w bazie danych.
     * Dopasowanie odbywa się na podstawie klucza głównego obiektu [client].
     * @param client Obiekt [Client] z zaktualizowanymi danymi.
     */
    @Update
    suspend fun updateClient(client: Client)

    /**
     * Usuwa klienta z bazy danych.
     * Dopasowanie odbywa się na podstawie klucza głównego obiektu [client].
     * UWAGA: Usunięcie klienta spowoduje kaskadowe usunięcie powiązanych paragonów (zdefiniowane w [Receipt]).
     * @param client Obiekt [Client] do usunięcia.
     */
    @Delete
    suspend fun deleteClient(client: Client)

    /**
     * Pobiera wszystkich klientów z bazy danych jako [Flow].
     * Flow emituje nową listę za każdym razem, gdy dane w tabeli 'clients' ulegną zmianie.
     * @return [Flow] emitujący listę obiektów [Client].
     */
    @Query("SELECT * FROM clients")
    fun getAllClients(): Flow<List<Client>>

    /**
     * Pobiera pojedynczego klienta na podstawie jego ID.
     * Jest to operacja jednorazowa (suspend).
     * @param clientId ID klienta do pobrania.
     * @return Obiekt [Client] lub `null`, jeśli klient o podanym ID nie został znaleziony.
     */
    @Query("SELECT * FROM clients WHERE id = :clientId")
    suspend fun getClientById(clientId: Long): Client?

    // Można dodać inne metody, np. wyszukiwanie klientów po opisie, numerze aplikacji itp.
    // @Query("SELECT * FROM clients WHERE description LIKE :query")
    // suspend fun findClientsByDescription(query: String): List<Client>
}

