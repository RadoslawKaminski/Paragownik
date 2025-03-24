package com.kaminski.paragownik

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kaminski.paragownik.viewmodel.AddClientViewModel
import com.kaminski.paragownik.viewmodel.StoreViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale

class AddClientActivity : AppCompatActivity() {

    private lateinit var storeNumberEditTextFirstReceipt: EditText
    private lateinit var receiptNumberEditText: EditText
    private lateinit var receiptDateEditText: EditText
    private lateinit var verificationDateEditText: EditText
    private lateinit var verificationDateTodayCheckBox: CheckBox
    private lateinit var clientDescriptionEditText: EditText
    private lateinit var addClientButton: Button
    private lateinit var addAdditionalReceiptButton: Button
    private lateinit var addClientViewModel: AddClientViewModel
    private lateinit var storeViewModel: StoreViewModel
    private lateinit var receiptsContainer: LinearLayout
    private val receiptFieldsList = ArrayList<ReceiptFields>()
    private var storeIdFromIntent: Long = -1L

    private data class ReceiptFields(
        val storeNumberEditText: EditText? = null,
        val receiptNumberEditText: EditText,
        val receiptDateEditText: EditText
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_client)

        storeNumberEditTextFirstReceipt = findViewById(R.id.receiptStoreNumberEditText)
        receiptNumberEditText = findViewById(R.id.receiptNumberEditText)
        receiptDateEditText = findViewById(R.id.receiptDateEditText)
        verificationDateEditText = findViewById(R.id.verificationDateEditText)
        verificationDateTodayCheckBox = findViewById(R.id.verificationDateTodayCheckBox)
        clientDescriptionEditText = findViewById(R.id.clientDescriptionEditText)
        addClientButton = findViewById(R.id.addClientButton)
        addAdditionalReceiptButton = findViewById(R.id.addAdditionalReceiptButton)
        receiptsContainer = findViewById(R.id.receiptsContainer)

        addClientViewModel = ViewModelProvider(this).get(AddClientViewModel::class.java)
        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        if (intent.hasExtra("STORE_ID")) {
            storeIdFromIntent = intent.getLongExtra("STORE_ID", -1L)
            storeNumberEditTextFirstReceipt.isEnabled = false

            lifecycleScope.launch {
                val store = storeViewModel.getStoreById(storeIdFromIntent)
                store?.let {
                    storeNumberEditTextFirstReceipt.setText(it.storeNumber)
                }
            }
        }

        setupDateEditText(receiptDateEditText)
        setupDateEditText(verificationDateEditText)

        receiptFieldsList.add(ReceiptFields(null, receiptNumberEditText, receiptDateEditText))

        verificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                verificationDateEditText.setText(currentDate)
                verificationDateEditText.isEnabled = false
            } else {
                verificationDateEditText.text.clear()
                verificationDateEditText.isEnabled = true
            }
        }

        addAdditionalReceiptButton.setOnClickListener {
            addNewReceiptFields()
        }

        addClientButton.setOnClickListener {
            val clientDescription = clientDescriptionEditText.text.toString()
            val verificationDateString = verificationDateEditText.text.toString()
            val receiptsToAdd = mutableListOf<ReceiptData>()
            var hasEmptyFields = false

            for (receiptFields in receiptFieldsList) {
                val receiptNumber = receiptFields.receiptNumberEditText.text.toString()
                val receiptDate = receiptFields.receiptDateEditText.text.toString()
                val storeNumberForReceipt = receiptFields.storeNumberEditText?.text?.toString()
                val storeNumberForFirstReceipt = storeNumberEditTextFirstReceipt.text?.toString()

                val currentStoreNumber: String = if (receiptFieldsList.indexOf(receiptFields) == 0) {
                    storeNumberForFirstReceipt ?: ""
                } else {
                    storeNumberForReceipt ?: ""
                }

                if (receiptNumber.isEmpty() || receiptDate.isEmpty() || currentStoreNumber.isEmpty()) {
                    hasEmptyFields = true
                    break
                }

                receiptsToAdd.add(
                    ReceiptData(currentStoreNumber, receiptNumber, receiptDate)
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
                // Utwórz klienta TYLKO RAZ przed pętlą paragonów
                val clientId = addClientViewModel.insertClient(clientDescription) // Dodaj klienta i pobierz jego ID

                if (clientId == -1L) { // Obsługa błędu dodawania klienta
                    Toast.makeText(this@AddClientActivity, "Błąd dodawania klienta.", Toast.LENGTH_LONG).show()
                    return@launch
                }


                for (i in receiptsToAdd.indices) { // Użyj indeksów zamiast iteracji po obiektach
                    val receiptData = receiptsToAdd[i]
                    val currentVerificationDateString: String? = if (i == 0) { // Użyj indeksu 'i' zamiast indexOf(receiptData)
                        verificationDateString
                    } else {
                        null
                    }

                    val isSuccess = addClientViewModel.insertReceipt(
                        storeId = -1L, // NIEUŻYWANE - ustawione na -1L
                        receiptNumber = receiptData.receiptNumber,
                        receiptDateString = receiptData.receiptDate,
                        verificationDateString = currentVerificationDateString,
                        storeNumberForReceipt = receiptData.storeNumber,
                        clientId = clientId // Przekaż clientId do ViewModelu
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
        // Wymuś warstwę programową dla EditTextów
        storeNumberEditTextFirstReceipt.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        receiptNumberEditText.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        receiptDateEditText.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        verificationDateEditText.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        clientDescriptionEditText.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        // Dodaj warstwy programowe dla dodatkowych EditTextów, jeśli dynamicznie dodajesz je
        receiptFieldsList.forEach {
            it.storeNumberEditText?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            it.receiptNumberEditText.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            it.receiptDateEditText.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
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

                formattedText = formattedText.filter { it.isDigit() }

                if (formattedText.length > 2) {
                    formattedText = formattedText.substring(0, 2) + "-" + formattedText.substring(2)
                }
                if (formattedText.length > 5) {
                    formattedText = formattedText.substring(0, 5) + "-" + formattedText.substring(5)
                }

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
        val storeNumber: String,
        val receiptNumber: String,
        val receiptDate: String
    )
}