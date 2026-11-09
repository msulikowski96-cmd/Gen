package com.btpad

/**
 * Opis urządzenia (deskryptor HID): jeden standardowy pad w układzie Xbox.
 *
 * Celowo tylko pad – mały (poniżej 100 B) i bez dodatków, żeby każdy telefon
 * (i GeForce NOW) rozpoznał go jak zwykły pad Bluetooth.
 *
 * Raport (9 bajtów):
 *   0-1  przyciski (16 bitów, kolejność jak w Androidzie: A, B, –, X, Y, –, LB, RB, LT, RT,
 *        Widok, Menu, Xbox, L3, R3)
 *   2    D-pad (0 = góra, dalej zgodnie z zegarem co 45°, 8 = puszczony)
 *   3-6  lewa gałka X, Y, prawa gałka X (Z), Y (Rz): -127..127
 *   7-8  LT (Brake), RT (Accelerator): 0..255
 */
object Hid {
    const val REPORT_ID = 1
    const val REPORT_SIZE = 9
    const val HAT_NONE = 8

    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 3
    const val Y = 1 shl 4
    const val LB = 1 shl 6
    const val RB = 1 shl 7
    const val LT = 1 shl 8
    const val RT = 1 shl 9
    const val VIEW = 1 shl 10
    const val MENU = 1 shl 11
    const val HOME = 1 shl 12
    const val L3 = 1 shl 13
    const val R3 = 1 shl 14

    val DESCRIPTOR: ByteArray = intArrayOf(
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x05,       // Usage (Gamepad)
        0xA1, 0x01,       // Collection (Application)
        0x85, REPORT_ID,  //   Report ID
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
        0x25, 0x07,       //   Logical Maximum (7)
        0x35, 0x00,       //   Physical Minimum (0)
        0x46, 0x3B, 0x01, //   Physical Maximum (315)
        0x65, 0x14,       //   Unit (Degrees)
        0x75, 0x04,       //   Report Size (4)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x42,       //   Input (Data, Var, Abs, Null State)
        0x65, 0x00,       //   Unit (None)
        0x45, 0x00,       //   Physical Maximum (0)
        0x81, 0x03,       //   Input (Const) — dopełnienie 4 bity
        0x09, 0x30,       //   Usage (X)  — lewa gałka
        0x09, 0x31,       //   Usage (Y)
        0x09, 0x32,       //   Usage (Z)  — prawa gałka
        0x09, 0x35,       //   Usage (Rz)
        0x15, 0x81,       //   Logical Minimum (-127)
        0x25, 0x7F,       //   Logical Maximum (127)
        0x75, 0x08,       //   Report Size (8)
        0x95, 0x04,       //   Report Count (4)
        0x81, 0x02,       //   Input (Data, Var, Abs)
        0x05, 0x02,       //   Usage Page (Simulation Controls)
        0x09, 0xC5,       //   Usage (Brake) — LT
        0x09, 0xC4,       //   Usage (Accelerator) — RT
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x00, //   Logical Maximum (255)
        0x95, 0x02,       //   Report Count (2)
        0x81, 0x02,       //   Input (Data, Var, Abs)
        0xC0,             // End Collection
    ).map { it.toByte() }.toByteArray()

    /** Stan pada do wysłania. */
    data class PadState(
        val buttons: Int = 0,
        val hat: Int = HAT_NONE,
        val lx: Int = 0,
        val ly: Int = 0,
        val rx: Int = 0,
        val ry: Int = 0,
        val lt: Int = 0,
        val rt: Int = 0,
    ) {
        fun toReport(): ByteArray = byteArrayOf(
            (buttons and 0xFF).toByte(),
            ((buttons shr 8) and 0xFF).toByte(),
            (hat and 0x0F).toByte(),
            lx.coerceIn(-127, 127).toByte(),
            ly.coerceIn(-127, 127).toByte(),
            rx.coerceIn(-127, 127).toByte(),
            ry.coerceIn(-127, 127).toByte(),
            lt.coerceIn(0, 255).toByte(),
            rt.coerceIn(0, 255).toByte(),
        )
    }
}
