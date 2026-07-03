# Dual-Modus E2E-Abnahme ONE ↔ Tablet — Testprotokoll 2026-07-03

**Setup:** ONE (RK3588, e27915a669970b5f, 0.5.0-alpha, DIRECT) + Samsung Galaxy Tab A9+
(SM-X210, R92Y30SLM8X, 0.5.0-alpha, WIFI-Modus). Beide per USB-adb ferngesteuert
(input tap / screencap / logcat / ss / tcpdump).

## Ergebnis in einem Satz

**Der komplette Videopfad ONE→Tablet funktioniert** (V4L2 → CameraFrameBus → H264Encoder
→ RTSP :8554 → LOHS-Hotspot → Tablet-Player, LIVE-Bild verifiziert) — aber erst nach
manuellen Workarounds; die automatische Kopplungskette bricht an 4 Stellen.

## Was funktioniert (abgenommen)

| # | Prüfpunkt | Ergebnis |
|---|---|---|
| 1 | Pairing-Screen (DIRECT): Schalter → Hotspot, QR + SSID/Passwort-Anzeige | ✅ LOHS-Fallback greift (SoftAP scheitert erwartet mit REASON_PRIVILEGE); SSID `AndroidShare_5090` |
| 2 | Hotspot-Stabilität | ✅ überlebt Verlassen des Pairing-Screens und Navigation |
| 3 | Tablet joint Hotspot | ✅ (per adb simuliert; IP via DHCP von der ONE, Gateway = ONE) |
| 4 | ONE Discovery-Broadcast :8555 | ✅ sendet alle 2 s gerichteten Broadcast (tcpdump-verifiziert, `192.168.86.198 → .255:8555`) |
| 5 | RTSP-Video am Tablet | ✅ LIVE (manuelle URL `rtsp://192.168.86.198:8554/1234`, Stream preview „Connected") |
| 6 | H264Encoder-Leistung | ✅ encode 25–43 ms, ~30 fps stabil über 21.000+ Frames |

## Blocker / Fix-Liste (Priorität)

### F1 — Discovery erreicht das Tablet nicht (KRITISCH)
Das Samsung filtert eingehende UDP-**Broadcasts** unterhalb der App weg (selbst ein nackter
`nc`-Listener in der Shell empfängt nichts, während die ONE nachweislich sendet).
Zusätzlich testet der Direkt-Pfad nur die hartkodierte `targetIp = 192.168.43.1`
(`OneHardwareConfig`) — der LOHS-Fallback vergibt aber zufällige Subnetze (hier 192.168.86.x).
**Fix-Idee (robusteste zuerst):**
1. **Gateway-Probe:** Im Hotspot-Setup IST die ONE der DHCP-Gateway — die App kennt die
   Gateway-IP bereits (WiFi-Status-Karte zeigt sie!). Gateway-IP als ersten TCP-:12345-Kandidaten
   probieren → deterministisch, kein Broadcast nötig.
2. Client-initiierte Discovery: Tablet broadcastet Anfrage, ONE antwortet **Unicast**
   (Unicast wird nicht gefiltert). OneRemoteServer lauscht ohnehin auf :8555.
3. `WifiManager.MulticastLock` während der Discovery halten (hilft auf manchen Chipsets).

### F2 — Android wirft internetlose WLANs raus bzw. macht sie nicht zum Default-Netz (KRITISCH)
Das Tablet trennte den Hotspot nach ~10 min selbstständig; solange das WLAN nicht
„VALIDATED" war, hatte die **App** kein Default-Netz (alle Connects → ENETUNREACH,
App-Karte „Not connected to WiFi"), obwohl Shell-Tools durchkamen. Workaround im Test:
`settings put global captive_portal_mode 0` + Reconnect.
**Produkt-Fix:** Beim QR-Join **`WifiNetworkSpecifier`**-Request + `bindProcessToNetwork`
(Per-App-Netz ohne Internet-Anspruch, OS hält die Verbindung, keine Dialoge) — prüfen, ob
`WifiController` (Tablet-Scan-Flow) das schon tut; falls nicht, umstellen. Exakt das
Muster aus BWELLs `EthernetPreferUtil` (vgl. FREMDCODE_BWELL_ANALYSE_2026-07-03.md).

### F3 — InspectionScreen (Tablet) spielt nur über die Discovery-Kette
Die manuell eingegebene RTSP-URL speist nur die Stream-Vorschau im ConnectionScreen;
der Inspektions-Screen bleibt ohne erfolgreiche Hardware-Probe schwarz („No stream active").
**Fix:** `lastRtspUrl`/aktive Stream-URL auch im InspectionScreen als Quelle nutzen.

### F4 — Telemetrie/Steuerung (:12345) ungetestet
Hängt vollständig an F1 (kein manueller IP-Eintrag für den DeviceService möglich).
Nach F1-Fix: Licht/Sonde/Meter vom Tablet aus testen (Abnahme offen).

### Nebenbefunde
- **ONE-Uhr springt auf 2021-01-01 zurück** — auch ohne Reboot (Time-Detector verwirft
  manuelle Zeit bei `auto_time=1` ohne Zeitquelle?). Workaround: `auto_time=0` gesetzt +
  Zeit neu gestellt. Produktthema: Zeit-Sync z. B. beim Tablet-Pairing übernehmen.
- WiFi-Karte am Tablet: SSID `<unknown ssid>` (Location-Permission fehlt für SSID-Read),
  Hinweistext verlangt veraltet „ONE_01". RTSP-Pfad-Doku `:554 vs :8554` ist mit `:8554`
  am Gerät bestätigt.
- QR-Inhalt/Kopplung per App-Scan konnte remote nicht getestet werden (Kamera müsste den
  ONE-Screen sehen) — der adb-WLAN-Join ist nur eine Näherung; der echte Scan-Flow
  (WifiController) ist gesondert am Gerät abzunehmen.

## Workarounds, die im Test aktiv waren (Gerätezustand!)

| Gerät | Änderung | Zweck |
|---|---|---|
| Tablet | `captive_portal_mode=0`, `network_avoid_bad_wifi=0` | Hotspot ohne Internet wird Default-Netz und fliegt nicht raus |
| ONE | `auto_time=0`, Uhr manuell gestellt | Zeit springt nicht mehr auf 2021 zurück |
| ONE | ueventd.rc-Patch `/dev/video* 0666` (overlayfs) | V4L2-Kamera für die App öffenbar (reboot-fest, s. Memory) |
| ONE | minipush deinstalliert (Backup tools/_oem/) | Kamera-/Port-Konflikt beseitigt |
