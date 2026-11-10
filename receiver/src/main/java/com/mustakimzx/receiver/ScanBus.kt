package com.mustakimzx.receiver

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** One value that arrived from the scanner phone. */
data class Received(val value: String, val time: Long)

/**
 * In-process bus between the Bluetooth service, the accessibility service,
 * the IME and the UI. Everything runs in the same process, so a singleton
 * is enough — no broadcasts needed.
 */
object ScanBus {

    private const val HISTORY = 100

    // MutableStateFlow always needs an initial value; null means "no scan yet".
    private val _latest = MutableStateFlow<Received?>(null)
    val latest: StateFlow<Received?> = _latest.asStateFlow()

    /** Every scan, once. SharedFlow with no replay: listeners get new scans only. */
    private val _events = MutableSharedFlow<Received>(extraBufferCapacity = 64)
    val events: SharedFlow<Received> = _events.asSharedFlow()

    private val _history = MutableStateFlow<List<Received>>(emptyList())
    val history: StateFlow<List<Received>> = _history.asStateFlow()

    fun publish(value: String) {
        val received = Received(value = value, time = System.currentTimeMillis())
        _latest.value = received
        _history.value = (listOf(received) + _history.value).take(HISTORY)
        _events.tryEmit(received)
    }

    fun clearHistory() {
        _history.value = emptyList()
    }
}

/** Connection state of the Bluetooth server, shared with the UI. */
object ServerState {

    sealed interface State {
        data object Stopped : State
        data object Listening : State
        data class Client(val name: String) : State
        data class Error(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state.asStateFlow()

    /** True while the user wants the receiver running (survives UI recreation). */
    var wanted: Boolean = false

    fun update(newState: State) {
        _state.value = newState
    }
}
