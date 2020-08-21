package com.dragonfly.btserver


import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.ToastUtils
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "BTServer"
    val XXH_UUID = UUID.fromString("33719b35-639a-4edc-b9bc-345cf8bf3829")
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val thread = AcceptThread()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvUUID.text = "配对UUID：$XXH_UUID \n" +
                "已配对设备:\n" +
                bluetoothAdapter?.bondedDevices?.map {
                    it.name + " " +  it.address + "\n"
                }

        thread.start()
        btDisConnect.visibility = View.VISIBLE
        btDisConnect.setOnClickListener {
            thread.cancel()
        }
    }

    private inner class AcceptThread : Thread() {
        private val TAG = "AcceptThread"
        private val NAME = "XXH"

        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(NAME, XXH_UUID)
        }

        override fun run() {
            // Keep listening until exception occurs or a socket is returned.
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    shouldLoop = false
                    null
                }
                socket?.also {
                    manageMyConnectedSocket(it)
                    mmServerSocket?.close()
                    shouldLoop = false
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    private fun manageMyConnectedSocket(it: BluetoothSocket) {
        ToastUtils.showShort("连接成功")
        Log.e(TAG, it.toString())
    }
}