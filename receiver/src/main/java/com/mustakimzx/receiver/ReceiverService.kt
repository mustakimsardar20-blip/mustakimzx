package com.mustakimzx.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mustakimzx.shared.BtProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Long-running Bluetooth server.
 *
 * Opens an RFCOMM server socket on [BtProtocol.RFCOMM_UUID]; the scanner phone
 * connects to it and writes one line per scan. Every line is published to
 * [ScanBus], which feeds the keyboard, the accessibility service and the UI.
 */
class ReceiverService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServerState.wanted = true
        startInForeground(getString(R.string.notif_text_listening))

        if (!hasBluetoothPermission()) {
            ServerState.update(ServerState.State.Error("Bluetooth permission missing"))
            stopSelf()
            return START_NOT_STICKY
        }

        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            ServerState.update(ServerState.State.Error("Bluetooth is off"))
            stopSelf()
            return START_NOT_STICKY
        }

        if (acceptJob == null) {
            acquireWakeLock()
            acceptJob = scope.launch { acceptLoop(adapter) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ServerState.wanted = false
        closeEverything()
        scope.cancel()
        releaseWakeLock()
        ServerState.update(ServerState.State.Stopped)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ server

    @SuppressLint("MissingPermission")
    private fun acceptLoop(adapter: BluetoothAdapter) {
        while (scope.isActive) {
            try {
                ServerState.update(ServerState.State.Listening)
                notifyText(getString(R.string.notif_text_listening))

                val server = adapter.listenUsingRfcommWithServiceRecord(
                    BtProtocol.SERVICE_NAME,
                    BtProtocol.RFCOMM_UUID
                )
                serverSocket = server

                // Blocks until the scanner phone connects.
                val socket = server.accept()
                clientSocket = socket
                server.close()
                serverSocket = null

                val name = socket.remoteDevice?.name ?: "Scanner phone"
                ServerState.update(ServerState.State.Client(name))
                notifyText(getString(R.string.notif_text_connected, name))

                readLoop(socket)
            } catch (e: IOException) {
                ServerState.update(ServerState.State.Error(e.message ?: "socket error"))
                runCatching { Thread.sleep(1000) }
            } catch (e: SecurityException) {
                ServerState.update(ServerState.State.Error(e.message ?: "permission denied"))
                break
            }
        }
    }

    private fun readLoop(socket: BluetoothSocket) {
        try {
            val reader = BufferedReader(
                InputStreamReader(socket.inputStream, StandardCharsets.UTF_8)
            )
            while (scope.isActive) {
                val line = reader.readLine() ?: break
                val value = line.trim()
                if (value.isEmpty()) continue
                onScan(value)
            }
        } catch (e: IOException) {
            // Client went away (out of range, scanner closed) — back to accept loop.
        } finally {
            runCatching { socket.close() }
            if (clientSocket === socket) clientSocket = null
            ServerState.update(ServerState.State.Listening)
        }
    }

    private fun onScan(value: String) {
        ScanBus.publish(value)
        vibrate()
    }

    // -------------------------------------------------------------- lifecycle

    private fun closeEverything() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        runCatching { clientSocket?.close() }
        serverSocket = null
        clientSocket = null
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun bluetoothAdapter(): BluetoothAdapter? =
        getSystemService(BluetoothManager::class.java)?.adapter

    private fun vibrate() {
        if (!Prefs(this).vibrate) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MUSTAKIMZX:receiver")
        runCatching { wakeLock?.acquire(10 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    // ------------------------------------------------------------ notification

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_receiver)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startInForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyText(text: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "receiver"
        private const val NOTIFICATION_ID = 4711

        fun start(context: Context) {
            val intent = Intent(context, ReceiverService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            ServerState.wanted = false
            context.stopService(Intent(context, ReceiverService::class.java))
        }
    }
}
