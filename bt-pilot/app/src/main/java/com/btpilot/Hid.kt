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

    val DESCRIPTOR: ByteArray = intArrayOf(
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
    ).map { it.toByte() }.toByteArray()
}
