package com.mustakimzx.scanner

import android.content.Context
import android.content.SharedPreferences

/** Small persistent store for the chosen receiver + scanner options. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("mustakimzx", Context.MODE_PRIVATE)

    var receiverAddress: String?
        get() = sp.getString(KEY_ADDRESS, null)
        set(value) = sp.edit().putString(KEY_ADDRESS, value).apply()

    var receiverName: String?
        get() = sp.getString(KEY_NAME, null)
        set(value) = sp.edit().putString(KEY_NAME, value).apply()

    /** Ignore the same code if it was scanned again within this window. */
    var duplicateWindowMs: Long
        get() = sp.getLong(KEY_DUP_WINDOW, 1500L)
        set(value) = sp.edit().putLong(KEY_DUP_WINDOW, value).apply()

    /** Vibrate/beep on every successful scan. */
    var feedbackEnabled: Boolean
        get() = sp.getBoolean(KEY_FEEDBACK, true)
        set(value) = sp.edit().putBoolean(KEY_FEEDBACK, value).apply()

    companion object {
        private const val KEY_ADDRESS = "receiver_address"
        private const val KEY_NAME = "receiver_name"
        private const val KEY_DUP_WINDOW = "duplicate_window"
        private const val KEY_FEEDBACK = "feedback"
    }
}
