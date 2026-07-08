package com.uip.oneapp.ui.screens.projects

import com.uip.oneapp.network.internal.CameraHead

/**
 * Louis-W3 / Aufgabe A: Reine Ableitung des Default-Kameratyps fürs Projektformular.
 *
 * Beim **Neuanlegen** eines Projekts soll der erkannte Kamerakopf (C10/C18) den
 * Kameratyp automatisch vorbelegen — als reiner Default, kein Zwang:
 *
 * - Ist das Feld bereits belegt/angetippt ([currentValue] nicht leer) → `null`
 *   (nie überschreiben; der manuelle Report-Override bleibt).
 * - [CameraHead.UNKNOWN] → `null` (nie raten).
 * - C10/C18 → das passende, **bereits lokalisierte** Label ([c10Label]/[c18Label]).
 *
 * Der Editier-Modus ruft das gar nicht erst auf (Vorbelegung nur beim neuen Projekt).
 * Als reine Funktion ist die Kopf→Label-Abbildung ohne Compose/Android unit-testbar.
 *
 * @return das vorzubelegende Label oder `null`, wenn nichts vorzubelegen ist.
 */
fun cameraTypePrefill(
    detectedHead: CameraHead,
    currentValue: String,
    c10Label: String,
    c18Label: String
): String? {
    if (currentValue.isNotBlank()) return null
    return when (detectedHead) {
        CameraHead.C10 -> c10Label
        CameraHead.C18 -> c18Label
        CameraHead.UNKNOWN -> null
    }
}
