package com.mustakimzx.scanner

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormat {
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun hhmmss(millis: Long): String = formatter.format(Date(millis))
}
