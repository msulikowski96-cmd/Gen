package com.btpad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Ekranowy pad w układzie Xbox, obsługuje wiele palców naraz.
 *
 *  - Lewa gałka "pływa": kładziesz kciuk gdziekolwiek w lewej dolnej części ekranu
 *    i tam pojawia się środek gałki (można to wyłączyć w ustawieniach).
 *  - Szybkie stuknięcie gałki bez ruchu = wciśnięcie gałki (L3 / R3).
 *  - LT / RT są analogowe: dotknięcie = połowa siły, im niżej palec, tym mocniej.
 *  - D-pad i A/B/X/Y pozwalają przesuwać palec między przyciskami.
 */
class PadView(context: Context) : View(context) {

    var onChange: ((Hid.PadState) -> Unit)? = null
    var haptics = true
    var floatingStick = true

    private enum class Kind { STICK_L, STICK_R, DPAD, FACE, SHOULDER, TRIGGER_L, TRIGGER_R, SMALL }

    /** Element pada. Środek (fx, fy) jako ułamek szerokości/wysokości, rozmiar jako ułamek wysokości. */
    private inner class Control(
        val kind: Kind,
        val label: String,
        val fx: Float,
        val fy: Float,
        val size: Float,
        val bit: Int = 0,
        val color: Int = COLOR_KEY,
        val wide: Float = 1f,
    ) {
        val cx get() = fx * width
        val cy get() = fy * height
        val r get() = size * height
        val isRect get() = wide > 1f

        fun contains(x: Float, y: Float, slack: Float): Boolean =
            if (isRect) abs(x - cx) <= r * wide * slack && abs(y - cy) <= r * slack
            else hypot(x - cx, y - cy) <= r * slack
    }

    private val controls = listOf(
        Control(Kind.TRIGGER_L, "LT", 0.13f, 0.09f, 0.075f, Hid.LT, wide = 2.3f),
        Control(Kind.SHOULDER, "LB", 0.13f, 0.25f, 0.06f, Hid.LB, wide = 2.3f),
        Control(Kind.TRIGGER_R, "RT", 0.87f, 0.09f, 0.075f, Hid.RT, wide = 2.3f),
        Control(Kind.SHOULDER, "RB", 0.87f, 0.25f, 0.06f, Hid.RB, wide = 2.3f),
        Control(Kind.STICK_L, "", 0.16f, 0.62f, 0.19f, Hid.L3),
        Control(Kind.DPAD, "", 0.37f, 0.78f, 0.16f),
        Control(Kind.STICK_R, "", 0.63f, 0.78f, 0.16f, Hid.R3),
        Control(Kind.FACE, "A", 0.86f, 0.76f, 0.075f, Hid.A, 0xFF3FB950.toInt()),
        Control(Kind.FACE, "B", 0.95f, 0.575f, 0.075f, Hid.B, 0xFFF85149.toInt()),
        Control(Kind.FACE, "X", 0.77f, 0.575f, 0.075f, Hid.X, 0xFF58A6FF.toInt()),
        Control(Kind.FACE, "Y", 0.86f, 0.40f, 0.075f, Hid.Y, 0xFFE3B341.toInt()),
        Control(Kind.SMALL, "View", 0.40f, 0.42f, 0.05f, Hid.VIEW),
        Control(Kind.SMALL, "Menu", 0.60f, 0.42f, 0.05f, Hid.MENU),
        Control(Kind.SMALL, "Xbox", 0.50f, 0.28f, 0.065f, Hid.HOME, 0xFF107C10.toInt()),
    )
    private val leftStick = controls.first { it.kind == Kind.STICK_L }
    private val rightStick = controls.first { it.kind == Kind.STICK_R }
    private val dpad = controls.first { it.kind == Kind.DPAD }

    /** Co trzyma dany palec. */
    private inner class Hold(var control: Control, val downTime: Long, val ox: Float, val oy: Float) {
        var x = ox
        var y = oy
        var moved = false
    }

    private val holds = HashMap<Int, Hold>()
    private var clicks = 0 // L3/R3 wciśnięte stuknięciem
    private var state = Hid.PadState()

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()

    // ------------------------------------------------------------------ rysowanie

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(COLOR_BG)
        val pressed = state.buttons
        for (c in controls) {
            when (c.kind) {
                Kind.STICK_L -> drawStick(canvas, c, state.lx, state.ly, leftOrigin())
                Kind.STICK_R -> drawStick(canvas, c, state.rx, state.ry, null)
                Kind.DPAD -> drawDpad(canvas, c)
                Kind.TRIGGER_L -> drawBar(canvas, c, state.lt / 255f)
                Kind.TRIGGER_R -> drawBar(canvas, c, state.rt / 255f)
                Kind.SHOULDER -> drawBar(canvas, c, if (pressed and c.bit != 0) 1f else 0f)
                Kind.FACE, Kind.SMALL -> drawRound(canvas, c, pressed and c.bit != 0)
            }
        }
    }

    private fun drawRound(canvas: Canvas, c: Control, down: Boolean) {
        fill.color = if (down) c.color else COLOR_KEY
        canvas.drawCircle(c.cx, c.cy, c.r * (if (down) 0.94f else 1f), fill)
        stroke.color = if (c.kind == Kind.FACE) c.color else COLOR_EDGE
        canvas.drawCircle(c.cx, c.cy, c.r, stroke)
        text.color = if (down) 0xFF0B0D11.toInt() else if (c.kind == Kind.FACE) c.color else COLOR_TEXT
        text.textSize = c.r * (if (c.label.length <= 2) 0.95f else 0.5f)
        canvas.drawText(c.label, c.cx, c.cy - (text.descent() + text.ascent()) / 2, text)
    }

    private fun drawBar(canvas: Canvas, c: Control, level: Float) {
        val hw = c.r * c.wide
        rect.set(c.cx - hw, c.cy - c.r, c.cx + hw, c.cy + c.r)
        val corner = c.r * 0.6f
        fill.color = COLOR_KEY
        canvas.drawRoundRect(rect, corner, corner, fill)
        if (level > 0f) {
            fill.color = COLOR_ACCENT
            canvas.save()
            canvas.clipRect(rect.left, rect.bottom - rect.height() * level, rect.right, rect.bottom)
            canvas.drawRoundRect(rect, corner, corner, fill)
            canvas.restore()
        }
        stroke.color = COLOR_EDGE
        canvas.drawRoundRect(rect, corner, corner, stroke)
        text.color = COLOR_TEXT
        text.textSize = c.r * 0.75f
        canvas.drawText(c.label, c.cx, c.cy - (text.descent() + text.ascent()) / 2, text)
    }

    private fun drawStick(canvas: Canvas, c: Control, vx: Int, vy: Int, origin: Pair<Float, Float>?) {
        val (ox, oy) = origin ?: (c.cx to c.cy)
        fill.color = COLOR_WELL
        canvas.drawCircle(ox, oy, c.r, fill)
        stroke.color = COLOR_EDGE
        canvas.drawCircle(ox, oy, c.r, stroke)
        val travel = c.r * TRAVEL
        val kx = ox + vx / 127f * travel
        val ky = oy + vy / 127f * travel
        fill.color = if (state.buttons and c.bit != 0) COLOR_ACCENT else COLOR_KNOB
        canvas.drawCircle(kx, ky, c.r * 0.46f, fill)
        stroke.color = 0x33FFFFFF
        canvas.drawCircle(kx, ky, c.r * 0.46f, stroke)
    }

    private fun drawDpad(canvas: Canvas, c: Control) {
        val arm = c.r * 0.34f
        val active = hatDirections(state.hat)
        val dirs = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
        for ((i, d) in dirs.withIndex()) {
            val cx = c.cx + d.first * c.r * 0.6f
            val cy = c.cy + d.second * c.r * 0.6f
            rect.set(cx - arm, cy - arm, cx + arm, cy + arm)
            fill.color = if (active[i]) COLOR_ACCENT else COLOR_KEY
            canvas.drawRoundRect(rect, arm * 0.35f, arm * 0.35f, fill)
            stroke.color = COLOR_EDGE
            canvas.drawRoundRect(rect, arm * 0.35f, arm * 0.35f, stroke)
        }
        rect.set(c.cx - arm, c.cy - arm, c.cx + arm, c.cy + arm)
        fill.color = COLOR_KEY
        canvas.drawRect(rect, fill)
    }

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
                    // Pływająca lewa gałka: środek tam, gdzie położono kciuk.
                    val floating = c == leftStick && floatingStick
                    holds[e.getPointerId(i)] = Hold(c, e.eventTime, if (floating) x else c.cx, if (floating) y else c.cy)
                        .also { it.x = x; it.y = y }
                    buzz()
                }
            }

            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val h = holds[e.getPointerId(i)] ?: continue
                val x = e.getX(i)
                val y = e.getY(i)
                if (hypot(x - h.x, y - h.y) > 0.5f) {
                    if (hypot(x - h.ox, y - h.oy) > 8f * density) h.moved = true
                    h.x = x
                    h.y = y
                    // Przesuwanie palca między przyciskami A/B/X/Y.
                    if (h.control.kind == Kind.FACE) {
                        val other = controls.firstOrNull { it.kind == Kind.FACE && it.contains(x, y, 1.0f) }
                        if (other != null && other != h.control) {
                            h.control = other
                            buzz()
                        }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val h = holds.remove(e.getPointerId(e.actionIndex))
                val c = h?.control
                if (c != null && (c.kind == Kind.STICK_L || c.kind == Kind.STICK_R) &&
                    !h.moved && e.eventTime - h.downTime < TAP_MS
                ) {
                    clickStick(c.bit)
                }
            }

            MotionEvent.ACTION_CANCEL -> holds.clear()
        }
        update()
        return true
    }

    private fun hit(x: Float, y: Float): Control? {
        controls.firstOrNull { (it.kind == Kind.FACE || it.kind == Kind.SMALL ||
            it.kind == Kind.SHOULDER || it.kind == Kind.TRIGGER_L || it.kind == Kind.TRIGGER_R) &&
            it.contains(x, y, 1.15f) }?.let { return it }
        if (dpad.contains(x, y, 1.3f)) return dpad
        if (rightStick.contains(x, y, 1.35f)) return rightStick
        // Lewa gałka: cała lewa dolna część ekranu.
        if (floatingStick) {
            if (x < width * 0.30f && y > height * 0.34f) return leftStick
        } else if (leftStick.contains(x, y, 1.35f)) {
            return leftStick
        }
        return null
    }

    private fun leftOrigin(): Pair<Float, Float>? =
        holds.values.firstOrNull { it.control == leftStick }?.let { it.ox to it.oy }

    private fun clickStick(bit: Int) {
        clicks = clicks or bit
        update()
        postDelayed({
            clicks = clicks and bit.inv()
            update()
        }, 70)
    }

    private fun buzz() {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Liczy stan pada z palców na ekranie i wysyła go, jeśli się zmienił. */
    private fun update() {
        var b = clicks
        var hat = Hid.HAT_NONE
        var lx = 0
        var ly = 0
        var rx = 0
        var ry = 0
        var lt = 0
        var rt = 0
        for (h in holds.values) {
            val c = h.control
            when (c.kind) {
                Kind.FACE, Kind.SMALL, Kind.SHOULDER -> b = b or c.bit
                Kind.STICK_L -> stick(c, h).let { lx = it.first; ly = it.second }
                Kind.STICK_R -> stick(c, h).let { rx = it.first; ry = it.second }
                Kind.DPAD -> hat = dpadValue(c, h.x, h.y)
                Kind.TRIGGER_L, Kind.TRIGGER_R -> {
                    val rel = ((h.y - (c.cy - c.r)) / (2 * c.r)).coerceIn(0f, 1f)
                    val v = (128 + rel * 127).roundToInt()
                    if (c.kind == Kind.TRIGGER_L) lt = v else rt = v
                    if (v > 200) b = b or c.bit
                }
            }
        }
        val next = Hid.PadState(b, hat, lx, ly, rx, ry, lt, rt)
        if (next != state) {
            state = next
            onChange?.invoke(next)
        }
        invalidate()
    }

    private fun stick(c: Control, h: Hold): Pair<Int, Int> {
        val travel = c.r * TRAVEL
        var dx = (h.x - h.ox) / travel
        var dy = (h.y - h.oy) / travel
        val len = hypot(dx, dy)
        if (len < DEADZONE) return 0 to 0
        if (len > 1f) {
            dx /= len
            dy /= len
        }
        return (dx * 127).roundToInt() to (dy * 127).roundToInt()
    }

    private fun dpadValue(c: Control, x: Float, y: Float): Int {
        val dx = x - c.cx
        val dy = y - c.cy
        if (hypot(dx, dy) < c.r * 0.2f) return Hid.HAT_NONE
        val deg = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360.0) % 360.0
        return ((deg + 22.5) / 45.0).toInt() % 8
    }

    /** Puszcza wszystko (np. gdy aplikacja schodzi w tło). */
    fun releaseAll() {
        holds.clear()
        clicks = 0
        update()
    }

    private companion object {
        const val TRAVEL = 0.8f
        const val DEADZONE = 0.08f
        const val TAP_MS = 200L
        const val COLOR_BG = 0xFF0B0D11.toInt()
        const val COLOR_KEY = 0xFF1F232B.toInt()
        const val COLOR_WELL = 0xFF15181E.toInt()
        const val COLOR_KNOB = 0xFF3A404C.toInt()
        const val COLOR_EDGE = 0xFF343A46.toInt()
        const val COLOR_TEXT = 0xFFC9D1D9.toInt()
        const val COLOR_ACCENT = 0xFF107C10.toInt()
    }
}
