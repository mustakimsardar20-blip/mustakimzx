package com.mustakimzx.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.mustakimzx.receiver.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var historyAdapter: HistoryAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // The same launcher handles Bluetooth (needed for the server) and
        // notifications (only for the foreground notification).
        if (granted.containsKey(Manifest.permission.BLUETOOTH_CONNECT)) {
            if (granted[Manifest.permission.BLUETOOTH_CONNECT] == true) {
                ensureServerRunning()
            } else {
                toast(getString(R.string.toast_bt_permission))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        historyAdapter = HistoryAdapter()
        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = historyAdapter

        binding.btnToggleServer.setOnClickListener { toggleServer() }
        binding.btnBluetooth.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        }
        binding.btnKeyboard.setOnClickListener { openInputMethodSettings() }
        binding.btnPickKeyboard.setOnClickListener { showKeyboardPicker() }
        binding.btnAutotype.setOnClickListener { openAccessibilitySettings() }
        binding.btnOpenChrome.setOnClickListener { openChrome() }

        binding.switchAutoEnter.isChecked = prefs.autoEnter
        binding.switchAutoEnter.setOnCheckedChangeListener { _, checked -> prefs.autoEnter = checked }

        binding.switchAutoInsert.isChecked = prefs.imeAutoInsert
        binding.switchAutoInsert.setOnCheckedChangeListener { _, checked -> prefs.imeAutoInsert = checked }

        binding.switchAutoType.isChecked = prefs.accessibilityAutoType
        binding.switchAutoType.setOnCheckedChangeListener { _, checked ->
            prefs.accessibilityAutoType = checked
            // Enabling is only meaningful once the service is on; offer the settings page.
            if (checked && !AutoTypeService.isEnabled(this)) openAccessibilitySettings()
        }

        observeServer()
        observeScans()
        maybeAskNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshKeyboardStatus()
        refreshAutoTypeStatus()
        if (ServerState.wanted) ensureServerRunning()
    }

    // ----------------------------------------------------------------- server

    private fun toggleServer() {
        if (ServerState.wanted) {
            ReceiverService.stop(this)
            ServerState.update(ServerState.State.Stopped)
            renderServer(ServerState.State.Stopped)
            return
        }
        if (!hasBluetoothPermission()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            return
        }
        if (!isBluetoothEnabled()) {
            toast(getString(R.string.toast_enable_bt))
            runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            return
        }
        ensureServerRunning()
        toast(getString(R.string.toast_server_running))
    }

    private fun ensureServerRunning() {
        if (!hasBluetoothPermission()) return
        ServerState.wanted = true
        ReceiverService.start(this)
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun isBluetoothEnabled(): Boolean =
        getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    private fun observeServer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerState.state.collect { renderServer(it) }
            }
        }
    }

    private fun renderServer(state: ServerState.State) {
        val (color, text) = when (state) {
            is ServerState.State.Stopped ->
                ContextCompat.getColor(this, R.color.text_secondary) to
                    getString(R.string.status_server_off)
            is ServerState.State.Listening ->
                ContextCompat.getColor(this, R.color.brand) to
                    getString(R.string.status_listening)
            is ServerState.State.Client ->
                ContextCompat.getColor(this, R.color.accent) to
                    getString(R.string.status_client_connected, state.name)
            is ServerState.State.Error ->
                ContextCompat.getColor(this, R.color.error) to
                    getString(R.string.status_server_error, state.reason)
        }
        binding.statusText.text = text
        binding.statusDot.background?.mutate()?.setTint(color)
        binding.btnToggleServer.text = getString(
            if (ServerState.wanted) R.string.btn_stop_server else R.string.btn_start_server
        )
    }

    // ------------------------------------------------------------------ scans

    private fun observeScans() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScanBus.history.collect { entries ->
                    historyAdapter.submit(entries)
                    binding.historyEmpty.visibility =
                        if (entries.isEmpty()) View.VISIBLE else View.GONE
                    val latest = entries.firstOrNull()
                    binding.lastScan.text = latest?.value ?: "—"
                    binding.lastTime.text = if (latest == null) {
                        getString(R.string.last_scan_label)
                    } else {
                        getString(R.string.last_scan_label) + " · " +
                            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                                .format(java.util.Date(latest.time))
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------- keyboard

    private fun currentImeId(): String? = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD
    )

    private fun isOurKeyboardEnabled(): Boolean {
        val imm = getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun isOurKeyboardActive(): Boolean =
        currentImeId()?.startsWith("$packageName/") == true

    private fun refreshKeyboardStatus() {
        binding.keyboardStatus.text = when {
            isOurKeyboardActive() -> getString(R.string.keyboard_active)
            isOurKeyboardEnabled() -> getString(R.string.keyboard_enabled)
            else -> getString(R.string.status_no_keyboard)
        }
        binding.btnKeyboard.visibility =
            if (isOurKeyboardEnabled()) View.GONE else View.VISIBLE
    }

    private fun refreshAutoTypeStatus() {
        binding.autotypeStatus.text = getString(
            if (AutoTypeService.isEnabled(this)) {
                R.string.status_accessibility_ok
            } else {
                R.string.status_accessibility_off
            }
        )
    }

    private fun openInputMethodSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        toast(getString(R.string.toast_keyboard_hint))
    }

    private fun showKeyboardPicker() {
        val imm = getSystemService(InputMethodManager::class.java)
        if (imm == null) {
            openInputMethodSettings()
            return
        }
        imm.showInputMethodPicker()
    }

    private fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        toast(getString(R.string.toast_autotype_hint))
    }

    private fun openChrome() {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.vrl.com"))
        intent.setPackage("com.android.chrome")
        val fallback = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.vrl.com"))
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(fallback) }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
