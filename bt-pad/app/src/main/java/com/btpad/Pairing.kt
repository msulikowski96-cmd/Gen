package com.btpad

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.btpad.BluetoothPad.HostStatus

/**
 * Łączenie z drugim telefonem i naprawa parowania.
 *
 * Drugi telefon zapamiętuje „opis pada” tylko w chwili parowania. Jeśli parowanie zrobiono
 * z inną aplikacją (np. BT Pilot) albo gdy BT Pad nie był włączony, pad nie działa –
 * kreator prowadzi przez ponowne sparowanie.
 */
@SuppressLint("MissingPermission")
object Pairing {

    private fun adapter(a: Activity): BluetoothAdapter? =
        a.getSystemService(BluetoothManager::class.java)?.adapter

    fun name(d: BluetoothDevice): String = try {
        d.name ?: d.address
    } catch (_: SecurityException) {
        d.address
    }

    private fun myName(a: Activity): String = try {
        adapter(a)?.name ?: "ten telefon"
    } catch (_: SecurityException) {
        "ten telefon"
    }

    fun statusText(s: HostStatus): String = when (s) {
        HostStatus.CURRENT -> "✅ gotowy"
        HostStatus.OUTDATED -> "⚠ zna starą wersję – napraw parowanie"
        HostStatus.UNKNOWN -> "❔ nieznana wersja – napraw, jeśli pad nie działa"
    }

    // ------------------------------------------------------------------ lista urządzeń

    fun showConnectDialog(a: Activity) {
        val adapter = adapter(a) ?: return
        val bonded = adapter.bondedDevices.orEmpty().sortedBy { name(it).lowercase() }
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        labels += "➕  Sparuj nowy telefon"
        actions += { showNewDevice(a) }

        for (d in bonded) {
            val connected = BluetoothPad.isConnected && BluetoothPad.device == d
            labels += (if (connected) "🔗  " else "📱  ") + name(d) + "\n      " +
                statusText(BluetoothPad.hostStatus(d))
            actions += { chooseDevice(a, d) }
        }

        if (BluetoothPad.device != null) {
            labels += "✖  Rozłącz"
            actions += { BluetoothPad.disconnect() }
        }

        labels += "🩺  Diagnostyka połączenia"
        actions += { showDiagnostics(a) }

        AlertDialog.Builder(a)
            .setTitle("Połącz z telefonem")
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    /** Wybrano sparowane urządzenie: łączymy albo proponujemy naprawę parowania. */
    fun chooseDevice(a: Activity, d: BluetoothDevice) {
        val status = BluetoothPad.hostStatus(d)
        if (status == HostStatus.CURRENT) {
            BluetoothPad.connect(d)
            return
        }
        val msg = if (status == HostStatus.OUTDATED) {
            "„${name(d)}” był sparowany, gdy ten telefon udawał inne urządzenie " +
                "(np. w aplikacji BT Pilot).\n\n" +
                "Pad nie zadziała, dopóki nie sparujesz telefonów od nowa (zajmie to minutę)."
        } else {
            "„${name(d)}” sparowano zanim zainstalowałeś BT Pad (albo gdy BT Pad był wyłączony).\n\n" +
                "Drugi telefon najpewniej nie wie, że to pad – napraw parowanie."
        }
        AlertDialog.Builder(a)
            .setTitle(name(d))
            .setMessage(msg)
            .setPositiveButton("Napraw parowanie") { _, _ -> startRepair(a, d) }
            .setNeutralButton("Połącz mimo to") { _, _ -> BluetoothPad.connect(d, force = true) }
            .show()
    }

    // ------------------------------------------------------------------ kreator naprawy

    fun startRepair(a: Activity, d: BluetoothDevice) {
        val other = name(d)
        val me = myName(a)
        AlertDialog.Builder(a)
            .setTitle("Napraw parowanie – krok 1 z 3")
            .setMessage(
                "Weź do ręki telefon „$other” (ten z grą):\n\n" +
                    "Ustawienia → Bluetooth → ⚙ przy „$me” → Rozłącz parowanie (Zapomnij).",
            )
            .setPositiveButton("Zrobione, dalej") { _, _ -> repairStep2(a, d) }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun repairStep2(a: Activity, d: BluetoothDevice) {
        BluetoothPad.disconnect()
        if (BluetoothPad.removeBond(d)) {
            Toast.makeText(a, "Usunięto parowanie na tym telefonie", Toast.LENGTH_SHORT).show()
            a.window.decorView.postDelayed({ repairStep3(a, name(d)) }, 800)
            return
        }
        val other = name(d)
        AlertDialog.Builder(a)
            .setTitle("Napraw parowanie – krok 2 z 3")
            .setMessage(
                "Teraz na TYM telefonie:\n\n" +
                    "Ustawienia → Bluetooth → ⚙ przy „$other” → Rozłącz parowanie (Zapomnij).\n\n" +
                    "Potem wróć do BT Pad.",
            )
            .setPositiveButton("Otwórz ustawienia Bluetooth") { _, _ ->
                repairStep3(a, other)
                a.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun repairStep3(a: Activity, other: String) {
        AlertDialog.Builder(a)
            .setTitle("Napraw parowanie – krok 3 z 3")
            .setMessage(pairInstructions(a, other))
            .setPositiveButton("Uczyń widocznym") { _, _ -> makeDiscoverable(a) }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    fun showNewDevice(a: Activity) {
        AlertDialog.Builder(a)
            .setTitle("Sparuj nowy telefon")
            .setMessage(pairInstructions(a, "drugi telefon"))
            .setPositiveButton("Uczyń widocznym") { _, _ -> makeDiscoverable(a) }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun pairInstructions(a: Activity, other: String): String {
        val me = myName(a)
        return "1. Kliknij „Uczyń widocznym” i zezwól.\n\n" +
            "2. Na „$other”: Ustawienia → Bluetooth → wyszukaj „$me” → Sparuj i potwierdź kod.\n\n" +
            "3. Gotowe – na górze BT Pad pojawi się „Połączono”, a na liście ✅.\n\n" +
            "Nie zamykaj BT Pad podczas parowania."
    }

    private fun makeDiscoverable(a: Activity) {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 180)
        try {
            a.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(a, "Nie można włączyć widoczności: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------ diagnostyka

    fun showDiagnostics(a: Activity) {
        val adapter = adapter(a)
        val devices = try {
            adapter?.bondedDevices.orEmpty().joinToString("\n") {
                "  • ${name(it)}: ${BluetoothPad.hostStatus(it)}"
            }
        } catch (_: SecurityException) {
            "  (brak uprawnień)"
        }
        val info = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
            "${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Stan: ${BluetoothPad.state}, wersja pada: ${java.lang.Long.toHexString(BluetoothPad.fingerprint)}\n" +
            (BluetoothPad.lastError?.let { "Błąd: $it\n" } ?: "") +
            "Sparowane:\n$devices\n\n"
        val text = info + BluetoothPad.logText().ifEmpty { "(brak wpisów)" }
        val view = TextView(a).apply {
            this.text = text
            textSize = 11f
            setTextIsSelectable(true)
            val p = (16 * a.resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
        }
        AlertDialog.Builder(a)
            .setTitle("Diagnostyka")
            .setView(ScrollView(a).apply { addView(view) })
            .setPositiveButton("Kopiuj") { _, _ ->
                a.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("BT Pad", text))
                Toast.makeText(a, "Skopiowano – wklej w czacie", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Zamknij", null)
            .show()
    }
}
