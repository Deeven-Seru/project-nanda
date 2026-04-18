package com.harvey.magictrackpad

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class BluetoothHidService : Service() {
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothHidService = this@BluetoothHidService
    }

    override fun onBind(intent: Intent): IBinder = binder

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        val adapter = BluetoothAdapter.getDefaultAdapter()
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
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Project Nanda",
            "Magic Trackpad Emulator",
            "Harvey",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.DESCRIPTOR
        )
        bluetoothHidDevice?.registerApp(sdp, null, null, mainExecutor, object : BluetoothHidDevice.Callback() {
            override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevice = device
                    Log.d("HID", "Connected to ${device.name}")
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevice = null
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun sendTouchReport(x: Int, y: Int, isDown: Boolean) {
        val report = ByteArray(5)
        report[0] = if (isDown) 1.toByte() else 0.toByte()
        report[1] = (x and 0xFF).toByte()
        report[2] = ((x shr 8) and 0xFF).toByte()
        report[3] = (y and 0xFF).toByte()
        report[4] = ((y shr 8) and 0xFF).toByte()
        
        connectedDevice?.let {
            bluetoothHidDevice?.sendReport(it, 2, report)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendKeyboardReport(modifier: Byte, key: Byte) {
        val report = ByteArray(8)
        report[0] = modifier
        report[2] = key
        connectedDevice?.let {
            bluetoothHidDevice?.sendReport(it, 1, report)
            // Send empty report to release key
            bluetoothHidDevice?.sendReport(it, 1, ByteArray(8))
        }
    }
}
