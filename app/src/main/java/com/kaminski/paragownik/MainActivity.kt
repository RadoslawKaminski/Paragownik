package com.kaminski.paragownik

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.Store
import com.kaminski.paragownik.viewmodel.StoreViewModel

class MainActivity : AppCompatActivity(), StoreAdapter.OnItemClickListener { // Implementuj StoreAdapter.OnItemClickListener

    private lateinit var storeViewModel: StoreViewModel
    private lateinit var storeAdapter: StoreAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val storeRecyclerView: RecyclerView = findViewById(R.id.storeRecyclerView)
        storeRecyclerView.layoutManager = LinearLayoutManager(this)

        storeAdapter = StoreAdapter(emptyList(), this) // Przekaż 'this' (MainActivity) jako OnItemClickListener
        storeRecyclerView.adapter = storeAdapter

        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        storeViewModel.allStores.observe(this) { stores ->
            stores?.let {
                storeAdapter.storeList = it
                storeAdapter.notifyDataSetChanged()
            }
        }

        storeViewModel.allStores.observe(this) { stores ->
            if (stores.isNullOrEmpty()) {
                insertSampleStores()
            }
            stores?.let {
                storeAdapter.storeList = it
                storeAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun insertSampleStores() {
        storeViewModel.insertStore(Store(storeNumber = "123"))
        storeViewModel.insertStore(Store(storeNumber = "456"))
        storeViewModel.insertStore(Store(storeNumber = "789"))
        storeViewModel.insertStore(Store(storeNumber = "101"))
    }

    override fun onItemClick(storeId: Long) { // Implementacja metody onItemClick z interfejsu
        val intent = Intent(this, ReceiptListActivity::class.java)
        intent.putExtra("STORE_ID", storeId) // Przekaż storeId jako extra do Intent
        startActivity(intent) // Uruchom ReceiptListActivity
    }
}