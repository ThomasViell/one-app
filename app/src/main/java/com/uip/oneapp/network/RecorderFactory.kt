package com.uip.oneapp.network

import android.content.Context

object RecorderFactory {
    // Runde 5 (P-1): der Bus ist Pflicht — die Ausstiegssperre darf nicht still wegfallen,
    // weil ein Aufrufer ihn vergisst.
    fun create(context: Context, arbiter: CameraEncoderArbiter, bus: RecordingStateBus): Recorder =
        FallbackRecorder(context, arbiter, bus)
}
