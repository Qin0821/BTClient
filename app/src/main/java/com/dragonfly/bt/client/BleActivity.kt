package com.dragonfly.bt.client

import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.kq.btb.toStr
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class BleActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private lateinit var mDiscoverAdapter: BaseQuickAdapter<BluetoothDevice, BaseViewHolder>
    private lateinit var mPairedAdapter: BaseQuickAdapter<BluetoothDevice, BaseViewHolder>
    private var mFilterNull = false
    private var mScanning: Boolean = false
    private val SCAN_PERIOD: Long = 10000
    private val mScanCallback: ScanCallback? = null
    private var mLeScanCallback: LeScanCallback? = null
    private var mBluetoothGattCallback: BluetoothGattCallback? = null
    private var mBluetoothDevice: BluetoothDevice? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mCharacteristic: BluetoothGattCharacteristic? = null
    private val UUID_SERVER = UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb")
    private val UUID_CHARREAD = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")
    private val CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val UUID_CHARWRITE = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val UUID_ADV_SERVER = UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mDiscoverAdapter =
            object : BaseQuickAdapter<BluetoothDevice, BaseViewHolder>(R.layout.item_bt) {
                override fun convert(holder: BaseViewHolder, item: BluetoothDevice) {
                    holder.setText(R.id.tvBt, item.toStr())
                }
            }
        mDiscoverAdapter.addChildClickViewIds(R.id.tvBt)
        mDiscoverAdapter.setOnItemChildClickListener { adapter, view, position ->
            connect(adapter.getItem(position) as BluetoothDevice)
        }
        rvBt.layoutManager = LinearLayoutManager(this)
        rvBt.adapter = mDiscoverAdapter

        btDiscover.setOnClickListener {
            mFilterNull = false
            scan()
        }
        btDiscoverFilter.setOnClickListener {
            mFilterNull = true
            scan()
        }

        btConnect.setOnClickListener {
            if (mBluetoothDevice != null) {
                connect(mBluetoothDevice!!)
            }
        }

        btDisConnect.setOnClickListener {
            disConnect()
        }

        btWrite.setOnClickListener {
            val data: String = etWrite.text.toString().trim()
            write(data.toByteArray())
        }

        initCallback()
    }

    fun scan() {
        addTip("开始扫描")
        mDiscoverAdapter.setList(listOf())

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        //    UUID[] uuids=new UUID[]{UUID_ADV_SERVER};
        bluetoothManager.adapter.startLeScan( /*uuids,*/mLeScanCallback)
    }

    private fun stopScan() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter.stopLeScan(mLeScanCallback)
        //扫描真正停止很多时候有点延迟
        mHandler.postDelayed({ addTip("stopScan") }, 500)
    }

    /**
     * 写特征
     * @param data 最大20byte
     */
    private fun write(data: ByteArray) {
        if (mBluetoothGatt != null && mCharacteristic != null) {
            addTip(
                "开始写 uuid：" + mCharacteristic!!.uuid.toString() + " str:" + String(
                    data
                )
            )
            mCharacteristic!!.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

//      mCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            mCharacteristic!!.value = data
            mBluetoothGatt!!.writeCharacteristic(mCharacteristic)
        } else {
            addTip("写失败")
        }
    }

    private fun initCallback() {
        mLeScanCallback = LeScanCallback { device, rssi, scanRecord ->
//            addTip("onLeScan:" + " name:" + device.name + " mac:" + device.address + " rssi:" + rssi)
//            runOnUiThread { mTvScanState.setText("扫描中") }
            if ("Ble Server" == device.name) {
                addTip("发现Ble Server")
                mBluetoothDevice = device
                stopScan()
            }
            val deviceName = device.name
            if (mFilterNull && (deviceName == null || deviceName == "null" || deviceName == "NULL") || mDiscoverAdapter.data.contains(device)) {
                return@LeScanCallback
            }
            mDiscoverAdapter.addData(device)
        }
        mBluetoothGattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                addTip("onConnectionStateChange status:$status newState:$newState")
                when (newState) {
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        addTip("STATE_DISCONNECTED")
                        gatt.close()
                    }
                    BluetoothProfile.STATE_CONNECTED -> {
                        addTip("STATE_CONNECTED")
                        addTip("start discoverServices")
                        gatt.discoverServices()
                    }
                    else -> {
                        addTip("newState:$newState")
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                addTip("onServicesDiscovered status:$status")
                val service = gatt.getService(UUID_SERVER)
                if (service != null) {
                    mCharacteristic = service.getCharacteristic(UUID_CHARWRITE)
                    if (mCharacteristic != null) {
                        addTip("获取到目标特征")
                    }
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic, status: Int
            ) {
                addTip(
                    "statu:" + status + " ,str:"
                            + String(characteristic.value)
                )
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic, status: Int
            ) {
                addTip(
                    "statu:" + status + " ,str:"
                            + String(characteristic.value)
                )
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                addTip(
                    " hexValue:" + " ,str:"
                            + String(characteristic.value)
                )
            }

            override fun onDescriptorRead(
                gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                addTip("onDescriptorRead status:" + status + " value:" + String(descriptor.value))
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                addTip("onDescriptorWrite status:" + status + " value:" + String(descriptor.value))
            }
        }
    }


    private val mHandler = Handler()

    /**
     * 连接设备
     * @param device 需要连接的蓝牙设备
     */
    private fun connect(device: BluetoothDevice) {
        addTip("开始连接")
        mBluetoothGatt = device.connectGatt(this, false, mBluetoothGattCallback)
    }

    /**
     * 断开连接
     */
    private fun disConnect() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt!!.disconnect()
            //      mBluetoothGatt.close();
        }
    }

    fun write(bleDevice: BluetoothDevice) {
//        BleManager.getInstance().write(
//            bleDevice,
//            XXH_UUID.toString(),
//            XXH_WRITE.toString(),
//            "这是中文This is english".toByteArray(),
//            object : BleWriteCallback() {
//                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray) {
//                    addTip("写入成功:$current/$total $justWrite")
//                }
//
//                override fun onWriteFailure(exception: BleException) {
//                    addTip("写入失败:${exception.description}")
//                }
//            })
    }

    fun addTip(msg: String) {
        runOnUiThread {
            tvTip2.text = "${tvTip2.text}\n$msg"
        }
    }
}