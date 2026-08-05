# DrainQ.ONE — Dual-Modus Nacht-Run (2026-06-25)

**Branch:** `feature/dual-mode` · **Ausführung:** Claude Code (Opus), autonom, Welle für Welle ·
**Vorgabe:** `PLAN_DUAL_MODE_2026-06-24.md` + Server-Vertrag aus der alten Slave-App
(`ONE.APP/.../OneHardwareService.kt` + `OneHardwareModels.kt`).

Kein Gerät verwendet — nur **Build + Unit-Tests**. Geräte-/Tablet-Tests und echte
Produktentscheidungen sind als **TODO(device)** bzw. **OPEN DECISION** markiert und NICHT geraten.
Kein Merge nach `master`/`feature/louis-feedback`. `OneInternalHardwareService` wurde **nicht**
geändert, nur konsumiert.

## Ausgangslage (vor diesem Run)
- W1 (`24fb54f`): ONE-Remote WiFi-Client (`OneHardwareService` + `OneRemoteProtocol` + Modelle).
- W2 (`1eda5c1`): `HardwareModeDetector` + `AppModule`-Auswahl `one_transport` (auto/internal/remote).

## Commits dieses Runs (auf `1eda5c1` aufgesetzt)
| Welle | Commit | Inhalt |
|------|--------|--------|
| 3b | `f302c51` | ONE-Remote-**Server** (:12345 Telemetrie/Steuerung + :8555 Discovery) |
| 4  | `10203de` | Modusabhängige UI/Settings + **„TWO" komplett entfernt** |
| 3a | `fa41705` | SoftAP-Host **AccessPointController** (DIRECT) |

## Build-/Test-Status (gesamt)
`./gradlew assembleDebug test` → **BUILD SUCCESSFUL** (Debug-APK gebaut; Debug- **und**
Release-Unit-Tests grün). Neue/erweiterte Tests, alle grün:
- `OneRemoteProtocolTest` — **35** (W1 + 8 neue für die Server-Inverse).
- `OneRemoteServerTest` — **7** (Mock-`HardwareService`).
- `AccessPointSpecTest` — **9**.
- Bestehende (`HardwareModeDetectorTest`, `OneFrameCodecTest`, …) unverändert grün.

Hinweis: Im Arbeitsbaum liegen unbeteiligte Änderungen vom Branch `spike/video-rtsp`
(`app/build.gradle.kts` `.spike`-Suffix, `AndroidManifest.xml` SpikeRtspActivity, Ordner
`spike/`, diverse `*.md`). Sie wurden **bewusst nicht** mit-committet (gezieltes Stagen, kein
`git add -A` — CRLF-Phantom). Die Spike-`debug`-Variante hängt `.spike` an die applicationId;
für einen sauberen Produktions-Debug-Build diese Working-Tree-Änderung vorher verwerfen.

---

## Welle 3b — Steuer-/Telemetrie-Server (`f302c51`)

**Gebaut + getestet.** Die ONE bildet im DIRECT-Modus selbst den Bominwell-`DeviceService` nach
und spiegelt die lokal seriell angebundene Hardware über WLAN für ein Tablet.

- `OneRemoteServer` (neue Klasse): TCP `:12345` (Telemetrie-Push aus `hardwareState` +
  Steuerbefehle vom Tablet) + UDP `:8555` Discovery-Broadcast. Bekommt die **existierende**
  `HardwareService`-Instanz injiziert (Koin-Single), startet **nur** im DIRECT-Modus über
  `OneApp` (Gate = neuer `HardwareMode`-Single).
- Reine, ohne Socket testbare Übersetzung in `OneRemoteProtocol` (exakte **Inverse** des
  W1-Clients): `decodeBaseCommand` (Header + XOR-Checksumme), `freqValue` (invertiert `freqLabel`
  **und** das interne `"33 kHz"`-Label), `miniPushFrom` (State→`miniPushInfo`),
  `isRelativeMeterReset`, `discoveryPayload`.
- Befehls-Mapping exakt nach ONE.APP-Vertrag: `sendCommand`→Licht/Sonde (entprellt gegen den
  2-Hz-Keepalive — nur Änderungen gehen auf den seriellen Bus), `miniPushInfo.currentDistance==1.0`
  →Relativ-Reset, `videoOverlay.isShowOSD`→OSD an/aus.
- Telemetrie-Round-Trip ist im Test bewiesen: `miniPushFrom(state)` → JSON → der W1-Client-Pfad
  `telemetryFrom(...)` liest Distanz/Akku/Frequenz korrekt zurück.

**TODO(device) / OPEN:**
- **Echter Socket-Round-Trip Tablet↔ONE** (Welle 5) — nur Build + Unit-Tests hier.
- **Akku-Telemetrie:** `OneInternalHardwareService` schreibt `batteryLevel` bewusst NICHT in den
  State (kommt dort aus dem Android-System) → über Remote derzeit 0. Quelle anbinden.
- **Raw-Keepalive:** Der W1-Client sendet abwechselnd roh-binär und JSON; der Server verarbeitet
  nur JSON-`SdkSendData` (kanonisches Protokoll). Unkritisch (jede echte Änderung kommt als JSON),
  aber Härtung (SDK-Header-Framing statt Brace-Matching) erst nach Byte-Strom-Mitschnitt am Gerät.
- **OPEN DECISION — Arbitrierung lokale-UI ↔ Tablet** auf EINEM seriellen Bus (Welle 3d): beim
  ersten Verbinden setzt der Keepalive den Stand auf den Tablet-Spiegel (typ. Licht=0). Echte
  Master/Slave-Logik fehlt noch.
- **Lifecycle:** Start in `OneApp` (DIRECT); Stop = Prozessende (Feldgerät dauerhaft im Kiosk).
  Setzt voraus, dass die lokale Hardware pollt (Serial offen) — sonst sind Befehle stille No-ops.

---

## Welle 4 — Modusabhängige UI/Settings + „TWO" raus (`10203de`)

**Gebaut + getestet (reiner Code, keine neuen Unit-Tests — Removal/UI-Gating, durch Compile +
bestehende Tests abgesichert).**

- Neuer `HardwareMode`-Single (aus dem aufgelösten `HardwareService` abgeleitet) = **eine Quelle
  der Wahrheit** für die modusabhängige Sichtbarkeit **und** das W3b-Server-Gate.
- **DIRECT** blendet den Verbindungs-/RTSP-/Diagnose-Screen **inkl. Log-Panel** aus (Backlog #1) —
  auf der ONE gibt es keine Netzverbindung zu konfigurieren.
- **WiFi/Tablet** blendet **Kiosk/Geräteeigentümer** und **Bildschirmhelligkeit** aus (steuern die
  ONE-Feldeinheit bzw. das ONE-Display; das Tablet regelt das über sein eigenes OS).
- `SettingsViewModel` initialisiert den Modus synchron → kein UI-Umspringen nach dem Prefs-Laden.
- **„TWO" vollständig entfernt:** `DeviceType.TWO` + `TwoHardwareConfig` raus (`DeviceType.ONE`
  bleibt als OSD-Namensquelle), `TwoHardwareService.kt` gelöscht, `AppModule`-TWO-Branch +
  `device_type`-Pref-Read weg (Transport nur noch über `one_transport` + Auto-Erkennung),
  `device_type`/`two_camera_*` aus `SettingsViewModel`, ungenutzte TWO-Imports in
  `ConnectionViewModel` bereinigt, OSD nutzt fest `DeviceType.ONE`.

**Hinweise / bewusst belassen:**
- **„Autostart"** (aus der Vorgabe für WiFi-Ausblendung): Es existiert **kein** Autostart-Setting
  im Code (kein `BOOT_COMPLETED`, kein autostart-Pref) → nichts auszublenden.
- Der **Gerätetyp-Selektor** war in der aktuellen `SettingsScreen` bereits nicht mehr vorhanden;
  entfernt wurde die restliche Pref-/DI-Verdrahtung dahinter.
- Tote l10n-Keys (`device_type`, `device_type_subtitle`, `two_camera_settings`) bewusst belassen
  (harmlos, unreferenziert; 20-Sprachen-Churn vermieden). Optionaler Cleanup später.

**TODO(device):** On-Device prüfen, dass im jeweiligen Modus genau die richtigen Settings sichtbar
sind (DIRECT: Kiosk+Helligkeit da, Verbindung weg / WiFi: umgekehrt). Helligkeit-im-Tablet-Modus
war im Backlog als „borderline" markiert — finale Freigabe durch Thomas.

---

## Welle 3a — SoftAP-Host (`fa41705`)

**Teilweise gebaut + getestet** (die testbaren/öffentlichen Teile). Der eigentliche AP-Start ist
gerätegebunden.

- `AccessPointSpec` (rein, Android-frei, **9 Tests**): feste SSID `DrainQ-ONE-<Seriennr>`
  (Alphanumerik-Filter + 32-Oktett-Klemmung), WPA2-Passphrase-Längenregel (8..63), Start-Gate
  (nur DIRECT; auf der ONE — ein `wlan0` — kein STA bei aktivem AP, da STA+AP exklusiv).
- `AccessPointController`: Gating + STA/AP-Exklusivitäts-Check (`WifiManager`, öffentliche API) +
  validierte Eingaben (`ssid`/`passphrase`). Als Koin-Single registriert (injizierbar), Seriennummer
  best-effort über `Build.getSerial()`.

**TODO(device) — der gesamte AP-Start-Pfad ist `@SystemApi` (NICHT im öffentlichen SDK):**
`SoftApConfiguration.Builder` ließ sich erwartungsgemäß **nicht** kompilieren. Daher **bewusst kein
spekulativer Reflection-Code**; der Pfad ist im Code präzise dokumentiert:
1. `SoftApConfiguration.Builder().setSsid(ssid).setPassphrase(pw, SECURITY_TYPE_WPA2_PSK).build()`
2. `WifiManager.setSoftApConfiguration(config)` (NETWORK_SETTINGS / OVERRIDE_WIFI_CONFIG)
3. `android.net.TetheringManager.startTethering(TETHERING_WIFI, …)`

Erfordert die ins **Werks-Image** gegebene privilegierte Tether-Permission + Geräteeigentümer →
am Gerät zu verifizieren (HW kann SoftAP — hostapd-Test war ok, siehe Backlog).

**OPEN DECISION (Produkt/UX):**
- **STA+AP exklusiv** → aktiver Hotspot = kein gleichzeitiges WLAN-Internet (Cloud/Update). **WANN**
  kommt der AP hoch (immer im DIRECT-Modus vs. on-demand, sobald ein Tablet erwartet wird)? →
  deshalb **kein Auto-Start** am Lebenszyklus (anders als der `OneRemoteServer`).
- **Passphrase:** Default `drainq-one-2026` ist ein Platzhalter; eine APK-weite Passphrase ist kein
  echtes Geheimnis → pro Gerät provisionieren.
- **Seriennummer-Quelle:** `Build.getSerial()` braucht Geräteeigentümer/Privileg; sonst SSID-Fallback
  `DrainQ-ONE`.

---

## Welle 3c — Produktiv-Video (RTSP aus V4L2) — NICHT in diesem Run

Bewusst **nicht blind gebaut** (Vorgabe). Long-Pole/Hauptrisiko: V4L2-Frames per MediaCodec (H.264)
re-enkodieren und als RTSP publishen — Machbarkeit + Latenz auf RK3588 sind der kritische Punkt.
Auf `spike/video-rtsp` liegt dazu bereits ein separater Spike (`app/.../spike/`,
`SPIKE_W3C_VIDEO_RTSP_RESULT.md`). **TODO(device):** Video-Spike zuerst (De-Risk), dann W3c voll.

---

## Empfohlener nächster betreuter Schritt
1. **Geräte-Verifikation W3b (höchster Hebel, kein neuer Code nötig):** ONE im DIRECT-Modus, Tablet
   per `one_transport=remote` + `one_remote_ip` auf die ONE; prüfen, ob Telemetrie (Meter/Sonde/Kopf)
   ankommt und Licht/Sonde/Meter-Reset/OSD vom Tablet greifen. Belegt den Server-Vertrag end-to-end.
   (Voraussetzung: eine IP-Verbindung — zunächst über ein vorhandenes WLAN, AP muss dafür noch nicht
   stehen.)
2. **W3a am Gerät:** privilegierte Tether-Permission im Werks-Image + die drei `@SystemApi`-Aufrufe
   verdrahten und `start()` testen; Produktentscheidung „AP immer an vs. on-demand" treffen.
3. **W3c-Video-Spike** bewerten (vom `spike/video-rtsp`-Branch) → über W3c-Vollausbau entscheiden.
4. Danach **Welle 5 (Abnahme Tablet+ONE)**: Video + alle Steuerfunktionen + Telemetrie über WiFi,
   Direkt-Modus unverändert, Feature-Parität.

## Blocker
Keine. Alle drei machbaren Wellen sind gebaut, getestet (grün) und einzeln committet; alles
Geräte-/Entscheidungsabhängige ist sauber als TODO(device)/OPEN DECISION markiert.
