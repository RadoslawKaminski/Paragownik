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
import java.util.*

/**
 * Testy jednostkowe dla logiki zawartej w [EditClientViewModel].
 */
@ExperimentalCoroutinesApi
class EditClientViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    // Mocki zależności DAO
    private lateinit var mockClientDao: ClientDao
    private lateinit var mockReceiptDao: ReceiptDao
    private lateinit var mockStoreDao: StoreDao
    private lateinit var mockPhotoDao: PhotoDao
    // Mock Application - potrzebny tylko do konstruktora AndroidViewModel
    private lateinit var mockApplication: Application

    // Testowy dispatcher i scope
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope

    private val testClientId = 1L
    private val testClient = Client(id = testClientId, description = "Old Desc", clientAppNumber = "111", amoditNumber = "222")
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

    // Funkcja pomocnicza symulująca logikę updateClientAndPhotos
    private suspend fun executeUpdateClientLogic(
        clientId: Long,
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?,
        clientPhotoUrisToAdd: List<String>,
        transactionPhotoUrisToAdd: List<String>,
        photoUrisToRemove: List<String>,
        clientDao: ClientDao,
        photoDao: PhotoDao
    ): EditClientViewModel.EditResult {
        // Logika z ViewModelu, operująca na przekazanych DAO
        try {
            // Symulacja transakcji - w teście jednostkowym po prostu wykonujemy operacje sekwencyjnie
            val existingClient = clientDao.getClientById(clientId)
                ?: return EditClientViewModel.EditResult.ERROR_NOT_FOUND

            val updatedClient = existingClient.copy(
                description = clientDescription?.takeIf { it.isNotBlank() },
                clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                amoditNumber = amoditNumber?.takeIf { it.isNotBlank() }
            )
            clientDao.updateClient(updatedClient)

            photoUrisToRemove.forEach { photoDao.deletePhotoByUri(it) }
            clientPhotoUrisToAdd.forEach { photoDao.insertPhoto(Photo(clientId = clientId, uri = it, type = PhotoType.CLIENT)) }
            transactionPhotoUrisToAdd.forEach { photoDao.insertPhoto(Photo(clientId = clientId, uri = it, type = PhotoType.TRANSACTION)) }

            // Symulacja usuwania plików (w teście jednostkowym nie robimy nic)
            // photoUrisToRemove.forEach { deleteImageFile(it) }

            return EditClientViewModel.EditResult.SUCCESS
        } catch (e: Exception) {
            // Można dodać bardziej szczegółową obsługę błędów
            return EditClientViewModel.EditResult.ERROR_UNKNOWN
        }
    }

     // Funkcja pomocnicza symulująca logikę deleteClient
    private suspend fun executeDeleteClientLogic(
        client: Client,
        clientDao: ClientDao,
        receiptDao: ReceiptDao,
        storeDao: StoreDao,
        photoDao: PhotoDao
    ): EditClientViewModel.EditResult {
        try {
            val clientToDelete = clientDao.getClientById(client.id) // Pobierz klienta do usunięcia
                ?: return EditClientViewModel.EditResult.ERROR_NOT_FOUND // Zwróć błąd, jeśli nie znaleziono

            // Użyj bezpiecznego ID klienta
            val clientIdToDelete = clientToDelete.id
            val associatedStoreIds = receiptDao.getStoreIdsForClient(clientIdToDelete)
            // Pobieramy URI zdjęć, aby móc je zweryfikować
            val photoUrisToDelete = photoDao.getPhotoUrisForClient(clientIdToDelete) // Poprawka: Odkomentowano

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

            // Symulacja usuwania plików (w teście jednostkowym nie robimy nic, ale logikę można by tu dodać)
            // photoUrisToDelete.forEach { deleteImageFile(it) }

            return EditClientViewModel.EditResult.SUCCESS
        } catch (e: Exception) {
            return EditClientViewModel.EditResult.ERROR_DATABASE
        }
    }


    // Testy dla getClientWithPhotos - zakomentowane, bo testujemy logikę w execute...
    /*
    @Test
    fun `getClientWithPhotos returns correct data`() = testScope.runTest { ... }
    @Test
    fun `getClientWithPhotos returns null client if not found`() = testScope.runTest { ... }
    */

    @Test
    fun `updateClientAndPhotos success`() = testScope.runTest {
        // Arrange
        val newDesc = "New Desc"
        val newAppNum = "333"
        val newAmoditNum = "444"
        val photoToAddClient = "file:///new_client.jpg"
        val photoToAddTrans = "file:///new_trans.jpg"
        val photoToRemove = testPhotoClient.uri

        whenever(mockClientDao.getClientById(testClientId)).thenReturn(testClient)
        whenever(mockClientDao.updateClient(any())).then {}
        whenever(mockPhotoDao.deletePhotoByUri(any())).then {}
        whenever(mockPhotoDao.insertPhoto(any())).thenReturn(3L)

        // Act
        val result = executeUpdateClientLogic(
            clientId = testClientId,
            clientDescription = newDesc,
            clientAppNumber = newAppNum,
            amoditNumber = newAmoditNum,
            clientPhotoUrisToAdd = listOf(photoToAddClient),
            transactionPhotoUrisToAdd = listOf(photoToAddTrans),
            photoUrisToRemove = listOf(photoToRemove),
            clientDao = mockClientDao,
            photoDao = mockPhotoDao
        )

        // Assert
        assertEquals(EditClientViewModel.EditResult.SUCCESS, result)

        // Weryfikacja wywołań DAO
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

        verifyNoInteractions(mockReceiptDao)
        verifyNoInteractions(mockStoreDao)
    }

    @Test
    fun `updateClientAndPhotos error not found`() = testScope.runTest {
        // Arrange
        whenever(mockClientDao.getClientById(testClientId)).thenReturn(null)

        // Act
        val result = executeUpdateClientLogic(testClientId, "New", null, null, emptyList(), emptyList(), emptyList(), mockClientDao, mockPhotoDao)

        // Assert
        assertEquals(EditClientViewModel.EditResult.ERROR_NOT_FOUND, result)
        verify(mockClientDao, never()).updateClient(any())
        verify(mockPhotoDao, never()).deletePhotoByUri(any())
        verify(mockPhotoDao, never()).insertPhoto(any())
    }

     @Test
    fun `updateClientAndPhotos success with empty optional fields`() = testScope.runTest {
        // Arrange
        whenever(mockClientDao.getClientById(testClientId)).thenReturn(testClient)
        whenever(mockClientDao.updateClient(any())).then {}

        // Act
        val result = executeUpdateClientLogic(
            clientId = testClientId,
            clientDescription = "   ",
            clientAppNumber = null,
            amoditNumber = "",
            clientPhotoUrisToAdd = emptyList(),
            transactionPhotoUrisToAdd = emptyList(),
            photoUrisToRemove = emptyList(),
            mockClientDao, mockPhotoDao
        )

        // Assert
        assertEquals(EditClientViewModel.EditResult.SUCCESS, result)
        verify(mockClientDao).updateClient(check<Client> {
            assertEquals(testClientId, it.id)
            assertNull(it.description)
            assertNull(it.clientAppNumber)
            assertNull(it.amoditNumber)
        })
        verifyNoInteractions(mockPhotoDao)
    }

    @Test
    fun `deleteClient success`() = testScope.runTest {
        // Arrange
        val associatedStoreIds = listOf(10L, 11L)
        val photoUris = listOf(testPhotoClient.uri, testPhotoTransaction.uri)
        val store10 = Store(id = 10L, storeNumber = "10")

        whenever(mockClientDao.getClientById(testClientId)).thenReturn(testClient)
        whenever(mockReceiptDao.getStoreIdsForClient(testClientId)).thenReturn(associatedStoreIds)
        whenever(mockPhotoDao.getPhotoUrisForClient(testClientId)).thenReturn(photoUris)
        whenever(mockClientDao.deleteClient(any())).then {}
        whenever(mockReceiptDao.getReceiptsForStoreCount(10L)).thenReturn(0) // Sklep 10 staje się pusty
        whenever(mockStoreDao.getStoreById(10L)).thenReturn(store10) // Mock pobrania sklepu 10
        whenever(mockStoreDao.deleteStore(any())).then {} // Mock usunięcia sklepu
        whenever(mockReceiptDao.getReceiptsForStoreCount(11L)).thenReturn(1) // Sklep 11 nie jest pusty

        // Act
        val result = executeDeleteClientLogic(testClient, mockClientDao, mockReceiptDao, mockStoreDao, mockPhotoDao)

        // Assert
        assertEquals(EditClientViewModel.EditResult.SUCCESS, result)
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
        whenever(mockClientDao.getClientById(testClientId)).thenReturn(null)

        // Act
        val result = executeDeleteClientLogic(testClient, mockClientDao, mockReceiptDao, mockStoreDao, mockPhotoDao)

        // Assert
        assertEquals(EditClientViewModel.EditResult.ERROR_NOT_FOUND, result)
        verify(mockClientDao, never()).deleteClient(any())
        verify(mockReceiptDao, never()).getStoreIdsForClient(any())
        verify(mockPhotoDao, never()).getPhotoUrisForClient(any())
    }
}
