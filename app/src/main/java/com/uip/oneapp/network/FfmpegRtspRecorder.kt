package com.uip.oneapp.network

import android.content.Context
import android.util.Log
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.uip.oneapp.export.OsdBackground
import com.uip.oneapp.export.OsdColor
import com.uip.oneapp.export.OsdFontSize
import com.uip.oneapp.export.OsdRenderer
import com.uip.oneapp.export.OsdSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val TAG = "FfmpegRtspRecorder"

enum class FfmpegRecordingState { IDLE, RECORDING, ERROR }

/**
 * Records an RTSP stream to MP4 with OSD burned in during encoding via FFmpeg drawtext filter.
 *
 * Architecture: parallel to VLC display session (Variante A — two independent RTSP sessions).
 * OSD text is written to cache files which FFmpegKit reloads each frame via drawtext reload=1,
 * enabling dynamic content (meter value) without frame-level manipulation.
 *
 * R3-PIVOT: applied because RESULT_PHASE_2.md is absent — FFmpegKit live pipeline was never
 * tested on hardware. VLC handles display; this recorder opens a second RTSP connection for
 * recording only.
 */
class FfmpegRtspRecorder(private val context: Context) {

    private val _state = MutableStateFlow(FfmpegRecordingState.IDLE)
    val state: StateFlow<FfmpegRecordingState> = _state.asStateFlow()

    private val line1File: File get() = File(context.cacheDir, "osd_rec_line1.txt")
    private val line2File: File get() = File(context.cacheDir, "osd_rec_line2.txt")

    fun startRecording(
        rtspUrl: String,
        outputFile: File,
        osdSettings: OsdSettings,
        initialLine1: String,
        initialLine2: String
    ) {
        if (_state.value == FfmpegRecordingState.RECORDING) return

        runCatching {
            line1File.writeText(OsdRenderer.asciiSafe(initialLine1))
            line2File.writeText(OsdRenderer.asciiSafe(initialLine2))
        }.onFailure { Log.w(TAG, "OSD text file write failed: ${it.message}") }

        val command = buildFullCommand(
            rtspUrl, outputFile.absolutePath,
            line1File.absolutePath, line2File.absolutePath,
            osdSettings
        )
        Log.d(TAG, "startRecording: $command")

        FFmpegKit.executeAsync(
            command,
            { session ->
                val rc = session.returnCode?.value ?: -1
                Log.d(TAG, "Session ended rc=$rc")
                // rc=255 means cancelled by stopRecording() — treat as clean stop
                _state.value = if (rc == 0 || rc == 255) FfmpegRecordingState.IDLE
                               else FfmpegRecordingState.ERROR
            },
            { log -> Log.v(TAG, log.message?.trim() ?: "") },
            null
        )
        _state.value = FfmpegRecordingState.RECORDING
    }

    /** Call each second during recording to update the dynamic bottom bar (meter value etc.). */
    fun updateOsdLine2(line2: String) {
        if (_state.value == FfmpegRecordingState.RECORDING) {
            runCatching { line2File.writeText(OsdRenderer.asciiSafe(line2)) }
                .onFailure { Log.w(TAG, "OSD line2 update failed: ${it.message}") }
        }
    }

    /**
     * Stops the active recording session. Output is fragmented MP4
     * (see buildFullCommand muxFlags), so the file is already playable —
     * we don't depend on FFmpegKit.cancel() running av_write_trailer().
     */
    fun stopRecording() {
        Log.d(TAG, "stopRecording: cancelling active session")
        FFmpegKit.cancel()
    }

    companion object {

        /** Pure command builder — testable without Android context. */
        internal fun buildFullCommand(
            rtspUrl: String,
            outPath: String,
            l1Path: String,
            l2Path: String,
            osdSettings: OsdSettings
        ): String {
            val videoArgs = if (osdSettings.enableOsdBurnIn) {
                val vf = buildDrawtextFilter(l1Path, l2Path, osdSettings)
                "-vf $vf -c:v libx264 -preset fast -crf 23"
            } else {
                "-c:v libx264 -preset fast -crf 23"
            }
            // Fragmented MP4: each 1-s fragment is self-contained, so the file stays
            // playable even when FFmpegKit.cancel() (or a crash, kill, battery loss)
            // prevents av_write_trailer() from running. The classic +faststart variant
            // produced files with no moov atom → unplayable. See INSTALL_RESULT.md.
            val muxFlags = "-f mp4 -movflags +frag_keyframe+empty_moov+default_base_moof " +
                           "-frag_duration 1000000"
            return "-rtsp_transport tcp -i $rtspUrl $videoArgs -an $muxFlags -y $outPath"
        }

        /** Builds FFmpeg drawtext filter string for two OSD bars. */
        internal fun buildDrawtextFilter(l1Path: String, l2Path: String, osdSettings: OsdSettings): String {
            val fontSizePx = when (osdSettings.fontSize) {
                OsdFontSize.Small  -> 18
                OsdFontSize.Medium -> 22
                OsdFontSize.Large  -> 28
                OsdFontSize.Maxi   -> 36
            }
            val fontColor = when (osdSettings.fontColor) {
                OsdColor.Green  -> "0x64FF64FF"
                OsdColor.White  -> "0xFFFFFFFF"
                OsdColor.Yellow -> "0xFAE164FF"
            }
            val boxAlpha = when (osdSettings.background) {
                OsdBackground.Transparent     -> "00"
                OsdBackground.SemiTransparent -> "80"
                OsdBackground.Solid           -> "D0"
            }
            val boxColor = "0x000000$boxAlpha"
            val s2 = (fontSizePx - 4).coerceAtLeast(12)

            // Escape colons in file paths for FFmpeg filter graph syntax
            val l1Esc = l1Path.replace(":", "\\:")
            val l2Esc = l2Path.replace(":", "\\:")

            return "\"drawtext=textfile='$l1Esc':reload=1:x=8:y=8:" +
                   "fontsize=$fontSizePx:fontcolor=$fontColor:box=1:boxcolor=$boxColor," +
                   "drawtext=textfile='$l2Esc':reload=1:x=8:y=h-${s2 + 8}:" +
                   "fontsize=$s2:fontcolor=0xCCCCCCFF:box=1:boxcolor=$boxColor\""
        }
    }
}
