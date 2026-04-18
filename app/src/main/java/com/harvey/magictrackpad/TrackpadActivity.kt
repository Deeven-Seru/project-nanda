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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TrackpadActivity : AppCompatActivity() {
    private var hidService: BluetoothHidService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as BluetoothHidService.LocalBinder
                hidService = binder.getService()
                isBound = true
                updateStatus("Connected to HID service. Waiting for Mac...")
            } catch (e: Exception) {
                updateStatus("Service error: ${e.message}")
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            updateStatus("Service disconnected")
        }
    }

    private var statusView: TextView? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trackpad)

        statusView = findViewById(R.id.trackpad_status)

        // Fullscreen & Stealth
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = 0.01f
        window.attributes = lp

        try {
            Intent(this, BluetoothHidService::class.java).also { intent ->
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        } catch (e: Exception) {
            updateStatus("Failed to start Bluetooth: ${e.message}")
            return
        }

        val trackpadSurface = findViewById<View>(R.id.trackpad_surface)

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                showKeyboard()
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double-tap to toggle status visibility
                statusView?.visibility = if (statusView?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                return true
            }
        })

        trackpadSurface.setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)

            val action = event.actionMasked
            val isDown = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL

            val x = (event.x / view.width * 4095).toInt().coerceIn(0, 4095)
            val y = (event.y / view.height * 4095).toInt().coerceIn(0, 4095)

            try {
                hidService?.sendTouchReport(x, y, isDown)
            } catch (e: Exception) {
                // Silently ignore send failures
            }
            true
        }

        updateStatus("Starting Bluetooth HID service...")
    }

    private fun updateStatus(msg: String) {
        runOnUiThread {
            statusView?.text = msg
        }
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val hidKey = mapKeyCode(keyCode)
        if (hidKey != 0.toByte()) {
            try {
                hidService?.sendKeyboardReport(0, hidKey)
            } catch (e: Exception) {
                // Ignore
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun mapKeyCode(keyCode: Int): Byte {
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> 0x04; KeyEvent.KEYCODE_B -> 0x05
            KeyEvent.KEYCODE_C -> 0x06; KeyEvent.KEYCODE_D -> 0x07
            KeyEvent.KEYCODE_E -> 0x08; KeyEvent.KEYCODE_F -> 0x09
            KeyEvent.KEYCODE_G -> 0x0A; KeyEvent.KEYCODE_H -> 0x0B
            KeyEvent.KEYCODE_I -> 0x0C; KeyEvent.KEYCODE_J -> 0x0D
            KeyEvent.KEYCODE_K -> 0x0E; KeyEvent.KEYCODE_L -> 0x0F
            KeyEvent.KEYCODE_M -> 0x10; KeyEvent.KEYCODE_N -> 0x11
            KeyEvent.KEYCODE_O -> 0x12; KeyEvent.KEYCODE_P -> 0x13
            KeyEvent.KEYCODE_Q -> 0x14; KeyEvent.KEYCODE_R -> 0x15
            KeyEvent.KEYCODE_S -> 0x16; KeyEvent.KEYCODE_T -> 0x17
            KeyEvent.KEYCODE_U -> 0x18; KeyEvent.KEYCODE_V -> 0x19
            KeyEvent.KEYCODE_W -> 0x1A; KeyEvent.KEYCODE_X -> 0x1B
            KeyEvent.KEYCODE_Y -> 0x1C; KeyEvent.KEYCODE_Z -> 0x1D
            KeyEvent.KEYCODE_1 -> 0x1E; KeyEvent.KEYCODE_2 -> 0x1F
            KeyEvent.KEYCODE_3 -> 0x20; KeyEvent.KEYCODE_4 -> 0x21
            KeyEvent.KEYCODE_5 -> 0x22; KeyEvent.KEYCODE_6 -> 0x23
            KeyEvent.KEYCODE_7 -> 0x24; KeyEvent.KEYCODE_8 -> 0x25
            KeyEvent.KEYCODE_9 -> 0x26; KeyEvent.KEYCODE_0 -> 0x27
            KeyEvent.KEYCODE_ENTER -> 0x28; KeyEvent.KEYCODE_ESCAPE -> 0x29
            KeyEvent.KEYCODE_DEL -> 0x2A; KeyEvent.KEYCODE_TAB -> 0x2B
            KeyEvent.KEYCODE_SPACE -> 0x2C
            KeyEvent.KEYCODE_COMMA -> 0x36; KeyEvent.KEYCODE_PERIOD -> 0x37
            else -> 0
        }.toByte()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try { unbindService(connection) } catch (_: Exception) {}
            isBound = false
        }
    }
}
