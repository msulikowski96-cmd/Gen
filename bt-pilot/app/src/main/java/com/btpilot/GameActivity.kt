package com.btpilot

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Tryb gry: kontroler dotykowy z konfiguracją pod League of Legends: Wild Rift. */
class GameActivity : Activity(), HidController.Listener {

    private lateinit var pad: GamepadView
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var mainBar: LinearLayout
    private lateinit var editBar: LinearLayout
    private lateinit var rotationButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        prefs = getSharedPreferences("settings", MODE_PRIVATE)

        pad = GamepadView(this).apply {
            controls = GameLayouts.load(prefs)
            rotation = runCatching {
                TargetRotation.valueOf(prefs.getString(PREF_ROTATION, null) ?: "")
            }.getOrDefault(TargetRotation.ROT_90)
            onLayoutEdited = { GameLayouts.save(prefs, controls) }
        }

        status = TextView(this).apply {
            setOnClickListener {
                val d = HidController.device
                if (d != null) Pairing.chooseDevice(this@GameActivity, d) else Pairing.showConnectDialog(this@GameActivity)
            }
            textSize = 12f
            setTextColor(getColor(R.color.text))
            setPadding(dp(10), 0, dp(10), 0)
            gravity = Gravity.CENTER_VERTICAL
        }

        mainBar = bar().apply {
            addView(status)
            addView(smallButton("✎ Edytuj") { setEditing(true) })
            addView(smallButton("🖼 Tło") { pickBackground() })
            rotationButton = smallButton("") { cycleRotation() }
            addView(rotationButton)
            addView(smallButton("?") { showHelp() })
            addView(smallButton("✕") { finish() })
        }

        editBar = bar().apply {
            visibility = View.GONE
            addView(smallButton("−") { pad.resizeSelected(0.9f) })
            addView(smallButton("+") { pad.resizeSelected(1.1f) })
            addView(smallButton("Domyślne") { resetLayout() })
            addView(smallButton("Usuń tło") { clearBackground() })
            addView(smallButton("✓ Gotowe") { setEditing(false) })
        }

        val root = FrameLayout(this)
        root.addView(pad, FrameLayout.LayoutParams(-1, -1))
        val top = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(4)
        }
        root.addView(mainBar, top)
        root.addView(editBar, FrameLayout.LayoutParams(top))
        setContentView(root)

        updateRotationLabel()
        loadBackground()
        if (!HidController.touchEnabled) {
            Toast.makeText(
                this,
                "Włączony jest tryb zgodności (⚙) – dotyk nie będzie wysyłany",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onStart() {
        super.onStart()
        HidController.addListener(this)
    }

    override fun onStop() {
        pad.releaseAll()
        HidController.removeListener(this)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onHidStateChanged(state: HidController.State, device: BluetoothDevice?) {
        val connected = state == HidController.State.CONNECTED
        val current = device != null && HidController.hostStatus(device) == HidController.HostStatus.CURRENT
        status.text = when {
            !connected -> "● Brak połączenia"
            current -> "● Połączono"
            else -> "⚠ Napraw parowanie"
        }
        status.setTextColor(getColor(if (!connected) R.color.err else if (current) R.color.ok else R.color.warn))
    }

    // ------------------------------------------------------------------ edycja i ustawienia

    private fun setEditing(on: Boolean) {
        pad.editMode = on
        mainBar.visibility = if (on) View.GONE else View.VISIBLE
        editBar.visibility = if (on) View.VISIBLE else View.GONE
        if (!on) GameLayouts.save(prefs, pad.controls)
    }

    private fun resetLayout() {
        GameLayouts.reset(prefs)
        pad.controls = GameLayouts.wildRiftDefault()
        Toast.makeText(this, "Przywrócono domyślny układ Wild Rift", Toast.LENGTH_SHORT).show()
    }

    private fun cycleRotation() {
        pad.rotation = pad.rotation.next()
        prefs.edit().putString(PREF_ROTATION, pad.rotation.name).apply()
        updateRotationLabel()
        Toast.makeText(this, "Ekran gry: ${pad.rotation.hint}", Toast.LENGTH_LONG).show()
    }

    private fun updateRotationLabel() {
        rotationButton.text = "↻ ${pad.rotation.label}"
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Kontroler Wild Rift")
            .setMessage(
                """
                Ten ekran działa jak ekran dotykowy drugiego telefonu – do 3 palców naraz (ruch + umiejętność + atak).

                • Dotknij przycisku (Ruch, Q, W, E, R, Atak…) – na drugim telefonie palec trafi dokładnie w ten przycisk, a dalej podąża za Twoim ruchem (joystick, celowanie).
                • Dotyk poza przyciskami trafia w to samo miejsce ekranu gry (sklep, ulepszanie umiejętności, minimapa).

                Pierwsze ustawienie:
                1. Na telefonie z grą zrób zrzut ekranu w czasie meczu (np. w trybie treningu).
                2. Przenieś go na ten telefon i wczytaj przyciskiem 🖼 Tło.
                3. ✎ Edytuj – przeciągnij kółka na przyciski ze zrzutu, − / + zmienia rozmiar.
                4. Jeśli dotyk trafia w złe miejsce, zmień obrót przyciskiem ↻ (zależy, w którą stronę obrócony jest telefon z grą).

                Wskazówka: na telefonie z grą włącz Opcje programisty → „Pokaż dotknięcia”, żeby widzieć, gdzie trafiają palce.

                Jeśli na górze widzisz „⚠ Napraw parowanie”, stuknij ten napis – kreator przeprowadzi Cię przez ponowne sparowanie.
                """.trimIndent(),
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ------------------------------------------------------------------ tło (zrzut ekranu gry)

    private fun pickBackground() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_IMAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        prefs.edit().putString(PREF_BACKGROUND, uri.toString()).apply()
        loadBackground()
        if (pad.backgroundImage != null) setEditing(true)
    }

    private fun loadBackground() {
        val s = prefs.getString(PREF_BACKGROUND, null) ?: return
        pad.backgroundImage = decode(Uri.parse(s))
        if (pad.backgroundImage == null) {
            Toast.makeText(this, "Nie udało się wczytać zrzutu ekranu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decode(uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            if (w > MAX_BG_WIDTH) {
                decoder.setTargetSize(MAX_BG_WIDTH, info.size.height * MAX_BG_WIDTH / w)
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun clearBackground() {
        prefs.edit().remove(PREF_BACKGROUND).apply()
        pad.backgroundImage = null
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

    private fun bar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.game_bar_bg)
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    private fun smallButton(label: String, onClick: () -> Unit) =
        Button(this, null, 0, R.style.KeyButton).apply {
            text = label
            textSize = 13f
            minWidth = dp(40)
            minimumWidth = dp(40)
            minHeight = dp(34)
            minimumHeight = dp(34)
            layoutParams = LinearLayout.LayoutParams(-2, dp(34)).apply { marginStart = dp(4) }
            setOnClickListener { onClick() }
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQ_IMAGE = 10
        const val PREF_ROTATION = "game_rotation"
        const val PREF_BACKGROUND = "game_background"
        const val MAX_BG_WIDTH = 1600
    }
}
