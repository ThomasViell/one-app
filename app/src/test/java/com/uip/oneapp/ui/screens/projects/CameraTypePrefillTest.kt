package com.uip.oneapp.ui.screens.projects

import com.uip.oneapp.network.internal.CameraHead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Louis-W3 / Aufgabe A: Sichert die reine Kopf→Default-Kameratyp-Abbildung ab.
 * Deckt die adversarialen Fälle aus dem Prompt ab: UNKNOWN nie raten, belegtes/
 * angetipptes Feld nie überschreiben, leere Strings als „unbelegt" behandeln.
 */
class CameraTypePrefillTest {

    private val c10 = "C10"
    private val c18 = "C18"

    @Test
    fun c10VorbelegtWennFeldLeer() {
        assertEquals(c10, cameraTypePrefill(CameraHead.C10, "", c10, c18))
    }

    @Test
    fun c18VorbelegtWennFeldLeer() {
        assertEquals(c18, cameraTypePrefill(CameraHead.C18, "", c10, c18))
    }

    @Test
    fun unknownRaetNie() {
        assertNull(cameraTypePrefill(CameraHead.UNKNOWN, "", c10, c18))
    }

    @Test
    fun belegtesFeldWirdNieUeberschrieben() {
        // Nutzer hat manuell C18 gewählt, Detektion meldet C10 → kein Vorbelegungs-Vorschlag.
        // Die Funktion liefert null (nichts vorzubelegen); der Aufrufer lässt das Feld dann in Ruhe.
        assertNull(cameraTypePrefill(CameraHead.C10, c18, c10, c18))
        assertNull(cameraTypePrefill(CameraHead.C18, c10, c10, c18))
    }

    @Test
    fun leerzeichenGiltAlsUnbelegt() {
        // isBlank(): reine Whitespace-Eingabe zählt als leer → Vorbelegung greift.
        assertEquals(c10, cameraTypePrefill(CameraHead.C10, "   ", c10, c18))
    }

    @Test
    fun belegtesFeldMitUnknownBleibtUnangetastet() {
        assertNull(cameraTypePrefill(CameraHead.UNKNOWN, c10, c10, c18))
    }

    @Test
    fun lokalisierteLabelsWerdenDurchgereicht() {
        // Andere Sprache/Schreibweise: die Funktion reicht die übergebenen Labels 1:1 durch.
        assertEquals("Kopf-10", cameraTypePrefill(CameraHead.C10, "", "Kopf-10", "Kopf-18"))
        assertEquals("Kopf-18", cameraTypePrefill(CameraHead.C18, "", "Kopf-10", "Kopf-18"))
    }
}

class CameraTypeAccumulateTest {

    private val c10 = "C10"
    private val c18 = "C18"

    @Test
    fun leerPlusC18ErgibtC18() {
        assertEquals(c18, cameraTypeAccumulate(CameraHead.C18, "", c10, c18))
    }

    @Test
    fun c18PlusC10ErgibtC18KommaC10() {
        assertEquals("C18, C10", cameraTypeAccumulate(CameraHead.C10, c18, c10, c18))
    }

    @Test
    fun c18PlusC18ErgibtNull() {
        assertNull(cameraTypeAccumulate(CameraHead.C18, c18, c10, c18))
    }

    @Test
    fun c18KommaC10PlusC10ErgibtNull() {
        assertNull(cameraTypeAccumulate(CameraHead.C10, "C18, C10", c10, c18))
    }

    @Test
    fun unknownErgibtImmerNull() {
        assertNull(cameraTypeAccumulate(CameraHead.UNKNOWN, "", c10, c18))
        assertNull(cameraTypeAccumulate(CameraHead.UNKNOWN, c18, c10, c18))
    }
}
