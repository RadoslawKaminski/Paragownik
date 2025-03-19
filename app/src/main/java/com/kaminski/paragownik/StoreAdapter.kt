package com.kaminski.paragownik

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.Store

class StoreAdapter(
    var storeList: List<Store>,
    private val onClickListener: OnItemClickListener // Dodaj OnItemClickListener jako argument
) :
    RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    interface OnItemClickListener { // Interfejs OnItemClickListener
        fun onItemClick(storeId: Long)
    }

    class StoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val storeNameTextView: TextView = itemView.findViewById(R.id.storeNameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.store_item, parent, false)
        return StoreViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val currentStore = storeList[position]
        holder.storeNameTextView.text = currentStore.storeNumber

        holder.itemView.setOnClickListener { // Obsługa kliknięcia na element listy
            onClickListener.onItemClick(currentStore.id) // Wywołaj onClickListener i przekaż storeId
        }
    }

    override fun getItemCount() = storeList.size
}