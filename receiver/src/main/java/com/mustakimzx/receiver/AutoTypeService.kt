package com.mustakimzx.receiver

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional "auto type" path for native input fields.
 *
 * When a scan arrives it writes the text into the field that currently has
 * focus and then presses Enter. Web pages inside Chrome do not reliably expose
 * editable nodes, so for VRL the MUSTAKIMZX keyboard is the recommended path —
 * this service simply stays out of the way while that keyboard is active.
 */
class AutoTypeService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var prefs: Prefs? = null
    private var collectJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        collectJob?.cancel()
        collectJob = scope.launch {
            ScanBus.events.collect { received ->
                handleScan(received.value)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handleScan(value: String) {
        val settings = prefs ?: return
        if (!settings.accessibilityAutoType) return
        // Our own keyboard already inserts the scan; avoid typing it twice.
        if (isOwnImeActive()) return

        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: rootInActiveWindow
        val editable = findEditable(focused ?: return) ?: return

        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            value
        )
        if (!editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return

        // ACTION_IME_ENTER exists from API 30; nothing to do on older releases.
        if (settings.autoEnter && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            editable.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)
        }
    }

    /** Breadth-first search for the closest editable node under [node]. */
    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(node)
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 300) {
            val current = queue.removeFirst()
            if (current.isEditable && current.isEnabled) return current
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.addLast(it) }
            }
        }
        return null
    }

    /** True when MUSTAKIMZX Keyboard is the selected IME (it types the scan itself). */
    private fun isOwnImeActive(): Boolean {
        val current = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        return !TextUtils.isEmpty(current) && current?.startsWith("$packageName/") == true
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${AutoTypeService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
