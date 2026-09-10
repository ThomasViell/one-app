package com.uip.oneapp.network

import android.content.Context
import android.util.Log
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegSession
import com.uip.oneapp.export.OsdBackground
import com.uip.oneapp.export.OsdColor
import com.uip.oneapp.export.OsdFontSize
import com.uip.oneapp.export.OsdSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val TAG = "FfmpegRtspRecorder"

enum class FfmpegRecordingState { IDLE, RECORDING, ERROR }

/**
 * Kette ausstiegsmeldung (E-3): Naht fuer die FFmpegKit-Session. Die echte FFmpegKit-
 * Nativlast ist unter Robolectric nicht ladbar (UnsatisfiedLinkError beim Laden von
 * FFmpegKitConfig, gemessen 08.09.2026) — der Completion-Ablauf „Cancel → Callback
 * unterwegs → stopRecording(onFinalized)" (Auflage des Beraters) muss aber genau
 * unit-testbar sein. Hausmuster: injizierbarer Delegate wie RemuxDelegate.
 */
interface RtspSessionRunner {
    /** Startet die FFmpegKit-Session. [onComplete] feuert genau einmal beim Session-Ende
     *  (Return-Code aus dem FFmpegSession). Liefert die Sitzungs-ID. */
    fun executeAsync(command: String, onComplete: (Int) -> Unit, onLog: (String) -> Unit): Long

    /** Bricht genau die uebergebene Session ab (sessionId-gebunden — cancel() ohne Id
     *  wuerde fremde FFmpegKit-Sessions treffen). */
    fun cancel(sessionId: Long)
}

/** Produktivfall: echte FFmpegKit-Session, Callback- und Log-Signatur umgebogen. */
private class FfmpegKitSessionRunner : RtspSessionRunner {
    override fun executeAsync(command: String, onComplete: (Int) -> Unit, onLog: (String) -> Unit): Long {
        val s = FFmpegKit.executeAsync(
            command,
            { onComplete(it.returnCode?.value ?: -1) },
            { onLog(it.message?.trim() ?: "") },
            null
        )
        return s.sessionId
    }

    override fun cancel(sessionId: Long) {
        FFmpegKit.cancel(sessionId)
    }
}

/**
 * Kette ausstiegsmeldung (E-3): Absicht je Aufnahme. [epoch] ist die Kennung der Aufnahme
 * (zaehlt jede startRecording()), [callback] die Meldung, die NUR der Completion-Callback
 * DERSELBEN Epoche aufrufen darf. Ohne Epochenvergleich wuerde ein noch laufender Callback
 * einer aelteren, per Stopp-Taste gecancelten Session die Absicht der neueren Aufnahme
 * feuern — genau die Meldung, die Bedingung 2 verbietet (Auflage des Beraters, 08.09.2026;
 * die Auflage „Feld nur setzen, wenn wirklich eine Session gecancelt wird" allein traegt
 * nicht, gemessen am Ablauf Stopp → neue Aufnahme → Zurueck: zwei Callbacks unterwegs).
 */
private class FinalizeIntent(val epoch: Long, val callback: (String?) -> Unit)

/**
 * Records an RTSP stream to MP4 with OSD burned in during encoding via FFmpeg drawtext filter.
 *
 * Architecture: parallel to VLC display session (Variante A — two independent RTSP sessions).
 * OSD text is written to cache files which FFmpegKit reloads each frame via drawtext reload=1,
 * enabling dynamic content (meter value + finding flash) without frame-level manipulation.
 *
 * Three OSD layers:
 *  - line1 (top, static):   project/device info
 *  - line2 (bottom, ticks each second): meter, date, sonde frequency
 *  - finding (center-top, transient): damage/observation flash, visible while non-empty
 */
class FfmpegRtspRecorder(
    private val context: Context,
    remuxDelegate: RemuxDelegate? = null,
    // Kette ausstiegsmeldung (E-3): injizierbar, damit der Completion-Ablauf ohne
    // FFmpegKit-Nativlast testbar ist (siehe RtspSessionRunner).
    sessionRunner: RtspSessionRunner? = null,
) {

    private val _state = MutableStateFlow(FfmpegRecordingState.IDLE)
    val state: StateFlow<FfmpegRecordingState> = _state.asStateFlow()

    // Injizierbarer Remux (Default: echter remuxToFaststart). Beim gewollten Stopp wird die
    // absturzsicher fragmentierte Aufnahme einmal verlustfrei in eine normale MP4 mit korrektem
    // moov umgebaut — behebt „nur Endzeit" (#5b) und den Abbruch nach Pause (#9a).
    private val remux: RemuxDelegate = remuxDelegate ?: { s, d -> remuxToFaststart(s, d) }
    private val runner: RtspSessionRunner = sessionRunner ?: FfmpegKitSessionRunner()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Eigene Session behalten: stopRecording() darf NUR diese canceln — FFmpegKit.cancel() ohne
    // Id bricht ALLE FFmpegKit-Sessions ab (z. B. einen parallel laufenden Video-Export).
    @Volatile
    private var session: Long? = null

    // Kette ausstiegsmeldung (E-3): Absicht „nach der Finalisierung melden", je Aufnahme.
    // Wird NUR gesetzt, wenn stopRecording() wirklich eine Session cancelt (Auflage des
    // Beraters); der Epochenvergleich in FinalizeIntent verhindert zusaetzlich, dass der
    // noch laufende Callback einer aelteren Session die neuere Absicht feuert.
    @Volatile
    private var finalizeIntent: FinalizeIntent? = null
    // Kennung der Aufnahme; nur vom Hauptthread (start/stop) geschrieben/gelesen,
    // der Completion-Callback bekommt seine Epoche als eingefangenen Wert.
    private var startEpoch = 0L

    // Live wird absturzsicher in fragFile geschrieben; im Completion-Callback nach finalOutput remuxt.
    @Volatile
    private var fragFile: File? = null
    @Volatile
    private var finalOutput: File? = null

    // BEWUSST KEINE Meter-Spur im RTSP-Modus (Welle 4b): Es gibt hier keine app-seitige
    // Frame-Schleife — die Medienzeit stammt aus den Stream-PTS und ist app-seitig nicht
    // exakt auf Meter-Samples abbildbar (Latenz/Stalls). Eine geratene (Wall-Clock-)Spur
    // wäre plausibel-aber-falsch → schlimmer als keine. Wiedergabe fällt hier auf das
    // leere Pflichtfeld (Stufe 1). Begründung siehe RESULT_LOUIS_W4B.md.

    private val line1File: File   get() = File(context.cacheDir, "osd_rec_line1.txt")
    private val line2File: File   get() = File(context.cacheDir, "osd_rec_line2.txt")
    private val findingFile: File get() = File(context.cacheDir, "osd_rec_finding.txt")

    fun startRecording(
        rtspUrl: String,
        outputFile: File,
        osdSettings: OsdSettings,
        initialLine1: String,
        initialLine2: String,
        initialFinding: String = "",
        sdResolution: Boolean = false
    ) {
        if (_state.value == FfmpegRecordingState.RECORDING) return

        runCatching {
            // UTF-8 (writeText-Default): drawtext rendert Unicode direkt, solange der
            // Font die Glyphen enthält (Inter: Latin inkl. Umlaute/Akzente + Kyrillisch).
            line1File.writeText(initialLine1)
            line2File.writeText(initialLine2)
            // Empty finding file means drawtext renders nothing — pre-create so reload=1
            // doesn't log file-missing warnings each frame.
            findingFile.writeText(initialFinding)
        }.onFailure { Log.w(TAG, "OSD text file write failed: ${it.message}") }

        // SA-Design: Inter als drawtext-fontfile (echte .ttf extrahiert). Fällt auf
        // Roboto zurück, falls die Extraktion scheitert — Aufnahme darf NIE abbrechen.
        val fontFile = com.uip.oneapp.util.DqFonts.osdFontFile(context)?.absolutePath ?: ANDROID_DEFAULT_FONT

        // Live absturzsicher in eine Temp-Frag-Datei schreiben; im Completion-Callback nach
        // outputFile remuxen (korrekter moov). Reste eines früheren Absturzes/Cancels aufräumen.
        val frag = File(outputFile.absolutePath + FRAG_SUFFIX)
        cleanupOrphanFrags(outputFile.parentFile)
        finalOutput = outputFile
        fragFile = frag

        val command = buildFullCommand(
            rtspUrl, frag.absolutePath,
            line1File.absolutePath, line2File.absolutePath, findingFile.absolutePath,
            osdSettings, fontFile, sdResolution
        )
        Log.d(TAG, "startRecording (font=$fontFile): $command")

        // Zustand VOR executeAsync setzen: die Completion-Callback kann bei einer sofort
        // scheiternden Session (falsche URL/Font/Pfad) noch vor der Rückkehr von executeAsync
        // laufen — würde RECORDING danach gesetzt, bliebe der Zustand für immer hängen
        // (startRecording returned bei RECORDING sofort).
        _state.value = FfmpegRecordingState.RECORDING
        val myEpoch = ++startEpoch
        session = runner.executeAsync(
            command,
            { rc -> handleSessionEnded(myEpoch, rc) },
            { log -> Log.v(TAG, log) }
        )
    }

    /**
     * Completion-Callback der Session [myEpoch]. Feuert die in stopRecording() hinterlegte
     * Absicht (Kette ausstiegsmeldung) erst NACH dem Remux — vorher existiert die Zieldatei
     * nicht (Bedingung 4) — und nur bei gleicher Epoche (Bedingung 2, Auflage des Beraters).
     */
    private fun handleSessionEnded(myEpoch: Long, rc: Int) {
        Log.d(TAG, "Session ended rc=$rc")
        val fragF = fragFile
        val finalF = finalOutput
        // rc=255 = von stopRecording() gecancelt (gewollter Stopp); rc=0 = normales Ende.
        if ((rc == 0 || rc == 255) && fragF != null && finalF != null &&
            fragF.exists() && fragF.length() > 0L) {
            // Encode-Session ist fertig → Zustand sofort terminal setzen; der verlustfreie
            // Remux (Frag→final, korrekter moov) läuft als reine Nachbearbeitung im Hintergrund.
            // Bei Remux-Fehler bleibt die Frag-Datei als finalF erhalten (Aufnahme nie verlieren).
            _state.value = FfmpegRecordingState.IDLE
            scope.launch {
                val result = finalizeFragRecording(fragF, finalF, remux)
                val intent = finalizeIntent
                if (intent != null && intent.epoch == myEpoch) intent.callback(result)
            }
        } else {
            // Harter Fehler: Frag bleibt spielbar (Absturzsicherheit), nächster Start räumt auf.
            _state.value = if (rc == 0 || rc == 255) FfmpegRecordingState.IDLE
                           else FfmpegRecordingState.ERROR
            val intent = finalizeIntent
            if (intent != null && intent.epoch == myEpoch) intent.callback(null)
        }
    }

    /** Call each second during recording to update the dynamic bottom bar (meter value etc.). */
    fun updateOsdLine2(line2: String) {
        if (_state.value == FfmpegRecordingState.RECORDING) {
            runCatching { line2File.writeText(line2) }
                .onFailure { Log.w(TAG, "OSD line2 update failed: ${it.message}") }
        }
    }

    /**
     * Update the finding-flash OSD line — shown prominently centered near the top.
     * Pass null or empty string to hide the flash (file is rewritten as empty,
     * drawtext renders nothing for empty content while still reloading each frame).
     */
    fun updateFinding(finding: String?) {
        if (_state.value == FfmpegRecordingState.RECORDING) {
            runCatching { findingFile.writeText(finding ?: "") }
                .onFailure { Log.w(TAG, "OSD finding update failed: ${it.message}") }
        }
    }

    /**
     * Stops the active recording session. The live file is fragmented MP4 (see buildFullCommand
     * muxFlags) — crash-safe and already playable without a trailer. Cancelling the session
     * (rc=255) fires the completion callback, which remuxes the fragment into a normal MP4 with a
     * correct moov (running duration + seekable, Louis #5b/#9a). On a real crash (no callback) the
     * fragment stays playable and is cleaned up on the next startRecording().
     *
     * Kette ausstiegsmeldung (E-3): [onFinalized] wird nach dem Remux mit dem finalen Pfad
     * (oder null) aufgerufen — aber NUR, wenn wirklich eine Session gecancelt wird. Ohne
     * Session (s == null) passiert nichts: der Fall „Stopp-Taste, dann sofort Zurueck"
     * (state noch RECORDING, Completion-Callback der gecancelten Session noch unterwegs)
     * darf keine Meldung ausloesen (Bedingung 2, Auflage des Beraters 08.09.2026).
     * Bestehende Aufrufer laufen mit dem Default unveraendert.
     */
    fun stopRecording(onFinalized: ((String?) -> Unit)? = null) {
        val s = session
        if (s != null) {
            Log.d(TAG, "stopRecording: cancelling session $s")
            // Gezielt NUR die eigene Session — cancel() ohne Id würde auch fremde
            // FFmpegKit-Sessions (z. B. Export-Encodes) mitten im File abbrechen.
            if (onFinalized != null) finalizeIntent = FinalizeIntent(startEpoch, onFinalized)
            runner.cancel(s)
            session = null
        } else {
            Log.d(TAG, "stopRecording: keine aktive Session")
        }
    }

    companion object {

        // Roboto ships with every Android since 4.0; DroidSans.ttf is a symlink
        // to this file on modern devices. Used as the explicit fontfile for
        // every drawtext layer — without it, ffmpeg fails to resolve fontconfig
        // family names on Android and aborts the entire encoding.
        internal const val ANDROID_DEFAULT_FONT = "/system/fonts/Roboto-Regular.ttf"

        /** Pure command builder — testable without Android context. */
        internal fun buildFullCommand(
            rtspUrl: String,
            outPath: String,
            l1Path: String,
            l2Path: String,
            findingPath: String,
            osdSettings: OsdSettings,
            fontFile: String = ANDROID_DEFAULT_FONT,
            sdResolution: Boolean = false
        ): String {
            val drawtext = buildDrawtextFilter(l1Path, l2Path, findingPath, osdSettings, fontFile)
            // M1: SD = echte 720x576-Aufnahme via Scale-Filter; HD = native Auflösung (kein Scale).
            // Scale läuft vor drawtext, damit die OSD-Schrift (feste px-Größe) auf dem SD-Bild
            // proportional korrekt liegt.
            val filters = listOfNotNull(
                if (sdResolution) "scale=720:576" else null,
                drawtext.ifEmpty { null }
            )
            val videoArgs = if (filters.isNotEmpty()) {
                "-vf ${filters.joinToString(",")} -c:v libx264 -preset fast -crf 23"
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

        /**
         * Builds FFmpeg drawtext filter string. Combines up to three independent layers:
         *  - line1 (when enableOsdBurnIn):       top-left, project/device info
         *  - line2 (when enableOsdBurnIn):       bottom-left, meter/date/sonde
         *  - finding (when enableFindingBurnIn): centered near top, prominent red flash
         *
         * Layers are independent because damage findings exist only in the app — even
         * when the camera renders its own hardware OSD (so OSD burn-in is off), the
         * finding flash must still be drawn on top of the recorded stream.
         *
         * Returns the empty string when no layer is enabled — caller drops the -vf flag.
         */
        internal fun buildDrawtextFilter(
            l1Path: String,
            l2Path: String,
            findingPath: String,
            osdSettings: OsdSettings,
            fontFile: String = ANDROID_DEFAULT_FONT
        ): String {
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
            // Finding flash is bigger than the static lines so it stands out at a glance.
            val sFinding = (fontSizePx + 8).coerceAtMost(48)

            // Escape colons in file paths for FFmpeg filter graph syntax
            val l1Esc      = l1Path.replace(":", "\\:")
            val l2Esc      = l2Path.replace(":", "\\:")
            val findingEsc = findingPath.replace(":", "\\:")

            // drawtext on Android can't resolve fontconfig family names ("Sans"
            // etc.) — the bundled ffmpegkit has no fontconfig database. Without
            // an explicit fontfile= each filter init fails with "Cannot find a
            // valid font for the family Sans" and the whole encoding aborts
            // before writing the first byte. Roboto-Regular ships with Android
            // since 4.0 and is also the target of the DroidSans.ttf symlink, so
            // it's the safe fallback when the Inter fontfile can't be resolved.
            // SA-Design: bevorzugt Inter (vom Aufrufer via fontFile übergeben).

            // SA-Design: untere Telemetrie-Zeile (Station/Meter) in Amber (#FF9900).
            val line2Color = "0xFF9900FF"

            val layers = mutableListOf<String>()
            if (osdSettings.enableOsdBurnIn) {
                layers += "drawtext=fontfile=$fontFile:textfile='$l1Esc':reload=1:x=8:y=8:" +
                          "fontsize=$fontSizePx:fontcolor=$fontColor:box=1:boxcolor=$boxColor"
                layers += "drawtext=fontfile=$fontFile:textfile='$l2Esc':reload=1:x=8:y=h-${s2 + 8}:" +
                          "fontsize=$s2:fontcolor=$line2Color:box=1:boxcolor=$boxColor"
            }
            if (osdSettings.enableFindingBurnIn) {
                // When the static OSD bar is off (hardware-OSD mode), place the flash
                // a bit lower so it doesn't collide with the camera-rendered top bar.
                val findingY = if (osdSettings.enableOsdBurnIn) fontSizePx + 24 else 80
                layers += "drawtext=fontfile=$fontFile:textfile='$findingEsc':reload=1:" +
                          "x=(w-text_w)/2:y=$findingY:" +
                          "fontsize=$sFinding:fontcolor=0xFFFFFFFF:" +
                          "box=1:boxcolor=0xCC0000E0:boxborderw=8"
            }
            if (layers.isEmpty()) return ""
            return "\"" + layers.joinToString(",") + "\""
        }
    }
}
