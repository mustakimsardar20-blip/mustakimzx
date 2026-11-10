package com.mustakimzx.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.mustakimzx.scanner.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var historyAdapter: HistoryAdapter
    private val feedback by lazy { Feedback(this) }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            maybeRestoreConnection()
        } else {
            toast(getString(R.string.bt_permission_required))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        BtClient.init(this)

        historyAdapter = HistoryAdapter()
        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = historyAdapter

        binding.btnDevices.setOnClickListener { openDevicePicker() }
        binding.btnScan.setOnClickListener { openScanner() }
        binding.btnSendLast.setOnClickListener { resendLast() }
        binding.btnClear.setOnClickListener {
            ScanLog.clear()
            binding.lastScan.text = "—"
        }

        requestBluetoothPermissionIfNeeded()
        observeState()
        observeLog()
    }

    override fun onStart() {
        super.onStart()
        if (hasBluetoothPermission()) maybeRestoreConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        feedback.release()
    }

    // ---------------------------------------------------------------- bluetooth

    private fun hasBluetoothPermission(): Boolean = BtClient.hasConnectPermission(this)

    private fun requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    private fun maybeRestoreConnection() {
        val address = prefs.receiverAddress
        if (address == null) {
            renderState(BtClient.State.Idle)
            return
        }
        val current = BtClient.state.value
        if (current is BtClient.State.Connected || current is BtClient.State.Connecting) {
            renderState(current)
            return
        }
        BtClient.choose(address, prefs.receiverName.orEmpty())
    }

    @SuppressLint("MissingPermission")
    private fun openDevicePicker() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissionIfNeeded()
            return
        }
        val adapter = BtClient.adapter()
        if (adapter == null || !adapter.isEnabled) {
            AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.enable_bluetooth)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        val bonded: List<BluetoothDevice> = adapter.bondedDevices?.toList().orEmpty()
        if (bonded.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.pick_device)
                .setMessage(R.string.no_paired_devices)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        DevicePickerDialog.show(this, bonded, prefs.receiverAddress) { device ->
            prefs.receiverAddress = device.address
            prefs.receiverName = device.name
            BtClient.choose(device.address, device.name ?: device.address)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                BtClient.state.collect { renderState(it) }
            }
        }
    }

    private fun renderState(state: BtClient.State) {
        val (dotColor, text) = when (state) {
            is BtClient.State.Idle ->
                ContextCompat.getColor(this, R.color.text_secondary) to
                    getString(R.string.status_not_paired)
            is BtClient.State.Connecting ->
                ContextCompat.getColor(this, R.color.brand) to
                    getString(R.string.status_connecting, state.name)
            is BtClient.State.Connected ->
                ContextCompat.getColor(this, R.color.accent) to
                    getString(R.string.status_connected, state.name)
            is BtClient.State.Failed ->
                ContextCompat.getColor(this, R.color.error) to
                    getString(R.string.status_error, state.reason)
        }
        binding.statusText.text = text
        binding.deviceName.text = when (state) {
            is BtClient.State.Connected -> state.name
            is BtClient.State.Connecting -> state.name
            else -> prefs.receiverName ?: getString(R.string.status_not_paired)
        }
        binding.statusDot.background?.mutate()?.setTint(dotColor)
    }

    // -------------------------------------------------------------------- scans

    private fun observeLog() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScanLog.entries.collect { entries ->
                    historyAdapter.submit(entries)
                    binding.historyEmpty.visibility =
                        if (entries.isEmpty()) View.VISIBLE else View.GONE
                    binding.lastScan.text = entries.firstOrNull()?.value ?: "—"
                }
            }
        }
    }

    private fun openScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            toast(getString(R.string.camera_permission_required))
            return
        }
        if (BtClient.state.value !is BtClient.State.Connected) {
            toast(getString(R.string.not_connected_toast))
        }
        startActivity(Intent(this, QrScanActivity::class.java))
    }

    private fun resendLast() {
        val last = ScanLog.last()?.value
        if (last.isNullOrBlank()) return
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { BtClient.send(last) }
            if (ok) {
                feedback.success()
                ScanLog.add(last, delivered = true)
                toast(getString(R.string.sent_toast))
            } else {
                feedback.error()
                toast(getString(R.string.send_failed_toast))
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
