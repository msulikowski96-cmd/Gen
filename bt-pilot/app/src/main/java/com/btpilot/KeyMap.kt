package com.btpilot

/**
 * Zamiana znaków na kody klawiszy HID (układ US).
 * Polskie znaki wysyłane są jako Prawy Alt (AltGr) + litera, czyli tak jak
 * w układzie "Polski (programisty)" — taki układ musi być ustawiony na urządzeniu docelowym.
 */
object KeyMap {
    const val MOD_LCTRL = 0x01
    const val MOD_LSHIFT = 0x02
    const val MOD_LALT = 0x04
    const val MOD_LGUI = 0x08
    const val MOD_RALT = 0x40

    const val KEY_ENTER = 0x28
    const val KEY_ESC = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_F1 = 0x3A
    const val KEY_INSERT = 0x49
    const val KEY_HOME = 0x4A
    const val KEY_PAGE_UP = 0x4B
    const val KEY_DELETE = 0x4C
    const val KEY_END = 0x4D
    const val KEY_PAGE_DOWN = 0x4E
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52

    data class Stroke(val mods: Int, val code: Int)

    private val map = HashMap<Char, Stroke>()

    init {
        for (c in 'a'..'z') {
            val code = 0x04 + (c - 'a')
            map[c] = Stroke(0, code)
            map[c.uppercaseChar()] = Stroke(MOD_LSHIFT, code)
        }
        for (c in '1'..'9') map[c] = Stroke(0, 0x1E + (c - '1'))
        map['0'] = Stroke(0, 0x27)

        "!@#$%^&*()".forEachIndexed { i, c -> map[c] = Stroke(MOD_LSHIFT, 0x1E + i) }

        map['\n'] = Stroke(0, KEY_ENTER)
        map['\t'] = Stroke(0, KEY_TAB)
        map[' '] = Stroke(0, KEY_SPACE)

        val punct = listOf(
            Triple('-', '_', 0x2D),
            Triple('=', '+', 0x2E),
            Triple('[', '{', 0x2F),
            Triple(']', '}', 0x30),
            Triple('\\', '|', 0x31),
            Triple(';', ':', 0x33),
            Triple('\'', '"', 0x34),
            Triple('`', '~', 0x35),
            Triple(',', '<', 0x36),
            Triple('.', '>', 0x37),
            Triple('/', '?', 0x38),
        )
        for ((plain, shifted, code) in punct) {
            map[plain] = Stroke(0, code)
            map[shifted] = Stroke(MOD_LSHIFT, code)
        }

        // Polskie znaki: AltGr + litera (układ "Polski programisty").
        val polish = mapOf(
            'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n',
            'ó' to 'o', 'ś' to 's', 'ź' to 'x', 'ż' to 'z',
        )
        for ((pl, base) in polish) {
            val code = 0x04 + (base - 'a')
            map[pl] = Stroke(MOD_RALT, code)
            map[pl.uppercaseChar()] = Stroke(MOD_RALT or MOD_LSHIFT, code)
        }

        // Typograficzne odpowiedniki, które klawiatura telefonu lubi wstawiać.
        map['’'] = map.getValue('\'')
        map['‘'] = map.getValue('\'')
        map['“'] = map.getValue('"')
        map['”'] = map.getValue('"')
        map['„'] = map.getValue('"')
        map['–'] = map.getValue('-')
        map[' '] = map.getValue(' ')
    }

    fun forChar(c: Char): Stroke? = map[c]
}
