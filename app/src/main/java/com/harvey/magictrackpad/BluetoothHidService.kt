package com.harvey.magictrackpad

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class BluetoothHidService : Service() {
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    var isConnected = false
        private set
    var connectionStatus = "Initializing..."
        private set
    var onStatusChanged: ((String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothHidService = this@BluetoothHidService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            updateStatus("No Bluetooth hardware")
            return
        }
        updateStatus("Registering HID...")
        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    bluetoothHidDevice = proxy as BluetoothHidDevice
                    registerApp()
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    bluetoothHidDevice = null
                    updateStatus("HID disconnected")
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Project Nanda",
            "Magic Trackpad",
            "Harvey",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.DESCRIPTOR
        )
        bluetoothHidDevice?.registerApp(sdp, null, null, mainExecutor, object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                if (registered) {
                    updateStatus("HID ready. Pair from Mac > Bluetooth")
                    tryReconnectLastDevice()
                } else {
                    updateStatus("HID registration failed")
                }
            }
            override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedDevice = device
                        isConnected = true
                        saveLastDevice(device)
                        updateStatus("Connected: ${device.name ?: device.address}")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDevice = null
                        isConnected = false
                        updateStatus("Disconnected. Waiting...")
                    }
                    BluetoothProfile.STATE_CONNECTING -> updateStatus("Connecting...")
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun tryReconnectLastDevice() {
        val prefs = getSharedPreferences(SetupActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val lastAddress = prefs.getString(SetupActivity.KEY_LAST_DEVICE, null) ?: return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        try {
            val device = adapter.getRemoteDevice(lastAddress)
            updateStatus("Reconnecting to ${device.name ?: lastAddress}...")
            bluetoothHidDevice?.connect(device)
        } catch (e: Exception) {
            Log.d("HID", "Auto-reconnect failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun saveLastDevice(device: BluetoothDevice) {
        val prefs = getSharedPreferences(SetupActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(SetupActivity.KEY_LAST_DEVICE, device.address)
            .putString(SetupActivity.KEY_LAST_DEVICE_NAME, device.name ?: device.address)
            .apply()
    }

    private fun updateStatus(msg: String) {
        connectionStatus = msg
        Log.d("HID", msg)
        onStatusChanged?.invoke(msg)
    }

    @SuppressLint("MissingPermission")
    fun sendMouseMove(dx: Int, dy: Int) {
        val device = connectedDevice ?: return
        val report = byteArrayOf(0, dx.coerceIn(-127, 127).toByte(), dy.coerceIn(-127, 127).toByte(), 0)
        bluetoothHidDevice?.sendReport(device, 2, report)
    }

    @SuppressLint("MissingPermission")
    fun sendClick(button: Int = 1) {
        val device = connectedDevice ?: return
        bluetoothHidDevice?.sendReport(device, 2, byteArrayOf(button.toByte(), 0, 0, 0))
        bluetoothHidDevice?.sendReport(device, 2, byteArrayOf(0, 0, 0, 0))
    }

    @SuppressLint("MissingPermission")
    fun sendScroll(amount: Int) {
        val device = connectedDevice ?: return
        bluetoothHidDevice?.sendReport(device, 2, byteArrayOf(0, 0, 0, amount.coerceIn(-127, 127).toByte()))
    }

    @SuppressLint("MissingPermission")
    fun sendKeyboardReport(modifier: Byte, key: Byte) {
        val device = connectedDevice ?: return
        val report = ByteArray(8)
        report[0] = modifier
        report[2] = key
        bluetoothHidDevice?.sendReport(device, 1, report)
        bluetoothHidDevice?.sendReport(device, 1, ByteArray(8))
    }
}
