package com.uip.oneapp.network

// Rebranding (2026-06-07): Produktname ist DrainQ — der displayName steht sichtbar
// in OSD-Zeile 1 und wird damit in JEDES Foto und Video eingebrannt.
//
// Dual-Modus (Welle 4): „TWO" ist ein anderes Produkt und wurde entfernt (inkl.
// TwoHardwareService + two_camera_*-Prefs + Gerätetyp-Selektor). ONE bleibt als einzige
// Variante — die Direkt-/WiFi-Auswahl läuft über HardwareModeDetector + `one_transport`,
// NICHT mehr über einen Gerätetyp. Das Enum bleibt als displayName-Quelle fürs OSD.
enum class DeviceType(val displayName: String) {
    ONE("DrainQ ONE")
}
