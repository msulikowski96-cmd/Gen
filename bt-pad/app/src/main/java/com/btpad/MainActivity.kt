package com.btpad

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
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.btpad.BluetoothPad.HostStatus
import com.btpad.BluetoothPad.State

@SuppressLint("MissingPermission")
class MainActivity : Activity(), BluetoothPad.Listener {

    private lateinit var pad: PadView
    private lateinit var status: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var gyro: GyroAim
    private lateinit var bar: View
    private lateinit var editBar: View
    private var onboardingShown = false
    private var lastBackPress = 0L

    private val adapter: BluetoothAdapter?
        get() = getSystemService(BluetoothManager::class.java)?.adapter

    // ------------------------------------------------------------------ start

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)

        pad = PadView(this).apply {
            haptics = prefs.getBoolean(PREF_HAPTICS, true)
            floatingStick = prefs.getBoolean(PREF_FLOATING, true)
            stickSensitivity = prefs.getFloat(PREF_STICK_SENS, 1f)
            fullTriggers = prefs.getBoolean(PREF_FULL_TRIGGERS, false)
            gyroMode = prefs.getInt(PREF_GYRO_MODE, PadView.GYRO_OFF)
            applyLayout(prefs.getString(PREF_LAYOUT, null))
            onLayoutEdited = { prefs.edit().putString(PREF_LAYOUT, layoutJson()).apply() }
            onChange = { BluetoothPad.send(it) }
        }

        @Suppress("DEPRECATION")
        gyro = GyroAim(this, { windowManager.defaultDisplay.rotation }) { x, y -> pad.setGyro(x, y) }.apply {
            sensitivity = prefs.getFloat(PREF_GYRO_SENS, 1f)
            invertX = prefs.getBoolean(PREF_GYRO_INV_X, false)
            invertY = prefs.getBoolean(PREF_GYRO_INV_Y, false)
        }

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFE6EDF3.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
            setOnClickListener { onStatusTap() }
        }
        val gear = TextView(this).apply {
            text = "⚙"
            textSize = 17f
            setTextColor(0xFFE6EDF3.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(14), 0)
            setOnClickListener { showSettings() }
        }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.pill_bg)
            addView(status, LinearLayout.LayoutParams(-2, dp(36)))
            addView(gear, LinearLayout.LayoutParams(-2, dp(36)))
        }
        editBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.pill_bg)
            visibility = View.GONE
            addView(barButton("  −  ") { pad.resizeSelected(0.9f) })
            addView(barButton("  +  ") { pad.resizeSelected(1.1f) })
            addView(barButton("Domyślny układ") { confirmResetLayout() })
            addView(barButton("✓ Gotowe") { setEditing(false) })
        }

        val root = FrameLayout(this)
        root.addView(pad, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            bar,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) },
        )
        root.addView(
            editBar,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) },
        )
        setContentView(root)

        ensurePermissions()
    }

    override fun onStart() {
        super.onStart()
        BluetoothPad.addListener(this)
    }

    override fun onStop() {
        pad.releaseAll()
        BluetoothPad.removeListener(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Gdyby Android wyłączył pad, gdy aplikacja była w tle – włącz ponownie.
        if (hasPermission() && adapter?.isEnabled == true) BluetoothPad.ensureRegistered()
        if (pad.gyroMode != PadView.GYRO_OFF) gyro.start()
    }

    override fun onPause() {
        gyro.stop()
        super.onPause()
    }

    /** Wstecz nie zamyka pada od razu – łatwo o to w trakcie gry. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (pad.editMode) {
            setEditing(false)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        } else {
            lastBackPress = now
            Toast.makeText(this, "Naciśnij Wstecz jeszcze raz, aby zamknąć pada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            BluetoothPad.stop()
            PadService.stop(this)
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // ------------------------------------------------------------------ uprawnienia i Bluetooth

    private fun ensurePermissions() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyArray()
        }
        // Powiadomienie „BT Pad działa” – mile widziane, ale niewymagane.
        val optional = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        val missing = (needed + optional).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) ensureBluetoothOn() else requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
    }

    private fun hasPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSIONS) return
        if (hasPermission()) ensureBluetoothOn() else setStatus("● Brak uprawnień Bluetooth – stuknij", COLOR_ERR)
    }

    private fun ensureBluetoothOn() {
        val a = adapter
        when {
            a == null -> setStatus("● Ten telefon nie ma Bluetooth", COLOR_ERR)
            !a.isEnabled -> {
                setStatus("● Włącz Bluetooth", COLOR_WARN)
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT)
            }
            else -> startPad()
        }
    }

    private fun startPad() {
        BluetoothPad.start(this)
        PadService.start(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ENABLE_BT) {
            if (adapter?.isEnabled == true) startPad() else setStatus("● Bluetooth wyłączony – stuknij", COLOR_ERR)
        }
    }

    // ------------------------------------------------------------------ stan

    override fun onPadStateChanged(state: State, device: BluetoothDevice?) {
        val name = BluetoothPad.name(device)
        when (state) {
            State.STARTING -> setStatus("● Uruchamianie… (stuknij, jeśli trwa długo)", COLOR_WARN)
            State.NO_BLUETOOTH -> setStatus("● Ten telefon nie ma Bluetooth", COLOR_ERR)
            State.NOT_SUPPORTED -> setStatus("● Pad nie wystartował – stuknij", COLOR_ERR)
            State.READY -> {
                setStatus("● Niepołączony – stuknij, aby połączyć", COLOR_WARN)
                maybeOnboard()
            }
            State.CONNECTING -> setStatus("● Łączenie z $name…", COLOR_WARN)
            State.CONNECTED -> {
                val s = device?.let { BluetoothPad.hostStatus(it) }
                if (s == HostStatus.CURRENT) {
                    setStatus("● Pad połączony: $name", COLOR_OK)
                } else {
                    setStatus("⚠ $name nie widzi pada – stuknij, aby naprawić", COLOR_WARN)
                }
            }
        }
    }

    private fun setStatus(text: String, color: Int) {
        status.text = text
        status.setTextColor(color)
    }

    private fun onStatusTap() {
        when {
            !hasPermission() -> ensurePermissions()
            adapter?.isEnabled != true -> ensureBluetoothOn()
            BluetoothPad.state == State.NOT_SUPPORTED || BluetoothPad.state == State.STARTING -> AlertDialog.Builder(this)
                .setTitle("Pad nie wystartował")
                .setMessage(
                    (BluetoothPad.lastError ?: "Pad jeszcze się uruchamia.") +
                        "\n\nJeśli to się powtarza: zamknij BT Pilot, wyłącz i włącz Bluetooth, potem „Spróbuj ponownie”.",
                )
                .setPositiveButton("Spróbuj ponownie") { _, _ ->
                    BluetoothPad.restart(this)
                    PadService.start(this)
                }
                .setNeutralButton("Diagnostyka") { _, _ -> Pairing.showDiagnostics(this) }
                .setNegativeButton("Zamknij", null)
                .show()
            BluetoothPad.isConnected && BluetoothPad.device != null &&
                BluetoothPad.hostStatus(BluetoothPad.device!!) != HostStatus.CURRENT ->
                Pairing.chooseDevice(this, BluetoothPad.device!!)
            else -> Pairing.showConnectDialog(this)
        }
    }

    /** Pierwsze uruchomienie: brak telefonu, który zna pad → od razu kreator parowania. */
    private fun maybeOnboard() {
        if (onboardingShown || BluetoothPad.lastHost() != null) return
        val known = try {
            adapter?.bondedDevices.orEmpty().any { BluetoothPad.hostStatus(it) == HostStatus.CURRENT }
        } catch (_: SecurityException) {
            false
        }
        if (known) return
        onboardingShown = true
        AlertDialog.Builder(this)
            .setTitle("Witaj w BT Pad 🎮")
            .setMessage(
                "Ten telefon będzie padem Xbox dla drugiego telefonu (np. z GeForce NOW).\n\n" +
                    "Najpierw jednorazowo sparuj telefony. Jeśli były już sparowane wcześniej " +
                    "(np. przez BT Pilot) – usuń to parowanie na obu telefonach.\n\n" +
                    "Ważne: zamknij aplikację BT Pilot, jeśli jest uruchomiona.",
            )
            .setPositiveButton("Sparuj teraz") { _, _ -> Pairing.showNewDevice(this) }
            .setNeutralButton("Mam już sparowane") { _, _ -> Pairing.showConnectDialog(this) }
            .show()
    }

    // ------------------------------------------------------------------ ustawienia

    private fun showSettings() {
        val p = dp(20)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p / 2, p, 0)
        }
        fun check(label: String, on: Boolean) = CheckBox(this).apply {
            text = label
            isChecked = on
            box.addView(this)
        }
        fun header(label: String) = box.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(0xFF3FB950.toInt())
            setPadding(0, p / 2, 0, dp(4))
        })
        /** Suwak min..max; zwraca funkcję odczytu wartości. */
        fun slider(title: String, value: Float, min: Float, max: Float): () -> Float {
            val label = TextView(this)
            val bar = SeekBar(this).apply {
                this.max = 100
                progress = (((value - min) / (max - min)) * 100).toInt().coerceIn(0, 100)
            }
            fun current() = min + (max - min) * bar.progress / 100f
            fun show() {
                label.text = "%s: %.1f".format(title, current())
            }
            show()
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) = show()
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
            box.addView(label)
            box.addView(bar)
            return ::current
        }

        header("Sterowanie")
        val haptics = check("Wibracja przy naciśnięciu", pad.haptics)
        val floating = check("Pływająca lewa gałka (środek tam, gdzie kciuk)", pad.floatingStick)
        val fullTriggers = check("LT / RT od razu z pełną siłą", pad.fullTriggers)
        val stickSens = slider("Czułość gałek", pad.stickSensitivity, 0.5f, 2f)

        header("Celowanie ruchem telefonu (żyroskop → prawa gałka)")
        val gyroGroup = RadioGroup(this)
        val gyroLabels = listOf("Wyłączone", "Zawsze", "Tylko gdy trzymasz LT (celowanie)")
        val gyroButtons = gyroLabels.mapIndexed { i, label ->
            RadioButton(this).apply {
                text = label
                id = View.generateViewId()
                isEnabled = gyro.available || i == 0
                gyroGroup.addView(this)
            }
        }
        gyroGroup.check(gyroButtons[pad.gyroMode.coerceIn(0, 2)].id)
        box.addView(gyroGroup)
        if (!gyro.available) {
            box.addView(TextView(this).apply { text = "Ten telefon nie ma żyroskopu." })
        }
        val gyroSens = slider("Czułość żyroskopu", gyro.sensitivity, 0.3f, 3f)
        val gyroInvX = check("Żyroskop: odwróć lewo/prawo", gyro.invertX)
        val gyroInvY = check("Żyroskop: odwróć góra/dół", gyro.invertY)

        header("Układ przycisków")
        var dialog: AlertDialog? = null
        box.addView(Button(this).apply {
            text = "✎ Przesuń / zmień rozmiar przycisków"
            setOnClickListener {
                dialog?.dismiss()
                setEditing(true)
            }
        })

        dialog = AlertDialog.Builder(this)
            .setTitle("Ustawienia")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Zapisz") { _, _ ->
                pad.haptics = haptics.isChecked
                pad.floatingStick = floating.isChecked
                pad.fullTriggers = fullTriggers.isChecked
                pad.stickSensitivity = stickSens()
                pad.gyroMode = gyroButtons.indexOfFirst { it.isChecked }.coerceAtLeast(0)
                gyro.sensitivity = gyroSens()
                gyro.invertX = gyroInvX.isChecked
                gyro.invertY = gyroInvY.isChecked
                prefs.edit()
                    .putBoolean(PREF_HAPTICS, pad.haptics)
                    .putBoolean(PREF_FLOATING, pad.floatingStick)
                    .putBoolean(PREF_FULL_TRIGGERS, pad.fullTriggers)
                    .putFloat(PREF_STICK_SENS, pad.stickSensitivity)
                    .putInt(PREF_GYRO_MODE, pad.gyroMode)
                    .putFloat(PREF_GYRO_SENS, gyro.sensitivity)
                    .putBoolean(PREF_GYRO_INV_X, gyro.invertX)
                    .putBoolean(PREF_GYRO_INV_Y, gyro.invertY)
                    .apply()
                if (pad.gyroMode != PadView.GYRO_OFF) gyro.start() else gyro.stop()
            }
            .setNeutralButton("Pomoc") { _, _ -> showHelp() }
            .setNegativeButton("Diagnostyka") { _, _ -> Pairing.showDiagnostics(this) }
            .show()
    }

    // ------------------------------------------------------------------ edycja układu

    private fun setEditing(on: Boolean) {
        pad.editMode = on
        bar.visibility = if (on) View.GONE else View.VISIBLE
        editBar.visibility = if (on) View.VISIBLE else View.GONE
        if (on) gyro.stop() else if (pad.gyroMode != PadView.GYRO_OFF) gyro.start()
    }

    private fun confirmResetLayout() {
        AlertDialog.Builder(this)
            .setMessage("Przywrócić domyślne położenie i rozmiar wszystkich przycisków?")
            .setPositiveButton("Przywróć") { _, _ ->
                pad.resetLayout()
                prefs.edit().remove(PREF_LAYOUT).apply()
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun barButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(0xFFE6EDF3.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        layoutParams = LinearLayout.LayoutParams(-2, dp(40))
        setOnClickListener { onClick() }
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Jak grać w GeForce NOW")
            .setMessage(
                """
                1. Ten telefon (pad) i telefon z grą muszą być sparowane przez BT Pad – na górze ma być „● Pad połączony”.
                2. Na telefonie z grą uruchom GeForce NOW i grę.
                3. Naciśnij dowolny przycisk na padzie – GeForce NOW przełączy się na sterowanie padem.

                Sprawdzenie, czy telefon z grą widzi pad: otwórz na nim w Chrome stronę hardwaretester.com/gamepad i poruszaj gałką.

                Sterowanie:
                • Lewa gałka – połóż kciuk gdziekolwiek w lewym dolnym rogu.
                • Stuknięcie gałki bez ruchu = wciśnięcie gałki (L3 / R3).
                • LT / RT – im niżej palec, tym mocniej (w ⚙ można ustawić pełną siłę od razu).
                • ⚙ → żyroskop: celowanie ruchem telefonu (np. tylko gdy trzymasz LT).
                • ⚙ → „Przesuń / zmień rozmiar przycisków”: własny układ pada.
                • Xbox, View (Widok) i Menu – na środku ekranu.

                Nie uruchamiaj jednocześnie BT Pilot – tylko jedna aplikacja może udawać urządzenie Bluetooth.
                """.trimIndent(),
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ------------------------------------------------------------------ pomocnicze

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQ_PERMISSIONS = 1
        const val REQ_ENABLE_BT = 2
        const val PREF_HAPTICS = "haptics"
        const val PREF_FLOATING = "floating_stick"
        const val PREF_FULL_TRIGGERS = "full_triggers"
        const val PREF_STICK_SENS = "stick_sensitivity"
        const val PREF_GYRO_MODE = "gyro_mode"
        const val PREF_GYRO_SENS = "gyro_sensitivity"
        const val PREF_GYRO_INV_X = "gyro_invert_x"
        const val PREF_GYRO_INV_Y = "gyro_invert_y"
        const val PREF_LAYOUT = "layout"
        const val COLOR_OK = 0xFF3FB950.toInt()
        const val COLOR_WARN = 0xFFE3B341.toInt()
        const val COLOR_ERR = 0xFFF85149.toInt()
    }
}
