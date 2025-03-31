package com.kaminski.paragownik.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.kaminski.paragownik.AddClientActivity.ReceiptData
import com.kaminski.paragownik.data.Client
import com.kaminski.paragownik.data.Photo
import com.kaminski.paragownik.data.PhotoType
import com.kaminski.paragownik.data.Receipt
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.data.daos.ClientDao
import com.kaminski.paragownik.data.daos.PhotoDao
import com.kaminski.paragownik.data.daos.ReceiptDao
import com.kaminski.paragownik.data.daos.StoreDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Testy jednostkowe dla logiki zawartej w [AddClientViewModel].
 * Testujemy logikę metody `addClientWithReceiptsTransactionally` w izolacji.
 */
@ExperimentalCoroutinesApi
class AddClientViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    // Mocki zależności DAO
    private lateinit var mockClientDao: ClientDao
    private lateinit var mockReceiptDao: ReceiptDao
    private lateinit var mockStoreDao: StoreDao
    private lateinit var mockPhotoDao: PhotoDao
    // Mock Application - potrzebny tylko do konstruktora AndroidViewModel, ale nie użyjemy go do pobierania bazy
    private lateinit var mockApplication: Application

    // Testowy dispatcher dla korutyn
    private val testDispatcher = StandardTestDispatcher()
    // TestScope do uruchamiania korutyn testowych
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher) // Utwórz TestScope
        Dispatchers.setMain(testDispatcher) // Ustaw główny dispatcher

        // Inicjalizacja mocków
        mockApplication = mock() // Mock Application
        mockClientDao = mock()
        mockReceiptDao = mock()
        mockStoreDao = mock()
        mockPhotoDao = mock()

        // Usunięto domyślne mockowanie insertClient z setUp
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // Resetuj dispatcher
    }

    // Poprawiona funkcja pomocnicza createReceiptData
    private fun createReceiptData(
        storeNumber: String = "1234",
        receiptNumber: String = "5678",
        receiptDate: String = "01-01-2024",
        cashRegisterNumber: String? = null, // Dodano parametr cashRegisterNumber
        verificationDate: String? = null
    ) = ReceiptData(storeNumber, receiptNumber, receiptDate, cashRegisterNumber, verificationDate) // Zaktualizowano wywołanie konstruktora

    private val testClientPhotoUri = "file:///client/photo.jpg"
    private val testTransactionPhotoUri = "file:///transaction/photo.jpg"
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).apply { isLenient = false }

    // Funkcja pomocnicza symulująca wywołanie logiki transakcyjnej ViewModelu
    private suspend fun executeAddClientLogic(
        clientDescription: String?,
        clientAppNumber: String?,
        amoditNumber: String?,
        clientPhotoUris: List<String>,
        transactionPhotoUris: List<String>,
        receiptsData: List<ReceiptData>,
        clientDao: ClientDao,
        photoDao: PhotoDao,
        storeDao: StoreDao,
        receiptDao: ReceiptDao
    ): AddClientViewModel.AddResult {
        try {
            val clientId = clientDao.insertClient(
                Client(
                    description = clientDescription?.takeIf { it.isNotBlank() },
                    clientAppNumber = clientAppNumber?.takeIf { it.isNotBlank() },
                    amoditNumber = amoditNumber?.takeIf { it.isNotBlank() }
                )
            )
            if (clientId == -1L) {
                 println("Test detected client insertion failure (clientId = -1)")
                 return AddClientViewModel.AddResult.ERROR_DATABASE
            }
             if (clientId == null) throw IllegalStateException("Mocked clientDao.insertClient returned null unexpectedly")


            clientPhotoUris.forEach { uri ->
                photoDao.insertPhoto(Photo(clientId = clientId, uri = uri, type = PhotoType.CLIENT))
            }
            transactionPhotoUris.forEach { uri ->
                photoDao.insertPhoto(Photo(clientId = clientId, uri = uri, type = PhotoType.TRANSACTION))
            }

            for (receiptData in receiptsData) {
                if (receiptData.storeNumber.isBlank()) return AddClientViewModel.AddResult.ERROR_STORE_NUMBER_MISSING

                val receiptDate: Date = try {
                    dateFormat.parse(receiptData.receiptDate) ?: throw ParseException("Parsing returned null for receiptDate: ${receiptData.receiptDate}", 0)
                } catch (e: ParseException) {
                    println("Test caught ParseException for receiptDate: ${receiptData.receiptDate}")
                    return AddClientViewModel.AddResult.ERROR_DATE_FORMAT
                }

                val verificationDate: Date? = if (!receiptData.verificationDateString.isNullOrBlank()) {
                    try {
                        dateFormat.parse(receiptData.verificationDateString) ?: throw ParseException("Parsing returned null for verificationDate: ${receiptData.verificationDateString}", 0)
                    } catch (e: ParseException) {
                         println("Test caught ParseException for verificationDate: ${receiptData.verificationDateString}")
                        return AddClientViewModel.AddResult.ERROR_VERIFICATION_DATE_FORMAT
                    }
                } else {
                    null
                }

                var store = storeDao.getStoreByNumber(receiptData.storeNumber)
                val storeId: Long
                if (store == null) {
                    store = Store(storeNumber = receiptData.storeNumber)
                    storeDao.insertStore(store)
                    store = storeDao.getStoreByNumber(receiptData.storeNumber)
                    if (store == null) throw Exception("Failed to create/get store after insertion attempt")
                    storeId = store.id
                } else {
                    storeId = store.id
                }

                val existingReceipt = receiptDao.findByNumberDateStore(receiptData.receiptNumber, receiptDate, storeId)
                if (existingReceipt != null) return AddClientViewModel.AddResult.ERROR_DUPLICATE_RECEIPT

                receiptDao.insertReceipt(
                    Receipt(
                        receiptNumber = receiptData.receiptNumber,
                        receiptDate = receiptDate,
                        storeId = storeId,
                        cashRegisterNumber = receiptData.cashRegisterNumber?.takeIf { it.isNotBlank() }, // Dodano obsługę numeru kasy
                        verificationDate = verificationDate,
                        clientId = clientId
                    )
                )
            }
            return AddClientViewModel.AddResult.SUCCESS
        } catch (e: ParseException) {
             println("Test caught outer ParseException: ${e.message}")
             return AddClientViewModel.AddResult.ERROR_DATE_FORMAT
        } catch (e: Exception) {
            println("Test caught generic exception: ${e.message}")
            e.printStackTrace()
            return AddClientViewModel.AddResult.ERROR_UNKNOWN
        }
    }


    @Test
    fun `addClientWithReceiptsTransactionally success`() = testScope.runTest {
        // Arrange
        val clientDesc = "Test Client"
        val clientAppNum = "111"
        val amoditNum = "222"
        val testCashRegisterNum = "5" // Dodano numer kasy do testu
        val receiptData = createReceiptData(cashRegisterNumber = testCashRegisterNum) // Przekazanie numeru kasy
        val storeNumber = receiptData.storeNumber
        val receiptDateParsed = dateFormat.parse(receiptData.receiptDate)!!
        val newClientId = 1L
        val storeId = 10L
        val store = Store(id = storeId, storeNumber = storeNumber)

        // Mockowanie wywołań DAO
        whenever(mockClientDao.insertClient(any())).thenReturn(newClientId) // Mock dla tego testu
        whenever(mockPhotoDao.insertPhoto(any())).thenReturn(1L)
        whenever(mockStoreDao.getStoreByNumber(storeNumber)).thenReturn(store)
        whenever(mockReceiptDao.findByNumberDateStore(receiptData.receiptNumber, receiptDateParsed, storeId)).thenReturn(null)
        whenever(mockReceiptDao.insertReceipt(any())).thenReturn(100L)

        // Act
        val result = executeAddClientLogic(
            clientDescription = clientDesc, clientAppNumber = clientAppNum, amoditNumber = amoditNum,
            clientPhotoUris = listOf(testClientPhotoUri), transactionPhotoUris = listOf(testTransactionPhotoUri),
            receiptsData = listOf(receiptData),
            clientDao = mockClientDao, photoDao = mockPhotoDao, storeDao = mockStoreDao, receiptDao = mockReceiptDao
        )

        // Assert
        assertEquals(AddClientViewModel.AddResult.SUCCESS, result)

        // Weryfikacja wywołań DAO
        verify(mockClientDao).insertClient(check<Client> {
            assertEquals(clientDesc, it.description)
            assertEquals(clientAppNum, it.clientAppNumber)
            assertEquals(amoditNum, it.amoditNumber)
        })
        val photoCaptor = argumentCaptor<Photo>()
        verify(mockPhotoDao, times(2)).insertPhoto(photoCaptor.capture())
        val capturedPhotos = photoCaptor.allValues
        assertTrue(capturedPhotos.any { it.uri == testClientPhotoUri && it.type == PhotoType.CLIENT && it.clientId == newClientId })
        assertTrue(capturedPhotos.any { it.uri == testTransactionPhotoUri && it.type == PhotoType.TRANSACTION && it.clientId == newClientId })

        verify(mockStoreDao).getStoreByNumber(storeNumber)
        verify(mockStoreDao, never()).insertStore(any())
        verify(mockReceiptDao).findByNumberDateStore(receiptData.receiptNumber, receiptDateParsed, storeId)
        verify(mockReceiptDao).insertReceipt(check<Receipt> {
            assertEquals(receiptData.receiptNumber, it.receiptNumber)
            assertEquals(receiptDateParsed, it.receiptDate)
            assertEquals(storeId, it.storeId)
            assertEquals(testCashRegisterNum, it.cashRegisterNumber) // Weryfikacja numeru kasy
            assertEquals(newClientId, it.clientId)
            assertNull(it.verificationDate)
        })
    }

    @Test
    fun `addClientWithReceiptsTransactionally success with new store`() = testScope.runTest {
        // Arrange
        val receiptData = createReceiptData(storeNumber = "9999")
        val storeNumber = receiptData.storeNumber
        val newClientId = 1L
        val newStoreId = 11L
        val newStore = Store(id = newStoreId, storeNumber = storeNumber)

        whenever(mockClientDao.insertClient(any())).thenReturn(newClientId) // Mock dla tego testu
        whenever(mockPhotoDao.insertPhoto(any())).thenReturn(1L)
        whenever(mockStoreDao.getStoreByNumber(storeNumber)).thenReturn(null).thenReturn(newStore)
        whenever(mockStoreDao.insertStore(any())).then {}
        whenever(mockReceiptDao.findByNumberDateStore(any(), any(), eq(newStoreId))).thenReturn(null)
        whenever(mockReceiptDao.insertReceipt(any())).thenReturn(101L)

        // Act
        val result = executeAddClientLogic(null, null, null, emptyList(), emptyList(), listOf(receiptData),
            mockClientDao, mockPhotoDao, mockStoreDao, mockReceiptDao)

        // Assert
        assertEquals(AddClientViewModel.AddResult.SUCCESS, result)
        verify(mockStoreDao, times(2)).getStoreByNumber(storeNumber)
        verify(mockStoreDao).insertStore(check<Store> { assertEquals(storeNumber, it.storeNumber) })
        verify(mockReceiptDao).insertReceipt(check<Receipt> { assertEquals(newStoreId, it.storeId) })
    }

    @Test
    fun `addClientWithReceiptsTransactionally error duplicate receipt`() = testScope.runTest {
        // Arrange
        val receiptData = createReceiptData()
        val storeNumber = receiptData.storeNumber
        val receiptDateParsed = dateFormat.parse(receiptData.receiptDate)!!
        val newClientId = 1L
        val storeId = 10L
        val store = Store(id = storeId, storeNumber = storeNumber)
        val existingReceipt = Receipt(id = 99L, receiptNumber = receiptData.receiptNumber, receiptDate = receiptDateParsed, storeId = storeId, clientId = 5L)

        whenever(mockClientDao.insertClient(any())).thenReturn(newClientId) // Mock dla tego testu
        whenever(mockPhotoDao.insertPhoto(any())).thenReturn(1L)
        whenever(mockStoreDao.getStoreByNumber(storeNumber)).thenReturn(store)
        whenever(mockReceiptDao.findByNumberDateStore(receiptData.receiptNumber, receiptDateParsed, storeId)).thenReturn(existingReceipt)

        // Act
        val result = executeAddClientLogic(null, null, null, emptyList(), emptyList(), listOf(receiptData),
            mockClientDao, mockPhotoDao, mockStoreDao, mockReceiptDao)

        // Assert
        assertEquals(AddClientViewModel.AddResult.ERROR_DUPLICATE_RECEIPT, result)
        verify(mockReceiptDao).findByNumberDateStore(receiptData.receiptNumber, receiptDateParsed, storeId)
        verify(mockReceiptDao, never()).insertReceipt(any())
    }

    @Test
    fun `addClientWithReceiptsTransactionally error invalid date format`() = testScope.runTest {
        // Arrange
        val receiptData = createReceiptData(receiptDate = "invalid-date")
        whenever(mockClientDao.insertClient(any())).thenReturn(1L) // Mock dla tego testu

        // Act
        val result = executeAddClientLogic(null, null, null, emptyList(), emptyList(), listOf(receiptData),
            mockClientDao, mockPhotoDao, mockStoreDao, mockReceiptDao)

        // Assert
        assertEquals(AddClientViewModel.AddResult.ERROR_DATE_FORMAT, result)
        verify(mockClientDao).insertClient(any()) // Klient mógł zostać wstawiony
        verify(mockReceiptDao, never()).insertReceipt(any())
    }

    @Test
    fun `addClientWithReceiptsTransactionally error invalid verification date format`() = testScope.runTest {
        // Arrange
        val receiptData = createReceiptData(verificationDate = "invalid-date")

        whenever(mockClientDao.insertClient(any())).thenReturn(1L) // Mock dla tego testu
        whenever(mockPhotoDao.insertPhoto(any())).thenReturn(1L)
        whenever(mockStoreDao.getStoreByNumber(any())).thenReturn(Store(id = 1L, storeNumber = "1234"))

        // Act
        val result = executeAddClientLogic(null, null, null, emptyList(), emptyList(), listOf(receiptData),
            mockClientDao, mockPhotoDao, mockStoreDao, mockReceiptDao)

        // Assert
        assertEquals(AddClientViewModel.AddResult.ERROR_VERIFICATION_DATE_FORMAT, result)
        verify(mockReceiptDao, never()).insertReceipt(any())
    }

     @Test
    fun `addClientWithReceiptsTransactionally error store number missing`() = testScope.runTest {
        // Arrange
        val receiptData = createReceiptData(storeNumber = "")
        whenever(mockClientDao.insertClient(any())).thenReturn(1L) // Mock dla tego testu

        // Act
        val result = executeAddClientLogic(null, null, null, emptyList(), emptyList(), listOf(receiptData),
            mockClientDao, mockPhotoDao, mockStoreDao, mockReceiptDao)

        // Assert
        assertEquals(AddClientViewModel.AddResult.ERROR_STORE_NUMBER_MISSING, result)
        verify(mockClientDao).insertClient(any()) // Klient mógł zostać wstawiony
        verify(mockReceiptDao, never()).insertReceipt(any())
    }

    @Test
    fun `addClientWithReceiptsTransactionally success with null verification date`() = testScope.runTest {
        // Arrange
        val receiptData = createReceiptData(verificationDate = null)
        val storeNumber = receiptData.storeNumber
        val newClientId = 1L
        val storeId = 10L
        val store = Store(id = storeId, storeNumber = storeNumber)

        whenever(mockClientDao.insertClient(any())).thenReturn(newClientId) // Mock dla tego testu
        whenever(mockPhotoDao.insertPhoto(any())).thenReturn(1L)
        whenever(mockStoreDao.getStoreByNumber(storeNumber)).thenReturn(store)
        whenever(mockReceiptDao.findByNumberDateStore(any(), any(), any())).thenReturn(null)
        whenever(mockReceiptDao.insertReceipt(any())).thenReturn(100L)

        // Act
        val result = executeAddClientLogic(null, null, null, emptyList(), emptyList(), listOf(receiptData),
            mockClientDao, mockPhotoDao, mockStoreDao, mockReceiptDao)

        // Assert
        assertEquals(AddClientViewModel.AddResult.SUCCESS, result)
        verify(mockReceiptDao).insertReceipt(check<Receipt> {
            assertNull(it.verificationDate)
        })
    }

     @Test
    fun `addClientWithReceiptsTransactionally success with empty optional fields`() = testScope.runTest {
        // Arrange
        val receiptData = createReceiptData(cashRegisterNumber = "  ") // Pusty numer kasy
        val storeNumber = receiptData.storeNumber
        val newClientId = 1L
        val storeId = 10L
        val store = Store(id = storeId, storeNumber = storeNumber)

        whenever(mockClientDao.insertClient(any())).thenReturn(newClientId) // Mock dla tego testu
        whenever(mockPhotoDao.insertPhoto(any())).thenReturn(1L)
        whenever(mockStoreDao.getStoreByNumber(storeNumber)).thenReturn(store)
        whenever(mockReceiptDao.findByNumberDateStore(any(), any(), any())).thenReturn(null)
        whenever(mockReceiptDao.insertReceipt(any())).thenReturn(100L)

        // Act
        val result = executeAddClientLogic(
            clientDescription = "",
            clientAppNumber = null,
            amoditNumber = "   ",
            clientPhotoUris = emptyList(),
            transactionPhotoUris = emptyList(),
            receiptsData = listOf(receiptData),
            mockClientDao, mockPhotoDao, mockStoreDao, mockReceiptDao
        )

        // Assert
        assertEquals(AddClientViewModel.AddResult.SUCCESS, result)
        verify(mockClientDao).insertClient(check<Client> {
            assertNull(it.description)
            assertNull(it.clientAppNumber)
            assertNull(it.amoditNumber)
        })
        verify(mockPhotoDao, never()).insertPhoto(any())
        verify(mockReceiptDao).insertReceipt(check<Receipt> {
            assertNull(it.cashRegisterNumber) // Sprawdzenie, czy pusty numer kasy jest zapisywany jako null
        })
    }
}