package com.mustakimzx.receiver

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Received scans, newest first. */
class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    private val items = mutableListOf<Received>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @SuppressLint("NotifyDataSetChanged")
    fun submit(entries: List<Received>) {
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return Holder(view, timeFormat)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    class Holder(view: View, private val format: SimpleDateFormat) : RecyclerView.ViewHolder(view) {
        private val value: TextView = view.findViewById(R.id.value)
        private val time: TextView = view.findViewById(R.id.time)

        fun bind(entry: Received) {
            value.text = entry.value
            time.text = format.format(Date(entry.time))
        }
    }
}
