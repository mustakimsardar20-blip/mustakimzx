package com.mustakimzx.scanner

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** One row per scan in the "sent history" list. */
class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    private val items = mutableListOf<ScanLog.Entry>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(entries: List<ScanLog.Entry>) {
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val value: TextView = view.findViewById(R.id.value)
        private val time: TextView = view.findViewById(R.id.time)
        private val dot: View = view.findViewById(R.id.dot)

        fun bind(entry: ScanLog.Entry) {
            value.text = entry.value
            time.text = TimeFormat.hhmmss(entry.time)
            dot.background?.mutate()?.setTint(
                if (entry.delivered) 0xFF00C853.toInt() else 0xFFC62828.toInt()
            )
        }
    }
}
