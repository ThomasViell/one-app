package com.uip.oneapp.util

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import java.io.File

/**
 * Inter (SA-Design) für die OSD-/Burn-in-Pfade bereitstellen.
 *
 * - [osdTypeface]: Typeface für Canvas-Rendering (Live-OSD, Foto-/Frame-Burn-in via OsdRenderer).
 * - [osdFontFile]: echte .ttf-Datei für den FFmpeg-`drawtext`-`fontfile`-Parameter
 *   (FFmpeg braucht einen Dateipfad, keine Android-Resource).
 *
 * Quelle: `assets/fonts/inter_regular.ttf` (Kopie der OFL-Schrift aus res/font).
 * Evidenz-Sicherheit: schlägt das Laden fehl, liefern die Aufrufer einen Fallback
 * (MONOSPACE bzw. Roboto), damit Aufnahme/Burn-in NIE abbricht.
 */
object DqFonts {

    private const val TAG = "DqFonts"
    private const val ASSET_PATH = "fonts/inter_regular.ttf"
    private const val EXTRACTED_NAME = "osd_inter_regular.ttf"

    @Volatile private var cachedTypeface: Typeface? = null
    @Volatile private var cachedFile: File? = null

    /** Inter-Typeface für Canvas-OSD; null-sicher (Aufrufer fällt auf MONOSPACE zurück). */
    fun osdTypeface(context: Context): Typeface? {
        cachedTypeface?.let { return it }
        return runCatching {
            Typeface.createFromAsset(context.assets, ASSET_PATH)
        }.onFailure { Log.w(TAG, "Inter-Typeface laden fehlgeschlagen: ${it.message}") }
            .getOrNull()
            ?.also { cachedTypeface = it }
    }

    /**
     * Pfad zur Inter-.ttf für FFmpeg. Extrahiert die Asset-Datei einmalig nach filesDir.
     * Liefert null bei Fehler → FfmpegRtspRecorder nutzt dann den Roboto-Fallback.
     */
    fun osdFontFile(context: Context): File? {
        cachedFile?.let { if (it.exists()) return it }
        return runCatching {
            val out = File(context.filesDir, EXTRACTED_NAME)
            if (!out.exists() || out.length() == 0L) {
                context.assets.open(ASSET_PATH).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
            out
        }.onFailure { Log.w(TAG, "Inter-fontfile extrahieren fehlgeschlagen: ${it.message}") }
            .getOrNull()
            ?.also { cachedFile = it }
    }
}
