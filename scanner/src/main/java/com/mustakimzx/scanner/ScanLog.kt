package com.mustakimzx.scanner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory list of everything this app tried to send, shown on the main screen. */
object ScanLog {

    data class Entry(
        val value: String,
        val time: Long,
        val delivered: Boolean
    )

    private const val CAPACITY = 200

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(value: String, delivered: Boolean) {
        val entry = Entry(value = value, time = System.currentTimeMillis(), delivered = delivered)
        _entries.value = (listOf(entry) + _entries.value).take(CAPACITY)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun last(): Entry? = _entries.value.firstOrNull()
}
