package com.uip.oneapp.network.internal

/**
 * Linearisierung + Stabilisierung des Roh-Meterzählers aus Group 22 (Einheit: mm).
 *
 * Zwei Stufen gegen das beobachtete „Wackeln" (Feedback Louis #6):
 *  1. Plausibilitätsfilter: ein Sprung > [MAX_STEP_MM] gegen den letzten gültigen Wert
 *     (oder ein negativer Wert durch ein gekipptes High-Byte) gilt als Ausreißer und wird
 *     verworfen — der letzte Ausgabewert wird gehalten. Nach [RECOVER_AFTER] konsistenten
 *     Ausreißern wird der neue Wert akzeptiert, damit echte schnelle Bewegung / ein Sprung
 *     nach dem Nullsetzen nicht dauerhaft verschluckt wird.
 *  2. Median-über-3 der akzeptierten Werte: beseitigt Einzelausreißer, die den Filter passieren,
 *     ohne nennenswerte Verzögerung (auf einer Rampe nur 1 Tick Versatz, im Stillstand exakt).
 *
 * Linearisierung bleibt vorerst Identität (Pilot). Für Produktion: Stützstellen-Tabelle aus
 *   one-revers/decoded/jadx/sources/com/bominwell/robot/control/SerialControl/LinearDataPoints.java
 * portieren (charakteristische Kabel-Dehnung der ONE) — orthogonal zur Filterung hier.
 *
 * Threadsafe ([calculateWithDiscount] läuft im rxLoop, [reset] ggf. aus einer UI-Aktion).
 */
class LinearMeterCalculator {

    private var lastAcceptedMm: Int? = null
    private val window = ArrayDeque<Int>()   // letzte (max. 3) akzeptierten Rohwerte → Median
    private var lastOutput: Double = 0.0
    private var rejectStreak = 0

    @Synchronized
    fun calculateWithDiscount(rawValueMm: Int): Double {
        val last = lastAcceptedMm
        val implausible = rawValueMm < 0 ||
            (last != null && kotlin.math.abs(rawValueMm.toLong() - last.toLong()) > MAX_STEP_MM)
        if (implausible && rejectStreak < RECOVER_AFTER) {
            rejectStreak++
            return lastOutput            // Ausreißer: letzten gültigen Wert halten
        }
        rejectStreak = 0
        lastAcceptedMm = rawValueMm
        if (window.size == 3) window.removeFirst()
        window.addLast(rawValueMm)
        lastOutput = medianOf(window).toDouble()
        return lastOutput
    }

    /** Filter-/Glättungshistorie leeren (z. B. beim Nullsetzen des Meterzählers). */
    @Synchronized
    fun reset() {
        lastAcceptedMm = null
        window.clear()
        lastOutput = 0.0
        rejectStreak = 0
    }

    private fun medianOf(values: Collection<Int>): Int {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    companion object {
        /** Max. plausible Änderung pro Update (mm). Großzügig: ~5 m/s ≙ ~165 mm/33ms-Tick;
         *  ein gekipptes Byte injiziert ≥ 65536 mm → sicher gefangen, echte Bewegung nicht. */
        private const val MAX_STEP_MM = 300L
        /** Nach so vielen konsistenten Ausreißern wird der neue Wert akzeptiert (Recovery). */
        private const val RECOVER_AFTER = 4
    }
}
