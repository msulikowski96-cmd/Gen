package com.btpilot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Gładzik:
 *  - 1 palec: ruch kursora, stuknięcie = lewy klik
 *  - 2 palce: przewijanie, stuknięcie = prawy klik
 *  - 3 palce: stuknięcie = środkowy klik
 *  - przytrzymanie 1 palcem i przesunięcie = przeciąganie (lewy przycisk wciśnięty)
 */
class TouchpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Callback {
        fun onMove(dx: Int, dy: Int)
        fun onScroll(vertical: Int, horizontal: Int)
        fun onTap(fingers: Int)
        fun onDragStart()
        fun onDragEnd()
    }

    var callback: Callback? = null
    var sensitivity = 1.5f
    var scrollSpeed = 1f
    var invertScroll = false

    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop / 2f

    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var maxPointers = 0
    private var travelled = 0f
    private var moved = false
    private var dragging = false
    private var touching = false
    private var fracX = 0f
    private var fracY = 0f
    private var scrollAccX = 0f
    private var scrollAccY = 0f

    private val longPress = Runnable {
        if (!moved && maxPointers == 1 && touching) {
            dragging = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            callback?.onDragStart()
            invalidate()
        }
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C8496.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 13f * density
    }
    private val rect = RectF()

    var hint: List<String> = listOf(
        "1 palec: ruch • stuknij = klik",
        "2 palce: przewijanie • stuknij = prawy klik",
        "3 palce: stuknij = środkowy klik",
        "przytrzymaj i przesuń = przeciąganie",
    )

    override fun onDraw(canvas: Canvas) {
        val r = 18f * density
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        bgPaint.color = when {
            dragging -> 0xFF2B3A67.toInt()
            touching -> 0xFF22262F.toInt()
            else -> 0xFF1B1E25.toInt()
        }
        borderPaint.color = if (dragging) 0xFF5C7CFA.toInt() else 0xFF2E333D.toInt()
        canvas.drawRoundRect(rect, r, r, bgPaint)
        rect.inset(borderPaint.strokeWidth / 2, borderPaint.strokeWidth / 2)
        canvas.drawRoundRect(rect, r, r, borderPaint)

        val lineH = textPaint.textSize * 1.6f
        var y = height / 2f - (hint.size - 1) * lineH / 2f
        for (line in hint) {
            canvas.drawText(line, width / 2f, y, textPaint)
            y += lineH
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touching = true
                downTime = e.eventTime
                maxPointers = 1
                travelled = 0f
                moved = false
                dragging = false
                fracX = 0f; fracY = 0f
                scrollAccX = 0f; scrollAccY = 0f
                lastX = e.x; lastY = e.y
                postDelayed(longPress, LONG_PRESS_MS)
                invalidate()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = max(maxPointers, e.pointerCount)
                removeCallbacks(longPress)
                resetAnchor(e, -1)
            }

            MotionEvent.ACTION_MOVE -> {
                val (cx, cy) = centroid(e, -1)
                val dx = cx - lastX
                val dy = cy - lastY
                lastX = cx; lastY = cy
                travelled += abs(dx) + abs(dy)
                if (!moved && travelled > slop) {
                    moved = true
                    removeCallbacks(longPress)
                }
                if (e.pointerCount >= 2 && !dragging) {
                    if (moved) scroll(dx, dy)
                } else if (moved || dragging) {
                    move(dx, dy)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> resetAnchor(e, e.actionIndex)

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                touching = false
                if (dragging) {
                    dragging = false
                    callback?.onDragEnd()
                } else if (e.actionMasked == MotionEvent.ACTION_UP &&
                    !moved && e.eventTime - downTime < TAP_MS
                ) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    callback?.onTap(maxPointers)
                }
                invalidate()
            }
        }
        return true
    }

    private fun move(dx: Float, dy: Float) {
        // Proste przyspieszenie: szybki ruch palca = większy skok kursora.
        val speed = hypot(dx, dy) / density
        val gain = sensitivity * (0.7f + (speed / 12f).coerceAtMost(2.3f))
        fracX += dx * gain
        fracY += dy * gain
        val ix = fracX.toInt()
        val iy = fracY.toInt()
        fracX -= ix
        fracY -= iy
        if (ix != 0 || iy != 0) callback?.onMove(ix, iy)
    }

    private fun scroll(dx: Float, dy: Float) {
        val step = 28f * density / scrollSpeed
        scrollAccY += dy
        scrollAccX += dx
        val ny = (scrollAccY / step).toInt()
        val nx = (scrollAccX / step).toInt()
        scrollAccY -= ny * step
        scrollAccX -= nx * step
        if (ny == 0 && nx == 0) return
        // "Naturalne" przewijanie jak na telefonie: palce w dół = treść w dół.
        val sign = if (invertScroll) -1 else 1
        callback?.onScroll(ny * sign, -nx * sign)
    }

    private fun resetAnchor(e: MotionEvent, skipIndex: Int) {
        val (cx, cy) = centroid(e, skipIndex)
        lastX = cx; lastY = cy
    }

    private fun centroid(e: MotionEvent, skipIndex: Int): Pair<Float, Float> {
        var sx = 0f
        var sy = 0f
        var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == skipIndex) continue
            sx += e.getX(i)
            sy += e.getY(i)
            n++
        }
        if (n == 0) return lastX to lastY
        return (sx / n) to (sy / n)
    }

    private companion object {
        const val LONG_PRESS_MS = 450L
        const val TAP_MS = 250L
    }
}
