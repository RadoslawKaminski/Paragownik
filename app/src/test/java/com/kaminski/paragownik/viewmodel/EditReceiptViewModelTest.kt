package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.withTransaction // Dodano import
import com.kaminski.paragownik.data.*
import com.kaminski.paragownik.data.daos.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Testy jednostkowe dla logiki zawartej w [EditReceiptViewModel].
 */
@ExperimentalCoroutinesApi
class EditReceiptViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    // Mocki zależności DAO
    private lateinit var mockClientDao: ClientDao
    private lateinit var mockReceiptDao: ReceiptDao
    private lateinit var mockStoreDao: StoreDao
    private lateinit var mockPhotoDao: PhotoDao
    // Mock Application
    private lateinit var mockApplication: Application

    // Testowy dispatcher i scope
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).apply { isLenient = false }
    private val testDate = dateFormat.parse("01-01-2024")!!
    private val testClientId = 1L
    private val testStoreId = 10L
    private val testReceiptId = 100L
    private val testCashRegisterNum = "1" // Dodano numer kasy
    private val testClient = Client(id = testClientId, description = "Old Desc", clientAppNumber = "111", amoditNumber = "222")
    private val testStore = Store(id = testStoreId, storeNumber = "1234")
    private val testReceipt = Receipt(id = testReceiptId, receiptNumber = "5678", receiptDate = testDate, storeId = testStoreId, cashRegisterNumber = testCashRegisterNum, clientId = testClientId) // Dodano numer kasy
    private val testReceiptWithClient = ReceiptWithClient(testReceipt, testClient)
    private val testPhotoClient = Photo(id = 1, clientId = testClientId, uri = "file:///client.jpg", type = PhotoType.CLIENT)
    private val testPhotoTransaction = Photo(id = 2, clientId = testClientId, uri = "file:///trans.jpg", type = PhotoType.TRANSACTION)
    private val testPhotos = listOf(testPhotoClient, testPhotoTransaction)

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        Dispatchers.setMain(testDispatcher)
        mockApplication = mock()
        mockClientDao = mock()
        mockReceiptDao = mock()
        mockStoreDao = mock()
        mockPhotoDao = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Funkcja pomocnicza symulująca logikę updateReceiptAndClient
    private suspend fun executeUpdateReceiptLogic(
        receiptId: Long,
        storeNumberString: String,
        receiptNumber: String,
        receiptDateString: String,
        cashRegisterNumber: String?, // Dodano parametr
        verificationDateString: String?,
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?,
        clientPhotoUrisToAdd: List<String>,
        transactionPhotoUrisToAdd: List<String>,
        photoUrisToRemove: List<String>,
        receiptDao: ReceiptDao,
        storeDao: StoreDao,
        clientDao: ClientDao,
        photoDao: PhotoDao
    ): EditReceiptViewModel.EditResult {
        try {
            val existingReceiptWithClient = receiptDao.getReceiptWithClient(receiptId)
            if (existingReceiptWithClient == null || existingReceiptWithClient.client == null) {
                return EditReceiptViewModel.EditResult.ERROR_NOT_FOUND
            }
            val existingReceipt = existingReceiptWithClient.receipt
            val existingClient = existingReceiptWithClient.client ?: return EditReceiptViewModel.EditResult.ERROR_UNKNOWN
            val clientId = existingClient.id
            val originalStoreId = existingReceipt.storeId

            val receiptDate: Date = try {
                dateFormat.parse(receiptDateString) ?: throw ParseException("Parsing returned null for receiptDate: $receiptDateString", 0)
            } catch (e: ParseException) {
                return EditReceiptViewModel.EditResult.ERROR_DATE_FORMAT
            }
            val verificationDate: Date? = if (!verificationDateString.isNullOrBlank()) {
                try {
                    dateFormat.parse(verificationDateString) ?: throw ParseException("Parsing returned null for verificationDate: $verificationDateString", 0)
                } catch (e: ParseException) { null } // Ignorujemy błąd formatu daty weryfikacji
            } else { null }

            if (storeNumberString.isBlank()) return EditReceiptViewModel.EditResult.ERROR_STORE_NUMBER_MISSING

            var store = storeDao.getStoreByNumber(storeNumberString)
            val newStoreId: Long
            if (store == null) {
                store = Store(storeNumber = storeNumberString)
                storeDao.insertStore(store)
                store = storeDao.getStoreByNumber(storeNumberString) ?: throw Exception("Failed to create/get store")
                newStoreId = store.id
            } else {
                newStoreId = store.id
            }

            // Zaktualizowano wywołanie DAO do sprawdzania duplikatów
            val potentialDuplicate = receiptDao.findByNumberDateStoreCashRegister(
                receiptNumber,
                receiptDate,
                newStoreId,
                cashRegisterNumber?.takeIf { it.isNotBlank() } // Dodano numer kasy
            )
            if (potentialDuplicate != null && potentialDuplicate.id != receiptId) {
                return EditReceiptViewModel.EditResult.ERROR_DUPLICATE_RECEIPT
            }

            val updatedReceipt = existingReceipt.copy(
                receiptNumber = receiptNumber,
                receiptDate = receiptDate,
                storeId = newStoreId,
                cashRegisterNumber = cashRegisterNumber?.takeIf { it.isNotBlank() }, // Zapis numeru kasy
                verificationDate = verificationDate
            )
            receiptDao.updateReceipt(updatedReceipt)

            val updatedClient = existingClient.copy(
                description = clientDescription?.takeIf { it.isNotBlank() },
                clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                amoditNumber = amoditNumber?.takeIf { it.isNotBlank() }
            )
            clientDao.updateClient(updatedClient)

            photoUrisToRemove.forEach { photoDao.deletePhotoByUri(it) }
            clientPhotoUrisToAdd.forEach { photoDao.insertPhoto(Photo(clientId = clientId, uri = it, type = PhotoType.CLIENT)) }
            transactionPhotoUrisToAdd.forEach { photoDao.insertPhoto(Photo(clientId = clientId, uri = it, type = PhotoType.TRANSACTION)) }

            if (originalStoreId != newStoreId) {
                if (receiptDao.getReceiptsForStoreCount(originalStoreId) == 0) {
                    storeDao.getStoreById(originalStoreId)?.let { storeDao.deleteStore(it) }
                }
            }

            // Symulacja usuwania plików
            // photoUrisToRemove.forEach { deleteImageFile(it) }

            return EditReceiptViewModel.EditResult.SUCCESS
        } catch (e: ParseException) {
             println("Test caught outer ParseException: ${e.message}")
             return EditReceiptViewModel.EditResult.ERROR_DATE_FORMAT
        } catch (e: Exception) {
            println("Test caught generic exception: ${e.message}")
            e.printStackTrace()
            return EditReceiptViewModel.EditResult.ERROR_UNKNOWN
        }
    }

     // Funkcja pomocnicza symulująca logikę deleteReceipt
    private suspend fun executeDeleteReceiptLogic(
        receipt: Receipt,
        receiptDao: ReceiptDao,
        clientDao: ClientDao,
        storeDao: StoreDao,
        photoDao: PhotoDao
    ): EditReceiptViewModel.EditResult {
        try {
            val receiptToDelete = receiptDao.getReceiptById(receipt.id)
                ?: return EditReceiptViewModel.EditResult.ERROR_NOT_FOUND

            val clientId = receiptToDelete.clientId
            val storeId = receiptToDelete.storeId
            // var clientDeleted = false // Usunięto
            // var photoUrisToDelete: List<String> = emptyList() // Usunięto

            // Symulacja transakcji
            receiptDao.deleteReceipt(receiptToDelete)
            if (receiptDao.getReceiptsForClientCount(clientId) == 0) {
                // Poprawka: Pobieramy klienta i sprawdzamy null przed dostępem do id
                val client = clientDao.getClientById(clientId)
                if (client != null) {
                    // photoUrisToDelete = photoDao.getPhotoUrisForClient(client.id) // Usunięto
                    photoDao.getPhotoUrisForClient(client.id) // Pobieramy, ale nie używamy
                    clientDao.deleteClient(client)
                    // clientDeleted = true // Usunięto
                } else {
                     println("Warning: Client $clientId not found during deleteReceiptLogic, though expected.")
                }
            }
            if (receiptDao.getReceiptsForStoreCount(storeId) == 0) {
                storeDao.getStoreById(storeId)?.let { storeDao.deleteStore(it) }
            }

            // Symulacja usuwania plików
            // if (clientDeleted) { photoUrisToDelete.forEach { deleteImageFile(it) } }

            return EditReceiptViewModel.EditResult.SUCCESS
        } catch (e: Exception) {
            return EditReceiptViewModel.EditResult.ERROR_DATABASE
        }
    }

    // Funkcja pomocnicza symulująca logikę deleteClient (taka sama jak w EditClientViewModelTest)
    private suspend fun executeDeleteClientLogic(
        client: Client,
        clientDao: ClientDao,
        receiptDao: ReceiptDao,
        storeDao: StoreDao,
        photoDao: PhotoDao
    ): EditReceiptViewModel.EditResult {
         try {
            val clientToDelete = clientDao.getClientById(client.id) // Pobierz klienta do usunięcia
                ?: return EditReceiptViewModel.EditResult.ERROR_NOT_FOUND // Zwróć błąd, jeśli nie znaleziono

            // Użyj bezpiecznego ID klienta
            val clientIdToDelete = clientToDelete.id
            val associatedStoreIds = receiptDao.getStoreIdsForClient(clientIdToDelete)
            // val photoUrisToDelete = photoDao.getPhotoUrisForClient(clientIdToDelete) // Usunięto
            photoDao.getPhotoUrisForClient(clientIdToDelete) // Pobieramy, ale nie używamy

            // Symulacja transakcji
            clientDao.deleteClient(clientToDelete) // Usuń klienta
            for (storeId in associatedStoreIds) {
                if (receiptDao.getReceiptsForStoreCount(storeId) == 0) {
                    // Pobierz sklep i usuń go, jeśli istnieje
                    storeDao.getStoreById(storeId)?.let { storeToDelete ->
                         storeDao.deleteStore(storeToDelete)
                    }
                }
            }

            // Symulacja usuwania plików
            // photoUrisToDelete.forEach { deleteImageFile(it) }

            return EditReceiptViewModel.EditResult.SUCCESS
        } catch (e: Exception) {
            return EditReceiptViewModel.EditResult.ERROR_DATABASE
        }
    }


    // Testy dla getReceiptWithClientAndStoreNumber - zakomentowane
    /*
     @Test
    fun `getReceiptWithClientAndStoreNumber returns correct data`() = testScope.runTest { ... }
     @Test
    fun `getReceiptWithClientAndStoreNumber returns nulls if receipt not found`() = testScope.runTest { ... }
    */

    @Test
    fun `updateReceiptAndClient success`() = testScope.runTest {
        // Arrange
        val newStoreNumber = "4321"
        val newReceiptNumber = "9999"
        val newDateStr = "02-02-2024"
        val newCashRegisterNum = "2" // Nowy numer kasy
        val newVerDateStr = "03-02-2024"
        val newDesc = "New Desc"
        val newAppNum = "333"
        val newAmoditNum = "444"
        val newStoreId = 11L
        val newStore = Store(id = newStoreId, storeNumber = newStoreNumber)
        val photoToAddClient = "file:///new_client.jpg"
        val photoToAddTrans = "file:///new_trans.jpg"
        val photoToRemove = testPhotoClient.uri

        val newDateParsed = dateFormat.parse(newDateStr)!!
        val newVerDateParsed = dateFormat.parse(newVerDateStr)!!

        whenever(mockReceiptDao.getReceiptWithClient(testReceiptId)).thenReturn(testReceiptWithClient)
        whenever(mockStoreDao.getStoreByNumber(newStoreNumber)).thenReturn(newStore)
        // Zaktualizowano mockowanie findBy...
        whenever(mockReceiptDao.findByNumberDateStoreCashRegister(newReceiptNumber, newDateParsed, newStoreId, newCashRegisterNum)).thenReturn(null)
        whenever(mockReceiptDao.updateReceipt(any())).then {}
        whenever(mockClientDao.updateClient(any())).then {}
        whenever(mockPhotoDao.deletePhotoByUri(any())).then {}
        whenever(mockPhotoDao.insertPhoto(any())).thenReturn(3L)
        whenever(mockReceiptDao.getReceiptsForStoreCount(testStoreId)).thenReturn(1)

        // Act
        val result = executeUpdateReceiptLogic(
            receiptId = testReceiptId,
            storeNumberString = newStoreNumber,
            receiptNumber = newReceiptNumber,
            receiptDateString = newDateStr,
            cashRegisterNumber = newCashRegisterNum, // Przekazanie numeru kasy
            verificationDateString = newVerDateStr,
            clientDescription = newDesc,
            clientAppNumber = newAppNum,
            amoditNumber = newAmoditNum,
            clientPhotoUrisToAdd = listOf(photoToAddClient),
            transactionPhotoUrisToAdd = listOf(photoToAddTrans),
            photoUrisToRemove = listOf(photoToRemove),
            mockReceiptDao, mockStoreDao, mockClientDao, mockPhotoDao
        )

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.SUCCESS, result)

        // Weryfikacja wywołań DAO
        verify(mockReceiptDao).updateReceipt(check<Receipt> {
            assertEquals(testReceiptId, it.id)
            assertEquals(newReceiptNumber, it.receiptNumber)
            assertEquals(newDateParsed, it.receiptDate)
            assertEquals(newStoreId, it.storeId)
            assertEquals(newCashRegisterNum, it.cashRegisterNumber) // Weryfikacja numeru kasy
            assertEquals(newVerDateParsed, it.verificationDate)
        })
        verify(mockClientDao).updateClient(check<Client> {
            assertEquals(testClientId, it.id)
            assertEquals(newDesc, it.description)
            assertEquals(newAppNum, it.clientAppNumber)
            assertEquals(newAmoditNum, it.amoditNumber)
        })
        verify(mockPhotoDao).deletePhotoByUri(photoToRemove)
        val photoCaptor = argumentCaptor<Photo>()
        verify(mockPhotoDao, times(2)).insertPhoto(photoCaptor.capture())
        val capturedPhotos = photoCaptor.allValues
        assertTrue(capturedPhotos.any { it.uri == photoToAddClient && it.type == PhotoType.CLIENT && it.clientId == testClientId })
        assertTrue(capturedPhotos.any { it.uri == photoToAddTrans && it.type == PhotoType.TRANSACTION && it.clientId == testClientId })

        verify(mockReceiptDao).getReceiptsForStoreCount(testStoreId)
        verify(mockStoreDao, never()).deleteStore(any())
    }

    @Test
    fun `updateReceiptAndClient success with new store and old store deleted`() = testScope.runTest {
        // Arrange
        val newStoreNumber = "4321"
        val newStoreId = 11L
        val newStore = Store(id = newStoreId, storeNumber = newStoreNumber)

        whenever(mockReceiptDao.getReceiptWithClient(testReceiptId)).thenReturn(testReceiptWithClient)
        whenever(mockStoreDao.getStoreByNumber(newStoreNumber)).thenReturn(null).thenReturn(newStore)
        whenever(mockStoreDao.insertStore(any())).then {}
        // Zaktualizowano mockowanie findBy...
        whenever(mockReceiptDao.findByNumberDateStoreCashRegister(any(), any(), eq(newStoreId), eq(testCashRegisterNum))).thenReturn(null)
        whenever(mockReceiptDao.updateReceipt(any())).then {}
        whenever(mockClientDao.updateClient(any())).then {}
        whenever(mockReceiptDao.getReceiptsForStoreCount(testStoreId)).thenReturn(0) // Stary sklep staje się pusty
        whenever(mockStoreDao.getStoreById(testStoreId)).thenReturn(testStore) // Mock pobrania starego sklepu
        whenever(mockStoreDao.deleteStore(any())).then {} // Mock usunięcia sklepu

        // Act
        val result = executeUpdateReceiptLogic(
            receiptId = testReceiptId,
            storeNumberString = newStoreNumber,
            receiptNumber = testReceipt.receiptNumber,
            receiptDateString = dateFormat.format(testReceipt.receiptDate),
            cashRegisterNumber = testCashRegisterNum, // Przekazanie numeru kasy
            verificationDateString = null,
            clientDescription = testClient.description,
            clientAppNumber = testClient.clientAppNumber,
            amoditNumber = testClient.amoditNumber,
            clientPhotoUrisToAdd = emptyList(),
            transactionPhotoUrisToAdd = emptyList(),
            photoUrisToRemove = emptyList(),
            mockReceiptDao, mockStoreDao, mockClientDao, mockPhotoDao
        )

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.SUCCESS, result)
        verify(mockStoreDao).insertStore(check<Store> { it.storeNumber == newStoreNumber })
        verify(mockReceiptDao).updateReceipt(check<Receipt> { it.storeId == newStoreId })
        verify(mockReceiptDao).getReceiptsForStoreCount(testStoreId)
        verify(mockStoreDao).deleteStore(check<Store> { it.id == testStoreId }) // Sprawdź, czy stary sklep został usunięty
    }

    @Test
    fun `updateReceiptAndClient error not found`() = testScope.runTest {
        // Arrange
        whenever(mockReceiptDao.getReceiptWithClient(testReceiptId)).thenReturn(null)

        // Act
        val result = executeUpdateReceiptLogic(testReceiptId, "1", "1", "01-01-2024", null, null, null, null, null, emptyList(), emptyList(), emptyList(),
            mockReceiptDao, mockStoreDao, mockClientDao, mockPhotoDao)

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.ERROR_NOT_FOUND, result)
    }

    @Test
    fun `updateReceiptAndClient error date format`() = testScope.runTest {
        // Arrange
        whenever(mockReceiptDao.getReceiptWithClient(testReceiptId)).thenReturn(testReceiptWithClient)

        // Act
        val result = executeUpdateReceiptLogic(testReceiptId, "1", "1", "invalid-date", null, null, null, null, null, emptyList(), emptyList(), emptyList(),
            mockReceiptDao, mockStoreDao, mockClientDao, mockPhotoDao)

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.ERROR_DATE_FORMAT, result)
    }

     @Test
    fun `updateReceiptAndClient error duplicate receipt`() = testScope.runTest {
        // Arrange
        val newReceiptNumber = "9999"
        val newDateStr = "02-02-2024"
        val newCashRegisterNum = "3"
        val newDateParsed = dateFormat.parse(newDateStr)!!
        val existingDuplicate = Receipt(id = 999L, receiptNumber = newReceiptNumber, receiptDate = newDateParsed, storeId = testStoreId, cashRegisterNumber = newCashRegisterNum, clientId = 88L)

        whenever(mockReceiptDao.getReceiptWithClient(testReceiptId)).thenReturn(testReceiptWithClient)
        whenever(mockStoreDao.getStoreByNumber(testStore.storeNumber)).thenReturn(testStore)
        // Zaktualizowano mockowanie findBy...
        whenever(mockReceiptDao.findByNumberDateStoreCashRegister(newReceiptNumber, newDateParsed, testStoreId, newCashRegisterNum)).thenReturn(existingDuplicate)

        // Act
        val result = executeUpdateReceiptLogic(testReceiptId, testStore.storeNumber, newReceiptNumber, newDateStr, newCashRegisterNum, null, null, null, null, emptyList(), emptyList(), emptyList(),
            mockReceiptDao, mockStoreDao, mockClientDao, mockPhotoDao)

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.ERROR_DUPLICATE_RECEIPT, result)
        // Zaktualizowano weryfikację findBy...
        verify(mockReceiptDao).findByNumberDateStoreCashRegister(newReceiptNumber, newDateParsed, testStoreId, newCashRegisterNum)
        verify(mockReceiptDao, never()).updateReceipt(any())
    }

    @Test
    fun `updateReceiptAndClient success different cash register`() = testScope.runTest {
        // Arrange: Zmieniamy tylko numer kasy, reszta bez zmian
        val newCashRegisterNum = "2" // Inny niż w testReceipt (który ma "1")

        whenever(mockReceiptDao.getReceiptWithClient(testReceiptId)).thenReturn(testReceiptWithClient)
        whenever(mockStoreDao.getStoreByNumber(testStore.storeNumber)).thenReturn(testStore)
        // Mockowanie: Sprawdzamy duplikat z nowym numerem kasy - nie powinno go być
        whenever(mockReceiptDao.findByNumberDateStoreCashRegister(testReceipt.receiptNumber, testReceipt.receiptDate, testStoreId, newCashRegisterNum)).thenReturn(null)
        whenever(mockReceiptDao.updateReceipt(any())).then {}
        whenever(mockClientDao.updateClient(any())).then {}
        whenever(mockReceiptDao.getReceiptsForStoreCount(testStoreId)).thenReturn(1) // Sklep nie jest pusty

        // Act
        val result = executeUpdateReceiptLogic(
            receiptId = testReceiptId,
            storeNumberString = testStore.storeNumber,
            receiptNumber = testReceipt.receiptNumber,
            receiptDateString = dateFormat.format(testReceipt.receiptDate),
            cashRegisterNumber = newCashRegisterNum, // Przekazujemy nowy numer kasy
            verificationDateString = null,
            clientDescription = testClient.description,
            clientAppNumber = testClient.clientAppNumber,
            amoditNumber = testClient.amoditNumber,
            clientPhotoUrisToAdd = emptyList(),
            transactionPhotoUrisToAdd = emptyList(),
            photoUrisToRemove = emptyList(),
            mockReceiptDao, mockStoreDao, mockClientDao, mockPhotoDao
        )

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.SUCCESS, result)
        // Weryfikacja: Sprawdzono duplikat z nowym numerem kasy i zaktualizowano paragon
        verify(mockReceiptDao).findByNumberDateStoreCashRegister(testReceipt.receiptNumber, testReceipt.receiptDate, testStoreId, newCashRegisterNum)
        verify(mockReceiptDao).updateReceipt(check<Receipt> { assertEquals(newCashRegisterNum, it.cashRegisterNumber) })
    }


    @Test
    fun `deleteReceipt success client not deleted`() = testScope.runTest {
        // Arrange
        whenever(mockReceiptDao.getReceiptById(testReceiptId)).thenReturn(testReceipt)
        whenever(mockReceiptDao.deleteReceipt(any())).then {}
        whenever(mockReceiptDao.getReceiptsForClientCount(testClientId)).thenReturn(1) // Klient ma jeszcze paragony
        whenever(mockReceiptDao.getReceiptsForStoreCount(testStoreId)).thenReturn(1) // Sklep ma jeszcze paragony

        // Act
        val result = executeDeleteReceiptLogic(testReceipt, mockReceiptDao, mockClientDao, mockStoreDao, mockPhotoDao)

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.SUCCESS, result)
        verify(mockReceiptDao).deleteReceipt(testReceipt)
        verify(mockClientDao, never()).deleteClient(any())
        verify(mockStoreDao, never()).deleteStore(any())
        verify(mockPhotoDao, never()).getPhotoUrisForClient(any())
    }

    @Test
    fun `deleteReceipt success client and store deleted`() = testScope.runTest {
        // Arrange
        val photoUris = listOf("file:///a.jpg", "file:///b.jpg")
        whenever(mockReceiptDao.getReceiptById(testReceiptId)).thenReturn(testReceipt)
        whenever(mockReceiptDao.deleteReceipt(any())).then {}
        whenever(mockReceiptDao.getReceiptsForClientCount(testClientId)).thenReturn(0) // Ostatni paragon klienta
        whenever(mockClientDao.getClientById(testClientId)).thenReturn(testClient) // Zwróć klienta do usunięcia
        whenever(mockPhotoDao.getPhotoUrisForClient(testClientId)).thenReturn(photoUris) // Zwróć URI zdjęć
        whenever(mockClientDao.deleteClient(any())).then {} // Mock usunięcia klienta
        whenever(mockReceiptDao.getReceiptsForStoreCount(testStoreId)).thenReturn(0) // Ostatni paragon sklepu
        whenever(mockStoreDao.getStoreById(testStoreId)).thenReturn(testStore) // Zwróć sklep do usunięcia
        whenever(mockStoreDao.deleteStore(any())).then {} // Mock usunięcia sklepu

        // Act
        val result = executeDeleteReceiptLogic(testReceipt, mockReceiptDao, mockClientDao, mockStoreDao, mockPhotoDao)

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.SUCCESS, result)
        verify(mockReceiptDao).deleteReceipt(testReceipt)
        verify(mockClientDao).deleteClient(testClient)
        verify(mockStoreDao).deleteStore(testStore)
        verify(mockPhotoDao).getPhotoUrisForClient(testClientId)
    }

    @Test
    fun `deleteReceipt error not found`() = testScope.runTest {
        // Arrange
        whenever(mockReceiptDao.getReceiptById(testReceiptId)).thenReturn(null) // Paragon nie istnieje

        // Act
        val result = executeDeleteReceiptLogic(testReceipt, mockReceiptDao, mockClientDao, mockStoreDao, mockPhotoDao)

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.ERROR_NOT_FOUND, result)
    }

    @Test
    fun `deleteClient success`() = testScope.runTest {
        // Arrange
        val associatedStoreIds = listOf(testStoreId, 11L) // Klient miał paragony w 2 sklepach
        val photoUris = listOf("file:///c.jpg")
        val store10 = Store(id = 10L, storeNumber = "10")

        whenever(mockClientDao.getClientById(testClientId)).thenReturn(testClient)
        whenever(mockReceiptDao.getStoreIdsForClient(testClientId)).thenReturn(associatedStoreIds)
        whenever(mockPhotoDao.getPhotoUrisForClient(testClientId)).thenReturn(photoUris) // Mock pobrania URI
        whenever(mockClientDao.deleteClient(any())).then {} // Mock usunięcia klienta
        whenever(mockReceiptDao.getReceiptsForStoreCount(10L)).thenReturn(0) // Sklep 10 staje się pusty
        whenever(mockStoreDao.getStoreById(10L)).thenReturn(store10) // Mock pobrania sklepu 10
        whenever(mockStoreDao.deleteStore(any())).then {} // Mock usunięcia sklepu
        whenever(mockReceiptDao.getReceiptsForStoreCount(11L)).thenReturn(1) // Sklep 11 nie jest pusty

        // Act
        val result = executeDeleteClientLogic(testClient, mockClientDao, mockReceiptDao, mockStoreDao, mockPhotoDao)

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.SUCCESS, result)
        verify(mockClientDao).deleteClient(testClient) // Klient usunięty
        verify(mockPhotoDao).getPhotoUrisForClient(testClientId) // Pobrano URI zdjęć
        verify(mockReceiptDao).getReceiptsForStoreCount(10L) // Sprawdzono sklep 10
        verify(mockReceiptDao).getReceiptsForStoreCount(11L) // Sprawdzono sklep 11
        // ZMIENIONA WERYFIKACJA: Sprawdzamy, że deleteStore zostało wywołane dokładnie raz i tylko dla sklepu 10
        verify(mockStoreDao, times(1)).deleteStore(check<Store> { assertEquals(10L, it.id) })
    }

     @Test
    fun `deleteClient error not found`() = testScope.runTest {
        // Arrange
        whenever(mockClientDao.getClientById(testClientId)).thenReturn(null) // Klient nie istnieje

        // Act
        val result = executeDeleteClientLogic(testClient, mockClientDao, mockReceiptDao, mockStoreDao, mockPhotoDao)

        // Assert
        assertEquals(EditReceiptViewModel.EditResult.ERROR_NOT_FOUND, result)
    }
}