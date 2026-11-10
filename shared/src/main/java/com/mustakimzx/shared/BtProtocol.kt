package com.mustakimzx.shared

import java.util.UUID

/**
 * Bluetooth RFCOMM protocol shared by both apps.
 *
 * Transport: classic Bluetooth RFCOMM (SPP style) socket.
 * Frame format: one UTF-8 text line per scan, terminated by '\n'.
 * Both phones must be paired once in Android Bluetooth settings.
 */
object BtProtocol {

    /** Service record name shown by the receiver when it starts listening. */
    const val SERVICE_NAME = "MUSTAKIMZX"

    /** Custom RFCOMM UUID, identical in the scanner and the receiver app. */
    val RFCOMM_UUID: UUID = UUID.fromString("8f7a3c1e-5c5a-4d3e-9f0a-1b2c3d4e5f60")

    /** Hard limit so a corrupted frame can never flood the receiver. */
    const val MAX_LINE_LENGTH = 4096

    /** Removes characters that would break the one-line framing. */
    fun encode(raw: String): String {
        val cleaned = raw
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
        return if (cleaned.length > MAX_LINE_LENGTH) cleaned.substring(0, MAX_LINE_LENGTH) else cleaned
    }
}
