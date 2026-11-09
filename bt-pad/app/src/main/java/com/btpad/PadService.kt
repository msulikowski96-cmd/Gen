package com.btpad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Usługa na pierwszym planie (z powiadomieniem).
 *
 * Android wyłącza tryb pada Bluetooth, gdy aplikacja przestaje być na pierwszym planie
 * – nawet na chwilę, gdy wyskoczy systemowe okienko. Działająca usługa sprawia,
 * że pad zostaje włączony, także gdy przełączysz się na inną aplikację.
 */
class PadService : Service(), BluetoothPad.Listener {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "BT Pad", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Pad Bluetooth działa"
                setShowBadge(false)
            },
        )
        val notification = build("Uruchamianie pada…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(ID, notification)
        }
        BluetoothPad.addListener(this)
        BluetoothPad.log("Usługa w tle uruchomiona")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BluetoothPad.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        BluetoothPad.start(this)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        BluetoothPad.removeListener(this)
        super.onDestroy()
    }

    override fun onPadStateChanged(state: BluetoothPad.State, device: BluetoothDevice?) {
        val text = when (state) {
            BluetoothPad.State.CONNECTED -> "Pad połączony: ${BluetoothPad.name(device)}"
            BluetoothPad.State.CONNECTING -> "Łączenie z ${BluetoothPad.name(device)}…"
            BluetoothPad.State.READY -> "Pad gotowy – niepołączony"
            BluetoothPad.State.NOT_SUPPORTED -> "Pad niedostępny – otwórz aplikację"
            else -> "Uruchamianie pada…"
        }
        getSystemService(NotificationManager::class.java).notify(ID, build(text))
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, PadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("BT Pad")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Zakończ", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "pad"
        private const val ID = 1
        const val ACTION_STOP = "com.btpad.STOP"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, PadService::class.java))
            } catch (e: Exception) {
                // Bez usługi pad i tak działa, dopóki aplikacja jest na ekranie.
                BluetoothPad.log("Nie udało się uruchomić usługi w tle: ${e.javaClass.simpleName}")
                BluetoothPad.start(context)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PadService::class.java))
        }
    }
}
