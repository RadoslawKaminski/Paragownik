package com.kaminski.paragownik

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kaminski.paragownik.viewmodel.EditReceiptViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class EditReceiptActivity : AppCompatActivity() {

    private lateinit var editReceiptStoreNumberEditText: EditText
    private lateinit var editReceiptNumberEditText: EditText
    private lateinit var editReceiptDateEditText: EditText
    private lateinit var editVerificationDateEditText: EditText
    private lateinit var editVerificationDateTodayCheckBox: CheckBox
    private lateinit var editClientDescriptionEditText: EditText
    private lateinit var saveReceiptButton: Button
    private lateinit var deleteReceiptButton: Button
    private lateinit var deleteClientButton: Button
    private lateinit var editReceiptViewModel: EditReceiptViewModel
    private var receiptId: Long = -1L // Przechowuj receiptId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_receipt)

        // Inicjalizacja widoków
        editReceiptStoreNumberEditText = findViewById(R.id.editReceiptStoreNumberEditText)
        editReceiptNumberEditText = findViewById(R.id.editReceiptNumberEditText)
        editReceiptDateEditText = findViewById(R.id.editReceiptDateEditText)
        editVerificationDateEditText = findViewById(R.id.editVerificationDateEditText)
        editVerificationDateTodayCheckBox = findViewById(R.id.editVerificationDateTodayCheckBox)
        editClientDescriptionEditText = findViewById(R.id.editClientDescriptionEditText)
        saveReceiptButton = findViewById(R.id.saveReceiptButton)
        deleteReceiptButton = findViewById(R.id.deleteReceiptButton)
        deleteClientButton = findViewById(R.id.deleteClientButton)

        editReceiptViewModel = ViewModelProvider(this).get(EditReceiptViewModel::class.java)

        // Pobierz receiptId z Intentu
        receiptId = intent.getLongExtra("RECEIPT_ID", -1L)

        if (receiptId == -1L) {
            // TODO: Obsłuż błąd - brak receiptId
            finish()
            return
        }

        setupDateEditText(editReceiptDateEditText)
        setupDateEditText(editVerificationDateEditText)
        setupVerificationDateCheckBox()

        loadReceiptData() // Załaduj dane paragonu
    }

    private fun loadReceiptData() {
        lifecycleScope.launch {
            editReceiptViewModel.getReceiptWithClient(receiptId)
                .collectLatest { pair ->
                    val receiptWithClient = pair.first
                    val storeNumber = pair.second

                    receiptWithClient?.let {
                        // Wypełnij pola danymi paragonu i klienta
                        editReceiptStoreNumberEditText.setText(storeNumber ?: "")
                        editReceiptNumberEditText.setText(it.receipt.receiptNumber)
                        editReceiptDateEditText.setText(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(it.receipt.receiptDate))
                        it.receipt.verificationDate?.let { verificationDate ->
                            editVerificationDateEditText.setText(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(verificationDate))
                        }
                        editClientDescriptionEditText.setText(it.client?.description ?: "")
                    }
                }
        }
    }


    private fun setupVerificationDateCheckBox() {
        editVerificationDateTodayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Ustaw aktualną datę i wyłącz EditText
                val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                editVerificationDateEditText.setText(currentDate)
                editVerificationDateEditText.isEnabled = false
            } else {
                // Włącz EditText i wyczyść tekst
                editVerificationDateEditText.text.clear()
                editVerificationDateEditText.isEnabled = true
            }
        }
    }


    private fun setupDateEditText(editText: EditText) {
        editText.inputType = InputType.TYPE_CLASS_DATETIME
        editText.addTextChangedListener(object : android.text.TextWatcher {
            private var updating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(editable: android.text.Editable?) {
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
}