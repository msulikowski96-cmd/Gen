package com.btpilot

/**
 * Deskryptor HID: jedno urządzenie z trzema raportami.
 *  - ID 1: klawiatura (8 bajtów: modyfikatory, zarezerwowany, 6 klawiszy)
 *  - ID 2: mysz (5 bajtów: przyciski, X, Y, kółko, przewijanie poziome)
 *  - ID 3: klawisze multimedialne / systemowe (2 bajty: kod "consumer")
 */
object Hid {
    const val ID_KEYBOARD = 1
    const val ID_MOUSE = 2
    const val ID_CONSUMER = 3

    const val KEYBOARD_REPORT_SIZE = 8
    const val MOUSE_REPORT_SIZE = 5
    const val CONSUMER_REPORT_SIZE = 2

    // Ekran dotykowy (tryb gry): do 5 palców naraz.
    const val ID_TOUCH = 4
    const val ID_TOUCH_MAX = 5
    const val ID_WIN8_BLOB = 6
    const val TOUCH_CONTACTS = 5
    const val TOUCH_CONTACT_SIZE = 6 // flagi, id, X (2 bajty), Y (2 bajty)
    const val TOUCH_REPORT_SIZE = TOUCH_CONTACTS * TOUCH_CONTACT_SIZE + 1
    const val TOUCH_MAX_COORD = 32767
    const val WIN8_BLOB_SIZE = 256

    // Pad w układzie Xbox: 16 przycisków, D-pad, 2 gałki, 2 analogowe spusty.
    const val ID_GAMEPAD = 7
    const val GAMEPAD_REPORT_SIZE = 9
    const val HAT_NONE = 8

    // Kolejność przycisków zgodna ze standardem Linux/Android (BTN_SOUTH…BTN_THUMBR).
    const val PAD_A = 1 shl 0
    const val PAD_B = 1 shl 1
    const val PAD_X = 1 shl 3
    const val PAD_Y = 1 shl 4
    const val PAD_LB = 1 shl 6
    const val PAD_RB = 1 shl 7
    const val PAD_LT = 1 shl 8
    const val PAD_RT = 1 shl 9
    const val PAD_VIEW = 1 shl 10
    const val PAD_MENU = 1 shl 11
    const val PAD_HOME = 1 shl 12
    const val PAD_L3 = 1 shl 13
    const val PAD_R3 = 1 shl 14

    const val BUTTON_LEFT = 0x01
    const val BUTTON_RIGHT = 0x02
    const val BUTTON_MIDDLE = 0x04

    // Kody "consumer" (strona 0x0C). Android mapuje je na Wstecz, Home, głośność itd.
    const val CC_HOME = 0x0223
    const val CC_BACK = 0x0224
    const val CC_VOLUME_UP = 0x00E9
    const val CC_VOLUME_DOWN = 0x00EA
    const val CC_MUTE = 0x00E2
    const val CC_PLAY_PAUSE = 0x00CD
    const val CC_NEXT = 0x00B5
    const val CC_PREVIOUS = 0x00B6
    const val CC_BRIGHTNESS_UP = 0x006F
    const val CC_BRIGHTNESS_DOWN = 0x0070

    /** Pełny deskryptor; [withTouch] dodaje ekran dotykowy do trybu gry. */
    fun descriptor(withTouch: Boolean, withGamepad: Boolean): ByteArray {
        var d = BASE
        if (withGamepad) d += GAMEPAD
        if (withTouch) d += touchscreen()
        return d.map { it.toByte() }.toByteArray()
    }

    private val GAMEPAD: IntArray = intArrayOf(
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x05,       // Usage (Gamepad)
        0xA1, 0x01,       // Collection (Application)
        0x85, ID_GAMEPAD, //   Report ID
        0x05, 0x09,       //   Usage Page (Buttons)
        0x19, 0x01,       //   Usage Minimum (1)
        0x29, 0x10,       //   Usage Maximum (16)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x01,       //   Logical Maximum (1)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x10,       //   Report Count (16)
        0x81, 0x02,       //   Input (Data, Var, Abs) — przyciski
        0x05, 0x01,       //   Usage Page (Generic Desktop)
        0x09, 0x39,       //   Usage (Hat Switch) — D-pad
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x07,       //   Logical Maximum (7)
        0x35, 0x00,       //   Physical Minimum (0)
        0x46, 0x3B, 0x01, //   Physical Maximum (315)
        0x65, 0x14,       //   Unit (Degrees)
        0x75, 0x04,       //   Report Size (4)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x42,       //   Input (Data, Var, Abs, Null State)
        0x65, 0x00,       //   Unit (None)
        0x45, 0x00,       //   Physical Maximum (0)
        0x75, 0x04,       //   Report Size (4)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x03,       //   Input (Const) — dopełnienie
        0x09, 0x01,       //   Usage (Pointer)
        0xA1, 0x00,       //   Collection (Physical)
        0x09, 0x30,       //     Usage (X)  — lewa gałka
        0x09, 0x31,       //     Usage (Y)
        0x09, 0x32,       //     Usage (Z)  — prawa gałka
        0x09, 0x35,       //     Usage (Rz)
        0x15, 0x81,       //     Logical Minimum (-127)
        0x25, 0x7F,       //     Logical Maximum (127)
        0x75, 0x08,       //     Report Size (8)
        0x95, 0x04,       //     Report Count (4)
        0x81, 0x02,       //     Input (Data, Var, Abs)
        0xC0,             //   End Collection
        0x05, 0x02,       //   Usage Page (Simulation Controls)
        0x09, 0xC5,       //   Usage (Brake) — LT
        0x09, 0xC4,       //   Usage (Accelerator) — RT
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x00, //   Logical Maximum (255)
        0x75, 0x08,       //   Report Size (8)
        0x95, 0x02,       //   Report Count (2)
        0x81, 0x02,       //   Input (Data, Var, Abs)
        0xC0,             // End Collection
    )

    private val BASE: IntArray = intArrayOf(
        // ---------- Klawiatura ----------
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x06,       // Usage (Keyboard)
        0xA1, 0x01,       // Collection (Application)
        0x85, ID_KEYBOARD, //   Report ID
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0,       //   Usage Minimum (Left Control)
        0x29, 0xE7,       //   Usage Maximum (Right GUI)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x01,       //   Logical Maximum (1)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x08,       //   Report Count (8)
        0x81, 0x02,       //   Input (Data, Var, Abs) — modyfikatory
        0x95, 0x01,       //   Report Count (1)
        0x75, 0x08,       //   Report Size (8)
        0x81, 0x01,       //   Input (Const) — bajt zarezerwowany
        0x95, 0x05,       //   Report Count (5)
        0x75, 0x01,       //   Report Size (1)
        0x05, 0x08,       //   Usage Page (LEDs)
        0x19, 0x01,       //   Usage Minimum (Num Lock)
        0x29, 0x05,       //   Usage Maximum (Kana)
        0x91, 0x02,       //   Output (Data, Var, Abs) — diody
        0x95, 0x01,       //   Report Count (1)
        0x75, 0x03,       //   Report Size (3)
        0x91, 0x01,       //   Output (Const) — dopełnienie
        0x95, 0x06,       //   Report Count (6)
        0x75, 0x08,       //   Report Size (8)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x73,       //   Logical Maximum (115)
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,       //   Usage Minimum (0)
        0x29, 0x73,       //   Usage Maximum (115)
        0x81, 0x00,       //   Input (Data, Array) — wciśnięte klawisze
        0xC0,             // End Collection

        // ---------- Mysz ----------
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x02,       // Usage (Mouse)
        0xA1, 0x01,       // Collection (Application)
        0x85, ID_MOUSE,   //   Report ID
        0x09, 0x01,       //   Usage (Pointer)
        0xA1, 0x00,       //   Collection (Physical)
        0x05, 0x09,       //     Usage Page (Buttons)
        0x19, 0x01,       //     Usage Minimum (1)
        0x29, 0x05,       //     Usage Maximum (5)
        0x15, 0x00,       //     Logical Minimum (0)
        0x25, 0x01,       //     Logical Maximum (1)
        0x95, 0x05,       //     Report Count (5)
        0x75, 0x01,       //     Report Size (1)
        0x81, 0x02,       //     Input (Data, Var, Abs)
        0x95, 0x01,       //     Report Count (1)
        0x75, 0x03,       //     Report Size (3)
        0x81, 0x01,       //     Input (Const)
        0x05, 0x01,       //     Usage Page (Generic Desktop)
        0x09, 0x30,       //     Usage (X)
        0x09, 0x31,       //     Usage (Y)
        0x09, 0x38,       //     Usage (Wheel)
        0x15, 0x81,       //     Logical Minimum (-127)
        0x25, 0x7F,       //     Logical Maximum (127)
        0x75, 0x08,       //     Report Size (8)
        0x95, 0x03,       //     Report Count (3)
        0x81, 0x06,       //     Input (Data, Var, Rel)
        0x05, 0x0C,       //     Usage Page (Consumer)
        0x0A, 0x38, 0x02, //     Usage (AC Pan) — przewijanie poziome
        0x15, 0x81,       //     Logical Minimum (-127)
        0x25, 0x7F,       //     Logical Maximum (127)
        0x75, 0x08,       //     Report Size (8)
        0x95, 0x01,       //     Report Count (1)
        0x81, 0x06,       //     Input (Data, Var, Rel)
        0xC0,             //   End Collection
        0xC0,             // End Collection

        // ---------- Klawisze multimedialne / systemowe ----------
        0x05, 0x0C,       // Usage Page (Consumer)
        0x09, 0x01,       // Usage (Consumer Control)
        0xA1, 0x01,       // Collection (Application)
        0x85, ID_CONSUMER, //  Report ID
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x03, //   Logical Maximum (1023)
        0x19, 0x00,       //   Usage Minimum (0)
        0x2A, 0xFF, 0x03, //   Usage Maximum (1023)
        0x75, 0x10,       //   Report Size (16)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x00,       //   Input (Data, Array)
        0xC0              // End Collection
    )

    /**
     * Ekran dotykowy z wieloma palcami (tryb gry). Blok "Windows 8" (raport 6) sprawia,
     * że sterownik hid-multitouch w Androidzie obsługuje też pozostałe części urządzenia
     * (mysz, klawiaturę) zamiast je pomijać.
     */
    private fun touchscreen(): IntArray {
        val d = mutableListOf(
            0x05, 0x0D,       // Usage Page (Digitizer)
            0x09, 0x04,       // Usage (Touch Screen)
            0xA1, 0x01,       // Collection (Application)
            0x85, ID_TOUCH,   //   Report ID
        )
        repeat(TOUCH_CONTACTS) {
            d += listOf(
                0x09, 0x22,       //   Usage (Finger)
                0xA1, 0x02,       //   Collection (Logical)
                0x09, 0x42,       //     Usage (Tip Switch)
                0x09, 0x32,       //     Usage (In Range)
                0x15, 0x00,       //     Logical Minimum (0)
                0x25, 0x01,       //     Logical Maximum (1)
                0x75, 0x01,       //     Report Size (1)
                0x95, 0x02,       //     Report Count (2)
                0x81, 0x02,       //     Input (Data, Var, Abs)
                0x95, 0x06,       //     Report Count (6)
                0x81, 0x03,       //     Input (Const) — dopełnienie
                0x09, 0x51,       //     Usage (Contact Identifier)
                0x25, 0x0F,       //     Logical Maximum (15)
                0x75, 0x08,       //     Report Size (8)
                0x95, 0x01,       //     Report Count (1)
                0x81, 0x02,       //     Input (Data, Var, Abs)
                0x05, 0x01,       //     Usage Page (Generic Desktop)
                0x26, 0xFF, 0x7F, //     Logical Maximum (32767)
                0x75, 0x10,       //     Report Size (16)
                0x95, 0x01,       //     Report Count (1)
                0x09, 0x30,       //     Usage (X)
                0x81, 0x02,       //     Input (Data, Var, Abs)
                0x09, 0x31,       //     Usage (Y)
                0x81, 0x02,       //     Input (Data, Var, Abs)
                0x05, 0x0D,       //     Usage Page (Digitizer)
                0xC0,             //   End Collection
            )
        }
        d += listOf(
            0x09, 0x54,           //   Usage (Contact Count)
            0x25, 0x0F,           //   Logical Maximum (15)
            0x75, 0x08,           //   Report Size (8)
            0x95, 0x01,           //   Report Count (1)
            0x81, 0x02,           //   Input (Data, Var, Abs)
            0x85, ID_TOUCH_MAX,   //   Report ID
            0x09, 0x55,           //   Usage (Contact Count Maximum)
            0xB1, 0x02,           //   Feature (Data, Var, Abs)
            0x06, 0x00, 0xFF,     //   Usage Page (Vendor 0xFF00)
            0x85, ID_WIN8_BLOB,   //   Report ID
            0x09, 0xC5,           //   Usage (0xC5) — blok certyfikatu Windows 8
            0x15, 0x00,           //   Logical Minimum (0)
            0x26, 0xFF, 0x00,     //   Logical Maximum (255)
            0x75, 0x08,           //   Report Size (8)
            0x96, 0x00, 0x01,     //   Report Count (256)
            0xB1, 0x02,           //   Feature (Data, Var, Abs)
            0xC0,                 // End Collection
        )
        return d.toIntArray()
    }
}
