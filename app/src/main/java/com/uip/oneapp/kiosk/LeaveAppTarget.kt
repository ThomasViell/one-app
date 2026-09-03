package com.uip.oneapp.kiosk

/**
 * Zielwahl fuer „App verlassen" (Kette kiosk-pflicht, 03.09.2026, Plan E5).
 *
 * Aus den HOME-faehigen Aktivitaeten des Geraets wird die Systemoberflaeche gewaehlt:
 * das eigene Paket (wir sind selbst HOME — uns zu starten hiesse: nicht verlassen) und
 * `com.android.settings/.FallbackHome` (Notfall-Home der Einrichtungsphase, kein
 * Bediener-Ziel) fallen raus. Gemessen 03.09.2026 (M0): drei HOME-faehige Programme,
 * nach Filter genau eines — `com.android.launcher3/.uioverrides.QuickstepLauncher`.
 *
 * Bleibt kein Kandidat: null — der Aufrufer bricht ab, zeigt einen Bedienertext und
 * der Kiosk bleibt aktiv (lieber im Kiosk bleiben als halb verlassen).
 */
object LeaveAppTarget {

    /** Kandidaten als (Paketname, Klassenname); Rueckgabe: der gewaehlte Kandidat oder null. */
    fun choose(candidates: List<Pair<String, String>>, ownPackage: String): Pair<String, String>? =
        candidates.firstOrNull { (pkg, cls) ->
            pkg != ownPackage && !(pkg == "com.android.settings" && cls.endsWith(".FallbackHome"))
        }
}
