package com.uip.oneapp.network

import android.os.Build
import java.io.File

/**
 * Laufzeit-Transport der App (Dual-Modus, Welle 2):
 *   - [DIRECT] — App läuft auf der ONE-Hardware selbst (Serial /dev/ttyS5 + V4L2 /dev/video0).
 *   - [WIFI]   — App läuft auf einem Tablet und spricht die ONE als Client über WLAN an.
 */
enum class HardwareMode { DIRECT, WIFI }

/**
 * Begründete Auto-Erkennungs-Entscheidung — trägt die Rohsignale für Logging/Diagnose
 * mit, ohne dass der Aufrufer die Auswahl-Logik kennen muss.
 */
data class HardwareModeDecision(
    val mode: HardwareMode,
    val isOneBoard: Boolean,
    val serialAccessible: Boolean,
    val videoAccessible: Boolean,
) {
    /** Kompakte, log-freundliche Begründung (keine PII, nur Hardware-Signale). */
    val reason: String
        get() = "mode=$mode board=$isOneBoard ttyS5=$serialAccessible video0=$videoAccessible"
}

/**
 * Erkennt automatisch, ob die App direkt auf der ONE-Hardware oder als WiFi-Client auf
 * einem Tablet läuft (Dual-Modus, Welle 2). Die Android-/Dateisystem-Zugriffe sind über
 * Lambdas gekapselt, damit die Entscheidungslogik ([decide]) rein und ohne Android testbar
 * bleibt.
 *
 * Entscheidungslogik:
 *  - **Primärsignal (führend):** `Build.MODEL == "rk3588_s"` bzw. `ro.product.board == "rk30sdk"`
 *    (= `Build.BOARD`). Ein Tablet meldet diese RK3588-Marker nie.
 *  - **Bestätigung:** `/dev/ttyS5` vorhanden + lesbar. Das ist die fest verbaute SoC-UART der
 *    ONE — auf normalen Tablets nicht zugänglich.
 *  - `/dev/video0` ist **kein** alleiniger Beweis: die Tablet-Frontkamera ist ebenfalls ein
 *    V4L2-Knoten, und auf der ONE kann der Knoten fehlen, wenn der Kamerakopf (MS2109-USB-
 *    Capture) abgezogen ist. Daher nur informativ (Logging), **nie entscheidend**.
 *
 * Ergebnis: [HardwareMode.DIRECT] wenn ONE-Hardware erkannt (Board **und** ttyS5), sonst
 * [HardwareMode.WIFI].
 *
 * Wird in [com.uip.oneapp.di.appModule] zur Auswahl des [HardwareService] genutzt; der
 * Aufruf erfolgt nach [com.uip.oneapp.bootstrap.DeviceFilePermissionBootstrap.grantIfNeeded],
 * sodass der chmod auf /dev/ttyS5 bereits angewandt ist.
 */
class HardwareModeDetector(
    private val isOneBoard: () -> Boolean = { isOneBoardModel(Build.MODEL, Build.BOARD) },
    private val serialAccessible: () -> Boolean = { isNodeReadable(SERIAL_PATH) },
    private val videoAccessible: () -> Boolean = { isNodeReadable(VIDEO_PATH) },
) {
    /** Volle Entscheidung inkl. Rohsignale (für Logging in der DI-Auswahl). */
    fun detectVerbose(): HardwareModeDecision =
        decide(isOneBoard(), serialAccessible(), videoAccessible())

    /** Nur der erkannte Modus. */
    fun detect(): HardwareMode = detectVerbose().mode

    companion object {
        const val SERIAL_PATH = "/dev/ttyS5"
        const val VIDEO_PATH = "/dev/video0"

        // Bekannte RK3588-Marker der ONE-Hardware (siehe docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md).
        private const val ONE_MODEL = "rk3588_s"
        private const val ONE_BOARD = "rk30sdk"

        /**
         * Reine Entscheidungslogik — ohne Android, voll testbar. Board/Modell führt, ttyS5
         * bestätigt; video0 ist nur informativ und beeinflusst den Modus nicht.
         */
        fun decide(
            isOneBoard: Boolean,
            serialAccessible: Boolean,
            videoAccessible: Boolean,
        ): HardwareModeDecision {
            val mode = if (isOneBoard && serialAccessible) HardwareMode.DIRECT else HardwareMode.WIFI
            return HardwareModeDecision(
                mode = mode,
                isOneBoard = isOneBoard,
                serialAccessible = serialAccessible,
                videoAccessible = videoAccessible,
            )
        }

        /** True, wenn Modell oder Board die ONE-RK3588-Marker tragen (case-insensitiv, getrimmt). */
        fun isOneBoardModel(model: String?, board: String?): Boolean =
            model?.trim().equals(ONE_MODEL, ignoreCase = true) ||
                board?.trim().equals(ONE_BOARD, ignoreCase = true)

        /** Geräteknoten vorhanden und lesbar. */
        private fun isNodeReadable(path: String): Boolean =
            File(path).let { it.exists() && it.canRead() }
    }
}
