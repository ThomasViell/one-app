package com.uip.oneapp.network.internal

/**
 * JNI-Bridge für das serielle Öffnen von /dev/ttyS5.
 *
 * Warum JNI statt FileOutputStream + stty:
 *   stty öffnet das TTY, setzt cfmakeraw(), und schließt wieder. Wenn
 *   der Treiber termios bei Last-Close zurücksetzt, öffnet FileOutputStream
 *   danach mit Default-Einstellungen (OPOST aktiv) — dann übersetzt der Kernel
 *   0x0D (das LL-Byte in jedem TX-Frame) per ONLCR in 0x0D 0x0A, was den
 *   XOR-Frame für die Hardware unlesbar macht.
 *
 *   openSerial() konfiguriert cfmakeraw() + tcsetattr() auf demselben fd der
 *   danach offen bleibt — identisch dem Bominwell SerialHelper.kt-Ansatz
 *   (com.naz.serial.port.SerialPort JNI).
 *
 * Bibliothek: libv4l2bridge.so (wird bereits von V4L2Camera geladen).
 */
internal object SerialPortNative {
    init {
        System.loadLibrary("v4l2bridge")
    }

    /**
     * Öffnet den seriellen Port mit O_RDWR|O_NOCTTY, ruft cfmakeraw() und
     * tcsetattr() auf dem selben fd auf. Gibt den fd zurück, -1 bei Fehler.
     * Aufrufer muss den fd über ParcelFileDescriptor.adoptFd() schließen.
     */
    @JvmStatic
    external fun openSerial(path: String, baudRate: Int): Int
}
