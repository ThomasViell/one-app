# RESULT: Auto-Reconnect zu bekannter ONE (Tablet-Seite)

**Datum:** 2026-07-04
**Branch:** `feature/dual-mode` (kein Merge, kein Tag)
**Commits:** `6fa0527` → `738007c` (5 Commits + dieser Bericht)
**Build/Tests:** `:app:compileDebugKotlin` + `:app:testDebugUnitTest` grün (20 neue Tests: 9 Store, 11 AutoConnector; bestehende Suite unverändert grün)

---

## Was gebaut wurde

### W1 — Bekannte ONEs persistieren + Auto-Reconnect (Specifier-Pfad)

| Baustein | Datei | Kern |
|---|---|---|
| `KnownOneStore` | `network/KnownOneStore.kt` | Pro gekoppelter ONE: `ssid`, `passphrase`, `security`, `lastConnectedEpochMs`. Nimmt NUR `DrainQ-ONE-*`-SSIDs an (Präfix aus `SoftApSpec.SSID_PREFIX` — keine Office-WLANs). API: `save`, `all`, `get`, `forget`, `touch`, `bestMatch(scanResults)` (exakter SSID-Match, stärkstes Signal gewinnt). Android-frei hinter `SecretKeyValueStore`-Seam. |
| `AndroidEncryptedStorage` | `network/AndroidEncryptedStorage.kt` | EncryptedSharedPreferences (AES256-SIV/GCM), Master-Key im Android Keystore. Korrupter Keystore → einmaliger Reset (Neu-Kopplung nötig); scheitert auch das → No-op statt Crash. Neue Dependency `androidx.security:security-crypto:1.1.0-alpha06`. |
| `OneAutoConnector` | `network/OneAutoConnector.kt` | Zustandsmaschine (IDLE/SCANNING/CONNECTING/CONNECTED/LOST), nur `HardwareMode.WIFI`. Trigger A (App-Start, 3 s Delay, Scan → `connectViaRequest` mit gespeicherten Credentials), Trigger B (Abriss → genau EIN Retry nach 3 s Backoff, danach LOST-Banner), Trigger C (System schon in bekannter SSID → nur `bindProcessToNetwork`, kein Dialog). Nach JEDEM Join: Hardware-Kette (`probeEndpoints` + `startPolling`; RTSP-VideoSource published `OneHardwareService` bei Discovery selbst). Races per Generation-Token entwertet (Muster LOHS-Stop-Race). |
| Join-Persistenz | `NetworkViewModel` | Erfolgspfad beider Wege (PRIVILEGED-ok und Specifier-`onAvailable`) speichert — nur ONE-Präfix. QR-Weg (`joinFromQr`) läuft durch denselben Pfad. |
| UI „Bekannte ONE" | `NetworkScreen` | Sektion unter der QR-Kopplung (nur Tablet): Eintrag mit Name, Status-Chip (Verbunden / In Reichweite / Nicht gefunden; ohne Scan-Wissen kein Chip), Aktionen „Verbinden" (gespeicherte Credentials) und „Vergessen". LOST-Banner „Verbindung zur ONE verloren" + „Erneut verbinden". Beim Öffnen mit gekoppelter ONE ein opportunistischer Scan für die Reichweite-Anzeige (ohne Berechtigung leise leer). |
| Setting | `SettingsViewModel`/`SettingsScreen` | „Automatisch mit bekannter ONE verbinden" — Toggle, **Default AN**, `app_settings`-Key `auto_connect_one`, nur im WiFi-Modus sichtbar. Wird vom AutoConnector bei jedem Versuch frisch gelesen. |
| Verdrahtung | `AppModule`/`OneApp` | Singles für Store + AutoConnector; Start in `OneApp` NUR im WiFi-Modus (Spiegelbild des `OneRemoteServer`-Starts im DIRECT-Modus). |
| L10n | `LocalizationManager` | 9 neue Keys de + en, chirurgisch eingefügt (Generator NICHT ausgeführt), keine Hardcodes. |

### W2 — WifiNetworkSuggestion (Null-Tap, rein additiv)

- Beim Persistieren wird zusätzlich eine `WifiNetworkSuggestion` (SSID + WPA2-Passphrase) via `addNetworkSuggestions` registriert (API 29+, darunter No-op). Android joint das Netz dann SELBST, sobald es in Reichweite ist → Trigger C bindet nur noch den Prozess = **0 Taps**.
- „Vergessen" entfernt die Suggestion rückstandsfrei (`removeNetworkSuggestions` — equals-Match, daher mit den gespeicherten Credentials gebaut). Passphrasen-Wechsel (ONE neu provisioniert) entfernt die alte Suggestion vor dem Neu-Anlegen.
- **Erstnutzung zeigt einmalig eine System-Notification** („App schlägt Netzwerke vor") — Zustimmung des Nutzers; lehnt er ab, bleibt der W1-Specifier-Pfad vollständiger Fallback.

### Review-Fixes (adversariale Selbst-Review)

1. **Trigger-C-Abrisswatcher:** Der System-Join-Pfad (Suggestion) hatte keinen Abriss-Kanal — `bindToCurrentWifi` registriert jetzt einen NetworkCallback-Watcher auf genau das gebundene Netz (einmaliges onLost, Selbst-Cleanup, `cancelRequest` räumt mit ab). Damit läuft auch der Null-Tap-Pfad durch Single-Retry/LOST-Banner.
2. **Thread-Hop:** `noteExternalJoin`/`noteManualConnectStarted` mutierten Zustand vom ConnectivityThread aus — beide laufen jetzt im Single-Thread-Scope (Main.immediate: vom Main-Thread inline, d. h. der Race-Schutz greift VOR dem nachfolgenden `connectViaRequest`).
3. **Start-Versuch abbrechbar:** Trigger A läuft als `attemptJob` — ein manueller Connect während des laufenden Start-Scans bricht ihn ab, statt dass der Scan danach die manuelle Specifier-Verbindung ersetzt.

**Leak-Check:** Es gibt maximal einen aktiven Specifier-Callback (`connectViaRequest` → `cancelRequest` zuerst) und maximal einen Netz-Watcher (`watchNetwork` → `unwatchNetwork` zuerst; zusätzlich in `cancelRequest` gelöst). Keine Registrierung ohne symmetrische Freigabe.

**KRITIS-Check:** Passphrase nur verschlüsselt at rest (Keystore); `KnownOne.toString()` maskiert; kein Log enthält Passphrasen (auch nicht gekürzt); UI-State trägt nur SSIDs. `allowBackup=true` exportiert nur Ciphertext — der Keystore-Key verlässt das Gerät nicht, ein Restore auf anderem Gerät ist wertlos (Neu-Kopplung nötig, gewollt).

**Unberührt (Leitplanke):** `OneInternalHardwareService`, Serial/V4L2, `OneRemoteServer`, `AccessPointController`, `PairingScreen`, Manifest (keine neuen Permissions — `CHANGE_WIFI_STATE` für Suggestions war vorhanden).

---

## Nutzer-Flow (Soll nach diesem Stand)

1. **Erst-Kopplung:** unverändert QR-Scan (1× pro ONE). Erfolg → Credentials verschlüsselt gespeichert + Suggestion hinterlegt (einmalige System-Notification).
2. **Jeder weitere Start:** App verbindet automatisch — via Suggestion/Trigger C mit 0 Taps, sonst Specifier mit Approval-Bypass (Android 11+: meist 0 Taps, sonst genau 1 Tap System-Dialog). Nie wieder QR.
3. **Abriss (ONE aus/Reichweite):** genau ein stiller Reconnect-Versuch nach 3 s; scheitert er → Banner mit Retry-Button im Netzwerk-Screen.
4. **Vergessen:** Eintrag + Suggestion restlos weg.

---

## Geräte-Testplan (nächste Session, Tablet + ONE)

| # | Test | Erwartung |
|---|---|---|
| 1 | Erst-Kopplung per QR → App **killen** → App neu starten (ONE-Hotspot aktiv) | Nach ~3–10 s automatisch verbunden (0–1 Tap, KEIN QR); Video/Telemetrie kommen automatisch (Hardware-Kette). Status-Chip „Verbunden". |
| 2 | Suggestion-Zustimmung | Beim ersten Speichern erscheint einmalig die System-Notification — annehmen. Danach Flugmodus-Zyklus: joint Android selbst? (`currentWifiSsid` + Trigger C, 0 Taps). |
| 3 | ONE **ausschalten** während verbunden | Nach ≤3 s + Timeout: LOST-Banner „Verbindung zur ONE verloren" (genau EIN Auto-Versuch, kein Dialog-Spam). ONE wieder an → „Erneut verbinden" verbindet. |
| 4 | ONE aus/an (kurzer Brownout) | Der eine Auto-Retry fängt den Abriss ab (ONE-AP braucht ~10–30 s zum Hochkommen → ggf. schlägt der Retry zu früh fehl → Banner; Befund notieren, ggf. Backoff erhöhen). |
| 5 | Samsung-Spezifik (internetlose Suggestion) | Prüfen: wertet Samsung die Suggestion ab / erscheint „Ohne Internet verbunden bleiben?"-Nachfrage (einmalig pro Netz erwartet)? W1-Fallback muss immer greifen. |
| 6 | Toggle AUS | Kein Auto-Versuch beim Start/Abriss; „Verbinden"-Button in „Bekannte ONE" funktioniert weiter. |
| 7 | Vergessen | Eintrag weg, Suggestion weg (Android-Einstellungen → gespeicherte Netzwerke/Vorschläge prüfen), kein Auto-Join mehr. |
| 8 | Race | Während des Start-Delays sofort QR scannen → manuelle Kopplung gewinnt, kein Dialog-Doppel. |
| 9 | `currentWifiSsid`-Sichtbarkeit | Auf dem Ziel-Tablet prüfen, ob die SSID ohne Standort-Grant sichtbar ist (bei Specifier-/Suggestion-Joins laut Plattform ja). Falls `<unknown ssid>`: Trigger C feuert nicht → Specifier-Pfad übernimmt (1 Tap) — funktional ok, notieren. |

## Offene Punkte

- **Flapping-AP:** `retryUsed` resettet nach jedem erfolgreichen Join — eine dauernd flackernde ONE kann wiederholt Dialoge zeigen (mit Approval-Bypass i. d. R. still). Falls Feldproblem: Erfolgs-Reset erst nach Haltezeit (z. B. 2 min).
- **Doppel-Probe-Fenster:** AutoConnector-Kette und `ConnectionViewModel`-Init-Probe prüfen beide `isConnected` — ein kleines paralleles Fenster bleibt (bestehendes Muster, bislang unkritisch).
- **Keystore-Init:** Erster Store-Zugriff (~100 ms, einmalig) läuft auf Main (+3 s nach Start). Falls messbar: auf IO-Dispatcher heben.
- **Banner-Sichtbarkeit:** LOST-Banner lebt im Netzwerk-Screen. Ein globales Overlay (z. B. in der Inspektion) wäre Folgearbeit, falls das Feld es braucht.
- **Backoff-Tuning:** 3 s ist für Screen-Wechsel-Abrisse richtig; für ONE-Reboots (~30 s) zu kurz → Test #4 entscheidet, ob ein zweiter, späterer Versuch (z. B. 30 s) dazukommt.
