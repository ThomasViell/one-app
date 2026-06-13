package com.uip.oneapp.network.internal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sichert Plausibilitätsfilter + Median-Glättung des Meterzählers ab (Feedback Louis #6):
 * Einzelne Ausreißer-Frames dürfen nicht als Sprung durchschlagen, echte Bewegung darf
 * nicht dauerhaft verschluckt werden, und das Nullsetzen muss die Historie leeren.
 */
class LinearMeterCalculatorTest {

    private fun feed(calc: LinearMeterCalculator, vararg values: Int): Double {
        var out = 0.0
        for (v in values) out = calc.calculateWithDiscount(v)
        return out
    }

    @Test
    fun steadyRampIsTrackedAndSettlesExactWhenStopped() {
        val calc = LinearMeterCalculator()
        // Realistische Bewegung ~50 mm/Tick — alle Schritte plausibel, werden akzeptiert.
        feed(calc, 0, 50, 100, 150, 200, 250)
        // Im Stillstand (gleicher Wert) konvergiert der Median exakt auf den Endwert.
        val out = feed(calc, 250, 250)
        assertEquals(250.0, out, 0.0)
    }

    @Test
    fun singleCorruptSpikeIsRejectedAndLastValueHeld() {
        val calc = LinearMeterCalculator()
        feed(calc, 1000, 1000, 1000)                          // stabil bei 1000
        val spike = calc.calculateWithDiscount(16_000_000)    // gekipptes High-Byte
        assertEquals(1000.0, spike, 0.0)                      // Ausreißer verworfen, Wert gehalten
        val after = calc.calculateWithDiscount(1000)
        assertEquals(1000.0, after, 0.0)
    }

    @Test
    fun negativeValueFromSignBitIsRejected() {
        val calc = LinearMeterCalculator()
        feed(calc, 500, 500, 500)
        val out = calc.calculateWithDiscount(-2_000_000)      // Vorzeichen-Bit durch korruptes Byte
        assertEquals(500.0, out, 0.0)
    }

    @Test
    fun smallInRangeJitterIsSmoothedAway() {
        val calc = LinearMeterCalculator()
        feed(calc, 100, 100, 100)                             // Median-Fenster = [100,100,100]
        // Einzelner Blip innerhalb der Plausibilitätsschwelle wird vom Median geschluckt.
        val blip = calc.calculateWithDiscount(160)            // |160-100|=60 <= 300 akzeptiert, Median bleibt 100
        assertEquals(100.0, blip, 0.0)
    }

    @Test
    fun sustainedRealMoveRecoversAfterPlausibilityHold() {
        val calc = LinearMeterCalculator()
        feed(calc, 1000, 1000, 1000)
        // Sprung um 4000 mm (> Schwelle): wird zunächst gehalten, nach Recovery übernommen.
        val out = feed(calc, 5000, 5000, 5000, 5000, 5000, 5000, 5000, 5000)
        assertEquals(5000.0, out, 0.0)                        // darf NICHT bei 1000 einfrieren
    }

    @Test
    fun resetClearsHistoryAndAcceptsNextValueImmediately() {
        val calc = LinearMeterCalculator()
        feed(calc, 1000, 1000, 1000)
        calc.reset()
        // Nach reset() gibt es keinen Vergleichswert → ein weit entfernter Wert wird sofort akzeptiert.
        val out = calc.calculateWithDiscount(9999)
        assertEquals(9999.0, out, 0.0)
    }

    @Test
    fun sustainedFastMovementWithinThresholdIsTrackedWithoutDrift() {
        val calc = LinearMeterCalculator()
        // ~15,7 m/s bei realer ~51 ms-Frame-Rate ≈ 800 mm/Tick — unter der 1000-mm-Schwelle,
        // also jeder Tick plausibel und akzeptiert (kein Verschlucken echter Schnellbewegung).
        var v = 0
        var out = 0.0
        repeat(20) { v += 800; out = calc.calculateWithDiscount(v) }
        // Median-über-3 = exakt 1 Tick Versatz (800 mm), KEIN aufsummierender Drift.
        assertEquals((v - 800).toDouble(), out, 0.0)
    }

    @Test
    fun corruptByteJumpIsStillRejectedAtNewThreshold() {
        val calc = LinearMeterCalculator()
        feed(calc, 2000, 2000, 2000)
        val spike = calc.calculateWithDiscount(2000 + 65536) // gekipptes High-Byte (16-bit)
        assertEquals(2000.0, spike, 0.0)                      // > 1000-mm-Schwelle → verworfen
    }
}
