package com.uip.oneapp.network

import android.util.Log
import com.antonkarpenko.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "RecorderRemux"

/** Endung der absturzsicheren Live-Aufnahme (fragmentiertes MP4) vor dem Remux. */
const val FRAG_SUFFIX = ".frag.mp4"

/**
 * Injizierbarer Remux-Delegate. Default ist der echte [remuxToFaststart]; Tests reichen
 * ein Fake herein, um Erfolg/Fehlschlag ohne echtes FFmpeg zu prüfen (siehe [finalizeFragRecording]).
 */
typealias RemuxDelegate = suspend (srcFrag: File, dstFinal: File) -> Boolean

/**
 * Baut die live absturzsicher geschriebene, fragmentierte MP4 (`empty_moov`, keine
 * Gesamtdauer im Kopf) EINMAL beim gewollten Stopp verlustfrei in eine normale MP4 mit
 * korrektem `moov` (vorn, `+faststart`) um.
 *
 * `-c copy` = reiner Stream-Copy: kein Re-Encode, Sekundenbruchteile, keine Qualitäts-,
 * Auflösungs- oder OSD-Änderung (das OSD ist im Capture-Pass bereits eingebrannt).
 *
 * Behebt Louis #5b (Player kannte die Länge nicht → nur Endzeit) und #9a (nach einer Pause
 * brach die Wiedergabe früh ab), weil die fertige Datei jetzt eine korrekte Gesamtdauer trägt.
 *
 * Eigene FFmpegKit-Session (synchrones [FFmpegKit.execute]); es wird NIE global
 * `FFmpegKit.cancel()` gerufen — parallele Export-Encodes bleiben unberührt.
 *
 * @return true nur, wenn rc==0 UND die Zieldatei existiert und > 0 Bytes hat.
 */
suspend fun remuxToFaststart(srcFrag: File, dstFinal: File): Boolean = withContext(Dispatchers.IO) {
    if (!srcFrag.exists() || srcFrag.length() == 0L) {
        Log.w(TAG, "remux übersprungen: Quelle fehlt/leer ${srcFrag.absolutePath}")
        return@withContext false
    }
    // -c copy: verlustfrei; +faststart: moov nach vorn → sofort seekbar mit korrekter Dauer.
    val cmd = "-i ${srcFrag.absolutePath} -c copy -movflags +faststart -y ${dstFinal.absolutePath}"
    val ok = try {
        val session = FFmpegKit.execute(cmd)
        val rc = session.returnCode?.value ?: -1
        val good = rc == 0 && dstFinal.exists() && dstFinal.length() > 0L
        if (!good) {
            Log.w(TAG, "remux fehlgeschlagen rc=$rc exists=${dstFinal.exists()} " +
                       "size=${if (dstFinal.exists()) dstFinal.length() else -1}")
        }
        good
    } catch (e: Exception) {
        Log.w(TAG, "remux warf Exception", e); false
    }
    ok
}

/**
 * Schließt eine Aufnahme beim gewollten Stopp sauber ab: [frag] → [finalFile] remuxen.
 * Bei Remux-Fehler wird die absturzsichere Frag-Datei verlustfrei ZUR finalen Datei gemacht —
 * die Aufnahme geht nie verloren.
 *
 * Der Remux läuft über den injizierbaren [remux]-Delegate (in Produktion der echte
 * [remuxToFaststart]), damit die Zustandslogik ohne echtes FFmpeg testbar ist.
 *
 * @return der finale Pfad (Erfolg wie Fallback), oder null wenn die Frag-Datei fehlt/leer ist
 *         und auch keine brauchbare Zieldatei existiert.
 */
suspend fun finalizeFragRecording(
    frag: File,
    finalFile: File,
    remux: RemuxDelegate
): String? {
    if (!frag.exists() || frag.length() == 0L) {
        // Nichts (Sinnvolles) aufgenommen. Falls trotzdem eine Zieldatei existiert, die zurückgeben.
        return if (finalFile.exists() && finalFile.length() > 0L) finalFile.absolutePath else null
    }
    val remuxed = try {
        remux(frag, finalFile)
    } catch (e: Exception) {
        Log.w(TAG, "remux-Delegate warf Exception", e); false
    }
    if (remuxed && finalFile.exists() && finalFile.length() > 0L) {
        try { frag.delete() } catch (_: Exception) {}
        return finalFile.absolutePath
    }
    // Fallback: Aufnahme erhalten — Frag verlustfrei zur finalen Datei machen.
    Log.w(TAG, "remux ohne Erfolg → behalte fragmentierte Aufnahme als ${finalFile.absolutePath}")
    return try {
        if (finalFile.exists()) finalFile.delete()
        if (frag.renameTo(finalFile)) {
            finalFile.absolutePath
        } else {
            // renameTo kann über FS-Grenzen scheitern — dann kopieren und Frag löschen.
            frag.copyTo(finalFile, overwrite = true)
            try { frag.delete() } catch (_: Exception) {}
            finalFile.absolutePath
        }
    } catch (e: Exception) {
        // Selbst der Fallback scheiterte — die Frag-Datei ist weiterhin spielbar.
        Log.w(TAG, "Fallback rename/copy fehlgeschlagen → gebe Frag-Pfad zurück", e)
        frag.absolutePath
    }
}

/**
 * Räumt verwaiste `*.frag.mp4` (Reste eines Absturzes/Cancels ohne Remux) im Zielordner auf,
 * bevor eine neue Aufnahme startet. Innerhalb eines Projektordners ist jede Frag-Datei
 * per Definition verwaist (eine laufende Aufnahme hält ihre Frag offen; parallel startet
 * der Zustands-Guard keine zweite).
 */
fun cleanupOrphanFrags(dir: File?) {
    if (dir == null) return
    try {
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(FRAG_SUFFIX)) {
                if (f.delete()) Log.d(TAG, "verwaiste Frag entfernt: ${f.name}")
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Aufräumen verwaister Frag-Dateien fehlgeschlagen", e)
    }
}
