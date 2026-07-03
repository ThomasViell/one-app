# BWELL/Bominwell-Fremdcode — Analyse 2026-07-03

**Anlass:** BWELL-Quellcode-Ablage unter `C:\Dev\` sichten; was ist für DrainQ.ONE übertragbar?

**Wichtigster Befund vorab:** `C:\Dev\NSP3CT_ONE` enthält **keinen Code** — nur ein frisch
initialisiertes Git-Repo (null Commits) mit Remote `https://github.com/BWELL-INT/NSP3CT_ONE.git`,
und auch das GitHub-Repo ist **leer** (`git ls-remote` liefert keine Refs). Die Quelle der
ONE-App (`com.bominwell.minipush`) liegt also weder lokal noch im Remote vor.
→ **TODO:** BWELL bitten, das Repo zu pushen (oder korrekten Ablageort klären).
Als Notnagel existiert das APK-Backup `tools/_oem/com.bominwell.minipush_backup.apk`.

Analysiert wurden stattdessen die beiden vorhandenen Schwesterprojekte:

| Projekt | Gerät | Stack |
|---|---|---|
| `C:\Dev\NSP3CT_HMX_Suite` | CCTV-Fahrwagen (T100/HMX2, RK3588-Panel) | IP-Kameras (RTSP/Ethernet), UDP/TCP-Steuerung, daniulive-SDK |
| `C:\Dev\NSP3CT_MHS` | Schachtinspektion (MH360 etc.) | Insta360/OSC-Kamera, UDP-Winde |

---

## 1. Kamera: kein V4L2/UVC im Fremdcode

Die HMX-Kameras sind IP-Kameras (`172.169.10.x`, RTSP :554); Erkennung per HTTP-CGI-Probe-Kette
mit Default-Credentials (`CameraCheckHttpUtil.java`). `CameraLibrary` ist irreführend benannt
(nur OSD-Room-DB). **Für unseren V4L2-Pfad gibt es hier nichts** — das machte nur minipush.
Übertragbar: das Muster „Kopf-Typ per Probe-Sequenz erkennen, dann typspezifisch konfigurieren"
(analog C10/C18 über ttyS5).

## 2. Protokoll-Hausnorm: zyklisches Soll-Zustands-Frame

`socketlibrary/utils/BaseControl.java`: Sende-Thread schickt alle **100 ms** den **kompletten
Soll-Zustand** als ein Frame (zugleich Heartbeat + Paketverlust-Toleranz); Einmal-Kommandos
separat via `runOneCommand()`. TCP-Robustheit (`base/Tcp.java`): Reconnect-Guard alle 2 s,
`setTcpNoDelay(true)`, SO_TIMEOUT 3 s, harter Reconnect nach 3× Read-Timeout.

Drei belegte Frame-Varianten (alle: Header + Länge + SubID + Checksumme):

| Variante | Header | Prüfsumme | Fundort |
|---|---|---|---|
| UDP Crawler (T1) | `A7 7A` + Subframe-Container | — (100-ms-Wiederholung) | `T1CommandCombine.kt` |
| TCP PTZ :8888 | TX `AA len cmd … CRC8(0xD5)`, RX `55 …` | CRC8 | `Ptz3zConnHelper.kt` |
| MHS UDP :20108 | `FE EF` + `0xFF`-Subframes | ModBus-CRC16 (0xA001) | `ControlBaseSend.java` |

**Beim Reverse-Engineering unserer ttyS5-/TCP-12345-Frames zuerst auf genau diese Muster prüfen.**

## 3. Meterzähler/Sonde-Kodierung (T1CableParser.kt)

- Meterzähler: **little-endian, Auflösung 1 mm**, Fehlerwerte `0xFE__` = Error,
  `0xFF__` = nicht verfügbar; Batterie 1 %-Auflösung, 254 = Fehler, 255 = n/a.
- Sonde: 512 Hz / 640 Hz / 33 kHz als 1-Byte-Enum im Lift/Licht-Subframe.

## 4. RTSP-Latenz-Rezept (daniulive-SDK, DnBaseFragment.kt)

FastStartup + LowLatencyMode + HW-Decode (Rockchip) + `HWRenderMode` (MediaCodec→Surface direkt)
+ konfigurierbarer Jitter-Buffer im **50-ms-Raster** + RTSP-over-TCP mit Auto-Fallback.
Außerdem: **Loopback-RTSP-Server :28554** als Stream-Verteiler (UI, Zweitschirm, Recorder
konsumieren denselben lokalen Stream) — Bestätigung unserer CameraFrameBus/OneVideoServer-
Architektur durch den Vendor selbst. Aufnahme ab I-Frame (AVI-Rohschreiber, robust gegen Abriss).

## 5. RK3588-Spezifika (direkt verwertbar)

- **`EthernetPreferUtil.java`** — bindet App-Prozess per `requestNetwork(TRANSPORT_ETHERNET)` +
  `bindProcessToNetwork` ans Ethernet, weil bei aktivem WLAN sonst die Default-Route kippt.
  Erkennung `Build.MODEL.contains("rk3588")`. **Relevant für unseren Dual-Modus (AP + Kamera)!**
- Board-SDK **`com.lztek.toolkit.Lztek`** (JAR): GPIO/LEDs, `hardShutdown()` — prüfen, ob das
  ONE-Board ebenfalls Lztek ist.
- `H264DecoderUtil.kt` — MediaCodec-Annex-B-Einzelbild-Decoder (Snapshot aus Rohstrom ohne
  zweiten Player) — Kandidat für Foto-aus-RTSP am Tablet.
- `UsbSerialHelper.kt` — USB-Serial-Reconnect-Strategie (Rescan-Delays 0/400/1200/2500 ms nach
  Attach, Health-Check 3 s) — Vorlage, falls wir je USB-Serial statt ttyS5 brauchen.

## 6. MHS-Kurzfassung

Insta360-/OSC-Kamera über WLAN, Winde/Licht/Meterzähler über UDP `FE EF`+CRC16-Frames an
`192.168.31.8`/`192.168.42.7:20108`, ebenfalls 100-ms-Zyklus. Kein ONE-Bezug.

---

**Kein einziger Verweis auf minipush/ONE/Schiebekamera in beiden Projekten** — die ONE-Quelle
bleibt zwingend über BWELL zu beschaffen.
