package com.kaminski.paragownik

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kaminski.paragownik.viewmodel.StoreViewModel

/**
 * Główna aktywność aplikacji. Wyświetla listę dostępnych drogerii (sklepów).
 * Umożliwia przejście do listy paragonów dla wybranej drogerii,
 * przejście do dodawania nowego klienta (bez kontekstu sklepu),
 * przejście do listy wszystkich klientów oraz do listy wszystkich paragonów.
 */
class MainActivity : AppCompatActivity(), StoreAdapter.OnItemClickListener {

    private lateinit var storeViewModel: StoreViewModel
    private lateinit var storeAdapter: StoreAdapter
    private lateinit var fabAddClientMain: FloatingActionButton
    private lateinit var viewClientsButton: Button
    private lateinit var viewAllReceiptsButton: Button

    /**
     * Metoda cyklu życia Aktywności, wywoływana przy jej tworzeniu.
     * Inicjalizuje RecyclerView, Adapter, ViewModel, obserwuje dane sklepów
     * i ustawia listenery dla FAB i przycisków nawigacyjnych.
     */
    @SuppressLint("NotifyDataSetChanged") // TODO: Rozważyć DiffUtil
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val storeRecyclerView: RecyclerView = findViewById(R.id.storeRecyclerView)
        storeRecyclerView.layoutManager = LinearLayoutManager(this)

        storeAdapter = StoreAdapter(emptyList(), this)
        storeRecyclerView.adapter = storeAdapter

        storeViewModel = ViewModelProvider(this).get(StoreViewModel::class.java)

        storeViewModel.allStores.observe(this) { stores ->
            stores?.let {
                storeAdapter.storeList = it
                storeAdapter.notifyDataSetChanged()
            }
        }

        fabAddClientMain = findViewById(R.id.fabAddClientMain)
        fabAddClientMain.setOnClickListener {
            val intent = Intent(this, AddClientActivity::class.java)
            startActivity(intent)
        }

        viewClientsButton = findViewById(R.id.viewClientsButton)
        viewClientsButton.setOnClickListener {
            val intent = Intent(this, ClientListActivity::class.java)
            startActivity(intent)
        }

        viewAllReceiptsButton = findViewById(R.id.viewAllReceiptsButton)
        viewAllReceiptsButton.setOnClickListener {
            val intent = Intent(this, AllReceiptsActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Metoda wywoływana, gdy użytkownik kliknie element na liście sklepów.
     * Implementacja interfejsu [StoreAdapter.OnItemClickListener].
     * Uruchamia [ReceiptListActivity] dla wybranego sklepu.
     * @param storeId ID klikniętego sklepu.
     */
    override fun onItemClick(storeId: Long) {
        val intent = Intent(this, ReceiptListActivity::class.java)
        intent.putExtra("STORE_ID", storeId)
        startActivity(intent)
    }
}

