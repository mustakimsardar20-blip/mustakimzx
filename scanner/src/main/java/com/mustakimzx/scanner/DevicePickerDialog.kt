package com.mustakimzx.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.appcompat.app.AlertDialog

/** Simple single-choice list of the phone's paired Bluetooth devices. */
object DevicePickerDialog {

    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        devices: List<BluetoothDevice>,
        selectedAddress: String?,
        onPick: (BluetoothDevice) -> Unit
    ) {
        val labels = devices.map { device ->
            val name = device.name ?: "Unknown device"
            "$name\n${device.address}"
        }.toTypedArray()

        val checked = devices.indexOfFirst { it.address == selectedAddress }

        AlertDialog.Builder(context)
            .setTitle(R.string.pick_device)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                onPick(devices[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
