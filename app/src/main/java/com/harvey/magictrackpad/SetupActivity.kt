package com.harvey.magictrackpad

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SetupActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_BT_PERMISSIONS = 100
        private const val REQUEST_ENABLE_BT = 101
        const val PREFS_NAME = "nanda_prefs"
        const val KEY_LAST_DEVICE = "last_device_address"
        const val KEY_LAST_DEVICE_NAME = "last_device_name"
    }

    private lateinit var statusText: TextView
    private lateinit var step1Status: TextView
    private lateinit var step2Status: TextView
    private lateinit var step3Status: TextView
    private lateinit var actionButton: TextView
    private lateinit var lastDeviceText: TextView

    private var currentStep = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        statusText = findViewById(R.id.status_text)
        step1Status = findViewById(R.id.step1_status)
        step2Status = findViewById(R.id.step2_status)
        step3Status = findViewById(R.id.step3_status)
        actionButton = findViewById(R.id.action_button)
        lastDeviceText = findViewById(R.id.last_device_text)

        actionButton.setOnClickListener { handleAction() }
        checkCurrentState()
    }

    override fun onResume() {
        super.onResume()
        checkCurrentState()
    }

    private fun checkCurrentState() {
        // Step 1: Permissions
        if (!hasBluetoothPermissions()) {
            currentStep = 1
            statusText.text = "Grant Bluetooth access"
            step1Status.text = "\u26A0\uFE0F  Permissions required"
            step2Status.text = "\u23F3  Bluetooth"
            step3Status.text = "\u23F3  Connection"
            actionButton.text = "Grant Permissions"
            return
        }
        step1Status.text = "\u2705  Permissions granted"

        // Step 2: Bluetooth on
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            statusText.text = "No Bluetooth hardware"
            actionButton.text = "Exit"
            actionButton.setOnClickListener { finish() }
            return
        }
        if (!adapter.isEnabled) {
            currentStep = 2
            statusText.text = "Turn on Bluetooth"
            step2Status.text = "\u26A0\uFE0F  Bluetooth is off"
            step3Status.text = "\u23F3  Connection"
            actionButton.text = "Enable Bluetooth"
            return
        }
        step2Status.text = "\u2705  Bluetooth is on"

        // Step 3: Ready
        currentStep = 3
        step3Status.text = "\u2705  Ready"

        // Show last paired device
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastName = prefs.getString(KEY_LAST_DEVICE_NAME, null)
        if (lastName != null) {
            statusText.text = "Ready to reconnect"
            lastDeviceText.text = "Last connected: $lastName"
            lastDeviceText.visibility = android.view.View.VISIBLE
            actionButton.text = "Connect to $lastName"
        } else {
            statusText.text = "Ready to connect"
            actionButton.text = "Launch Trackpad"
        }
    }

    private fun handleAction() {
        when (currentStep) {
            1 -> requestBluetoothPermissions()
            2 -> enableBluetooth()
            3 -> launchTrackpad()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                REQUEST_BT_PERMISSIONS
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun enableBluetooth() {
        try {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Enable Bluetooth in Settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchTrackpad() {
        startActivity(Intent(this, TrackpadActivity::class.java))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BT_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth", Toast.LENGTH_LONG).show()
            }
            checkCurrentState()
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) checkCurrentState()
    }
}
