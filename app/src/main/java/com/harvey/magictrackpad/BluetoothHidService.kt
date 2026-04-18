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

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothHidService = this@BluetoothHidService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = binder

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
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
            0xC0.toByte(), // SUBCLASS1_COMBO
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
        val device = connectedDevice ?: return
        val report = ByteArray(5)
        report[0] = if (isDown) 1.toByte() else 0.toByte()
        report[1] = (x and 0xFF).toByte()
        report[2] = ((x shr 8) and 0xFF).toByte()
        report[3] = (y and 0xFF).toByte()
        report[4] = ((y shr 8) and 0xFF).toByte()
        
        bluetoothHidDevice?.sendReport(device, 2, report)
    }

    @SuppressLint("MissingPermission")
    fun sendKeyboardReport(modifier: Byte, key: Byte) {
        val device = connectedDevice ?: return
        val report = ByteArray(8)
        report[0] = modifier
        report[2] = key
        bluetoothHidDevice?.sendReport(device, 1, report)
        
        // Key release
        val releaseReport = ByteArray(8)
        bluetoothHidDevice?.sendReport(device, 1, releaseReport)
    }
}
