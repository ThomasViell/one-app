# Auftrag: Auto-Reconnect zu bekannter ONE (Tablet-Seite)

**Branch:** `feature/dual-mode` (weiterarbeiten, KEIN Merge, kein Tag)
**Ziel:** Eine einmal per QR gekoppelte ONE wird künftig **automatisch** wiederverbunden — ohne erneuten QR-Scan. Maximal zulässig: die System-Bestätigung des Android-Specifier-Dialogs (1 Tap). Null Taps, wo die Plattform es hergibt.

## Problem (Ist-Zustand, verifiziert 2026-07-04)

Jeder Verbindungsaufbau Tablet↔ONE erfordert erneut QR-Scan + System-Dialog:

1. `WifiController.kt` (Z. 47, 181–224): `connectViaRequest()` nutzt `WifiNetworkSpecifier` + `requestNetwork`. Kommentar: „WLAN-Passwörter werden NICHT persistiert". Verbindung ist app-gebunden, stirbt mit dem Prozess.
2. `NetworkViewModel.kt` (Z. 132–144): `joinFromQr()` parst und verbindet — speichert nichts.
3. `ConnectionViewModel.kt`: persistiert nur `last_rtsp_url` (DataStore `connection_settings`).
4. Es gibt keinen Known-Devices-Store, kein Auto-Rejoin beim App-Start, keinen Reconnect nach `onLost`.

Die ONE-Seite ist bereits ideal: SoftAP hat **feste SSID `DrainQ-ONE-<serial>` + persistente Passphrase** (`AccessPointController.kt` Z. 51–52). ONE-Seite NICHT anfassen.

## Umsetzung in 2 Wellen (je Welle: Build + Tests grün, dann Commit)

### W1 — Bekannte ONEs persistieren + Auto-Reconnect über den vorhandenen Specifier-Pfad

1. **`KnownOneStore`** (neu, `network/` oder `data/`): speichert pro gekoppelter ONE: `ssid`, `passphrase`, `security`, `lastConnectedEpochMs`. **Passphrase verschlüsselt** — `EncryptedSharedPreferences` (androidx.security-crypto, Master-Key im Android Keystore). Kein Klartext in Logs, kein Export. API: `save(credentials)`, `all(): List`, `forget(ssid)`, `bestMatch(scanResults): KnownOne?` (Match auf exakte SSID, `DrainQ-ONE-*`).
2. **Speichern beim erfolgreichen Join:** In `NetworkViewModel.connect(...)`-Erfolgspfad (`onAvailable` bzw. PRIVILEGED-ok) die Credentials in den Store schreiben — aber nur für SSIDs mit Präfix `DrainQ-ONE-` (keine Office-WLANs horten). Gilt auch für den QR-Weg (`joinFromQr` → läuft durch `connect`).
3. **`OneAutoConnector`** (neu): zentrale Auto-Reconnect-Logik, nur aktiv wenn `HardwareMode.WIFI` (Tablet):
   - **Trigger A — App-Start:** einmalig nach Start (leichter Delay), wenn nicht verbunden: WLAN-Scan (`WifiController.scan()`), bekannte ONE in Reichweite → automatisch `connectViaRequest` mit gespeicherten Credentials. Hinweis: Ab Android 11 überspringt die Plattform den Specifier-Dialog oft für zuvor genehmigte Netze (Approval-Bypass) → real häufig 0 Taps; sonst genau 1 Tap, nie ein QR.
   - **Trigger B — Verbindungsabriss (`onLost`):** genau EIN automatischer Wiederverbindungsversuch mit kurzem Backoff (z. B. 3 s). Danach nicht weiter automatisch anfragen (kein Dialog-Spam), sondern UI-Banner „Verbindung zur ONE verloren — erneut verbinden" mit Retry-Button.
   - **Trigger C — bereits im richtigen Netz:** Wenn das System schon mit einer bekannten `DrainQ-ONE-*`-SSID verbunden ist (aktives WLAN prüfen), keinen Specifier-Request stellen: aktives WLAN-`Network` über `ConnectivityManager` ermitteln, `bindProcessToNetwork`, weiter mit Discovery.
   - **Nach jedem erfolgreichen Join:** automatisch die Hardware-Kette starten — Discovery/`probeEndpoints` + `startPolling` + RTSP (der bestehende Auto-Probe in `ConnectionViewModel` init feuert nur 2 s nach ViewModel-Start; er muss auch nach spätem WLAN-Join ausgelöst werden, z. B. Event vom AutoConnector).
4. **UI (NetworkScreen):** Sektion „Bekannte ONE" — Eintrag mit Name (`DrainQ-ONE-<serial>`), Status (in Reichweite / verbunden / nicht gefunden), Aktionen „Verbinden" und „Vergessen". QR-Kopplung bleibt für die Erst-Kopplung unverändert.
5. **Setting:** „Automatisch mit bekannter ONE verbinden" (Toggle, **Default AN**, DataStore `app_settings`).

### W2 — Null-Tap-Pfad: WifiNetworkSuggestion zusätzlich hinterlegen

1. Beim Speichern in den `KnownOneStore` zusätzlich eine `WifiNetworkSuggestion` (SSID + WPA2-Passphrase) via `WifiManager.addNetworkSuggestions` registrieren; bei „Vergessen" via `removeNetworkSuggestions` entfernen. Erstnutzung zeigt einmalig eine System-Notification (Zustimmung) — dokumentieren.
2. Wenn Android per Suggestion selbst joint, greift Trigger C aus W1 → komplett ohne Bestätigung.
3. **Bekanntes Risiko (am Gerät zu verifizieren, nicht im Code lösbar):** Netz ohne Internet — Android/Samsung kann Auto-Join für internetlose Suggestions abwerten oder eine „Ohne Internet verbunden bleiben?"-Nachfrage zeigen (einmalig pro Netz). Suggestion-Pfad daher rein additiv bauen; W1-Specifier-Pfad bleibt vollständiger Fallback.

## Leitplanken

- **Direkt-Modus (ONE) komplett unberührt:** `OneInternalHardwareService`, Serial/V4L2, `OneRemoteServer`, `AccessPointController`, PairingScreen — keine Änderungen.
- Keine neuen Pflicht-Permissions, die den Kunden-Flow verschlechtern (kein Standort-Zwang zurückbringen; Scan nutzt den vorhandenen Berechtigungsstand des NetworkScreens).
- KRITIS: Passphrase nur verschlüsselt at rest (Keystore), niemals loggen (auch nicht gekürzt), „Vergessen" löscht Store + Suggestion rückstandsfrei.
- L10n: alle neuen UI-Strings über `S()`-Keys in `LocalizationManager.kt` (chirurgisch, Generator NICHT ausführen), de + en; keine Hardcodes.
- Neue Logik unit-testen (Store-Roundtrip mit Fake-Storage, `bestMatch`, AutoConnector-Zustandsmaschine mit Fake-WifiController: Start/onLost/Backoff/Single-Retry, SSID-Präfix-Filter). Bestehende Tests dürfen nicht brechen.
- Kleine Commits je abgeschlossenem Schritt, Präfix `feat(auto-reconnect):` bzw. `test:`/`docs:`. Abschlussbericht `RESULT_AUTO_RECONNECT.md` (Repo-Root): was gebaut, Geräte-Testplan (Erst-Kopplung → App-Kill → Neustart → auto verbunden?; ONE aus/an → Reconnect-Banner; Suggestion-Verhalten Samsung), offene Punkte.
- adversariale Selbst-Review vor dem letzten Commit: Leaks (NetworkCallback doppelt registriert?), Dialog-Schleifen, Race AutoConnector vs. manueller Connect.
