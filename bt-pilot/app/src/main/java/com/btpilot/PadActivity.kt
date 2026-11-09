package com.btpilot

import android.app.Activity
import android.bluetooth.BluetoothDevice
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

/** Uniwersalny pad w układzie Xbox. */
class PadActivity : Activity(), HidController.Listener {

    private lateinit var pad: XboxPadView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        pad = XboxPadView(this)
        status = TextView(this).apply {
            textSize = 12f
            setPadding(dp(10), 0, dp(10), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.game_bar_bg)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(status)
            addView(Button(this@PadActivity, null, 0, R.style.KeyButton).apply {
                text = "✕"
                textSize = 13f
                minWidth = dp(40)
                minimumWidth = dp(40)
                minHeight = dp(30)
                minimumHeight = dp(30)
                layoutParams = LinearLayout.LayoutParams(-2, dp(30))
                setOnClickListener { finish() }
            })
        }

        val root = FrameLayout(this)
        root.addView(pad, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            bar,
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(4)
            },
        )
        setContentView(root)

        if (!HidController.gamepadEnabled) {
            Toast.makeText(
                this,
                "Pad jest wyłączony w ustawieniach (⚙) – przyciski nie będą wysyłane",
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
        if (!hasFocus) return
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

    override fun onHidStateChanged(state: HidController.State, device: BluetoothDevice?) {
        val connected = state == HidController.State.CONNECTED
        status.text = if (connected) "● Pad połączony" else "● Brak połączenia"
        status.setTextColor(getColor(if (connected) R.color.ok else R.color.err))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
