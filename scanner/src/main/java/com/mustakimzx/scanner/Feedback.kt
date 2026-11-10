package com.mustakimzx.scanner

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short beep + vibration so the operator knows every scan went through. */
class Feedback(private val context: Context) {

    private var tone: ToneGenerator? = null

    fun success() {
        beep(ToneGenerator.TONE_PROP_BEEP)
        vibrate(35)
    }

    fun error() {
        beep(ToneGenerator.TONE_PROP_NACK)
        vibrate(120)
    }

    @Synchronized
    private fun beep(kind: Int) {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone?.startTone(kind, 90)
        } catch (ignored: RuntimeException) {
            // Some devices have no tone generator; vibration still works.
        }
    }

    private fun vibrate(ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return

        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    fun release() {
        runCatching { tone?.release() }
        tone = null
    }
}
