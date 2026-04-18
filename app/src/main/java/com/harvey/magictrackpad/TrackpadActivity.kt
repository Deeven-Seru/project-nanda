package com.harvey.magictrackpad

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity

class TrackpadActivity : AppCompatActivity() {
    private var hidService: BluetoothHidService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothHidService.LocalBinder
            hidService = binder.getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Stealth Mode: Pure Black & Min Brightness
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = 0.01f
        window.attributes = lp

        // Bind Service
        Intent(this, BluetoothHidService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        val trackpad = findViewById<View>(R.id.trackpad_surface)
        trackpad.setOnTouchListener { v, event ->
            handleTouch(event)
            true
        }

        // Long press for Keyboard
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                showKeyboard()
            }
        })
        trackpad.setOnGenericMotionListener { v, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun handleTouch(event: MotionEvent) {
        val action = event.actionMasked
        val isDown = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
        
        // Map 0..width/height to 0..4095 (HID Range)
        val x = (event.x / v.width * 4095).toInt().coerceIn(0, 4095)
        val y = (event.y / v.height * 4095).toInt().coerceIn(0, 4095)

        hidService?.sendTouchReport(x, y, isDown)
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Map Android KeyCodes to HID Usage Codes
        val hidKey = mapToHid(keyCode)
        if (hidKey != 0.toByte()) {
            hidService?.sendKeyboardReport(0, hidKey)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun mapToHid(keyCode: Int): Byte {
        // Basic mapping (A=4, B=5, etc.)
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> 0x04.toByte()
            KeyEvent.KEYCODE_B -> 0x05.toByte()
            KeyEvent.KEYCODE_C -> 0x06.toByte()
            KeyEvent.KEYCODE_ENTER -> 0x28.toByte()
            KeyEvent.KEYCODE_DEL -> 0x2a.toByte()
            KeyEvent.KEYCODE_SPACE -> 0x2c.toByte()
            else -> 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
