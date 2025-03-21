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
import kotlinx.coroutines.launch
import java.util.ArrayList

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
    private lateinit var receiptsContainer: LinearLayout
    private val receiptFieldsList = ArrayList<ReceiptFields>()

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


        // Sprawdź, czy Activity zostało uruchomione z MainActivity (bez STORE_ID)
        if (!intent.hasExtra("STORE_ID")) {
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

                    val isSuccess = addClientViewModel.addClientAndReceipt(
                        storeId = -1L, // NIEUŻYWANE - ustawione na -1L
                        receiptNumber = receiptData.receiptNumber,
                        receiptDateString = receiptData.receiptDate,
                        verificationDateString = null,
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