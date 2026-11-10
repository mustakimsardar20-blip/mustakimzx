package com.mustakimzx.receiver

import android.content.Context
import android.content.SharedPreferences

/** Receiver settings. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("mustakimzx_receiver", Context.MODE_PRIVATE)

    /** Press Enter automatically after the received value is typed. */
    var autoEnter: Boolean
        get() = sp.getBoolean(KEY_AUTO_ENTER, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_ENTER, value).apply()

    /** The keyboard inserts a scan as soon as it arrives. */
    var imeAutoInsert: Boolean
        get() = sp.getBoolean(KEY_IME_AUTO_INSERT, true)
        set(value) = sp.edit().putBoolean(KEY_IME_AUTO_INSERT, value).apply()

    /** Vibrate when a scan arrives. */
    var vibrate: Boolean
        get() = sp.getBoolean(KEY_VIBRATE, true)
        set(value) = sp.edit().putBoolean(KEY_VIBRATE, value).apply()

    /** Accessibility auto-type (native fields); the IME is the Chrome path. */
    var accessibilityAutoType: Boolean
        get() = sp.getBoolean(KEY_A11Y_AUTOTYPE, false)
        set(value) = sp.edit().putBoolean(KEY_A11Y_AUTOTYPE, value).apply()

    private companion object {
        const val KEY_AUTO_ENTER = "auto_enter"
        const val KEY_IME_AUTO_INSERT = "ime_auto_insert"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_A11Y_AUTOTYPE = "a11y_autotype"
    }
}
