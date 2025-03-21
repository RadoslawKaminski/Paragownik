package com.kaminski.paragownik

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kaminski.paragownik.viewmodel.AddClientViewModel
import kotlinx.coroutines.launch
import java.util.ArrayList

class AddClientActivity : AppCompatActivity() {

    private lateinit var receiptNumberEditText: EditText
    private lateinit var receiptDateEditText: EditText
    private lateinit var verificationDateEditText: EditText
    private lateinit var clientDescriptionEditText: EditText
    private lateinit var addClientButton: Button
    private lateinit var addAdditionalReceiptButton: Button
    private lateinit var addClientViewModel: AddClientViewModel
    private lateinit var receiptsContainer: LinearLayout
    private val receiptFieldsList = ArrayList<ReceiptFields>()

    private data class ReceiptFields(
        val storeNumberEditText: EditText? = null, // Numer drogerii dla dodatkowych paragonów - teraz EditText dla numeru drogerii
        val receiptNumberEditText: EditText,
        val receiptDateEditText: EditText
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_client)

        receiptNumberEditText = findViewById(R.id.receiptNumberEditText)
        receiptDateEditText = findViewById(R.id.receiptDateEditText)
        verificationDateEditText = findViewById(R.id.verificationDateEditText)
        clientDescriptionEditText = findViewById(R.id.clientDescriptionEditText)
        addClientButton = findViewById(R.id.addClientButton)
        addAdditionalReceiptButton = findViewById(R.id.addAdditionalReceiptButton)
        receiptsContainer = findViewById(R.id.receiptsContainer)

        addClientViewModel = ViewModelProvider(this).get(AddClientViewModel::class.java)

        val storeId = intent.getLongExtra("STORE_ID", -1L)

        setupDateEditText(receiptDateEditText)
        setupDateEditText(verificationDateEditText)

        receiptFieldsList.add(ReceiptFields(null, receiptNumberEditText, receiptDateEditText)) // Pierwszy paragon - bez pola numeru drogerii

        addAdditionalReceiptButton.setOnClickListener {
            addNewReceiptFields()
        }

        addClientButton.setOnClickListener {
            val clientDescription = clientDescriptionEditText.text.toString()
            val receiptsToAdd = mutableListOf<ReceiptData>()
            var hasEmptyFields = false

            for (receiptFields in receiptFieldsList) {
                val storeNumberText = receiptFields.storeNumberEditText?.text?.toString()
                val receiptNumber = receiptFields.receiptNumberEditText.text.toString()
                val receiptDate = receiptFields.receiptDateEditText.text.toString()
                val storeNumberForReceipt = receiptFields.storeNumberEditText?.text?.toString() // Pobierz numer drogerii dla dodatkowych paragonów
                val storeIdForFirstReceipt = intent.getLongExtra("STORE_ID", -1L) // Pobierz storeId dla pierwszego paragonu

                val currentStoreId: Long = if (receiptFields.storeNumberEditText == null) { // Dla pierwszego paragonu użyj storeId z intent
                    storeIdForFirstReceipt
                } else {
                    -1L // Placeholder, zostanie rozwiązane w ViewModel przy użyciu storeNumberForReceipt
                }


                if (receiptNumber.isEmpty() || receiptDate.isEmpty() || (receiptFields.storeNumberEditText != null && storeNumberForReceipt.isNullOrEmpty())) {
                    hasEmptyFields = true
                    break
                }

                receiptsToAdd.add(
                    ReceiptData(storeNumberForReceipt ?: storeIdForFirstReceipt.toString(), receiptNumber, receiptDate) // Przekaż storeNumber lub storeId jako String dla ReceiptData
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
                    val storeIdLongForFirstReceipt = intent.getLongExtra("STORE_ID", -1L)
                    val storeIdToUse: Long = if (receiptFieldsList[receiptsToAdd.indexOf(receiptData)].storeNumberEditText == null) {
                        storeIdLongForFirstReceipt
                    } else {
                        -1L // Placeholder, zostanie rozwiązane w ViewModel przy użyciu storeNumberForReceipt
                    }

                    val isSuccess = addClientViewModel.addClientAndReceipt(
                        storeId = storeIdToUse, // Przekaż storeId dla pierwszego paragonu, -1L dla pozostałych (rozwiązane przy użyciu storeNumber)
                        receiptNumber = receiptData.receiptNumber,
                        receiptDateString = receiptData.receiptDate,
                        verificationDateString = null,
                        clientDescription = clientDescription,
                        storeNumberForReceipt = if (receiptFieldsList[receiptsToAdd.indexOf(receiptData)].storeNumberEditText != null) receiptData.storeNumber else null // Przekaż storeNumber dla dodatkowych paragonów
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
                    updating = false
                    return
                }
                updating = true
                val originalText = editable.toString()
                var formattedText = originalText
                formattedText = formattedText.replace("-", "")
                if (formattedText.length > 2) {
                    formattedText = formattedText.substring(0, 2) + "-" + formattedText.substring(2)
                    Log.d("AddClientActivity", "Formatted text: $formattedText")
                }
                if (formattedText.length > 5) {
                    formattedText = formattedText.substring(0, 5) + "-" + formattedText.substring(5)
                    Log.d("AddClientActivity", "Formatted text: $formattedText")
                }
                Log.d("AddClientActivity", "Formatted text: $formattedText, originalText: $originalText")
                if (formattedText != originalText) {
                    Log.d("AddClientActivity", "Updating text: $formattedText")
                    editable?.replace(0, editable.length, formattedText)
                    Log.d("AddClientActivity", "Updated text: $editable")
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