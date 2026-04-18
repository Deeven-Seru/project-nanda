package com.harvey.magictrackpad

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TrackpadActivity : AppCompatActivity() {
    private var hidService: BluetoothHidService? = null
    private var isBound = false

    private var lastX = 0f
    private var lastY = 0f
    private var tracking = false

    private var lastScrollY = 0f
    private var scrolling = false

    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var twoFingerTapTime = 0L

    private val SENSITIVITY = 2.0f
    private val TAP_THRESHOLD_MS = 200
    private val TAP_MOVE_THRESHOLD = 25f
    private val SCROLL_SENSITIVITY = 1.2f

    private lateinit var hiddenInput: EditText

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as BluetoothHidService.LocalBinder
                hidService = binder.getService()
                isBound = true
                hidService?.onStatusChanged = { msg -> updateStatus(msg) }
                updateStatus(hidService?.connectionStatus ?: "Connected")
            } catch (e: Exception) {
                updateStatus("Error: ${e.message}")
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            updateStatus("Disconnected")
        }
    }

    private var statusView: TextView? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trackpad)
        statusView = findViewById(R.id.trackpad_status)
        hiddenInput = findViewById(R.id.hidden_input)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences(SetupActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val savedBrightness = prefs.getInt("brightness", 1)
        setBrightness(savedBrightness)

        val slider = findViewById<SeekBar>(R.id.brightness_slider)
        slider.progress = savedBrightness
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) setBrightness(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.edit().putInt("brightness", sb?.progress ?: 1).apply()
            }
        })

        // Keyboard button — focuses hidden EditText and shows soft keyboard
        findViewById<View>(R.id.keyboard_button).setOnClickListener {
            hiddenInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
        }

        // Capture typed characters from soft keyboard
        setupKeyboardCapture()

        // Bind HID service
        try {
            Intent(this, BluetoothHidService::class.java).also {
                bindService(it, connection, Context.BIND_AUTO_CREATE)
            }
        } catch (e: Exception) {
            updateStatus("Failed: ${e.message}")
            return
        }

        // Trackpad surface
        val surface = findViewById<View>(R.id.trackpad_surface)
        surface.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
        updateStatus("Starting...")
    }

    private fun setupKeyboardCapture() {
        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0 && s != null && start + count <= s.length) {
                    val typed = s.subSequence(start, start + count).toString()
                    for (ch in typed) {
                        sendCharAsHid(ch)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {
                // Clear to keep the field clean
                if (s != null && s.length > 10) {
                    s.clear()
                }
            }
        })

        // Catch special keys (backspace, enter) via key listener
        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val hidKey = mapSpecialKey(keyCode)
                if (hidKey != 0.toByte()) {
                    try { hidService?.sendKeyboardReport(0, hidKey) } catch (_: Exception) {}
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    private fun sendCharAsHid(ch: Char) {
        val (modifier, key) = charToHid(ch)
        if (key != 0.toByte()) {
            try { hidService?.sendKeyboardReport(modifier, key) } catch (_: Exception) {}
        }
    }

    private fun charToHid(ch: Char): Pair<Byte, Byte> {
        // Returns (modifier, keycode)
        return when (ch) {
            in 'a'..'z' -> Pair(0, (0x04 + (ch - 'a')).toByte())
            in 'A'..'Z' -> Pair(0x02, (0x04 + (ch - 'A')).toByte()) // Left Shift
            in '1'..'9' -> Pair(0, (0x1E + (ch - '1')).toByte())
            '0' -> Pair(0, 0x27.toByte())
            ' ' -> Pair(0, 0x2C.toByte())  // SPACE
            '\n' -> Pair(0, 0x28.toByte()) // ENTER
            '\t' -> Pair(0, 0x2B.toByte()) // TAB
            '-' -> Pair(0, 0x2D.toByte())
            '=' -> Pair(0, 0x2E.toByte())
            '[' -> Pair(0, 0x2F.toByte())
            ']' -> Pair(0, 0x30.toByte())
            '\\' -> Pair(0, 0x31.toByte())
            ';' -> Pair(0, 0x33.toByte())
            '\'' -> Pair(0, 0x34.toByte())
            '`' -> Pair(0, 0x35.toByte())
            ',' -> Pair(0, 0x36.toByte())
            '.' -> Pair(0, 0x37.toByte())
            '/' -> Pair(0, 0x38.toByte())
            '!' -> Pair(0x02, 0x1E.toByte())
            '@' -> Pair(0x02, 0x1F.toByte())
            '#' -> Pair(0x02, 0x20.toByte())
            '$' -> Pair(0x02, 0x21.toByte())
            '%' -> Pair(0x02, 0x22.toByte())
            '^' -> Pair(0x02, 0x23.toByte())
            '&' -> Pair(0x02, 0x24.toByte())
            '*' -> Pair(0x02, 0x25.toByte())
            '(' -> Pair(0x02, 0x26.toByte())
            ')' -> Pair(0x02, 0x27.toByte())
            '_' -> Pair(0x02, 0x2D.toByte())
            '+' -> Pair(0x02, 0x2E.toByte())
            ':' -> Pair(0x02, 0x33.toByte())
            '"' -> Pair(0x02, 0x34.toByte())
            '<' -> Pair(0x02, 0x36.toByte())
            '>' -> Pair(0x02, 0x37.toByte())
            '?' -> Pair(0x02, 0x38.toByte())
            else -> Pair(0, 0.toByte())
        }
    }

    private fun mapSpecialKey(keyCode: Int): Byte = when (keyCode) {
        KeyEvent.KEYCODE_DEL -> 0x2A
        KeyEvent.KEYCODE_ENTER -> 0x28
        KeyEvent.KEYCODE_TAB -> 0x2B
        KeyEvent.KEYCODE_ESCAPE -> 0x29
        KeyEvent.KEYCODE_DPAD_UP -> 0x52
        KeyEvent.KEYCODE_DPAD_DOWN -> 0x51
        KeyEvent.KEYCODE_DPAD_LEFT -> 0x50
        KeyEvent.KEYCODE_DPAD_RIGHT -> 0x4F
        else -> 0
    }.toByte()

    private fun setBrightness(level: Int) {
        val lp = window.attributes
        lp.screenBrightness = (level.coerceIn(1, 100) / 100f).coerceIn(0.01f, 1f)
        window.attributes = lp
    }

    private fun handleTouch(event: MotionEvent) {
        val pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                tracking = true
                scrolling = false
                touchDownTime = System.currentTimeMillis()
                touchDownX = event.x
                touchDownY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount >= 2) {
                    scrolling = true
                    tracking = false
                    twoFingerTapTime = System.currentTimeMillis()
                    // Average Y of both fingers
                    lastScrollY = (event.getY(0) + event.getY(1)) / 2f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (scrolling && pointerCount >= 2) {
                    val avgY = (event.getY(0) + event.getY(1)) / 2f
                    val deltaY = lastScrollY - avgY
                    val scrollAmount = (deltaY * SCROLL_SENSITIVITY).toInt()
                    if (scrollAmount != 0) {
                        try { hidService?.sendScroll(scrollAmount) } catch (_: Exception) {}
                        lastScrollY = avgY
                    }
                } else if (tracking && pointerCount == 1 && !scrolling) {
                    val dx = ((event.x - lastX) * SENSITIVITY).toInt()
                    val dy = ((event.y - lastY) * SENSITIVITY).toInt()
                    if (dx != 0 || dy != 0) {
                        try { hidService?.sendMouseMove(dx, dy) } catch (_: Exception) {}
                        lastX = event.x
                        lastY = event.y
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!scrolling) {
                    val elapsed = System.currentTimeMillis() - touchDownTime
                    val dist = kotlin.math.hypot(
                        (event.x - touchDownX).toDouble(),
                        (event.y - touchDownY).toDouble()
                    )
                    if (elapsed < TAP_THRESHOLD_MS && dist < TAP_MOVE_THRESHOLD) {
                        try { hidService?.sendClick() } catch (_: Exception) {}
                    }
                }
                tracking = false
                scrolling = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Two-finger tap = right click
                if (pointerCount == 2) {
                    val elapsed = System.currentTimeMillis() - twoFingerTapTime
                    if (elapsed < TAP_THRESHOLD_MS) {
                        try { hidService?.sendClick(2) } catch (_: Exception) {} // Right click
                    }
                }
                scrolling = false
                tracking = false
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                scrolling = false
            }
        }
    }

    private fun updateStatus(msg: String) {
        runOnUiThread { statusView?.text = msg }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try { unbindService(connection) } catch (_: Exception) {}
            isBound = false
        }
    }
}
