package com.kaminski.paragownik

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kaminski.paragownik.viewmodel.AddClientViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel // Dodaj import StoreViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale

class AddClientActivity : AppCompatActivity() {

    private lateinit var storeNumberEditTextFirstReceipt: EditText
    private lateinit var storeNumberTextViewFirstReceipt: TextView
    private lateinit var receiptNumberEditText: EditText
    private lateinit var receiptDateEditText: EditText
    private lateinit var verificationDateEditText: EditText
    private lateinit var clientDescriptionEditText: EditText
    private lateinit var addClientButton: Button
    private lateinit var addAdditionalReceiptButton: Button
    private lateinit var addClientViewModel: AddClientViewModel
    private lateinit var storeViewModel: StoreViewModel // Dodaj StoreViewModel
    private lateinit var receiptsContainer: LinearLayout
    private val receiptFieldsList = ArrayList<ReceiptFields>()
    private var storeIdFromIntent: Long = -1L // Przechowuj storeId przekazane z Intentu

    private data class ReceiptFields(
        val storeNumberEditText: EditText? = null, // Numer drogerii dla dodatkowych paragonów
        val receiptNumberEditText: EditText,
        val receiptDateEditText: EditText
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_client)

        storeNumberEditTextFirstReceipt = findViewById(R.id.storeNumberEditText)
        storeNumberTextViewFirstReceipt = findViewById(R.id.storeNumberTextView)
        receiptNumberEditText = findViewById(R.id.receiptNumberEditText)
        receiptDateEditText = findViewById(R.id.receiptDateEditText)
        verificationDateEditText = findViewById(R.id.verificationDateEditText)
        clientDescriptionEditText = findViewById(R.id.clientDescriptionEditText)
        addClientButton = findViewById(R.id.addClientButton)
        addAdditionalReceiptButton = findViewById(R.id.addAdditionalReceiptButton)
        receiptsContainer = findViewById(R.id.receiptsContainer)

        addClientViewModel = ViewModelProvider(this).get(AddClientViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java) // Inicjalizacja StoreViewModel

        // Sprawdź, czy Activity zostało uruchomione z ReceiptListActivity (z STORE_ID)
        if (intent.hasExtra("STORE_ID")) {
            storeIdFromIntent = intent.getLongExtra("STORE_ID", -1L)
            storeNumberEditTextFirstReceipt.visibility = View.VISIBLE // Pokaż EditText, ale go zablokuj
            storeNumberTextViewFirstReceipt.visibility = View.VISIBLE // Pokaż TextView
            storeNumberEditTextFirstReceipt.isEnabled = false // Zablokuj edycję EditText

            lifecycleScope.launch {
                val store = storeViewModel.getStoreById(storeIdFromIntent) // Pobierz Store po ID
                store?.let {
                    storeNumberEditTextFirstReceipt.setText(it.storeNumber) // Ustaw numer drogerii w EditText
                }
            }
        } else {
            storeNumberEditTextFirstReceipt.visibility = View.VISIBLE
            storeNumberTextViewFirstReceipt.visibility = View.VISIBLE
        }

        setupDateEditText(receiptDateEditText)
        setupDateEditText(verificationDateEditText)

        receiptFieldsList.add(ReceiptFields(null, receiptNumberEditText, receiptDateEditText))

        addAdditionalReceiptButton.setOnClickListener {
            addNewReceiptFields()
        }

        addClientButton.setOnClickListener {
            val clientDescription = clientDescriptionEditText.text.toString()
            val verificationDateString = verificationDateEditText.text.toString() // Data weryfikacji pobrana JEDEN RAZ
            val receiptsToAdd = mutableListOf<ReceiptData>()
            var hasEmptyFields = false

            for (receiptFields in receiptFieldsList) {
                val storeNumberText = receiptFields.storeNumberEditText?.text?.toString()
                val receiptNumber = receiptFields.receiptNumberEditText.text.toString()
                val receiptDate = receiptFields.receiptDateEditText.text.toString()
                val storeNumberForReceipt = receiptFields.storeNumberEditText?.text?.toString()
                val storeNumberForFirstReceipt = storeNumberEditTextFirstReceipt.text?.toString()


                val currentStoreNumber: String = if (receiptFieldsList.indexOf(receiptFields) == 0) { // Dla pierwszego paragonu ZAWSZE pobierz numer drogerii z pola, niezależnie od punktu startowego
                    storeNumberForFirstReceipt ?: ""
                } else { // Dla dodatkowych paragonów użyj numeru drogerii z pola
                    storeNumberForReceipt ?: ""
                }


                if (receiptNumber.isEmpty() || receiptDate.isEmpty() || currentStoreNumber.isEmpty()) {
                    hasEmptyFields = true
                    break
                }

                receiptsToAdd.add(
                    ReceiptData(currentStoreNumber, receiptNumber, receiptDate) // Użyj currentStoreNumber
                )
            }

            if (hasEmptyFields) {
                Toast.makeText(this, "Wypełnij wszystkie wymagane pola paragonów", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (receiptsToAdd.isEmpty()) {
                Toast.makeText(this, "Dodaj przynajmniej jeden paragon", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            lifecycleScope.launch {
                var allReceiptsAdded = true
                for (receiptData in receiptsToAdd) {
                    val currentVerificationDateString: String? = if (receiptsToAdd.indexOf(receiptData) == 0) { // Data weryfikacji tylko dla pierwszego paragonu
                        verificationDateString
                    } else {
                        null // Dla kolejnych paragonów data weryfikacji jest null
                    }

                    val isSuccess = addClientViewModel.addClientAndReceipt(
                        storeId = -1L, // NIEUŻYWANE - ustawione na -1L
                        receiptNumber = receiptData.receiptNumber,
                        receiptDateString = receiptData.receiptDate,
                        verificationDateString = currentVerificationDateString, // Użyj currentVerificationDateString
                        clientDescription = clientDescription,
                        storeNumberForReceipt = receiptData.storeNumber // ZAWSZE przekazuj numer drogerii
                    )
                    if (!isSuccess) {
                        allReceiptsAdded = false
                    }
                }

                if (allReceiptsAdded) {
                    Toast.makeText(this@AddClientActivity, "Klient i paragony dodane", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddClientActivity, "Błąd formatu daty lub numeru drogerii.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun addNewReceiptFields() {
        val inflater = LayoutInflater.from(this)
        val receiptFieldsView = inflater.inflate(R.layout.additional_receipt_fields, receiptsContainer, false)

        val storeNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalStoreNumberEditText)
        val receiptNumberEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptNumberEditText)
        val receiptDateEditText = receiptFieldsView.findViewById<EditText>(R.id.additionalReceiptDateEditText)

        setupDateEditText(receiptDateEditText)

        receiptFieldsList.add(ReceiptFields(storeNumberEditText, receiptNumberEditText, receiptDateEditText))
        receiptsContainer.addView(receiptFieldsView)
    }


    private fun setupDateEditText(editText: EditText) {
        editText.inputType = InputType.TYPE_CLASS_DATETIME
        editText.addTextChangedListener(object : TextWatcher {
            private var updating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(editable: Editable?) {
                if (updating) {
                    return
                }
                updating = true
                val originalText = editable.toString().replace("-", "")
                var formattedText = originalText

                // Ograniczenie do cyfr
                formattedText = formattedText.filter { it.isDigit() }

                // Formatowanie daty z myślnikami
                if (formattedText.length > 2) {
                    formattedText = formattedText.substring(0, 2) + "-" + formattedText.substring(2)
                }
                if (formattedText.length > 5) {
                    formattedText = formattedText.substring(0, 5) + "-" + formattedText.substring(5)
                }

                // Ograniczenie długości do 10 znaków (DD-MM-YYYY)
                if (formattedText.length > 10) {
                    formattedText = formattedText.substring(0, 10)
                }


                if (formattedText != originalText) {
                    editable?.replace(0, editable.length, formattedText)
                }
                updating = false
            }
        })
    }

    private data class ReceiptData(
        val storeNumber: String, // Teraz przechowuje numer drogerii jako String
        val receiptNumber: String,
        val receiptDate: String
    )
}