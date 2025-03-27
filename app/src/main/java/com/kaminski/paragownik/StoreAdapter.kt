package com.kaminski.paragownik

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kaminski.paragownik.data.Store // Import modelu danych Store

/**
 * Adapter dla RecyclerView wyświetlającego listę sklepów (drogerii) w [MainActivity].
 */
class StoreAdapter(
    // Lista obiektów Store do wyświetlenia. `var` pozwala na aktualizację z zewnątrz.
    var storeList: List<Store>,
    // Listener (zazwyczaj MainActivity), który będzie powiadamiany o kliknięciu elementu listy.
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
        // Referencja do TextView wyświetlającego numer sklepu
        val storeNameTextView: TextView = itemView.findViewById(R.id.storeNameTextView)
    }

    /**
     * Tworzy nowy obiekt ViewHolder, gdy RecyclerView potrzebuje nowego elementu.
     * Influje widok z pliku `store_item.xml`.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        // Utwórz widok pojedynczego elementu listy
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.store_item, parent, false)
        // Zwróć nowo utworzony ViewHolder
        return StoreViewHolder(itemView)
    }

    /**
     * Łączy dane sklepu z określonej pozycji (`position`) z widokami w ViewHolderze (`holder`).
     * Ustawia również listener kliknięcia dla całego elementu listy.
     */
    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        // Pobierz obiekt Store dla bieżącej pozycji
        val currentStore = storeList[position]
        // Ustaw numer sklepu w TextView
        holder.storeNameTextView.text = currentStore.storeNumber

        // Ustaw listener kliknięcia dla całego widoku elementu listy (itemView)
        holder.itemView.setOnClickListener {
            // Wywołaj metodę interfejsu `onItemClick` przekazując ID klikniętego sklepu.
            // Listener (MainActivity) obsłuży to zdarzenie, np. uruchamiając ReceiptListActivity.
            onClickListener.onItemClick(currentStore.id)
        }
    }

    /**
     * Zwraca całkowitą liczbę sklepów na liście.
     */
    override fun getItemCount() = storeList.size
}

