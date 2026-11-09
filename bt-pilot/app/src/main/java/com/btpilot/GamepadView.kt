package com.btpilot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Kontroler gry: ekran telefonu odpowiada 1:1 ekranowi gry na drugim telefonie.
 *
 * Każdy palec staje się palcem na drugim telefonie (do 3 naraz). Jeśli dotkniesz
 * przycisku z konfiguracji (Ruch, Q, W, E, R, Atak…), dotyk na drugim telefonie zaczyna się
 * dokładnie w środku tego przycisku, a dalej podąża za ruchem palca – tak działa celowanie
 * umiejętnościami i joystick. Dotyk poza przyciskami trafia w to samo miejsce ekranu gry.
 */
class GamepadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var controls: MutableList<GameControl> = mutableListOf()
        set(value) {
            field = value
            invalidate()
        }

    var backgroundImage: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var rotation: TargetRotation = TargetRotation.ROT_90

    var editMode = false
        set(value) {
            releaseAll()
            field = value
            selected = null
            invalidate()
        }

    /** Ostatnio wybrany przycisk w trybie edycji (do zmiany rozmiaru). */
    var selected: GameControl? = null
        private set

    var onLayoutEdited: (() -> Unit)? = null

    private class Slot(
        val pointerId: Int,
        val contactId: Int,
        val control: GameControl?,
        val baseX: Float,
        val baseY: Float,
        val startX: Float,
        val startY: Float,
    ) {
        var x = baseX
        var y = baseY
        var fingerX = 0f
        var fingerY = 0f
    }

    private val slots = arrayOfNulls<Slot>(Hid.TOUCH_CONTACTS)

    // Część telefonów zwalnia "zawieszony" palec, jeśli przez chwilę nie ma raportu,
    // dlatego przy trzymanym palcu powtarzamy stan co chwilę.
    private val keepAlive = object : Runnable {
        override fun run() {
            if (slots.any { it != null }) {
                sendFrame(emptyList())
                postDelayed(this, KEEP_ALIVE_MS)
            }
        }
    }

    // Edycja
    private var dragging: GameControl? = null
    private var dragDx = 0f
    private var dragDy = 0f

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A91A3.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
    }
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val src = Rect()

    // ------------------------------------------------------------------ rysowanie

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFF0D0F13.toInt())
        val w = width.toFloat()
        val h = height.toFloat()

        backgroundImage?.let {
            src.set(0, 0, it.width, it.height)
            dst.set(0f, 0f, w, h)
            bgPaint.alpha = if (editMode) 190 else 110
            canvas.drawBitmap(it, src, dst, bgPaint)
        }

        val pressed = slots.mapNotNull { it?.control }.toSet()
        for (c in controls) {
            val cx = c.x * w
            val cy = c.y * h
            val r = c.r * h
            val color = colorFor(c.kind)
            fill.color = color
            fill.alpha = if (c in pressed) 170 else 55
            canvas.drawCircle(cx, cy, r, fill)
            stroke.color = if (editMode && c == selected) 0xFFFAB005.toInt() else color
            canvas.drawCircle(cx, cy, r, stroke)
            text.textSize = (r * if (c.label.length <= 2) 0.7f else 0.42f).coerceAtLeast(11f * density)
            canvas.drawText(c.label, cx, cy - (text.descent() + text.ascent()) / 2, text)
        }

        // Gdzie palce dotykają ekranu gry.
        for (s in slots) {
            if (s == null) continue
            fill.color = 0xFFFFFFFF.toInt()
            fill.alpha = 200
            canvas.drawCircle(s.x * w, s.y * h, 7f * density, fill)
        }

        if (editMode) {
            canvas.drawText(
                "Edycja: przeciągnij przyciski na miejsca z gry • − / + zmienia rozmiar",
                w / 2, h - 14f * density, hintText,
            )
        } else if (backgroundImage == null) {
            canvas.drawText(
                "Wskazówka: wczytaj zrzut ekranu z Wild Rift (🖼) i dopasuj przyciski (✎)",
                w / 2, h - 14f * density, hintText,
            )
        }
    }

    private fun colorFor(kind: GameControl.Kind): Int = when (kind) {
        GameControl.Kind.MOVE -> 0xFF4DABF7.toInt()
        GameControl.Kind.ATTACK -> 0xFFFA5252.toInt()
        GameControl.Kind.SKILL -> 0xFF5C7CFA.toInt()
        GameControl.Kind.SPELL -> 0xFFFD7E14.toInt()
        GameControl.Kind.UTIL -> 0xFF868E96.toInt()
    }

    // ------------------------------------------------------------------ dotyk

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (width == 0 || height == 0) return false
        if (editMode) return editTouch(e)

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                pointerDown(e.getPointerId(i), e.getX(i), e.getY(i))
                sendFrame(emptyList())
                removeCallbacks(keepAlive)
                postDelayed(keepAlive, KEEP_ALIVE_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                var changed = false
                for (i in 0 until e.pointerCount) {
                    val s = slotFor(e.getPointerId(i)) ?: continue
                    val fx = e.getX(i)
                    val fy = e.getY(i)
                    if (fx == s.fingerX && fy == s.fingerY) continue
                    s.fingerX = fx
                    s.fingerY = fy
                    s.x = (s.baseX + fx / width - s.startX).coerceIn(0f, 1f)
                    s.y = (s.baseY + fy / height - s.startY).coerceIn(0f, 1f)
                    changed = true
                }
                if (changed) sendFrame(emptyList())
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val id = e.getPointerId(e.actionIndex)
                val idx = slots.indexOfFirst { it?.pointerId == id }
                if (idx >= 0) {
                    val s = slots[idx]!!
                    slots[idx] = null
                    sendFrame(listOf(s))
                }
                if (slots.all { it == null }) removeCallbacks(keepAlive)
            }

            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        invalidate()
        return true
    }

    private fun pointerDown(pointerId: Int, fx: Float, fy: Float) {
        val idx = slots.indexOfFirst { it == null }
        if (idx < 0) return
        val nx = fx / width
        val ny = fy / height
        val control = hit(fx, fy)
        if (control != null) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        slots[idx] = Slot(
            pointerId = pointerId,
            contactId = idx,
            control = control,
            baseX = control?.x ?: nx,
            baseY = control?.y ?: ny,
            startX = nx,
            startY = ny,
        ).also {
            it.fingerX = fx
            it.fingerY = fy
        }
    }

    private fun slotFor(pointerId: Int): Slot? = slots.firstOrNull { it?.pointerId == pointerId }

    /** Najbliższy przycisk pod palcem (z lekkim zapasem). */
    private fun hit(fx: Float, fy: Float): GameControl? {
        var best: GameControl? = null
        var bestDist = Float.MAX_VALUE
        for (c in controls) {
            val d = hypot(fx - c.x * width, fy - c.y * height)
            if (d <= c.r * height * HIT_SLACK && d < bestDist) {
                best = c
                bestDist = d
            }
        }
        return best
    }

    /** Wysyła aktywne palce + palce właśnie podniesione ([released]). */
    private fun sendFrame(released: List<Slot>) {
        val list = ArrayList<HidController.Touch>(Hid.TOUCH_CONTACTS)
        for (s in released) {
            val (x, y) = rotation.toHid(s.x, s.y)
            list += HidController.Touch(s.contactId, false, x, y)
        }
        for (s in slots) {
            if (s == null) continue
            val (x, y) = rotation.toHid(s.x, s.y)
            list += HidController.Touch(s.contactId, true, x, y)
        }
        HidController.touch(list)
    }

    fun releaseAll() {
        val active = slots.filterNotNull()
        slots.fill(null)
        removeCallbacks(keepAlive)
        if (active.isNotEmpty()) sendFrame(active)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------ edycja

    private fun editTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val c = hit(e.x, e.y)
                dragging = c
                if (c != null) {
                    selected = c
                    dragDx = e.x / width - c.x
                    dragDy = e.y / height - c.y
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }

            MotionEvent.ACTION_MOVE -> dragging?.let {
                it.x = (e.x / width - dragDx).coerceIn(0f, 1f)
                it.y = (e.y / height - dragDy).coerceIn(0f, 1f)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging != null) onLayoutEdited?.invoke()
                dragging = null
            }
        }
        invalidate()
        return true
    }

    /** Zmienia rozmiar wybranego przycisku. */
    fun resizeSelected(factor: Float) {
        val c = selected ?: return
        c.r = (c.r * factor).coerceIn(0.02f, 0.35f)
        onLayoutEdited?.invoke()
        invalidate()
    }

    private companion object {
        const val KEEP_ALIVE_MS = 40L
        const val HIT_SLACK = 1.2f
    }
}
