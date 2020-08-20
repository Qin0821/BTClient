package com.kq.btb

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast
import com.clj.fastble.data.BleDevice

/**
 * @author : Qin
 * @date   : 2020/8/18
 */
fun BluetoothDevice.toStr(): String {
    return "name: $name mac: $address"
}

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun BleDevice.toStr(): String {
    return "name: $name mac: $mac"
}