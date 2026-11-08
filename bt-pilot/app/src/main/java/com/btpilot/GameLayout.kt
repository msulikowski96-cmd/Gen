package com.btpilot

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Przycisk na ekranie gry. Pozycja (x, y) to ułamek szerokości/wysokości ekranu gry
 * w poziomie (0..1), promień [r] to ułamek wysokości ekranu.
 */
data class GameControl(
    val id: String,
    val label: String,
    val kind: Kind,
    var x: Float,
    var y: Float,
    var r: Float,
) {
    enum class Kind { MOVE, ATTACK, SKILL, SPELL, UTIL }
}

/** Jak ekran gry jest obrócony względem naturalnej (pionowej) orientacji telefonu-celu. */
enum class TargetRotation(val label: String, val hint: String) {
    ROT_90("90°", "telefon obrócony w lewo (góra telefonu po lewej)"),
    ROT_270("270°", "telefon obrócony w prawo (góra telefonu po prawej)"),
    NONE("0°", "bez obracania"),
    ;

    /** Pozycja na ekranie gry (0..1) -> współrzędne HID w naturalnej orientacji. */
    fun toHid(gx: Float, gy: Float): Pair<Int, Int> {
        val (nx, ny) = when (this) {
            ROT_90 -> (1f - gy) to gx
            ROT_270 -> gy to (1f - gx)
            NONE -> gx to gy
        }
        val m = Hid.TOUCH_MAX_COORD
        return (nx.coerceIn(0f, 1f) * m).toInt() to (ny.coerceIn(0f, 1f) * m).toInt()
    }

    fun next(): TargetRotation = entries[(ordinal + 1) % entries.size]
}

object GameLayouts {
    private const val PREF_LAYOUT = "wild_rift_layout"

    /**
     * Przybliżone położenie przycisków domyślnego HUD-u Wild Rift.
     * Każdy telefon i ustawienia HUD-u są trochę inne – dopasuj je w trybie edycji.
     */
    fun wildRiftDefault(): MutableList<GameControl> = mutableListOf(
        GameControl("move", "Ruch", GameControl.Kind.MOVE, 0.135f, 0.72f, 0.17f),
        GameControl("attack", "Atak", GameControl.Kind.ATTACK, 0.885f, 0.79f, 0.105f),
        GameControl("q", "Q", GameControl.Kind.SKILL, 0.735f, 0.86f, 0.07f),
        GameControl("w", "W", GameControl.Kind.SKILL, 0.75f, 0.64f, 0.07f),
        GameControl("e", "E", GameControl.Kind.SKILL, 0.815f, 0.48f, 0.07f),
        GameControl("r", "R", GameControl.Kind.SKILL, 0.915f, 0.44f, 0.07f),
        GameControl("d", "D", GameControl.Kind.SPELL, 0.625f, 0.88f, 0.058f),
        GameControl("f", "F", GameControl.Kind.SPELL, 0.64f, 0.69f, 0.058f),
        GameControl("item", "Przedm.", GameControl.Kind.UTIL, 0.705f, 0.43f, 0.05f),
        GameControl("ward", "Ward", GameControl.Kind.UTIL, 0.615f, 0.51f, 0.05f),
        GameControl("recall", "Powrót", GameControl.Kind.UTIL, 0.53f, 0.9f, 0.05f),
        GameControl("shop", "Sklep", GameControl.Kind.UTIL, 0.055f, 0.42f, 0.05f),
    )

    fun load(prefs: SharedPreferences): MutableList<GameControl> {
        val defaults = wildRiftDefault()
        val json = prefs.getString(PREF_LAYOUT, null) ?: return defaults
        return try {
            val saved = JSONArray(json)
            val byId = (0 until saved.length()).map { saved.getJSONObject(it) }.associateBy { it.getString("id") }
            // Bierzemy listę domyślną i nadpisujemy pozycje zapisane przez użytkownika.
            defaults.onEach { c ->
                byId[c.id]?.let {
                    c.x = it.getDouble("x").toFloat()
                    c.y = it.getDouble("y").toFloat()
                    c.r = it.getDouble("r").toFloat()
                }
            }
        } catch (_: Exception) {
            defaults
        }
    }

    fun save(prefs: SharedPreferences, controls: List<GameControl>) {
        val arr = JSONArray()
        for (c in controls) {
            arr.put(JSONObject().put("id", c.id).put("x", c.x.toDouble()).put("y", c.y.toDouble()).put("r", c.r.toDouble()))
        }
        prefs.edit().putString(PREF_LAYOUT, arr.toString()).apply()
    }

    fun reset(prefs: SharedPreferences) {
        prefs.edit().remove(PREF_LAYOUT).apply()
    }
}
