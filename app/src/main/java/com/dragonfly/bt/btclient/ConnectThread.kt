package com.dragonfly.bt.btclient

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.blankj.utilcode.util.ToastUtils
import com.dragonfly.bt.btclient.MainActivity.Companion.TAG
import com.dragonfly.bt.btclient.MainActivity.Companion.XXH_UUID
import com.dragonfly.bt.btclient.MainActivity.Companion.mBtAdapter
import java.io.IOException

class ConnectThread(private val device: BluetoothDevice, private val connectCallback: (BluetoothSocket) -> Unit) : Thread() {

    private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        device.createRfcommSocketToServiceRecord(XXH_UUID)
    }

    override fun run() {
        // Cancel discovery because it otherwise slows down the connection.
        mBtAdapter.cancelDiscovery()

        mmSocket?.use { socket ->
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            try {
                ToastUtils.showShort("正在配对${device.address}")
                socket.connect()

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                connectCallback.invoke(socket)
            } catch (e: Exception) {
                ToastUtils.showShort("配对失败，请打开BTServer")
                e.printStackTrace()
            }
        }
    }

    // Closes the client socket and causes the thread to finish.
    fun cancel() {
        try {
            mmSocket?.close()
            ToastUtils.showShort("已断开连接")
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the client socket", e)
        }
    }
}