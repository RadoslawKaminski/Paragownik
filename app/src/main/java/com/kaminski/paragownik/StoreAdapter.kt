package com.kaminski.paragownik

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.Store

/**
 * Adapter dla RecyclerView wyświetlającego listę sklepów (drogerii) w [MainActivity].
 */
class StoreAdapter(
    var storeList: List<Store>,
    private val onClickListener: OnItemClickListener
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    /**
     * Interfejs definiujący metodę zwrotną (callback) wywoływaną po kliknięciu
     * elementu listy sklepów.
     */
    interface OnItemClickListener {
        /**
         * Wywoływane, gdy użytkownik kliknie element reprezentujący sklep.
         * @param storeId ID klikniętego sklepu.
         */
        fun onItemClick(storeId: Long)
    }

    /**
     * ViewHolder przechowuje referencję do widoku (TextView) w pojedynczym elemencie listy
     * (layout `store_item.xml`).
     */
    class StoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val storeNameTextView: TextView = itemView.findViewById(R.id.storeNameTextView)
    }

    /**
     * Tworzy nowy obiekt ViewHolder, gdy RecyclerView potrzebuje nowego elementu.
     * Influje widok z pliku `store_item.xml`.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.store_item, parent, false)
        return StoreViewHolder(itemView)
    }

    /**
     * Łączy dane sklepu z określonej pozycji (`position`) z widokami w ViewHolderze (`holder`).
     * Ustawia również listener kliknięcia dla całego elementu listy.
     */
    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val currentStore = storeList[position]
        holder.storeNameTextView.text = currentStore.storeNumber

        holder.itemView.setOnClickListener {
            onClickListener.onItemClick(currentStore.id)
        }
    }

    /**
     * Zwraca całkowitą liczbę sklepów na liście.
     */
    override fun getItemCount() = storeList.size
}



