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

    // ── M2: use_hardware_osd unterdrückt das Software-OSD-Burn-in ───────────────

    @Test
    fun hardwareOsd_disablesSoftwareBurnIn() {
        val s = SettingsUiState(osdEnabled = true, useHardwareOsd = true).toOsdSettings()
        assertFalse("Bei Hardware-OSD kein Software-Burn-in", s.enableOsdBurnIn)
    }

    @Test
    fun softwareOsd_enablesBurnIn_whenHardwareOsdOff() {
        val s = SettingsUiState(osdEnabled = true, useHardwareOsd = false).toOsdSettings()
        assertTrue("Ohne Hardware-OSD brennt die App ihr OSD ein", s.enableOsdBurnIn)
    }
}
