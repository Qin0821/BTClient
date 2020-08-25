package com.dragonfly.bt.btclient

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankj.utilcode.constant.PermissionConstants
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.ToastUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.kq.btb.toStr
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    companion object {

        const val TAG = "BTClient"
        val XXH_UUID = UUID.fromString("33719b35-639a-4edc-b9bc-345cf8bf3829")
        val mBtAdapter = BluetoothAdapter.getDefaultAdapter()

        const val MESSAGE_READ: Int = 0
        const val MESSAGE_WRITE: Int = 1
        const val MESSAGE_TOAST: Int = 2
    }
//    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private lateinit var mDiscoverAdapter: BaseQuickAdapter<BluetoothDevice, BaseViewHolder>
    private lateinit var mPairedAdapter: BaseQuickAdapter<BluetoothDevice, BaseViewHolder>

    private var mSocket: BluetoothSocket? = null

    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null


    private val mHi = arrayOf("hi", "Hello", "你好", "hanihaseiyo")

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PermissionUtils.permission(PermissionConstants.LOCATION, PermissionConstants.STORAGE)
            .callback { isAllGranted, _, _, _ ->
                if (!isAllGranted) finish()
            }.request()

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        btConnect.setOnClickListener {

            Log.e(TAG, "start connect")
            mDiscoverAdapter.setNewInstance(ArrayList())
            if (mBtAdapter.isDiscovering) mBtAdapter.cancelDiscovery()
            val result = mBtAdapter.startDiscovery()
            Log.e(TAG, result.toString())
        }

        btSayHi.setOnClickListener {
//            mConnectedThread?.write(mHi[(1..3).random()].toByteArray())
            write("这是中文this is english".toByteArray())
        }

        btRefresh.setOnClickListener {
            mPairedAdapter.setNewInstance(mBtAdapter.bondedDevices.toMutableList())
        }

        mDiscoverAdapter =
            object : BaseQuickAdapter<BluetoothDevice, BaseViewHolder>(R.layout.item_bt) {
                override fun convert(holder: BaseViewHolder, item: BluetoothDevice) {
                    holder.setText(R.id.tvBt, item.toStr())
                }
            }
        mDiscoverAdapter.addChildClickViewIds(R.id.tvBt)
        mDiscoverAdapter.setOnItemChildClickListener { adapter, view, position ->
            connect(adapter, position)
        }
        rvBt.layoutManager = LinearLayoutManager(this)
        rvBt.adapter = mDiscoverAdapter

        mPairedAdapter = object : BaseQuickAdapter<BluetoothDevice, BaseViewHolder>(
            R.layout.item_bt,
            mBtAdapter.bondedDevices.toMutableList()
        ) {
            override fun convert(holder: BaseViewHolder, item: BluetoothDevice) {
                holder.setText(R.id.tvBt, item.toStr())
            }
        }
        mPairedAdapter.addChildClickViewIds(R.id.tvBt)
        mPairedAdapter.addChildLongClickViewIds(R.id.tvBt)
        mPairedAdapter.setOnItemChildClickListener { adapter, view, position ->

            removeBond(adapter.getItem(position) as BluetoothDevice)
            mPairedAdapter.setNewInstance(mBtAdapter.bondedDevices.toMutableList())
        }
        mPairedAdapter.setOnItemChildLongClickListener { adapter, view, position ->
            connect(adapter, position)
            return@setOnItemChildLongClickListener true
        }
        rvPaired.layoutManager = LinearLayoutManager(this)
        rvPaired.adapter = mPairedAdapter
    }

    private fun connect(adapter: BaseQuickAdapter<*, *>, position: Int) {
        mBtAdapter.cancelDiscovery()

        val device = adapter.getItem(position) as BluetoothDevice
        mConnectThread = ConnectThread(device) { socket ->
            mSocket = socket

            runOnUiThread {
                ToastUtils.showShort("配对成功")
                mPairedAdapter.setNewInstance(mBtAdapter.bondedDevices.toMutableList())
            }

            Log.e(TAG, socket.toString())
//            write(mHi[(0..3).random()].toByteArray())
            write("这是中文this is english".toByteArray())
            //                mConnectedThread = ConnectedThread(mHandler, socket)
            //                mConnectedThread!!.start()
        }
        mConnectThread!!.start()
    }

    fun write(bytes: ByteArray) {
        val mmBuffer: ByteArray = ByteArray(1024)
        try {
            val mmOutStream: OutputStream = mSocket!!.outputStream
            mmOutStream.write(bytes)
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred when sending data", e)

            // Send a failure message back to the activity.
            val writeErrorMsg = mHandler.obtainMessage(MESSAGE_TOAST)
            val bundle = Bundle().apply {
                putString("toast", "Couldn't send data to the other device")
            }
            writeErrorMsg.data = bundle
            mHandler.sendMessage(writeErrorMsg)
            return
        }

        // Share the sent message with the UI activity.
        val writtenMsg = mHandler.obtainMessage(
            MESSAGE_WRITE, -1, -1, mmBuffer
        )
        writtenMsg.sendToTarget()
    }

    private fun removeBond(device: BluetoothDevice) {
        try {
            device::class.java.getMethod("removeBond").invoke(device)
        } catch (e: Exception) {
            ToastUtils.showShort("取消配对失败")
            Log.e(TAG, "Removing bond has been failed. ${e.message}")
        }
    }

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device == null) {
                        Log.e(TAG, "bt device is null")
                        return
                    }
                    val deviceName = device.name
                    val deviceHardwareAddress = device.address // MAC address

                    Log.e(TAG, "name: $deviceName mac: $deviceHardwareAddress")
                    mDiscoverAdapter.addData(device)
                }
            }
        }
    }

    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                MESSAGE_READ -> {
                    Log.e(TAG, "read " + msg.arg1 + msg.arg2 + msg.obj)
                }
                MESSAGE_WRITE -> {
                    Log.e(TAG, "write " + msg.arg1 + msg.arg2 + msg.obj)
                }
                MESSAGE_TOAST -> {
                    Log.e(TAG, "toast " + msg.arg1 + msg.arg2 + msg.obj)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
    }
}
