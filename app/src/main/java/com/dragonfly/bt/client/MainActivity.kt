package com.dragonfly.bt.client

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
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    companion object {

        const val TAG = "BTClient"
        val XXH_UUID: UUID = UUID.fromString("33719b35-639a-4edc-b9bc-345cf8bf3829")
        val XXH_WRITE: UUID = UUID.fromString("33719b35-639a-4edc-b9bc-345cf8bf3830")
        val mBtAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        const val MESSAGE_READ: Int = 0
        const val MESSAGE_WRITE: Int = 1
        const val MESSAGE_TOAST: Int = 2
    }

    private lateinit var mDiscoverAdapter: BaseQuickAdapter<BluetoothDevice, BaseViewHolder>
    private lateinit var mPairedAdapter: BaseQuickAdapter<BluetoothDevice, BaseViewHolder>

    private var mSocket: BluetoothSocket? = null

    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null
    private var mFilterNull = false

    private val mHi = arrayOf("hi", "Hello", "你好", "hanihaseiyo")

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PermissionUtils.permission(PermissionConstants.LOCATION, PermissionConstants.STORAGE)
            .callback { isAllGranted, _, _, _ ->
                if (!isAllGranted) ToastUtils.showShort("无相关权限")
            }.request()

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        btDiscover.setOnClickListener {
            mFilterNull = false
            startDiscover()
        }

        btDiscoverFilter.setOnClickListener {
            mFilterNull = true
            startDiscover()
        }

        btWrite.setOnClickListener {
//            mConnectedThread?.write(mHi[(1..3).random()].toByteArray())
            write("这是中文this is english".toByteArray())
        }

        btRefresh.setOnClickListener {
            tvTip2.text = ""
            addTip("刷新")
            mPairedAdapter.setList(mBtAdapter.bondedDevices.toMutableList())
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
            mPairedAdapter.setList(mBtAdapter.bondedDevices.toMutableList())
        }
        mPairedAdapter.setOnItemChildLongClickListener { adapter, view, position ->
            connect(adapter, position)
            return@setOnItemChildLongClickListener true
        }
        rvPaired.layoutManager = LinearLayoutManager(this)
        rvPaired.adapter = mPairedAdapter
    }

    private fun startDiscover() {
        Log.e(TAG, "start connect")
        mDiscoverAdapter.setList(ArrayList())
        if (mBtAdapter.isDiscovering) mBtAdapter.cancelDiscovery()
        val result = mBtAdapter.startDiscovery()
        Log.e(TAG, result.toString())
    }

    private fun connect(adapter: BaseQuickAdapter<*, *>, position: Int) {
        mBtAdapter.cancelDiscovery()

        val device = adapter.getItem(position) as BluetoothDevice
        addTip("开始连接${device.name} ${device.address}")
        mConnectThread = ConnectThread(device) { socket ->
            mSocket = socket

            addTip("连接成功")
            runOnUiThread {
                mPairedAdapter.setList(mBtAdapter.bondedDevices.toMutableList())
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
        addTip("发送消息：'这是中文this is english'")
        val mmBuffer = ByteArray(1024)
        try {
            val mmOutStream: OutputStream = mSocket!!.outputStream
            mmOutStream.write(bytes)
        } catch (e: Exception) {
            addTip("发送消息失败：${e.message}")
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

        addTip("发送消息结束")

        // Share the sent message with the UI activity.
        val writtenMsg = mHandler.obtainMessage(
            MESSAGE_WRITE, -1, -1, mmBuffer
        )
        writtenMsg.sendToTarget()
    }

    private fun removeBond(device: BluetoothDevice) {
        try {
            addTip("移除配对")
            device::class.java.getMethod("removeBond").invoke(device)
        } catch (e: Exception) {
            addTip("移除配对失败：${e.message}")
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
                    if (mFilterNull && (deviceName == null || deviceName == "null" || deviceName == "NULL")) {
                        Log.e(TAG, "bt deviceName is null")
                        return
                    }

                    val deviceHardwareAddress = device.address // MAC address

                    Log.e(TAG, "name: $deviceName mac: $deviceHardwareAddress")
                    mDiscoverAdapter.addData(device)
                }
            }
        }
    }

    private val mHandler: Handler = @SuppressLint("HandlerLeak")
    object : Handler() {
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

    fun addTip(msg: String) {
        runOnUiThread {
            tvTip2.text = "${tvTip2.text}\n$msg"
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(receiver)
    }
}
