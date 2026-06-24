package com.uip.oneapp.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert die reine Auto-Erkennungs-Logik des Dual-Modus (Welle 2) ab: Board/Modell führt,
 * /dev/ttyS5 bestätigt, /dev/video0 ist informativ aber nie entscheidend. Alle Android-/
 * Dateisystem-Signale sind über die Detector-Lambdas gemockt — kein echtes Build/File nötig.
 */
class HardwareModeDetectorTest {

    // ===== Reine decide()-Wahrheitstabelle =====

    @Test
    fun directWhenBoardAndSerial() {
        assertEquals(
            HardwareMode.DIRECT,
            HardwareModeDetector.decide(isOneBoard = true, serialAccessible = true, videoAccessible = true).mode
        )
    }

    @Test
    fun directEvenWhenVideoNodeAbsent() {
        // Kamerakopf abgezogen → /dev/video0 fehlt, ttyS5 (fest verbaut) bleibt da.
        // Auf der ONE muss das DIRECT bleiben — kein Regress.
        assertEquals(
            HardwareMode.DIRECT,
            HardwareModeDetector.decide(isOneBoard = true, serialAccessible = true, videoAccessible = false).mode
        )
    }

    @Test
    fun wifiWhenBoardButSerialNotAccessible() {
        // ONE-Marker, aber ttyS5 nicht zugänglich (z. B. chmod fehlgeschlagen) → keine
        // bestätigte lokale Hardware → WiFi.
        assertEquals(
            HardwareMode.WIFI,
            HardwareModeDetector.decide(isOneBoard = true, serialAccessible = false, videoAccessible = false).mode
        )
    }

    @Test
    fun wifiWhenSerialButNotOneBoard() {
        assertEquals(
            HardwareMode.WIFI,
            HardwareModeDetector.decide(isOneBoard = false, serialAccessible = true, videoAccessible = true).mode
        )
    }

    @Test
    fun video0AloneIsNotProof() {
        // Tablet-Frontkamera: /dev/video0 lesbar, aber kein ONE-Board und kein ttyS5.
        // Darf NIE DIRECT ergeben (Fehlalarm-Schutz).
        assertEquals(
            HardwareMode.WIFI,
            HardwareModeDetector.decide(isOneBoard = false, serialAccessible = false, videoAccessible = true).mode
        )
    }

    @Test
    fun wifiWhenNoSignalsAtAll() {
        assertEquals(
            HardwareMode.WIFI,
            HardwareModeDetector.decide(isOneBoard = false, serialAccessible = false, videoAccessible = false).mode
        )
    }

    @Test
    fun video0DoesNotChangeModeForAnyBoardSerialCombo() {
        // video0 ist informativ: bei sonst gleichen Signalen ändert es den Modus nie.
        for (board in listOf(true, false)) {
            for (serial in listOf(true, false)) {
                val withVideo = HardwareModeDetector.decide(board, serial, videoAccessible = true).mode
                val withoutVideo = HardwareModeDetector.decide(board, serial, videoAccessible = false).mode
                assertEquals("board=$board serial=$serial", withoutVideo, withVideo)
            }
        }
    }

    @Test
    fun decisionCarriesRawSignalsAndReason() {
        val d = HardwareModeDetector.decide(isOneBoard = true, serialAccessible = true, videoAccessible = false)
        assertTrue(d.isOneBoard)
        assertTrue(d.serialAccessible)
        assertFalse(d.videoAccessible)
        assertTrue(d.reason, d.reason.contains("mode=DIRECT"))
        assertTrue(d.reason, d.reason.contains("ttyS5=true"))
        assertTrue(d.reason, d.reason.contains("video0=false"))
    }

    // ===== Board/Modell-Erkennung =====

    @Test
    fun oneBoardModelMatchesKnownMarkers() {
        assertTrue(HardwareModeDetector.isOneBoardModel(model = "rk3588_s", board = "anything"))
        assertTrue(HardwareModeDetector.isOneBoardModel(model = "anything", board = "rk30sdk"))
        assertTrue(HardwareModeDetector.isOneBoardModel(model = "RK3588_S", board = null)) // case-insensitiv
        assertTrue(HardwareModeDetector.isOneBoardModel(model = " rk3588_s ", board = null)) // getrimmt
    }

    @Test
    fun oneBoardModelRejectsTabletAndNulls() {
        assertFalse(HardwareModeDetector.isOneBoardModel(model = "SM-T870", board = "exynos9810"))
        assertFalse(HardwareModeDetector.isOneBoardModel(model = null, board = null))
        assertFalse(HardwareModeDetector.isOneBoardModel(model = "", board = ""))
    }

    // ===== Detector mit gemockten Signal-Lambdas =====

    @Test
    fun detectorOnOneHardwareYieldsDirect() {
        val detector = HardwareModeDetector(
            isOneBoard = { true },
            serialAccessible = { true },
            videoAccessible = { true },
        )
        assertEquals(HardwareMode.DIRECT, detector.detect())
    }

    @Test
    fun detectorOnTabletYieldsWifi() {
        val detector = HardwareModeDetector(
            isOneBoard = { false },
            serialAccessible = { false },
            videoAccessible = { true }, // Frontkamera
        )
        assertEquals(HardwareMode.WIFI, detector.detect())
    }
}
