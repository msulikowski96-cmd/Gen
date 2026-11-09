package com.btpilot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Ekranowy pad w układzie Xbox. Obsługuje wiele palców naraz i wysyła stan jako pad HID:
 * lewa/prawa gałka, D-pad, A B X Y, LB RB, LT RT (analogowo – im niżej palec, tym mocniej),
 * Widok, Menu, Xbox. Szybkie stuknięcie gałki bez ruchu = wciśnięcie gałki (L3/R3).
 */
class XboxPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private enum class Type { STICK_L, STICK_R, DPAD, BUTTON, TRIGGER_L, TRIGGER_R }

    /** Element pada. Pozycje i rozmiary w ułamkach wysokości ekranu (x także względem wysokości po przeliczeniu). */
    private inner class Control(
        val type: Type,
        val label: String,
        val fx: Float,   // środek X jako ułamek szerokości
        val fy: Float,   // środek Y jako ułamek wysokości
        val size: Float, // promień (lub połowa wysokości prostokąta) jako ułamek wysokości
        val bit: Int = 0,
        val color: Int = 0xFF3A3F4B.toInt(),
        val wide: Float = 1f, // dla prostokątów: szerokość / wysokość
    ) {
        val cx get() = fx * width
        val cy get() = fy * height
        val r get() = size * height
        val isRect get() = wide > 1f

        fun contains(x: Float, y: Float, slack: Float = 1.15f): Boolean =
            if (isRect) {
                kotlin.math.abs(x - cx) <= r * wide * slack && kotlin.math.abs(y - cy) <= r * slack
            } else {
                hypot(x - cx, y - cy) <= r * slack
            }
    }

    private val controls = listOf(
        // Spusty i bumpery (góra ekranu)
        Control(Type.TRIGGER_L, "LT", 0.12f, 0.09f, 0.075f, Hid.PAD_LT, wide = 2.2f),
        Control(Type.BUTTON, "LB", 0.12f, 0.25f, 0.065f, Hid.PAD_LB, wide = 2.2f),
        Control(Type.TRIGGER_R, "RT", 0.88f, 0.09f, 0.075f, Hid.PAD_RT, wide = 2.2f),
        Control(Type.BUTTON, "RB", 0.88f, 0.25f, 0.065f, Hid.PAD_RB, wide = 2.2f),
        // Gałki i D-pad
        Control(Type.STICK_L, "L", 0.15f, 0.62f, 0.20f, Hid.PAD_L3),
        Control(Type.DPAD, "", 0.36f, 0.78f, 0.17f),
        Control(Type.STICK_R, "R", 0.64f, 0.78f, 0.17f, Hid.PAD_R3),
        // A B X Y
        Control(Type.BUTTON, "A", 0.86f, 0.75f, 0.075f, Hid.PAD_A, 0xFF2F9E44.toInt()),
        Control(Type.BUTTON, "B", 0.95f, 0.56f, 0.075f, Hid.PAD_B, 0xFFE03131.toInt()),
        Control(Type.BUTTON, "X", 0.77f, 0.56f, 0.075f, Hid.PAD_X, 0xFF1C7ED6.toInt()),
        Control(Type.BUTTON, "Y", 0.86f, 0.40f, 0.075f, Hid.PAD_Y, 0xFFF59F00.toInt()),
        // Środek
        Control(Type.BUTTON, "⧉", 0.40f, 0.40f, 0.055f, Hid.PAD_VIEW),
        Control(Type.BUTTON, "≡", 0.60f, 0.40f, 0.055f, Hid.PAD_MENU),
        Control(Type.BUTTON, "Ⓧ", 0.50f, 0.22f, 0.07f, Hid.PAD_HOME, 0xFF2B8A3E.toInt()),
    )

    /** Co trzyma dany palec. */
    private inner class Hold(val control: Control, val downTime: Long) {
        var x = 0f
        var y = 0f
        var moved = false
    }

    private val holds = HashMap<Int, Hold>()

    // Aktualny stan pada
    private var buttons = 0
    private var hat = Hid.HAT_NONE
    private var lx = 0
    private var ly = 0
    private var rx = 0
    private var ry = 0
    private var lt = 0
    private var rt = 0
    private var lastSent: List<Int> = emptyList()

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFF4A5060.toInt()
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()

    // ------------------------------------------------------------------ rysowanie

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFF0D0F13.toInt())
        for (c in controls) {
            when (c.type) {
                Type.STICK_L -> drawStick(canvas, c, lx, ly)
                Type.STICK_R -> drawStick(canvas, c, rx, ry)
                Type.DPAD -> drawDpad(canvas, c)
                Type.TRIGGER_L -> drawRect(canvas, c, lt / 255f)
                Type.TRIGGER_R -> drawRect(canvas, c, rt / 255f)
                Type.BUTTON -> if (c.isRect) {
                    drawRect(canvas, c, if (buttons and c.bit != 0) 1f else 0f)
                } else {
                    drawRound(canvas, c)
                }
            }
        }
    }

    private fun drawRound(canvas: Canvas, c: Control) {
        val pressed = buttons and c.bit != 0
        fill.color = c.color
        fill.alpha = if (pressed) 255 else 120
        canvas.drawCircle(c.cx, c.cy, c.r, fill)
        canvas.drawCircle(c.cx, c.cy, c.r, stroke)
        text.textSize = c.r * 0.9f
        canvas.drawText(c.label, c.cx, c.cy - (text.descent() + text.ascent()) / 2, text)
    }

    private fun drawRect(canvas: Canvas, c: Control, level: Float) {
        val hw = c.r * c.wide
        rect.set(c.cx - hw, c.cy - c.r, c.cx + hw, c.cy + c.r)
        val corner = c.r * 0.5f
        fill.color = 0xFF2A2E37.toInt()
        canvas.drawRoundRect(rect, corner, corner, fill)
        if (level > 0f) {
            fill.color = 0xFF5C7CFA.toInt()
            val top = rect.bottom - rect.height() * level
            canvas.save()
            canvas.clipRect(rect.left, top, rect.right, rect.bottom)
            canvas.drawRoundRect(rect, corner, corner, fill)
            canvas.restore()
        }
        canvas.drawRoundRect(rect, corner, corner, stroke)
        text.textSize = c.r * 0.8f
        canvas.drawText(c.label, c.cx, c.cy - (text.descent() + text.ascent()) / 2, text)
    }

    private fun drawStick(canvas: Canvas, c: Control, vx: Int, vy: Int) {
        fill.color = 0xFF1E222A.toInt()
        canvas.drawCircle(c.cx, c.cy, c.r, fill)
        canvas.drawCircle(c.cx, c.cy, c.r, stroke)
        val kx = c.cx + vx / 127f * c.r * STICK_TRAVEL
        val ky = c.cy + vy / 127f * c.r * STICK_TRAVEL
        fill.color = if (buttons and c.bit != 0) 0xFF5C7CFA.toInt() else 0xFF495057.toInt()
        canvas.drawCircle(kx, ky, c.r * 0.45f, fill)
        text.textSize = c.r * 0.3f
        canvas.drawText(c.label, kx, ky - (text.descent() + text.ascent()) / 2, text)
    }

    private fun drawDpad(canvas: Canvas, c: Control) {
        val arm = c.r * 0.36f
        val dirs = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0) // góra, prawo, dół, lewo
        val active = hatDirections(hat)
        for ((i, d) in dirs.withIndex()) {
            val (dx, dy) = d
            val cx = c.cx + dx * c.r * 0.62f
            val cy = c.cy + dy * c.r * 0.62f
            rect.set(cx - arm, cy - arm, cx + arm, cy + arm)
            fill.color = if (active[i]) 0xFF5C7CFA.toInt() else 0xFF3A3F4B.toInt()
            canvas.drawRoundRect(rect, arm * 0.4f, arm * 0.4f, fill)
        }
        fill.color = 0xFF2A2E37.toInt()
        rect.set(c.cx - arm, c.cy - arm, c.cx + arm, c.cy + arm)
        canvas.drawRect(rect, fill)
    }

    /** Które z 4 kierunków (góra, prawo, dół, lewo) są aktywne dla danej wartości D-pada. */
    private fun hatDirections(h: Int) = booleanArrayOf(
        h == 7 || h == 0 || h == 1,
        h in 1..3,
        h in 3..5,
        h in 5..7,
    )

    // ------------------------------------------------------------------ dotyk

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                val c = hit(x, y)
                if (c != null) {
                    holds[e.getPointerId(i)] = Hold(c, e.eventTime).also { it.x = x; it.y = y }
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }

            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val h = holds[e.getPointerId(i)] ?: continue
                if (hypot(e.getX(i) - h.x, e.getY(i) - h.y) > 6f * density) h.moved = true
                h.x = e.getX(i)
                h.y = e.getY(i)
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val h = holds.remove(e.getPointerId(e.actionIndex))
                val c = h?.control
                // Stuknięcie gałki bez ruchu = L3 / R3.
                if (c != null && (c.type == Type.STICK_L || c.type == Type.STICK_R) &&
                    !h.moved && e.eventTime - h.downTime < TAP_MS
                ) {
                    clickBit(c.bit)
                }
            }

            MotionEvent.ACTION_CANCEL -> holds.clear()
        }
        recompute()
        return true
    }

    private fun hit(x: Float, y: Float): Control? {
        // Gałki i D-pad mają większy obszar chwytu.
        return controls.firstOrNull { it.type == Type.BUTTON && it.contains(x, y) }
            ?: controls.firstOrNull { it.type != Type.BUTTON && it.contains(x, y, 1.35f) }
    }

    private fun clickBit(bit: Int) {
        buttons = buttons or bit
        send(force = true)
        postDelayed({
            buttons = buttons and bit.inv()
            send(force = true)
            invalidate()
        }, 60)
    }

    /** Liczy stan pada z palców na ekranie i wysyła go, jeśli się zmienił. */
    private fun recompute() {
        var b = buttons and (Hid.PAD_L3 or Hid.PAD_R3) // kliknięcia gałek obsługuje clickBit
        var h = Hid.HAT_NONE
        var nlx = 0
        var nly = 0
        var nrx = 0
        var nry = 0
        var nlt = 0
        var nrt = 0
        for (hold in holds.values) {
            val c = hold.control
            when (c.type) {
                Type.BUTTON -> b = b or c.bit
                Type.STICK_L, Type.STICK_R -> {
                    val (vx, vy) = stickValue(c, hold.x, hold.y)
                    if (c.type == Type.STICK_L) { nlx = vx; nly = vy } else { nrx = vx; nry = vy }
                }
                Type.DPAD -> h = dpadValue(c, hold.x, hold.y)
                Type.TRIGGER_L, Type.TRIGGER_R -> {
                    // Dotknięcie = co najmniej połowa; im niżej palec, tym mocniej (do 100%).
                    val rel = ((hold.y - (c.cy - c.r)) / (2 * c.r)).coerceIn(0f, 1f)
                    val v = (128 + rel * 127).roundToInt().coerceIn(0, 255)
                    if (c.type == Type.TRIGGER_L) nlt = v else nrt = v
                    if (v > 200) b = b or c.bit
                }
            }
        }
        buttons = b; hat = h; lx = nlx; ly = nly; rx = nrx; ry = nry; lt = nlt; rt = nrt
        send(force = false)
        invalidate()
    }

    private fun stickValue(c: Control, x: Float, y: Float): Pair<Int, Int> {
        val travel = c.r * STICK_TRAVEL
        var dx = (x - c.cx) / travel
        var dy = (y - c.cy) / travel
        val len = hypot(dx, dy)
        if (len > 1f) { dx /= len; dy /= len }
        return (dx * 127).roundToInt() to (dy * 127).roundToInt()
    }

    private fun dpadValue(c: Control, x: Float, y: Float): Int {
        val dx = x - c.cx
        val dy = y - c.cy
        if (hypot(dx, dy) < c.r * 0.2f) return Hid.HAT_NONE
        // 0 = góra, dalej zgodnie z ruchem wskazówek zegara, co 45°.
        val deg = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360.0) % 360.0
        return (((deg + 22.5) / 45.0).toInt()) % 8
    }

    private fun send(force: Boolean) {
        val state = listOf(buttons, hat, lx, ly, rx, ry, lt, rt)
        if (!force && state == lastSent) return
        lastSent = state
        HidController.gamepad(buttons, hat, lx, ly, rx, ry, lt, rt)
    }

    fun releaseAll() {
        holds.clear()
        buttons = 0
        recompute()
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val STICK_TRAVEL = 0.75f
        const val TAP_MS = 200L
    }
}
