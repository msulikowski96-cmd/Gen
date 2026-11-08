package com.btpilot

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
class MainActivity : Activity(), HidController.Listener {

    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var touchpad: TouchpadView
    private lateinit var input: EditText
    private lateinit var prefs: SharedPreferences

    /** Ctrl/Alt/Shift/Win włączone "na jeden klawisz". */
    private var stickyMods = 0
    private val modButtons = mutableMapOf<Int, Button>()

    private var lastText = ""
    private var ignoreTextChange = false

    private val bluetoothAdapter: BluetoothAdapter?
        get() = getSystemService(BluetoothManager::class.java)?.adapter

    // ------------------------------------------------------------------ start

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)

        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        touchpad = findViewById(R.id.touchpad)
        input = findViewById(R.id.input)

        setupTouchpad()
        setupMouseButtons()
        setupSystemRow()
        setupKeysRow()
        setupInput()
        applySettings()

        findViewById<Button>(R.id.btnConnect).setOnClickListener { showConnectDialog() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }

        ensurePermissions()
    }

    override fun onStart() {
        super.onStart()
        HidController.addListener(this)
    }

    override fun onStop() {
        HidController.removeListener(this)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) HidController.stop()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ uprawnienia i Bluetooth

    private fun ensurePermissions() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyArray()
        }
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            ensureBluetoothOn()
        } else {
            requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSIONS) return
        if (hasConnectPermission()) {
            ensureBluetoothOn()
        } else {
            showStatus("Brak uprawnień do Bluetooth. Nadaj je w ustawieniach aplikacji.", R.color.err)
        }
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun ensureBluetoothOn() {
        val adapter = bluetoothAdapter
        when {
            adapter == null -> showStatus("Ten telefon nie ma Bluetooth.", R.color.err)
            !adapter.isEnabled -> {
                showStatus("Włącz Bluetooth…", R.color.warn)
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT)
            }
            else -> HidController.start(this)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ENABLE_BT) {
            if (bluetoothAdapter?.isEnabled == true) {
                HidController.start(this)
            } else {
                showStatus("Bluetooth jest wyłączony.", R.color.err)
            }
        }
    }

    // ------------------------------------------------------------------ stan połączenia

    override fun onHidStateChanged(state: HidController.State, device: BluetoothDevice?) {
        val name = device?.let { safeName(it) } ?: ""
        when (state) {
            HidController.State.STARTING -> showStatus("Uruchamianie…", R.color.warn)
            HidController.State.NO_BLUETOOTH -> showStatus("Ten telefon nie ma Bluetooth.", R.color.err)
            HidController.State.NOT_SUPPORTED -> showStatus(
                "Ten telefon nie obsługuje trybu myszy/klawiatury Bluetooth. " +
                    (HidController.lastError ?: ""),
                R.color.err,
            )
            HidController.State.READY -> showStatus("Gotowy. Kliknij „Połącz”.", R.color.warn)
            HidController.State.CONNECTING -> showStatus("Łączenie z $name…", R.color.warn)
            HidController.State.CONNECTED -> showStatus("Połączono: $name", R.color.ok)
        }
    }

    private fun showStatus(text: String, colorRes: Int) {
        statusText.text = text
        statusDot.backgroundTintList = ColorStateList.valueOf(getColor(colorRes))
    }

    private fun safeName(d: BluetoothDevice): String =
        try {
            d.name ?: d.address
        } catch (_: SecurityException) {
            d.address
        }

    private fun showConnectDialog() {
        if (!hasConnectPermission()) {
            ensurePermissions()
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            ensureBluetoothOn()
            return
        }
        if (HidController.state == HidController.State.NOT_SUPPORTED) {
            AlertDialog.Builder(this)
                .setTitle("Brak obsługi HID")
                .setMessage(
                    "Producent tego telefonu wyłączył funkcję „urządzenie HID przez Bluetooth”.\n\n" +
                        (HidController.lastError ?: "") +
                        "\n\nSpróbuj wyłączyć i włączyć Bluetooth, potem uruchom aplikację ponownie.",
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val bonded = adapter.bondedDevices.orEmpty().sortedBy { safeName(it).lowercase() }
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        labels += "📡  Uczyń ten telefon widocznym (sparuj z drugiego)"
        actions += { makeDiscoverable() }

        for (d in bonded) {
            val connected = HidController.isConnected && HidController.device == d
            labels += (if (connected) "✅  " else "📱  ") + safeName(d)
            actions += { HidController.connect(d) }
        }

        if (HidController.device != null) {
            labels += "✖  Rozłącz"
            actions += { HidController.disconnect() }
        }

        labels += "⚙  Ustawienia Bluetooth"
        actions += { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }

        AlertDialog.Builder(this)
            .setTitle("Połącz z urządzeniem")
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun makeDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        try {
            startActivity(intent)
            Toast.makeText(
                this,
                "Na drugim telefonie: Ustawienia → Bluetooth → sparuj z tym telefonem",
                Toast.LENGTH_LONG,
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Nie można włączyć widoczności: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------ mysz

    private fun setupTouchpad() {
        touchpad.callback = object : TouchpadView.Callback {
            override fun onMove(dx: Int, dy: Int) = HidController.mouseMove(dx, dy)
            override fun onScroll(vertical: Int, horizontal: Int) =
                HidController.mouseScroll(vertical, horizontal)

            override fun onTap(fingers: Int) = HidController.click(
                when (fingers) {
                    1 -> Hid.BUTTON_LEFT
                    2 -> Hid.BUTTON_RIGHT
                    else -> Hid.BUTTON_MIDDLE
                },
            )

            override fun onDragStart() = HidController.mouseButton(Hid.BUTTON_LEFT, true)
            override fun onDragEnd() = HidController.mouseButton(Hid.BUTTON_LEFT, false)
        }
    }

    /** Przyciski myszy działają jak prawdziwe: wciśnięty = trzymany (można przeciągać). */
    private fun setupMouseButtons() {
        bindHoldButton(findViewById(R.id.btnLeft), Hid.BUTTON_LEFT)
        bindHoldButton(findViewById(R.id.btnMiddle), Hid.BUTTON_MIDDLE)
        bindHoldButton(findViewById(R.id.btnRight), Hid.BUTTON_RIGHT)
    }

    private fun bindHoldButton(b: Button, button: Int) {
        b.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    HidController.mouseButton(button, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    HidController.mouseButton(button, false)
                }
            }
            true
        }
    }

    // ------------------------------------------------------------------ klawisze

    private fun setupSystemRow() {
        val row = findViewById<LinearLayout>(R.id.rowSystem)
        addKey(row, "◀ Wstecz") { HidController.consumer(Hid.CC_BACK) }
        addKey(row, "⌂ Home") { HidController.consumer(Hid.CC_HOME) }
        addKey(row, "▣ Aplikacje") { HidController.key(KeyMap.MOD_LALT, KeyMap.KEY_TAB) }
        addKey(row, "🔉", repeat = true) { HidController.consumer(Hid.CC_VOLUME_DOWN) }
        addKey(row, "🔊", repeat = true) { HidController.consumer(Hid.CC_VOLUME_UP) }
        addKey(row, "🔇") { HidController.consumer(Hid.CC_MUTE) }
        addKey(row, "⏮") { HidController.consumer(Hid.CC_PREVIOUS) }
        addKey(row, "⏯") { HidController.consumer(Hid.CC_PLAY_PAUSE) }
        addKey(row, "⏭") { HidController.consumer(Hid.CC_NEXT) }
        addKey(row, "☀−", repeat = true) { HidController.consumer(Hid.CC_BRIGHTNESS_DOWN) }
        addKey(row, "☀+", repeat = true) { HidController.consumer(Hid.CC_BRIGHTNESS_UP) }
    }

    private fun setupKeysRow() {
        val row = findViewById<LinearLayout>(R.id.rowKeys)
        addModifier(row, "Ctrl", KeyMap.MOD_LCTRL)
        addModifier(row, "Alt", KeyMap.MOD_LALT)
        addModifier(row, "Shift", KeyMap.MOD_LSHIFT)
        addModifier(row, "Win", KeyMap.MOD_LGUI)
        addKey(row, "Esc") { sendKey(KeyMap.KEY_ESC) }
        addKey(row, "Tab") { sendKey(KeyMap.KEY_TAB) }
        addKey(row, "←", repeat = true) { sendKey(KeyMap.KEY_LEFT) }
        addKey(row, "↑", repeat = true) { sendKey(KeyMap.KEY_UP) }
        addKey(row, "↓", repeat = true) { sendKey(KeyMap.KEY_DOWN) }
        addKey(row, "→", repeat = true) { sendKey(KeyMap.KEY_RIGHT) }
        addKey(row, "Enter") { sendKey(KeyMap.KEY_ENTER) }
        addKey(row, "⌫", repeat = true) { sendKey(KeyMap.KEY_BACKSPACE) }
        addKey(row, "Del", repeat = true) { sendKey(KeyMap.KEY_DELETE) }
        addKey(row, "Home") { sendKey(KeyMap.KEY_HOME) }
        addKey(row, "End") { sendKey(KeyMap.KEY_END) }
        addKey(row, "PgUp", repeat = true) { sendKey(KeyMap.KEY_PAGE_UP) }
        addKey(row, "PgDn", repeat = true) { sendKey(KeyMap.KEY_PAGE_DOWN) }
        for (i in 1..12) addKey(row, "F$i") { sendKey(KeyMap.KEY_F1 + i - 1) }
    }

    private fun sendKey(code: Int) {
        HidController.key(stickyMods, code)
        clearSticky()
    }

    private fun newKeyButton(label: String): Button =
        Button(this, null, 0, R.style.KeyButton).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (46 * resources.displayMetrics.density).toInt(),
            ).apply { marginEnd = (6 * resources.displayMetrics.density).toInt() }
        }

    private fun addKey(row: LinearLayout, label: String, repeat: Boolean = false, action: () -> Unit) {
        val b = newKeyButton(label)
        if (repeat) {
            bindRepeat(b, action)
        } else {
            b.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                action()
            }
        }
        row.addView(b)
    }

    private fun addModifier(row: LinearLayout, label: String, mod: Int) {
        val b = newKeyButton(label)
        b.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            stickyMods = stickyMods xor mod
            b.isActivated = stickyMods and mod != 0
        }
        modButtons[mod] = b
        row.addView(b)
    }

    private fun clearSticky() {
        if (stickyMods == 0) return
        stickyMods = 0
        modButtons.values.forEach { it.isActivated = false }
    }

    /** Przytrzymanie przycisku powtarza akcję (strzałki, Backspace, głośność). */
    private fun bindRepeat(b: Button, action: () -> Unit) {
        val repeater = object : Runnable {
            override fun run() {
                action()
                b.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        b.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    action()
                    v.postDelayed(repeater, REPEAT_START_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.removeCallbacks(repeater)
                }
            }
            true
        }
    }

    // ------------------------------------------------------------------ pisanie tekstu

    private fun setupInput() {
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable) {
                if (ignoreTextChange) return
                val now = s.toString()
                val old = lastText
                lastText = now

                // Porównujemy stary i nowy tekst: usunięte znaki -> Backspace, nowe -> wpisujemy.
                var common = 0
                val max = minOf(old.length, now.length)
                while (common < max && old[common] == now[common]) common++

                repeat(old.length - common) { HidController.key(0, KeyMap.KEY_BACKSPACE) }
                val added = now.substring(common)
                if (added.isNotEmpty()) {
                    HidController.type(added, stickyMods)
                    clearSticky()
                }
            }
        })

        // Enter na klawiaturze telefonu = Enter na drugim urządzeniu.
        input.setOnEditorActionListener { _, _, _ ->
            HidController.key(0, KeyMap.KEY_ENTER)
            clearInput()
            true
        }

        // Backspace przy pustym polu nadal kasuje znaki na drugim urządzeniu.
        input.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when {
                keyCode == KeyEvent.KEYCODE_DEL && input.text.isEmpty() -> {
                    HidController.key(0, KeyMap.KEY_BACKSPACE); true
                }
                keyCode == KeyEvent.KEYCODE_ENTER -> {
                    HidController.key(0, KeyMap.KEY_ENTER); clearInput(); true
                }
                else -> false
            }
        }

        findViewById<Button>(R.id.btnKeyboard).setOnClickListener { toggleKeyboard() }
    }

    private fun clearInput() {
        ignoreTextChange = true
        input.setText("")
        lastText = ""
        ignoreTextChange = false
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        if (input.hasFocus()) {
            imm.hideSoftInputFromWindow(input.windowToken, 0)
            input.clearFocus()
        } else {
            input.requestFocus()
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // ------------------------------------------------------------------ ustawienia

    private fun applySettings() {
        touchpad.sensitivity = prefs.getFloat(PREF_SENSITIVITY, 1.5f)
        touchpad.scrollSpeed = prefs.getFloat(PREF_SCROLL, 1f)
        touchpad.invertScroll = prefs.getBoolean(PREF_INVERT, false)
    }

    private fun showSettingsDialog() {
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        fun slider(title: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
            val label = TextView(this).apply { setPadding(0, pad / 2, 0, 0) }
            val steps = 100
            fun show(v: Float) {
                label.text = "%s: %.1f".format(title, v)
            }
            show(value)
            val bar = SeekBar(this).apply {
                this.max = steps
                progress = (((value - min) / (max - min)) * steps).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        val v = min + (max - min) * p / steps
                        show(v)
                        onChange(v)
                    }

                    override fun onStartTrackingTouch(sb: SeekBar) = Unit
                    override fun onStopTrackingTouch(sb: SeekBar) = Unit
                })
            }
            box.addView(label)
            box.addView(bar)
        }

        var sens = touchpad.sensitivity
        var scroll = touchpad.scrollSpeed
        slider("Czułość kursora", sens, 0.3f, 4f) { sens = it }
        slider("Szybkość przewijania", scroll, 0.3f, 3f) { scroll = it }
        val invert = CheckBox(this).apply {
            text = "Odwróć kierunek przewijania"
            isChecked = touchpad.invertScroll
        }
        box.addView(invert)
        box.addView(TextView(this).apply {
            text = "Polskie znaki (ą, ę, ś…) działają, gdy na drugim urządzeniu " +
                "ustawisz układ fizycznej klawiatury „Polski (programisty)”."
            textSize = 12f
            setPadding(0, pad / 2, 0, 0)
            gravity = Gravity.START
        })

        AlertDialog.Builder(this)
            .setTitle("Ustawienia")
            .setView(box)
            .setPositiveButton("Zapisz") { _, _ ->
                prefs.edit()
                    .putFloat(PREF_SENSITIVITY, sens)
                    .putFloat(PREF_SCROLL, scroll)
                    .putBoolean(PREF_INVERT, invert.isChecked)
                    .apply()
                applySettings()
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private companion object {
        const val REQ_PERMISSIONS = 1
        const val REQ_ENABLE_BT = 2
        const val REPEAT_START_MS = 400L
        const val REPEAT_INTERVAL_MS = 70L
        const val PREF_SENSITIVITY = "sensitivity"
        const val PREF_SCROLL = "scroll"
        const val PREF_INVERT = "invert"
    }
}
