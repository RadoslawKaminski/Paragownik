package com.kaminski.paragownik

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kaminski.paragownik.viewmodel.ReceiptViewModel

class ReceiptListActivity : AppCompatActivity() {

    private lateinit var receiptRecyclerView: RecyclerView
    private lateinit var receiptAdapter: ReceiptAdapter
    private lateinit var receiptViewModel: ReceiptViewModel
    private lateinit var fabAddClient: FloatingActionButton // Dodaj deklarację FAB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_list)

        receiptRecyclerView = findViewById(R.id.receiptRecyclerView)
        receiptRecyclerView.layoutManager = LinearLayoutManager(this)

        receiptAdapter = ReceiptAdapter(emptyList())
        receiptRecyclerView.adapter = receiptAdapter

        receiptViewModel = ViewModelProvider(this).get(ReceiptViewModel::class.java)

        val storeId = intent.getLongExtra("STORE_ID", -1L)
        Log.d("ReceiptListActivity", "StoreID received: $storeId")

        receiptViewModel.loadReceiptsForStore(storeId)

        receiptViewModel.receiptsForStore.observe(this) { receipts ->
            receipts?.let {
                receiptAdapter.receiptList = it
                receiptAdapter.notifyDataSetChanged()
            }
        }

        fabAddClient = findViewById(R.id.fabAddClient) // Inicjalizacja FAB
        fabAddClient.setOnClickListener { // Obsługa kliknięcia FAB
            val intent = Intent(this, AddClientActivity::class.java)
            intent.putExtra("STORE_ID", storeId) // Opcjonalnie, przekaż storeId do AddClientActivity, jeśli potrzebne
            startActivity(intent) // Uruchom AddClientActivity
        }
    }
}