package com.uip.oneapp.network

import com.uip.oneapp.export.OsdSettings
import com.uip.oneapp.ui.screens.settings.SettingsUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AP3 (M1/M2): Belegt, dass die SD/HD- und Hardware-OSD-Schalter einen echten Code-Pfad haben
 * (keine toten Toggles mehr). Die tatsächliche SD-Aufnahme / der Burn-in im MP4 sind on-device
 * zu bestätigen (V-Checks), die Entscheidungslogik ist hier deterministisch getestet.
 */
class RecorderFormatAndOsdDecisionTest {

    // ── M1: SD/HD → Scale-Filter im Recorder-Kommando ──────────────────────────

    @Test
    fun sdResolution_addsScaleFilter_inRtspCommand() {
        val cmd = FfmpegRtspRecorder.buildFullCommand(
            rtspUrl = "rtsp://x", outPath = "/o.mp4",
            l1Path = "/l1", l2Path = "/l2", findingPath = "/f",
            osdSettings = OsdSettings(),
            sdResolution = true
        )
        assertTrue("SD muss auf 720x576 skalieren", cmd.contains("scale=720:576"))
    }

    @Test
    fun hdResolution_hasNoScaleFilter_inRtspCommand() {
        val cmd = FfmpegRtspRecorder.buildFullCommand(
            rtspUrl = "rtsp://x", outPath = "/o.mp4",
            l1Path = "/l1", l2Path = "/l2", findingPath = "/f",
            osdSettings = OsdSettings(),
            sdResolution = false
        )
        assertFalse("HD darf nicht skalieren", cmd.contains("scale="))
    }

    // ── OSD-Einbrennen hängt allein am osd_enabled-Schalter ────────────────────
    // (use_hardware_osd wurde entfernt — CEO-Beschluss 2026-06-07: die ONE rendert
    // kein Kamera-OSD, die App ist die einzige OSD-Quelle.)

    @Test
    fun osdEnabled_enablesBurnIn() {
        val s = SettingsUiState(osdEnabled = true).toOsdSettings()
        assertTrue("osd_enabled muss das Einbrennen aktivieren", s.enableOsdBurnIn)
    }

    @Test
    fun osdDisabled_disablesBurnIn() {
        val s = SettingsUiState(osdEnabled = false).toOsdSettings()
        assertFalse("Ohne osd_enabled kein Einbrennen", s.enableOsdBurnIn)
    }
}
