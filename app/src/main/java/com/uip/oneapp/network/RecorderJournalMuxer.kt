package com.uip.oneapp.network

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * **Journal → MP4 (Welle 5, ADR 0002 B2/B7).** Muxt einen absturzsicheren H.264-Journalstrom
 * ([H264StreamJournal]) verlustfrei in eine spielbare MP4 — beim gewollten Stopp und, für verwaiste
 * Journale nach einem Kill, beim nächsten App-Start ([recoverOrphanJournals]).
 *
 * `MediaMuxer` bekommt die exakten, streng steigenden Per-Sample-PTS aus dem Journal (VFR) →
 * Container-Dauer == pausenbereinigte Echtzeit. Führende Nicht-Keyframe-AUs werden übersprungen
 * (MediaMuxer verlangt erstes Sample = Keyframe). SPS/PPS liegen im Journal ohne Startcode (so
 * liefert sie der Encoder) → csd-0/csd-1 mit vorangestelltem Annex-B-Startcode (kanonische AVC-Form).
 */
object RecorderJournalMuxer {

    private const val TAG = "RecorderJournalMuxer"
    private val START_CODE = byteArrayOf(0, 0, 0, 1)
    /** Quarantäne für wiederholt nicht muxbare Journale (kein Endlos-Retry, kein stiller Verlust). */
    const val QUARANTINE_DIR = ".recovery_failed"

    /**
     * Monotonie-Garantie für MediaMuxer (B3): PTS müssen streng steigen. Gibt die zu verwendende
     * PTS zurück — `candidate`, außer sie wäre ≤ der letzten, dann `last + 1`. Reine Funktion.
     */
    internal fun monotonicPtsUs(candidateUs: Long, lastUs: Long?): Long =
        if (lastUs == null) candidateUs.coerceAtLeast(0L)
        else if (candidateUs <= lastUs) lastUs + 1 else candidateUs

    /**
     * Muxt [journalFile] nach [outFile]. true nur, wenn die MP4 existiert, > 0 Bytes hat und
     * mindestens ein (Keyframe-startendes) Sample geschrieben wurde.
     */
    fun muxJournalToMp4(journalFile: File, outFile: File): Boolean {
        if (!journalFile.exists() || journalFile.length() == 0L) return false
        var muxer: MediaMuxer? = null
        var started = false
        var samplesWritten = 0
        try {
            DataInputStream(BufferedInputStream(FileInputStream(journalFile))).use { input ->
                val header = H264JournalCodec.readHeader(input) ?: run {
                    Log.w(TAG, "Journal ohne gültigen Kopf: ${journalFile.name}")
                    return false
                }
                val format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC, header.width, header.height
                ).apply {
                    setByteBuffer("csd-0", ByteBuffer.wrap(START_CODE + header.sps))
                    setByteBuffer("csd-1", ByteBuffer.wrap(START_CODE + header.pps))
                }
                if (outFile.exists()) outFile.delete()
                outFile.parentFile?.mkdirs()
                muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val track = muxer!!.addTrack(format)
                muxer!!.start()
                started = true

                val info = MediaCodec.BufferInfo()
                var seenKeyframe = false
                var lastPts: Long? = null
                loop@ while (true) {
                    when (val r = H264JournalCodec.readRecord(input)) {
                        is H264JournalCodec.RecordResult.Data -> {
                            val rec = r.record
                            // Erst ab dem ersten Keyframe muxen (MediaMuxer: 1. Sample = Sync).
                            if (!seenKeyframe && !rec.keyframe) continue@loop
                            seenKeyframe = true
                            val pts = monotonicPtsUs(rec.ptsUs, lastPts)
                            lastPts = pts
                            info.set(
                                0, rec.annexB.size, pts,
                                if (rec.keyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            )
                            muxer!!.writeSampleData(track, ByteBuffer.wrap(rec.annexB), info)
                            samplesWritten++
                        }
                        H264JournalCodec.RecordResult.End -> break@loop
                        H264JournalCodec.RecordResult.Torn -> {
                            Log.w(TAG, "Torn tail verworfen: ${journalFile.name}")
                            break@loop
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Mux fehlgeschlagen (${journalFile.name}): ${e.message}")
        } finally {
            try { if (started) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
        val good = samplesWritten > 0 && outFile.exists() && outFile.length() > 0L
        if (!good && outFile.exists()) {
            try { outFile.delete() } catch (_: Exception) {}
        }
        return good
    }

    /**
     * Recovery beim App-Start: alle verwaisten `*.h264j` unter [root] (rekursiv) finalisieren.
     * Erfolg → Journal löschen; wiederholt nicht muxbar → nach [QUARANTINE_DIR] verschieben.
     * Idempotent, best-effort, jede Datei einzeln geloggt.
     */
    fun recoverOrphanJournals(root: File) {
        if (!root.exists()) return
        val journals = try {
            root.walkTopDown().filter { it.isFile && it.name.endsWith(JOURNAL_SUFFIX) }.toList()
        } catch (e: Exception) {
            Log.w(TAG, "Journal-Suche fehlgeschlagen: ${e.message}"); return
        }
        if (journals.isEmpty()) return
        Log.i(TAG, "Recovery: ${journals.size} verwaiste Journale gefunden")
        for (journal in journals) {
            val outFile = File(journal.absolutePath.removeSuffix(JOURNAL_SUFFIX))
            val ok = try {
                muxJournalToMp4(journal, outFile)
            } catch (e: Exception) {
                Log.w(TAG, "Recovery-Mux warf (${journal.name}): ${e.message}"); false
            }
            if (ok) {
                try { journal.delete() } catch (_: Exception) {}
                Log.i(TAG, "Recovery: ${outFile.name} wiederhergestellt")
            } else {
                quarantine(journal)
            }
        }
    }

    private fun quarantine(journal: File) {
        try {
            val qDir = File(journal.parentFile, QUARANTINE_DIR).apply { mkdirs() }
            val dest = File(qDir, journal.name)
            if (dest.exists()) dest.delete()
            if (!journal.renameTo(dest)) {
                journal.copyTo(dest, overwrite = true)
                journal.delete()
            }
            Log.w(TAG, "Journal nicht muxbar → Quarantäne: ${journal.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Quarantäne fehlgeschlagen (${journal.name}): ${e.message}")
        }
    }
}
