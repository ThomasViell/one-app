package com.uip.oneapp.network

import android.content.Context

/**
 * Wählt den Aufnahme-Recorder anhand [FeatureFlags.useHardwareRecorder] (Welle 5).
 *
 * - true (Default) → [HardwareBitmapRecorder] (HW-Encoder, echte VFR-PTS, Journal-Absturzsicherheit),
 * - false → [LocalBitmapRecorder] (Rückfallebene, JPEG/FIFO/libx264 unverändert).
 *
 * Der Flag wird **einmal beim Erzeugen** gelesen (die UI hält die Instanz für die Screen-Lebensdauer)
 * → ein Flip zur Laufzeit wirkt erst auf die nächste Aufnahme, kann keine aktive verwaisen.
 */
object RecorderFactory {
    fun create(context: Context, arbiter: CameraEncoderArbiter): Recorder =
        if (FeatureFlags.useHardwareRecorder) HardwareBitmapRecorder(context, arbiter)
        else LocalBitmapRecorder(context)
}
