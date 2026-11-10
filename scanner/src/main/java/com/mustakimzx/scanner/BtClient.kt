package com.mustakimzx.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mustakimzx.shared.BtProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Owns the single RFCOMM socket to the receiver phone.
 *
 * A singleton because both [MainActivity] and [QrScanActivity] send through it.
 * Reconnects automatically after the socket dies.
 */
object BtClient {

    sealed interface State {
        data object Idle : State
        data class Connecting(val name: String) : State
        data class Connected(val name: String) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendLock = Any()

    private var appContext: Context? = null
    private var socket: BluetoothSocket? = null
    private var writer: BufferedWriter? = null
    private var readJob: Job? = null
    private var address: String? = null
    private var name: String = ""

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    @SuppressLint("MissingPermission")
    fun adapter(): BluetoothAdapter? {
        val ctx = appContext ?: return null
        if (!hasConnectPermission(ctx)) return null
        return ctx.getSystemService(BluetoothManager::class.java)?.adapter
    }

    fun isBluetoothOn(): Boolean = adapter()?.isEnabled == true

    fun hasConnectPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Remember the receiver phone and start connecting. */
    fun choose(address: String, name: String) {
        this.address = address
        this.name = name
        closeSocket()
        connect()
    }

    /** Forgets the receiver; the socket is closed and no reconnect is attempted. */
    fun forget() {
        address = null
        name = ""
        closeSocket()
        _state.value = State.Idle
    }

    fun connect() {
        val target = address ?: return
        val ctx = appContext ?: return
        if (!hasConnectPermission(ctx)) {
            _state.value = State.Failed("Bluetooth permission missing")
            return
        }
        closeSocket()
        _state.value = State.Connecting(name)
        scope.launch {
            try {
                val btAdapter = adapter() ?: throw IOException("Bluetooth unavailable")
                val device: BluetoothDevice = btAdapter.getRemoteDevice(target)
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    throw IOException("Device is not paired")
                }
                val s = device.createRfcommSocketToServiceRecord(BtProtocol.RFCOMM_UUID)
                socket = s
                btAdapter.cancelDiscovery()
                s.connect() // blocking, IO dispatcher
                writer = BufferedWriter(
                    OutputStreamWriter(s.outputStream, StandardCharsets.UTF_8)
                )
                _state.value = State.Connected(name.ifBlank { device.name ?: target })
                startReadLoop(s)
            } catch (e: Exception) {
                closeSocket()
                _state.value = State.Failed(e.message ?: "connect failed")
                scheduleReconnect()
            }
        }
    }

    /**
     * Sends one scan as a single UTF-8 line.
     * Blocking — must be called from an IO thread.
     * Returns true when the frame reached the socket.
     */
    fun send(raw: String): Boolean {
        val line = BtProtocol.encode(raw)
        if (line.isEmpty()) return false
        val ctx = appContext ?: return false
        if (!hasConnectPermission(ctx)) return false
        val out = writer ?: return false
        return try {
            synchronized(sendLock) {
                out.write(line)
                out.write("\n")
                out.flush()
            }
            true
        } catch (e: IOException) {
            onConnectionLost(e)
            false
        }
    }

    /**
     * Non-blocking variant for the UI/analysis threads.
     * [onResult] is delivered on the main thread.
     */
    fun sendAsync(raw: String, onResult: (Boolean) -> Unit) {
        scope.launch {
            val ok = send(raw)
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    fun disconnect() {
        address = null
        closeSocket()
        _state.value = State.Idle
    }

    private fun startReadLoop(s: BluetoothSocket) {
        readJob?.cancel()
        readJob = scope.launch {
            val input: InputStream = s.inputStream
            val buffer = ByteArray(256)
            try {
                // The receiver never sends data; a read result of -1 means "closed".
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                }
                throw IOException("Receiver closed the connection")
            } catch (e: Exception) {
                if (socket === s) onConnectionLost(e)
            }
        }
    }

    private fun onConnectionLost(e: Exception) {
        closeSocket()
        _state.value = State.Failed(e.message ?: "send failed")
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val target = address ?: return
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (address == target && socket == null) connect()
        }
    }

    @Synchronized
    private fun closeSocket() {
        readJob?.cancel()
        readJob = null
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        writer = null
        socket = null
    }

    private const val RECONNECT_DELAY_MS = 1500L
}
