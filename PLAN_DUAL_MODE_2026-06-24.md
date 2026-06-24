# DrainQ.ONE — Umsetzungsplan Dual-Modus (Direkt / Tablet-WiFi)

**Stand:** 2026-06-24 · **Quelle:** Backlog `BACKLOG_ONE_2026-06-24.md` + Code-/RE-Analyse · **Umsetzung:** Claude Code (Opus), Welle für Welle.

## Ziel
Eine gepflegte App, zwei Laufzeit-Transporte hinter der gemeinsamen `HardwareService`-Schnittstelle, **automatisch erkannt**:
- **Direkt-auf-ONE:** lokal über Serial `/dev/ttyS5` + V4L2 (bleibt wie heute).
- **Tablet-über-WiFi:** App = Client gegen die ONE (RTSP-Video + DeviceService `:12345`).

~98 % der UI identisch; nur wenige Teile modusabhängig. **„TWO" ist ein anderes Produkt → raus.**

## Architektur-Anker (im Code verifiziert)
- `HardwareService` (Vertrag beider Modi): `hardwareState`, `videoSource` (Rtsp/LocalBitmap), `lastRtspUrl`, `probeEndpoints/startPolling/stopPolling/destroy`, Licht/Sonde/Meter/OSD-Methoden. Interface ist ausdrücklich für Netz-Impls vorbereitet („ONE-Remote").
- **Direkt:** `OneInternalHardwareService` (Serial + V4L2) — vorhanden.
- **WiFi-Client:** war `OneHardwareService` (TCP/JSON `:12345` + RTSP), am 19.05. in Commit `13b1384` geleert → **wiederherstellbar aus Vorgänger-Commit `8da3b98`**. `OneHardwareModels.kt` (:12345-Schema) noch vorhanden.
- **Auswahl heute:** `AppModule` liest Setting `device_type` → wird durch Auto-Erkennung ersetzt.
- **Adressierung ONE-als-AP:** `192.168.43.1` (RTSP `:554`, DeviceService `:12345`). RTSP-Bausteine (`RtspStreamTester`, `FfmpegRtspRecorder`) vorhanden.
- **Hotspot historisch:** Kamera-SDK `XApManager` (= `setWifiApEnabled`-Reflection, auf Android 12 tot) → modern: Device-Owner `SoftApConfiguration` + `TetheringManager`.

## KRITISCHE Vorab-Klärung — entscheidet den Umfang (Welle 0)
**Spannt die ONE/Bominwell-Einheit im Feld-Setup (Crawler/Basis angeschlossen, eingeschaltet) selbst ein WLAN auf und liefert dort RTSP + DeviceService `:12345`?**
- Indiz: Das Kamera-SDK (`XApManager`) hostet historisch den AP → **wahrscheinlich JA**.
- **JA → Architektur A:** Unsere App baut **keinen** Hotspot. Tablet joint den Einheit-AP, App = nur Client. ⇒ kleiner Umfang (nur W1, W2, W4, W5).
- **NEIN → Architektur B:** RK3588-Android (unsere App, Device-Owner) muss den Hotspot hosten **und** der DeviceService muss am AP-Interface erreichbar sein. ⇒ zusätzlich W3 + Server-Bindung klären.
- **Test:** ONE im Feldzustand hochfahren → mit Tablet/Laptop nach WLAN scannen → bei Verbindung `192.168.43.1` (bzw. `172.169.x`) auf `:554` (RTSP) und `:12345` prüfen.

## Wellen
- **W0 — Klärung (oben) + Cleanup:** TWO entfernen (`DeviceType.TWO`, `TwoHardwareService`, `two_camera_*`, `device_type`-Selektor); `.gitattributes` (`* text=auto eol=lf`) setzen, um das CRLF-Phantom dauerhaft zu beenden.
- **W1 — WiFi-Client wiederherstellen:** `OneHardwareService` aus `8da3b98` zurückholen, an das aktuelle `HardwareService`-Interface angleichen; RTSP (`videoSource=Rtsp`, `lastRtspUrl`) + `:12345`-Steuerung/Telemetrie über `OneHardwareModels`; Ziel-IP konfigurierbar (Default `192.168.43.1`). Mock-Tests.
- **W2 — Auto-Erkennung:** `HardwareModeDetector` — lokale Hardware vorhanden (`/dev/ttyS5` + `/dev/video0` zugreifbar bzw. `Build.MODEL=rk3588_s`) → Direkt-Modus, sonst WiFi-Modus. `AppModule` darauf umstellen; versteckter Dev-Override bleibt.
- **W3 — (nur Architektur B) Hotspot auf der ONE:** `AccessPointController` (Device-Owner `SoftApConfiguration`, feste SSID `DrainQ-ONE-<Seriennr>` + WPA2-PSK, `TetheringManager.startTethering`/`stopTethering`); ggf. privilegierte Tether-Permission ins ONE-Werks-Image. STA+AP-Limit beachten.
- **W4 — Modusabhängige UI/Settings** (Quelle: Backlog „Settings je Modus"): Direkt blendet Verbindungs-/RTSP-Screen + Log-Panel (Backlog #1) aus; Tablet blendet Kiosk/Geräteeigentümer, Helligkeit [borderline], Autostart aus; `device_type`/TWO ganz weg; im Direkt-Modus „Hotspot"-Kopplung (SSID/PW oder QR) anzeigen.
- **W5 — Abnahme Tablet+ONE:** Tablet joint, Video + alle Steuerfunktionen + Telemetrie (Akku, Kamerakopf, Meter) über WiFi; Direkt-Modus unverändert; Feature-Parität in beiden Modi.

## Risiken / offene Entscheidungen
- **STA+AP nicht gleichzeitig** → Hotspot an = kein gleichzeitiges WLAN-Internet auf der ONE (Cloud/Update nur ohne aktiven Tablet-Hotspot). Produkt-/UX-Entscheidung.
- **Tether-Berechtigung auf A12:** reicht Device-Owner, sonst Werks-Privileg ins Image. Am Gerät verifizieren (Hardware kann SoftAP — hostapd-Test ok).
- **DeviceService `:12345` am AP-Interface erreichbar?** (Architektur B) — Teil der W0-Klärung.
- **SSID/PW-Provisionierung + Auto-Join des Tablets** (vorkonfiguriert ausliefern vs. QR-Kopplung).

## Hand-off (Claude Code / Opus)
- Branch z. B. `feature/dual-mode` von `feature/louis-feedback`.
- Welle für Welle, je Welle Build + Tests grün; **kein `git add -A`** (CRLF-Phantom — gezielt stagen).
- Geräte-Tests (W0, W3, W5) macht Thomas am ONE + Tablet.
- Reihenfolge: **W0 zuerst** (klärt, ob W3 überhaupt nötig ist) — nicht blind alle Wellen einplanen.
