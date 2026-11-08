package com.btpilot

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Rejestruje telefon jako urządzenie Bluetooth HID (mysz + klawiatura)
 * i wysyła raporty do podłączonego hosta (drugi telefon, tablet, komputer, TV).
 *
 * Wszystkie metody publiczne wymagają uprawnienia BLUETOOTH_CONNECT (Android 12+),
 * które MainActivity uzyskuje przed wywołaniem [start].
 */
@SuppressLint("MissingPermission")
object HidController {

    enum class State { STARTING, NO_BLUETOOTH, NOT_SUPPORTED, READY, CONNECTING, CONNECTED }

    fun interface Listener {
        fun onHidStateChanged(state: State, device: BluetoothDevice?)
    }

    @Volatile var state: State = State.STARTING
        private set

    /** Urządzenie, z którym jesteśmy połączeni albo się łączymy. */
    @Volatile var device: BluetoothDevice? = null
        private set

    var lastError: String? = null
        private set

    private var adapter: BluetoothAdapter? = null
    @Volatile private var hid: BluetoothHidDevice? = null
    @Volatile private var registered = false
    private var proxyRequested = false
    private var pendingConnect: BluetoothDevice? = null

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val main = Handler(Looper.getMainLooper())
    // Jeden wątek = raporty wychodzą w tej samej kolejności, w jakiej je wysłano.
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    private var mouseButtons = 0

    // ------------------------------------------------------------------ cykl życia

    fun addListener(l: Listener) {
        listeners.add(l)
        l.onHidStateChanged(state, device)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    fun start(context: Context) {
        val ctx = context.applicationContext
        val a = adapter ?: ctx.getSystemService(BluetoothManager::class.java)?.adapter
        adapter = a
        if (a == null) {
            setState(State.NO_BLUETOOTH, null)
            return
        }
        if (proxyRequested) return
        proxyRequested = true
        if (!a.getProfileProxy(ctx, serviceListener, BluetoothProfile.HID_DEVICE)) {
            proxyRequested = false
            lastError = "System nie udostępnia profilu HID Device."
            setState(State.NOT_SUPPORTED, null)
        }
    }

    fun stop() {
        val h = hid
        try {
            device?.let { h?.disconnect(it) }
            if (registered) h?.unregisterApp()
        } catch (_: Exception) {
        }
        if (h != null) adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        hid = null
        registered = false
        proxyRequested = false
        mouseButtons = 0
        setState(State.STARTING, null)
    }

    fun connect(target: BluetoothDevice) {
        val h = hid
        if (h == null || !registered) {
            pendingConnect = target
            return
        }
        val current = device
        if (current == target && state == State.CONNECTED) return
        if (current != null && current != target && state == State.CONNECTED) {
            h.disconnect(current)
        }
        setState(State.CONNECTING, target)
        if (!h.connect(target)) {
            lastError = "Nie udało się rozpocząć połączenia."
            setState(State.READY, null)
        }
    }

    fun disconnect() {
        val d = device ?: return
        hid?.disconnect(d)
    }

    val isConnected: Boolean get() = state == State.CONNECTED

    // ------------------------------------------------------------------ mysz

    fun mouseMove(dx: Int, dy: Int) {
        var rx = dx
        var ry = dy
        while (rx != 0 || ry != 0) {
            val sx = rx.coerceIn(-127, 127)
            val sy = ry.coerceIn(-127, 127)
            sendMouse(sx, sy, 0, 0)
            rx -= sx
            ry -= sy
        }
    }

    fun mouseScroll(vertical: Int, horizontal: Int) {
        var v = vertical
        var h = horizontal
        while (v != 0 || h != 0) {
            val sv = v.coerceIn(-127, 127)
            val sh = h.coerceIn(-127, 127)
            sendMouse(0, 0, sv, sh)
            v -= sv
            h -= sh
        }
    }

    fun mouseButton(button: Int, down: Boolean) {
        mouseButtons = if (down) mouseButtons or button else mouseButtons and button.inv()
        sendMouse(0, 0, 0, 0)
    }

    fun click(button: Int) {
        mouseButton(button, true)
        mouseButton(button, false)
    }

    private fun sendMouse(x: Int, y: Int, wheel: Int, pan: Int) {
        send(
            Hid.ID_MOUSE,
            byteArrayOf(mouseButtons.toByte(), x.toByte(), y.toByte(), wheel.toByte(), pan.toByte()),
            0,
        )
    }

    // ------------------------------------------------------------------ klawiatura

    /** Wciska i puszcza klawisz (z modyfikatorami). */
    fun key(mods: Int, code: Int) {
        val down = ByteArray(Hid.KEYBOARD_REPORT_SIZE)
        down[0] = mods.toByte()
        down[2] = code.toByte()
        send(Hid.ID_KEYBOARD, down, KEY_DELAY_MS)
        send(Hid.ID_KEYBOARD, ByteArray(Hid.KEYBOARD_REPORT_SIZE), KEY_DELAY_MS)
    }

    /** Wpisuje tekst. Pierwszy znak dostaje dodatkowo [firstMods] (np. Ctrl dla Ctrl+C). */
    fun type(text: CharSequence, firstMods: Int = 0) {
        var extra = firstMods
        for (c in text) {
            val s = KeyMap.forChar(c) ?: continue
            key(s.mods or extra, s.code)
            extra = 0
        }
    }

    fun consumer(usage: Int) {
        val down = byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte())
        send(Hid.ID_CONSUMER, down, KEY_DELAY_MS)
        send(Hid.ID_CONSUMER, ByteArray(Hid.CONSUMER_REPORT_SIZE), KEY_DELAY_MS)
    }

    // ------------------------------------------------------------------ ekran dotykowy (tryb gry)

    /** Jeden palec. [x], [y] w zakresie 0..[Hid.TOUCH_MAX_COORD], w naturalnej (pionowej) orientacji ekranu. */
    data class Touch(val id: Int, val down: Boolean, val x: Int, val y: Int)

    /** Wysyła pełny stan dotyku: wszystkie palce, łącznie z tymi właśnie podniesionymi (down = false). */
    fun touch(contacts: List<Touch>) {
        val b = ByteArray(Hid.TOUCH_REPORT_SIZE)
        val n = minOf(contacts.size, Hid.TOUCH_CONTACTS)
        for (i in 0 until n) {
            val c = contacts[i]
            val o = i * Hid.TOUCH_CONTACT_SIZE
            val x = c.x.coerceIn(0, Hid.TOUCH_MAX_COORD)
            val y = c.y.coerceIn(0, Hid.TOUCH_MAX_COORD)
            b[o] = if (c.down) 0x03 else 0x00 // dotyk + w zasięgu
            b[o + 1] = c.id.toByte()
            b[o + 2] = (x and 0xFF).toByte()
            b[o + 3] = (x shr 8).toByte()
            b[o + 4] = (y and 0xFF).toByte()
            b[o + 5] = (y shr 8).toByte()
        }
        b[Hid.TOUCH_REPORT_SIZE - 1] = n.toByte()
        send(Hid.ID_TOUCH, b, 0)
    }

    // ------------------------------------------------------------------ wnętrzności

    private fun send(id: Int, data: ByteArray, delayAfterMs: Long) {
        val h = hid ?: return
        val d = device ?: return
        if (state != State.CONNECTED) return
        io.execute {
            try {
                h.sendReport(d, id, data)
                if (delayAfterMs > 0) Thread.sleep(delayAfterMs)
            } catch (_: Exception) {
            }
        }
    }

    private fun setState(s: State, d: BluetoothDevice?) {
        state = s
        device = d
        main.post { listeners.forEach { it.onHidStateChanged(s, d) } }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val h = proxy as BluetoothHidDevice
            hid = h
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "BT Pilot",
                "Mysz i klawiatura",
                "BT Pilot",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                Hid.DESCRIPTOR,
            )
            val ok = try {
                h.registerApp(sdp, null, null, io, callback)
            } catch (e: Exception) {
                lastError = e.message
                false
            }
            if (!ok) {
                if (lastError == null) {
                    lastError = "Rejestracja HID odrzucona. Zamknij inne aplikacje typu " +
                        "„klawiatura Bluetooth” i uruchom ponownie Bluetooth."
                }
                setState(State.NOT_SUPPORTED, null)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = null
            registered = false
            proxyRequested = false
            setState(State.STARTING, null)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            registered = isRegistered
            if (!isRegistered) {
                setState(State.STARTING, null)
                return
            }
            lastError = null
            if (state != State.CONNECTED && state != State.CONNECTING) setState(State.READY, null)
            val target = pendingConnect ?: pluggedDevice
            pendingConnect = null
            if (target != null) main.post { connect(target) }
        }

        override fun onConnectionStateChanged(dev: BluetoothDevice, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    mouseButtons = 0
                    setState(State.CONNECTED, dev)
                }
                BluetoothProfile.STATE_CONNECTING -> setState(State.CONNECTING, dev)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val current = device
                    if (current == null || current == dev) {
                        setState(if (registered) State.READY else State.STARTING, null)
                    }
                }
            }
        }

        override fun onGetReport(dev: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val feature = type == BluetoothHidDevice.REPORT_TYPE_FEATURE
            val data: ByteArray? = when (id.toInt()) {
                Hid.ID_KEYBOARD -> ByteArray(Hid.KEYBOARD_REPORT_SIZE)
                Hid.ID_MOUSE -> ByteArray(Hid.MOUSE_REPORT_SIZE)
                Hid.ID_CONSUMER -> ByteArray(Hid.CONSUMER_REPORT_SIZE)
                Hid.ID_TOUCH -> ByteArray(Hid.TOUCH_REPORT_SIZE)
                Hid.ID_TOUCH_MAX -> if (feature) byteArrayOf(Hid.TOUCH_CONTACTS.toByte()) else null
                Hid.ID_WIN8_BLOB -> if (feature) ByteArray(Hid.WIN8_BLOB_SIZE) else null
                else -> null
            }
            val h = hid ?: return
            if (data == null) {
                h.reportError(dev, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            } else {
                h.replyReport(dev, type, id, data)
            }
        }

        override fun onSetReport(dev: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            hid?.reportError(dev, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    private const val KEY_DELAY_MS = 6L
}
