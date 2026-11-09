package com.btpad

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.zip.CRC32

/**
 * Telefon jako pad Bluetooth (profil HID Device, Android 9+).
 *
 * Sam łączy się ponownie z ostatnim telefonem i pamięta, z którą wersją opisu pada
 * dany telefon był parowany (drugi telefon zapisuje ten opis tylko przy parowaniu).
 * Wymaga uprawnienia BLUETOOTH_CONNECT (Android 12+), które MainActivity uzyskuje przed [start].
 */
@SuppressLint("MissingPermission")
object BluetoothPad {

    enum class State { STARTING, NO_BLUETOOTH, NOT_SUPPORTED, READY, CONNECTING, CONNECTED }

    /** Czy sparowany telefon zna aktualny opis pada. */
    enum class HostStatus { CURRENT, OUTDATED, UNKNOWN }

    fun interface Listener {
        fun onPadStateChanged(state: State, device: BluetoothDevice?)
    }

    @Volatile var state = State.STARTING
        private set

    @Volatile var device: BluetoothDevice? = null
        private set

    var lastError: String? = null
        private set

    val isConnected get() = state == State.CONNECTED

    private var adapter: BluetoothAdapter? = null
    @Volatile private var hid: BluetoothHidDevice? = null
    @Volatile private var registered = false
    private var proxyRequested = false
    private var pendingConnect: BluetoothDevice? = null
    private var prefs: SharedPreferences? = null
    private var bondReceiverRegistered = false

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    /** Odcisk opisu pada – zmienia się tylko, gdy zmieni się deskryptor. */
    val fingerprint: Long = CRC32().apply { update(Hid.DESCRIPTOR) }.value

    // ------------------------------------------------------------------ dziennik

    private val logLines = ArrayDeque<String>()
    private val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT)

    fun log(msg: String) = synchronized(logLines) {
        logLines.addLast(timeFormat.format(java.util.Date()) + "  " + msg)
        while (logLines.size > 100) logLines.removeFirst()
    }

    fun logText(): String = synchronized(logLines) { logLines.joinToString("\n") }

    fun name(d: BluetoothDevice?): String =
        if (d == null) "-" else try { d.name ?: d.address } catch (_: SecurityException) { d.address }

    // ------------------------------------------------------------------ słuchacze

    fun addListener(l: Listener) {
        listeners.add(l)
        l.onPadStateChanged(state, device)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    private fun setState(s: State, d: BluetoothDevice?) {
        state = s
        device = d
        main.post { listeners.forEach { it.onPadStateChanged(s, d) } }
    }

    private fun notifyListeners() = setState(state, device)

    // ------------------------------------------------------------------ wersja u hosta

    fun hostStatus(d: BluetoothDevice): HostStatus {
        val p = prefs ?: return HostStatus.UNKNOWN
        val key = KEY_HOST + d.address
        if (!p.contains(key)) return HostStatus.UNKNOWN
        return if (p.getLong(key, -1) == fingerprint) HostStatus.CURRENT else HostStatus.OUTDATED
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val d: BluetoothDevice = (
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                ) ?: return
            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                BluetoothDevice.BOND_BONDED -> {
                    // Gdy BT Pad nie był aktywny, drugi telefon nie zobaczył pada.
                    prefs?.edit()?.putLong(KEY_HOST + d.address, if (registered) fingerprint else 0L)?.apply()
                    log("Sparowano: ${name(d)}" + if (registered) " – OK" else " – BT Pad nie był aktywny!")
                    notifyListeners()
                }
                BluetoothDevice.BOND_NONE -> {
                    prefs?.edit()?.remove(KEY_HOST + d.address)?.apply()
                    log("Usunięto parowanie: ${name(d)}")
                    notifyListeners()
                }
            }
        }
    }

    /** Próbuje usunąć parowanie z tej strony (nie każdy telefon na to pozwala). */
    fun removeBond(d: BluetoothDevice): Boolean = try {
        (d.javaClass.getMethod("removeBond").invoke(d) as? Boolean) == true
    } catch (e: Exception) {
        log("removeBond niedostępne: " + (e.cause?.javaClass?.simpleName ?: e.javaClass.simpleName))
        false
    }

    // ------------------------------------------------------------------ cykl życia

    fun start(context: Context) {
        val ctx = context.applicationContext
        if (prefs == null) prefs = ctx.getSharedPreferences("bt_pad", Context.MODE_PRIVATE)
        if (!bondReceiverRegistered) {
            val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(bondReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ctx.registerReceiver(bondReceiver, filter)
            }
            bondReceiverRegistered = true
        }
        val a = adapter ?: ctx.getSystemService(BluetoothManager::class.java)?.adapter
        adapter = a
        if (a == null) {
            setState(State.NO_BLUETOOTH, null)
            return
        }
        if (proxyRequested) return
        proxyRequested = true
        log("Start (opis pada ${Hid.DESCRIPTOR.size} B, wersja ${java.lang.Long.toHexString(fingerprint)})")
        if (!a.getProfileProxy(ctx, serviceListener, BluetoothProfile.HID_DEVICE)) {
            proxyRequested = false
            lastError = "System nie udostępnia profilu HID Device."
            log("BŁĄD: " + lastError)
            setState(State.NOT_SUPPORTED, null)
        }
    }

    fun stop() {
        log("Stop")
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
        setState(State.STARTING, null)
    }

    /** Telefony, którym użytkownik pozwolił wysyłać pad mimo nieaktualnego parowania. */
    private val forced = HashSet<String>()

    /**
     * [force] = użytkownik wybrał „Połącz mimo to”. Bez tego pad nic nie wysyła do telefonu,
     * który nie zna aktualnego opisu pada (mógłby odczytać naciśnięcia jako np. klawiaturę).
     */
    fun connect(target: BluetoothDevice, force: Boolean = false) {
        if (force) forced += target.address
        log("Łączenie z ${name(target)} (stan u niego: ${hostStatus(target)}${if (force) ", mimo to" else ""})")
        val h = hid
        if (h == null || !registered) {
            pendingConnect = target
            return
        }
        val current = device
        if (current == target && state == State.CONNECTED) return
        if (current != null && current != target && state == State.CONNECTED) h.disconnect(current)
        setState(State.CONNECTING, target)
        if (!h.connect(target)) {
            log("BŁĄD: connect() = false")
            setState(State.READY, null)
        }
    }

    fun disconnect() {
        val d = device ?: return
        log("Rozłącz ${name(d)}")
        hid?.disconnect(d)
    }

    /** Ostatni telefon, z którym był połączony pad – do automatycznego łączenia. */
    fun lastHost(): BluetoothDevice? {
        val addr = prefs?.getString(KEY_LAST, null) ?: return null
        return try {
            adapter?.bondedDevices?.firstOrNull { it.address == addr }
        } catch (_: SecurityException) {
            null
        }
    }

    // ------------------------------------------------------------------ wysyłanie

    private var lastReport: ByteArray? = null

    fun send(pad: Hid.PadState) {
        val h = hid ?: return
        val d = device ?: return
        if (state != State.CONNECTED) return
        if (hostStatus(d) != HostStatus.CURRENT && d.address !in forced) return
        val report = pad.toReport()
        lastReport = report
        io.execute {
            try {
                h.sendReport(d, Hid.REPORT_ID, report)
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------ callbacki systemu

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val h = proxy as BluetoothHidDevice
            hid = h
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "BT Pad",
                "Pad",
                "BT Pad",
                (BluetoothHidDevice.SUBCLASS1_NONE.toInt() or BluetoothHidDevice.SUBCLASS2_GAMEPAD.toInt()).toByte(),
                Hid.DESCRIPTOR,
            )
            val ok = try {
                h.registerApp(sdp, null, null, io, callback)
            } catch (e: Exception) {
                lastError = e.message
                false
            }
            log("Rejestracja pada: " + (if (ok) "wysłana" else "ODRZUCONA"))
            if (!ok) {
                lastError = "Telefon odrzucił rejestrację pada. Zamknij inne aplikacje udające " +
                    "pad/klawiaturę/mysz Bluetooth (np. BT Pilot), wyłącz i włącz Bluetooth i uruchom BT Pad ponownie."
                setState(State.NOT_SUPPORTED, null)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            log("Usługa HID zatrzymana przez system")
            hid = null
            registered = false
            proxyRequested = false
            setState(State.STARTING, null)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            registered = isRegistered
            log("Pad zarejestrowany: $isRegistered, zapamiętany telefon: ${name(pluggedDevice)}")
            if (!isRegistered) {
                setState(State.STARTING, null)
                return
            }
            lastError = null
            if (state != State.CONNECTED && state != State.CONNECTING) setState(State.READY, null)
            // Automatycznie łączymy tylko z telefonem, który zna aktualny opis pada.
            val target = pendingConnect
                ?: pluggedDevice?.takeIf { hostStatus(it) == HostStatus.CURRENT }
                ?: lastHost()?.takeIf { hostStatus(it) == HostStatus.CURRENT }
            pendingConnect = null
            if (target != null) main.post { connect(target) }
        }

        override fun onConnectionStateChanged(dev: BluetoothDevice, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("POŁĄCZONO z ${name(dev)} (wersja u niego: ${hostStatus(dev)})")
                    prefs?.edit()?.putString(KEY_LAST, dev.address)?.apply()
                    setState(State.CONNECTED, dev)
                    send(Hid.PadState()) // stan neutralny na start
                }
                BluetoothProfile.STATE_CONNECTING -> setState(State.CONNECTING, dev)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("Rozłączono: ${name(dev)}")
                    val current = device
                    if (current == null || current == dev) {
                        setState(if (registered) State.READY else State.STARTING, null)
                    }
                }
            }
        }

        override fun onGetReport(dev: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            log("Telefon pyta o stan pada (typ $type, id $id)")
            val h = hid ?: return
            if (id.toInt() == Hid.REPORT_ID && type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
                h.replyReport(dev, type, id, lastReport ?: Hid.PadState().toReport())
            } else {
                h.reportError(dev, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
            }
        }

        override fun onSetReport(dev: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            hid?.reportError(dev, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onVirtualCableUnplug(dev: BluetoothDevice) {
            log("Telefon usunął pad: ${name(dev)}")
        }
    }

    private const val KEY_HOST = "host_"
    private const val KEY_LAST = "last_host"
}
