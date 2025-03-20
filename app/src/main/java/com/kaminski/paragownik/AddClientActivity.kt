package com.kaminski.paragownik

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.kaminski.paragownik.viewmodel.AddClientViewModel
import kotlinx.coroutines.launch

class AddClientActivity : AppCompatActivity() {

    private lateinit var receiptNumberEditText: EditText
    private lateinit var receiptDateEditText: EditText
    private lateinit var verificationDateEditText: EditText
    private lateinit var clientDescriptionEditText: EditText
    private lateinit var addClientButton: Button
    private lateinit var addClientViewModel: AddClientViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_client)

        receiptNumberEditText = findViewById(R.id.receiptNumberEditText)
        receiptDateEditText = findViewById(R.id.receiptDateEditText)
        verificationDateEditText = findViewById(R.id.verificationDateEditText)
        clientDescriptionEditText = findViewById(R.id.clientDescriptionEditText)
        addClientButton = findViewById(R.id.addClientButton)

        addClientViewModel = ViewModelProvider(this).get(AddClientViewModel::class.java)

        val storeId = intent.getLongExtra("STORE_ID", -1L)

        setupDateEditText(receiptDateEditText) // Ustaw TextWatcher dla daty paragonu
        setupDateEditText(verificationDateEditText) // Ustaw TextWatcher dla daty weryfikacji

        addClientButton.setOnClickListener {
            val receiptNumber = receiptNumberEditText.text.toString()
            val receiptDate = receiptDateEditText.text.toString()
            val verificationDate = verificationDateEditText.text.toString()
            val clientDescription = clientDescriptionEditText.text.toString()

            if (receiptNumber.isEmpty() || receiptDate.isEmpty()) {
                Toast.makeText(this, "Numer i data paragonu są wymagane", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val isSuccess = addClientViewModel.addClientAndReceipt(
                    storeId = storeId,
                    receiptNumber = receiptNumber,
                    receiptDateString = receiptDate,
                    verificationDateString = verificationDate,
                    clientDescription = clientDescription
                )

                if (isSuccess) {
                    Toast.makeText(this@AddClientActivity, "Klient i paragon dodane", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@AddClientActivity, "Błąd formatu daty. Użyj DD-MM-YYYY", Toast.LENGTH_LONG).show()
                }
            }
        }
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
                val originalText = editable.toString() // Pobierz oryginalny tekst PRZED formatowaniem
                var formattedText = originalText // Użyj oryginalnego tekstu do formatowania
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
                if (formattedText != originalText) { // Porównaj sformatowany tekst z ORYGINALNYM tekstem
                    Log.d("AddClientActivity", "Updating text: $formattedText")
                    editable?.replace(0, editable.length, formattedText)
                    Log.d("AddClientActivity", "Updated text: $editable")
                }
                updating = false
            }
        })
    }
}