package com.uip.oneapp.export

import android.util.Log
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "VideoOverlayProcessor"

data class OverlayEntry(
    val timestampSec: Int,
    val text: String
)

/**
 * Project info lines shown for the first 5 seconds of the video.
 */
data class ProjectOverlayInfo(
    val projectNumber: String = "",
    val auftraggeber: String = "",
    val standort: String = "",
    val inspektor: String = "",
    val datum: String = "",
    val material: String = "",
    val durchmesser: String = "",
    val startpunkt: String = "",
    val endpunkt: String = ""
)

object VideoOverlayProcessor {

    /**
     * Generate ASS subtitle content from overlay entries.
     * Uses Android system font names (DroidSansMono, Roboto).
     * - Project info displayed top-center for the first 5 seconds (multiple lines)
     * - Meter + time displayed bottom-left permanently (each entry covers 1 second)
     */
    fun generateAssContent(
        entries: List<OverlayEntry>,
        projectInfo: ProjectOverlayInfo,
        videoWidth: Int = 1280,
        videoHeight: Int = 720
    ): String {
        val sb = StringBuilder()

        // ASS header
        sb.appendLine("[Script Info]")
        sb.appendLine("ScriptType: v4.00+")
        sb.appendLine("PlayResX: $videoWidth")
        sb.appendLine("PlayResY: $videoHeight")
        sb.appendLine("WrapStyle: 0")
        sb.appendLine()

        // Styles - using Android system font names
        sb.appendLine("[V4+ Styles]")
        sb.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        // Bottom-left meter overlay: Alignment 1 = bottom-left, BorderStyle 3 = opaque box
        sb.appendLine("Style: Meter,Roboto,28,&H00FFFFFF,&H000000FF,&H00000000,&HB0000000,1,0,0,0,100,100,0,0,3,2,0,1,20,20,20,1")
        // Top-center project info: Alignment 8 = top-center, multiline
        sb.appendLine("Style: ProjectTitle,Roboto,32,&H00FFFFFF,&H000000FF,&H00000000,&HB0000000,1,0,0,0,100,100,0,0,3,2,0,8,20,20,20,1")
        sb.appendLine("Style: ProjectInfo,Roboto,24,&H00FFFFFF,&H000000FF,&H00000000,&HB0000000,0,0,0,0,100,100,0,0,3,2,0,8,20,20,60,1")
        sb.appendLine()

        // Events
        sb.appendLine("[Events]")
        sb.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")

        // Project info (first 5 seconds) - title line
        val pi = projectInfo
        if (pi.projectNumber.isNotEmpty()) {
            sb.appendLine("Dialogue: 1,${formatAssTime(0)},${formatAssTime(5)},ProjectTitle,,0,0,0,,${pi.projectNumber}")
        }

        // Project info detail lines (first 5 seconds) - use \N for line breaks in ASS
        val infoLines = mutableListOf<String>()
        if (pi.auftraggeber.isNotEmpty()) infoLines.add("AG: ${pi.auftraggeber}")
        if (pi.standort.isNotEmpty()) infoLines.add("Standort: ${pi.standort}")
        if (pi.startpunkt.isNotEmpty() || pi.endpunkt.isNotEmpty()) {
            val strecke = listOfNotNull(
                pi.startpunkt.ifEmpty { null },
                pi.endpunkt.ifEmpty { null }
            ).joinToString(" - ")
            if (strecke.isNotEmpty()) infoLines.add("Strecke: $strecke")
        }
        if (pi.material.isNotEmpty() || pi.durchmesser.isNotEmpty()) {
            val rohr = listOfNotNull(
                pi.material.ifEmpty { null },
                pi.durchmesser.ifEmpty { null }?.let { "DN $it" }
            ).joinToString(" | ")
            if (rohr.isNotEmpty()) infoLines.add(rohr)
        }
        if (pi.inspektor.isNotEmpty()) infoLines.add("Inspektor: ${pi.inspektor}")
        if (pi.datum.isNotEmpty()) infoLines.add("Datum: ${pi.datum}")

        if (infoLines.isNotEmpty()) {
            val multilineText = infoLines.joinToString("\\N")
            sb.appendLine("Dialogue: 1,${formatAssTime(0)},${formatAssTime(5)},ProjectInfo,,0,0,0,,$multilineText")
        }

        // Meter + time entries (each entry lasts 1 second, covering full duration)
        for (entry in entries) {
            val start = formatAssTime(entry.timestampSec)
            val end = formatAssTime(entry.timestampSec + 1)
            sb.appendLine("Dialogue: 0,$start,$end,Meter,,0,0,0,,${entry.text}")
        }

        return sb.toString()
    }

    /**
     * Burn ASS subtitles into a video file using FFmpegKit.
     * Uses the 'subtitles' filter with fontsdir pointing to Android system fonts.
     *
     * @return the output mp4 File on success, null on failure
     */
    suspend fun burnOverlay(
        inputFile: File,
        outputFile: File,
        entries: List<OverlayEntry>,
        projectInfo: ProjectOverlayInfo,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        // Write ASS file next to input
        val assFile = File(inputFile.parentFile, "${inputFile.nameWithoutExtension}_overlay.ass")
        val assContent = generateAssContent(entries, projectInfo)
        assFile.writeText(assContent)
        Log.d(TAG, "ASS file written: ${assFile.absolutePath} (${assFile.length()} bytes)")
        Log.d(TAG, "ASS content preview:\n${assContent.take(800)}")

        // Get video duration for progress calculation
        val durationMs = getVideoDurationMs(inputFile)
        Log.d(TAG, "Input video duration: ${durationMs}ms, entries: ${entries.size}")

        val inputPath = inputFile.absolutePath
        val outputPath = outputFile.absolutePath

        // Escape the ASS path for FFmpeg filter
        val assPathEscaped = assFile.absolutePath
            .replace("\\", "/")
            .replace(":", "\\:")
            .replace("'", "\\'")

        // Use subtitles filter with fontsdir for Android system fonts
        val command = "-i $inputPath -vf subtitles='$assPathEscaped':fontsdir=/system/fonts -c:v libx264 -preset fast -crf 23 -c:a copy -y $outputPath"
        Log.d(TAG, "FFmpeg command: $command")

        // Enable statistics for progress tracking
        FFmpegKitConfig.enableStatisticsCallback { statistics ->
            if (durationMs > 0) {
                val progress = (statistics.time.toFloat() / durationMs).coerceIn(0f, 1f)
                onProgress(progress)
            }
        }

        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode

        // Clean up ASS file
        assFile.delete()

        if (ReturnCode.isSuccess(returnCode)) {
            Log.d(TAG, "FFmpeg burn-in successful: ${outputFile.name} (${outputFile.length()} bytes)")
            onProgress(1f)
            outputFile
        } else {
            val logs = session.allLogsAsString
            Log.e(TAG, "FFmpeg failed with rc=${returnCode?.value}: ${logs.takeLast(2000)}")
            if (outputFile.exists()) outputFile.delete()
            null
        }
    }

    private fun getVideoDurationMs(file: File): Long {
        val session = com.antonkarpenko.ffmpegkit.FFprobeKit.getMediaInformation(file.absolutePath)
        val info = session.mediaInformation
        return info?.duration?.toDoubleOrNull()?.times(1000)?.toLong() ?: 0L
    }

    private fun formatAssTime(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%d:%02d:%02d.00", h, m, s)
    }
}
