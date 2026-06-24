# DrainQ.ONE — Backlog (Thomas, 24.06.2026)

Punkte aus dem Durchsehen der 0.4.2 am Gerät. **Status: nur vermerkt, noch nicht umgesetzt.**

| # | Screen / Ort | Punkt | Status |
|---|--------------|-------|--------|
| 1 | Verbindungs-/Netzwerk-Screen (RTSP-Eingabe + „Hardware-Status" + „Kabel: local / Crawler: local") | **„Log"-Panel entfernen** — der Debug-Log-Block unten (`[HW] probeEndpoints …`) gehört nicht in die Endkunden-Ansicht. | offen |
| 2 | App-weit / Architektur (eigene Welle, kein Quick-Fix) | **Ein-App-Dual-Modus — NUR ONE** (TWO ist ein anderes Produkt, hier irrelevant): App läuft mal **direkt auf der ONE** (interne Kommunikation), mal **auf einem separaten Tablet über WiFi**. Ziel: EINE gepflegte Version, die beim Start selbst erkennt, ob lokale ONE-Hardware vorhanden ist → Direkt-Modus, sonst Tablet/WiFi-Modus; voller Funktionsumfang in beiden; nur wenige UI-Teile je Modus aus-/einblenden; Portal-Download = immer dieselbe APK. **Stand im Code:** Direkt-Modus vorhanden (`OneInternalHardwareService`, Serial `/dev/ttyS5`+V4L2). **WiFi/Tablet-Pfad wurde am 19.05.2026 ENTFERNT** — `OneHardwareService` (TCP/JSON über WLAN zum Bominwell-DeviceService **:12345** + Video via RTSP) ist seither leer („Migration A", `docs/PLAN_INTERNAL_HARDWARE_INTEGRATION.md` P5). Muss für den Tablet-Modus zurückgeholt werden; liegt in der Git-Historie vor dem 19.05., RTSP-Video-Bausteine (`RtspStreamTester`/`FfmpegRtspRecorder`) noch vorhanden → kein Nullstart. **To-do:** (a) WLAN-Steuerpfad :12345 als zweite `HardwareService`-Impl wiederherstellen; (b) Auto-Erkennung lokal vs. WiFi; (c) modusabhängige UI (siehe Detail unten). **Wichtig (Architektur):** Hotspot + RTSP-Video-Server + DeviceService `:12345` liefert die **Bominwell-Basis der ONE**, NICHT unsere App — die App hat keinen AP-/Server-Code (`WifiController` = nur Client/Join). App ist beidseitig nur Client: ONE-direkt → Serial/V4L2 (kein Netz nötig); Tablet → AP beitreten + RTSP/:12345 lesen. Für den Tablet-Modus also **nichts Server-seitiges** bauen, nur den :12345/RTSP-Client zurückholen. **Offen (Firmware/Setup, kein App-Code):** ist auf der ONE ein WLAN-AP aktiv, den ein Tablet sieht? Am Gerät prüfen. Test mit Tablet+ONE nötig. Aufwand mittel–groß. | offen |

## Detail zu Punkt 2 — Einstellungen je Modus (Analyse 24.06., reine Prüfung, nichts geändert)

Quelle: `ui/screens/settings/SettingsScreen.kt` + `SettingsViewModel.kt`.

**Nur Direkt-auf-ONE (im Tablet-Modus ausblenden):**
- Kiosk-Modus / Geräteeigentümer (`kiosk_mode`) — Lockdown der ONE-Feldeinheit.
- Bildschirmhelligkeit (`screen_brightness`) — steuert das ONE-Display; das Tablet regelt das über sein eigenes OS. *(borderline — von Thomas bestätigen)*

**Nur Tablet/WiFi (im Direkt-Modus ausblenden):**
- „Verbindung"/Netzwerk-RTSP-Connect-Screen (`nav_connection`) inkl. Log-Panel aus Punkt 1.
- WLAN-Verbindungsparameter: `rtsp_url`, `broker_ip`, `broker_port`.

**In beiden Modi sinnvoll (bleiben):**
Auto-Ausblenden, Sprache, Erscheinungsbild (Dunkel/Hell), Offline-Karten, Cloud-Konto, Berichte, Firmenangaben + Logo, Wetter-/Schaden-Presets, OSD-Einstellungen, Update.

**Ganz entfernen (ONE-only-App, Auto-Erkennung ersetzt das):**
- Gerätetyp-Auswahl `device_type` (ONE/TWO) + TWO-Kamerafelder `two_camera_ip/user/password` — TWO ist ein anderes Produkt, hier toter Ballast.

*Hinweis: Klassifizierung „beide vs. modusspezifisch" ist Vorschlag/Analyse — finale Festlegung mit der Umsetzung von Punkt 2.*

## Reverse-Engineering Hotspot (Bominwell) + ONE-Geräteanalyse — 24.06.

**Geräteanalyse ONE (`233b4bd2865177ed`, via adb):** rk3588_s / RK3588-S, **Android 12**, ein Interface `wlan0` (kein ap0). **STA+AP NICHT gleichzeitig** (Hotspot ⇒ kein paralleles WLAN-Internet). SoftAP-Hardware ok (hostapd brachte AP auf 2,4 GHz Kanal 6 hoch; `cmd wifi start-softap` läuft, liefert aber falschen Exit-Code). Kein Bominwell-/172.169.x-AP in Büro-Reichweite (im Feld zu verifizieren).

**Wie Bominwell den Hotspot ansteuerte** (`one-revers/.../ipc/android/sdk/Util/XApManager.java`): `createAP(ssid,pw,typ)` → WLAN-Client aus, dann per **Reflection `WifiManager.setWifiApEnabled(WifiConfiguration, true)`**; `createApInfo` baut SSID + WPA2-PSK (Typen 17 offen / 18 WEP / 19 WPA / 20 WPA2). `closeAp()`=`setWifiApEnabled(...,false)`, `isApEnabled()`=`isWifiApEnabled()`. Config-Modelle: `NetSDK_WIFIApConfig.java`, `NetSDK_WifiApInfos.java`, `NetworkHelper.java`.

**Haken:** `setWifiApEnabled` per Reflection = alte versteckte API, ab Android 8/9 gesperrt → auf der Android-12-ONE NICHT mehr nutzbar. **Konzept übernehmen (WPA2-AP, feste SSID/PW), Umsetzung modernisieren:** auf Android 12 als Geräteeigentümer via `TetheringManager`/`startTethering` oder `WifiManager.startLocalOnlyHotspot` (kein Root nötig). `one-reverse-software` = nur unsere eigene App, kein Hotspot-Code.

**Adressschema (ONE als eigener AP)** — aus `NetworkHelper.java` (`getBroadcastAddress` → `192.168.43.255` im AP-Modus): klassisches Android-Tethering-Subnetz **192.168.43.0/24**, ONE = Gateway **192.168.43.1**, Tablet per DHCP 192.168.43.x. Tablet erreicht die ONE unter `rtsp://192.168.43.1:554/…` (Video) + **192.168.43.1:12345** (DeviceService/Steuerung). Das frühere 172.169.x war das andere Produkt-/Kameraprofil, NICHT der ONE-als-AP-Fall. AP-Konfig-Schema (`NetSDK_WIFIApConfig`, XML): `ESSID`, `IPAddress`, `Netmask`, `ChannelNumber`, `Region`, WPA-`KeyValue` (WPA2-PSK) — SSID/PW waren Laufzeitwerte → eigene vergeben (z. B. `DrainQ-ONE-<Seriennr>` + festes WPA2-PW).

**Moderner Umsetzungsweg (Android 12, Geräteeigentümer) — empfohlen:**
- Device-Owner setzt feste `SoftApConfiguration` (SSID + WPA2-PW) + `TetheringManager.startTethering(TETHERING_WIFI)`; Stop via `stopTethering`. Stabile bekannte SSID/PW → Tablet koppelt automatisch; Gateway 192.168.43.1.
- NICHT das alte `setWifiApEnabled`-Reflection (A12 tot). `startLocalOnlyHotspot` nur falls feste SSID unnötig (zufällige SSID/PW, Subnetz 192.168.49.1 → für Auto-Kopplung schlechter).
- Berechtigung: Device-Owner + ggf. privilegierte Tethering-Permission fest ins ONE-Werks-Image geben (wir kontrollieren das Image) → kein Permission-Risiko. Am Gerät final verifizieren.
- Gesamtablauf: Direkt-Modus erkannt → Hotspot hoch → Tablet joint → Tablet-App = Client gegen 192.168.43.1 (RTSP + :12345 = zurückzuholender `OneHardwareService`). Hotspot an ⇒ kein gleichzeitiges WLAN-Internet (STA+AP-Limit).
