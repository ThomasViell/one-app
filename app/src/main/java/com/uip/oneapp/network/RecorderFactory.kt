package com.uip.oneapp.network

import android.content.Context

object RecorderFactory {
    fun create(context: Context, arbiter: CameraEncoderArbiter): Recorder =
        FallbackRecorder(context, arbiter)
}
