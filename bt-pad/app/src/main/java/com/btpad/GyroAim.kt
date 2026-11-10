package com.btpad

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Celowanie ruchem telefonu: prędkość obrotu z żyroskopu zamieniana na wychylenie prawej gałki.
 * Telefon działa jak "okno" – obrót w lewo = patrz w lewo, odchylenie górnej krawędzi do siebie = patrz w górę.
 *
 * @param rotation zwraca aktualny obrót ekranu (Surface.ROTATION_*), bo osie czujnika są związane z obudową
 * @param onAim wychylenie prawej gałki X, Y w zakresie -127..127
 */
class GyroAim(
    context: Context,
    private val rotation: () -> Int,
    private val onAim: (Int, Int) -> Unit,
) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val available get() = sensor != null

    /** 0.3..3.0 */
    var sensitivity = 1f
    var invertX = false
    var invertY = false

    private var running = false
    private var smoothX = 0f
    private var smoothY = 0f

    fun start() {
        val s = sensor ?: return
        if (running) return
        running = manager?.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME) == true
    }

    fun stop() {
        if (running) manager?.unregisterListener(this)
        running = false
        smoothX = 0f
        smoothY = 0f
        onAim(0, 0)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val gx = e.values[0] // obrót wokół osi X obudowy (rad/s)
        val gy = e.values[1] // obrót wokół osi Y obudowy
        // Przeliczenie na ekran w poziomie: w prawo (+) i w dół (+).
        var right: Float
        var down: Float
        if (rotation() == Surface.ROTATION_270) {
            right = gx
            down = -gy
        } else {
            right = -gx
            down = gy
        }
        if (invertX) right = -right
        if (invertY) down = -down

        // Lekkie wygładzenie drgań ręki.
        smoothX = smoothX * 0.5f + right * 0.5f
        smoothY = smoothY * 0.5f + down * 0.5f

        val speed = hypot(smoothX, smoothY)
        if (speed < DEADBAND) {
            onAim(0, 0)
            return
        }
        // Gry ignorują małe wychylenia gałki, więc zaczynamy od MIN_OUTPUT.
        val out = (MIN_OUTPUT + speed * GAIN * sensitivity).coerceAtMost(1f)
        onAim((smoothX / speed * out * 127).roundToInt(), (smoothY / speed * out * 127).roundToInt())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val DEADBAND = 0.04f // rad/s
        const val MIN_OUTPUT = 0.1f
        const val GAIN = 0.5f
    }
}
