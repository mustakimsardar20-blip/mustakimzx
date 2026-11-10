package com.mustakimzx.receiver.ime

import android.annotation.SuppressLint
import android.inputmethodservice.InputMethodService
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mustakimzx.receiver.Prefs
import com.mustakimzx.receiver.R
import com.mustakimzx.receiver.ScanBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The VRL keyboard.
 *
 * QWERTY layout plus a strip that shows the last value received over
 * Bluetooth. When a scan arrives while this keyboard is open it is typed into
 * the focused field immediately and Enter is pressed (toggleable) — exactly
 * what the VRL website needs after a barcode.
 */
class MustakimzxIme : InputMethodService(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var prefs: Prefs
    private var inputRoot: View? = null
    private var preview: TextView? = null
    private var statusChip: TextView? = null
    private var autoEnterKey: TextView? = null

    private var shift = false
    private var symbols = false
    private var lastInsertedScanTime = 0L
    private var scanJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        inputRoot = view
        preview = view.findViewById(R.id.scanPreview)
        statusChip = view.findViewById(R.id.statusChip)
        autoEnterKey = view.findViewById(R.id.btnToggleAutoEnter)

        view.findViewById<View>(R.id.btnSendLast).setOnClickListener { sendLastScan() }
        view.findViewById<View>(R.id.btnToggleAutoEnter).setOnClickListener { toggleAutoEnter() }
        view.findViewById<View>(R.id.btnSwitchIme).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.showInputMethodPicker()
        }

        buildKeyboard()
        refreshAutoEnterKey()
        observeScans()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        val latest = ScanBus.latest.value
        if (latest == null) {
            preview?.text = "—"
            statusChip?.text = getString(R.string.ime_hint_idle)
        } else {
            showScan(latest.value, getString(R.string.ime_hint_last, shortTime(latest.time)))
            // Only catch up on a scan that arrived moments ago (keyboard was closed
            // while the user tapped the field). Older values are never retyped.
            val age = System.currentTimeMillis() - latest.time
            if (latest.time > lastInsertedScanTime && age < CATCH_UP_WINDOW_MS) {
                lastInsertedScanTime = latest.time
                typeIntoField(latest.value)
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    // ------------------------------------------------------------------ scans

    private fun observeScans() {
        scanJob?.cancel()
        scanJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                ScanBus.events.collect { received ->
                    showScan(received.value, getString(R.string.ime_scan_received))
                    if (received.time > lastInsertedScanTime) {
                        lastInsertedScanTime = received.time
                        typeIntoField(received.value)
                    }
                }
            }
        }
    }

    private fun showScan(value: String, status: String) {
        preview?.text = value
        statusChip?.text = status
    }

    private fun typeIntoField(value: String) {
        if (!prefs.imeAutoInsert) return
        val connection = currentInputConnection ?: return
        try {
            connection.beginBatchEdit()
            connection.commitText(value, 1)
            if (prefs.autoEnter) pressEnter(connection)
            connection.endBatchEdit()
        } catch (e: RuntimeException) {
            // The field went away mid-commit (page navigation); nothing to do.
        }
    }

    private fun sendLastScan() {
        val last = ScanBus.latest.value
        val connection = currentInputConnection
        if (last == null || connection == null) {
            statusChip?.text = getString(R.string.toast_no_scan)
            return
        }
        connection.beginBatchEdit()
        connection.commitText(last.value, 1)
        if (prefs.autoEnter) pressEnter(connection)
        connection.endBatchEdit()
        statusChip?.text = getString(R.string.sent_label)
    }

    /**
     * Enter as a page would expect it: a search/go field wants its editor
     * action, a plain field wants the Enter key itself.
     */
    private fun pressEnter(connection: InputConnection) {
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        val hasAction = action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        // IME_FLAG_NO_ENTER_ACTION tells the IME to send Enter instead of an action.
        val wantsKey = options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0

        if (hasAction && !wantsKey) {
            connection.performEditorAction(action)
            return
        }
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun toggleAutoEnter() {
        prefs.autoEnter = !prefs.autoEnter
        refreshAutoEnterKey()
    }

    private fun refreshAutoEnterKey() {
        autoEnterKey?.text = getString(
            if (prefs.autoEnter) R.string.autoenter_on else R.string.autoenter_off
        )
        autoEnterKey?.alpha = if (prefs.autoEnter) 1f else 0.55f
    }

    // --------------------------------------------------------------- keyboard

    private fun buildKeyboard() {
        val root = inputRoot ?: return
        val rows = listOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5)
            .map { root.findViewById<LinearLayout>(it) }
        rows.forEach { it.removeAllViews() }
        if (symbols) fillSymbols(rows) else fillLetters(rows)
    }

    private fun fillLetters(rows: List<LinearLayout>) {
        "1234567890".forEach { rows[0].addKey(it.toString()) }
        "qwertyuiop".forEach { rows[1].addKey(labelFor(it)) }
        "asdfghjkl".forEach { rows[2].addKey(labelFor(it)) }

        rows[3].addKey(getString(R.string.key_shift), KeyAction.SHIFT, weight = 1.5f, alt = true)
        "zxcvbnm".forEach { rows[3].addKey(labelFor(it)) }
        rows[3].addKey(getString(R.string.key_backspace), KeyAction.BACKSPACE, weight = 1.5f, alt = true)

        rows[4].addKey(getString(R.string.key_symbols), KeyAction.SYMBOLS, weight = 1.5f, alt = true)
        rows[4].addKey(",", KeyAction.Text(","))
        rows[4].addKey(getString(R.string.key_space), KeyAction.SPACE, weight = 3.2f)
        rows[4].addKey(".", KeyAction.Text("."))
        rows[4].addKey(getString(R.string.key_enter), KeyAction.ENTER, weight = 1.8f, primary = true)
    }

    private fun fillSymbols(rows: List<LinearLayout>) {
        "1234567890".forEach { rows[0].addKey(it.toString()) }
        "-/:;()&\"".forEach { rows[1].addKey(it.toString()) }
        "@.,?!'#%".forEach { rows[2].addKey(it.toString()) }

        rows[3].addKey(getString(R.string.key_letters), KeyAction.SYMBOLS, weight = 1.5f, alt = true)
        listOf("_", "+", "=", "*", "~", "<", ">", "|").forEach { rows[3].addKey(it) }
        rows[3].addKey(getString(R.string.key_backspace), KeyAction.BACKSPACE, weight = 1.5f, alt = true)

        rows[4].addKey(getString(R.string.key_symbols), KeyAction.SYMBOLS, weight = 1.5f, alt = true)
        rows[4].addKey(",", KeyAction.Text(","))
        rows[4].addKey(getString(R.string.key_space), KeyAction.SPACE, weight = 3.2f)
        rows[4].addKey(".", KeyAction.Text("."))
        rows[4].addKey(getString(R.string.key_enter), KeyAction.ENTER, weight = 1.8f, primary = true)
    }

    private fun labelFor(char: Char): String =
        if (shift) char.uppercaseChar().toString() else char.toString()

    /** Text key that commits [label] itself. */
    private fun LinearLayout.addKey(label: String) =
        addKey(label, KeyAction.Text(label), weight = 1f, alt = false, primary = false)

    @SuppressLint("ClickableViewAccessibility")
    private fun LinearLayout.addKey(
        label: String,
        action: KeyAction,
        weight: Float,
        alt: Boolean = false,
        primary: Boolean = false
    ) {
        val density = resources.displayMetrics.density
        val key = TextView(this@MustakimzxIme)
        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        val margin = (3 * density).toInt()
        params.setMargins(margin, margin, margin, margin)
        key.layoutParams = params
        key.gravity = android.view.Gravity.CENTER
        key.text = label
        key.textSize = if (label.length > 2) 12f else 18f
        key.isClickable = true
        key.isFocusable = false
        key.setTextColor(context.getColor(if (primary) R.color.white else R.color.text_primary))
        key.setBackgroundResource(
            when {
                primary -> R.drawable.bg_key_accent
                alt -> R.drawable.bg_key_alt
                else -> R.drawable.bg_key
            }
        )
        key.contentDescription = label
        key.setOnTouchListener { view, _ ->
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            false
        }
        key.setOnClickListener { onKey(action) }
        addView(key)
    }

    private fun onKey(action: KeyAction) {
        when (action) {
            is KeyAction.Text -> commit(action.value)
            KeyAction.SHIFT -> {
                shift = !shift
                buildKeyboard()
            }
            KeyAction.SYMBOLS -> {
                symbols = !symbols
                buildKeyboard()
            }
            KeyAction.SPACE -> commit(" ")
            KeyAction.BACKSPACE -> backspace()
            KeyAction.ENTER -> currentInputConnection?.let { pressEnter(it) }
        }
    }

    private fun commit(text: String) {
        val connection = currentInputConnection ?: return
        connection.commitText(text, 1)
        // Auto-unshift after one character, like a normal phone keyboard.
        if (shift && text.length == 1) {
            shift = false
            buildKeyboard()
        }
    }

    private fun backspace() {
        val connection = currentInputConnection ?: return
        val selected = connection.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            connection.commitText("", 1)
            return
        }
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    private sealed interface KeyAction {
        data class Text(val value: String) : KeyAction
        data object SHIFT : KeyAction
        data object SYMBOLS : KeyAction
        data object SPACE : KeyAction
        data object BACKSPACE : KeyAction
        data object ENTER : KeyAction
    }

    private fun shortTime(time: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(time))

    private companion object {
        /** How long after arrival a scan may still be typed when the field gets focus. */
        const val CATCH_UP_WINDOW_MS = 5000L
    }
}
