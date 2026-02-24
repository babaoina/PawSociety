package com.example.pawsociety

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView

class SuggestionsAdapter : BaseAdapter(), Filterable {

    private var originalList: List<String> = emptyList()
    private var filteredList: List<String> = emptyList()

    fun setData(list: List<String>) {
        originalList = list
        filteredList = list
        notifyDataSetChanged()
    }

    override fun getCount(): Int = filteredList.size

    override fun getItem(position: Int): String = filteredList[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(parent?.context)
            .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)

        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = filteredList[position]
        textView.setTextColor(parent?.context?.getColor(android.R.color.black) ?: 0)

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val filtered = if (constraint.isNullOrEmpty()) {
                    originalList
                } else {
                    originalList.filter {
                        it.contains(constraint, ignoreCase = true)
                    }.take(10)
                }

                results.values = filtered
                results.count = filtered.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                @Suppress("UNCHECKED_CAST")
                filteredList = results?.values as? List<String> ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
}