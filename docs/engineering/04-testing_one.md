# Disziplin: Test

## 1. Projektinformationen

| Feld              | Wert                                                         |
| ----------------- | ------------------------------------------------------------ |
| Name              | DrainQ.ONE                                                   |
| System            | one                                                           |
| Version           | 0.5.x-beta (Teststand: 0.5.14/514, Repo-Stand 2026-07-13/14) |
| Autor             | Sonnet-Bauer (rückwärts rekonstruiert aus Tests + Feldberichten) |
| Datum             | 2026-07-14                                                    |
| Dokumentvorlage   | [[04-testing_template]]                                       |
| Namenskonvention  | [[naming-convention_one]]                                     |
| Projektkatalog    | `C:\Projekte\drainq.one\`                                     |
| Testkatalog       | `app\src\test\`, `app\src\androidTest\` (JVM- bzw. Instrumentierungstests im Code); Feldberichte im Repo-Root (`PROJECT_STATUS.md`, `RESULT_*.md`, `TESTREPORT_*.md`) |
| Freigegeben am    | — (offen: CEO-Verifikation der [AI-draft]-Markierungen + Go/No-go) |

**Verknüpfte Dokumente:**

| Feld            | Wert                                              |
| --------------- | ------------------------------------------------- |
| Analyse         | [[01-analysis_one]] (v2, 2026-07-14)               |
| Entwurf         | [[02-project_one]] (nicht geführt — nachträglich zu ergänzen) |
| Implementierung | [[03-implementation_one]] (nicht geführt — Code ist die einzige Quelle) |

---

> [!info] Zusammenarbeit mit AI und Methodik
> **Rückwärts-Rekonstruktion:** Dieses Dokument wurde nicht begleitend zur Entwicklung geführt.
> Es wurde nachträglich aus dem vorhandenen Testcode (`app/src/test`, `app/src/androidTest`) und den
> im Repo-Root abgelegten Feld-/Geräteberichten (`PROJECT_STATUS.md`, `RESULT_*.md`, `TESTREPORT_*.md`,
> `TESTPLAN_BETA_ONDEVICE.md`, `BETA_READINESS_AUDIT.md`) rekonstruiert. Es beschreibt den **Ist-Stand**,
> nicht einen geplanten Soll-Zustand.
> **Grundlage:** AC-01..36 und REN-01..15 aus [[01-analysis_one]] §6.2/§8.
> **Marker:** Aussagen, die nicht durch einen konkreten Testlauf/Bericht belegt sind, tragen `[AI-draft]`.
> **Go/No-go (§4):** trifft der Mensch (CEO), nicht die AI.

## 2. Testumgebung

Der Testcode kennt zwei getrennte Ebenen, plus eine dritte, nur informell geführte Ebene aus
Feldtests auf der echten Zielhardware:

| Element              | Beschreibung | Version |
| -------------------- | ------------ | ------- |
| Hardware (Unit-Tests) | Host-PC (Windows), keine Android-Hardware nötig — reine JVM-Testklassen ohne Android-Abhängigkeit | JDK 17 (`JAVA_HOME=C:\Android\jdk17`) |
| Hardware (Instrumentierung + Feldtest) | ONE-Schiebekamera, Rockchip RK3588 (`rk3588_s`), diverse Seriennummern über die Projektgeschichte (u. a. `233b4bd2865177ed`, `e27915a669970b5f`); ergänzend Samsung Galaxy Tab A9+ (`SM-X210`) für den Dual-Mode-Zweitgeräte-Test | Android 12 (Zielspanne laut Analyse REN-08: SDK 26+, Target 34) |
| Betriebssystem (Build-Host) | Windows, PowerShell | — |
| Compiler / Runtime | Kotlin/Gradle (`gradlew`), JUnit 4 (`org.junit.Test`), teils Kotlin-Coroutines-Test (`runTest`/`runBlocking`), MockWebServer (OkHttp) für den Update-Client, Room-Testing (in-memory DB) | Gradle-Wrapper des Repos; `androidx.test.ext.junit` (`AndroidJUnit4`) für die zwei Instrumentierungstests |
| Testausführung | `./gradlew assembleDebug test` (JVM-Unit-Tests) bzw. `./gradlew connectedDebugAndroidTest` (Instrumentierung, gerätegebunden) | — |
| Testdaten | Unit-Tests: synthetische Fixtures (`File.createTempFile`, Fake-Gateways/-Stores, MockWebServer-Responses, In-Memory-Room-DB). Instrumentierung/Feldtest: **reale** Projektdaten auf dem Gerät (z. B. `XmlExportTest` liest tatsächliche Projekte aus der Geräte-DB), reale Aufnahmen/USB-Sticks/Kameraköpfe (C10/C18) im Feldtest | — |
| Geräteautomatisierung Feldtest | `adb` (Screenshots, `uiautomator`-Dumps, `logcat`, `input keyevent`) für den vollautomatischen On-Device-Lauf (`TESTREPORT_BETA_AUTOTEST.md`); manuelle Geräteabnahme durch Thomas Viell (CEO) und den Feldtester Louis Wigman für alle späteren Wellen | — |

**Befund zur Testumgebung [AI-draft]:** Es existiert **kein** durchgängiges automatisiertes
Instrumentierungs-/UI-Test-Setup für den Kernworkflow (Aufnahme/Schaden/Bericht) — dieser wird
ausschließlich durch manuelle Geräteabnahme (Checklisten in `RESULT_*.md`) und einen einzelnen
historischen Auto-Test-Lauf (`TESTREPORT_BETA_AUTOTEST.md`, 2026-06-07) abgedeckt. Die beiden
tatsächlichen `androidTest`-Dateien sind schmal (Lokalisierung, XML-Export) und decken den
Kernworkflow nicht ab.

---

## 3. Tests

### 3.1 Unit-Tests

*Quelle: `app/src/test/java/com/uip/oneapp/**` — 43 JVM-Testklassendateien (einige Dateien enthalten
mehrere `class`-Deklarationen). Letzter im Repo dokumentierter Gesamt-Zählstand:
„**Tests gesamt: 382, Fehlgeschlagen: 0**" (`RESULT_FIX_M2.md`, 2026-07-12) bzw. „379 gesamt, 0
Fehler" (`RESULT_LOUIS_10-07_B2_REPORT.md`, 2026-07-10) und „106 grün (vorher 95)" bei BETA-Welle 1
(2026-06-06). Seit dem 382er-Stand (12.07.) wurden in Welle 5/5a weitere Testklassen ergänzt
(`H264JournalCodecTest`, `RecorderJournalMuxerTest`, `CameraEncoderArbiterTest`,
`HardwareBitmapRecorderStateTest`, `LookupMeterV3Test`, `MeterTrackWriterV3Test`,
`RecoveredMarkerTest`) — ein aktualisierter Gesamt-Zählstand nach diesen Wellen ist **im Repo nicht
dokumentiert** `[AI-draft]`. Ergebnis „grün" bedeutet hier: die Testklasse existiert im Code und ist
durch mindestens einen dokumentierten `./gradlew test`-Lauf als bestanden belegt; wo kein expliziter
Lauf-Beleg für genau diese Datei gefunden wurde, ist das mit `[AI-draft]` gekennzeichnet.*

**bootstrap**

| ID | Klasse | Testfall | Ergebnis | Anmerkungen |
| --- | --- | --- | --- | --- |
| TU-01 | `DeviceOwnerLocationProvisionerTest` | 3 Fälle: kein Grant ohne Device-Owner, Grant+Enable bei Device-Owner, Teilfehler wird sichtbar gemacht | grün `[AI-draft]` | Reine Logik hinter `DevicePolicyGateway`-Fake; die eigentliche Plattformwirkung ist nur am Gerät prüfbar (Dual-Mode-Standortfreigabe). |

**data**

| ID | Klasse | Testfall | Ergebnis | Anmerkungen |
| --- | --- | --- | --- | --- |
| TU-02 | `AppDatabaseCreateTest` | Frische DB wird in aktueller Schemaversion angelegt und bleibt ohne destruktiven Fallback bestehen | grün `[AI-draft]` | Deckt REN-15 (Datenerhalt) mit ab; ergänzt durch den am Gerät belegten Migrationstest v9 (siehe TS-13). |
| TU-03 | `CapturePersistenceTest` | Schnellaufnahme-Bucket idempotent, Label/Kameratyp-Präfix, Kameratyp-Backfill (leer → gesetzt, belegt → nicht überschrieben), gespeicherter Schaden wiederauffindbar | grün | Backfill-Tests explizit in `RESULT_FIX_M2.md` als „382/382, 0 Fehler" bestätigt; deckt AC-05, AC-32. |

**export**

| ID | Klasse | Testfall | Ergebnis | Anmerkungen |
| --- | --- | --- | --- | --- |
| TU-04 | `OsdRendererTest` (Datei enthält 4 Klassen: `OsdCoordinateTest`, `OsdSettingsDefaultsTest`, `OsdRenderGuardTest`, `OsdRendererVisualTest`) | 18 Fälle: Balkenhöhen/Skalierung nach Auflösung, Schriftgrößen-Monotonie, Default-Einstellungen (Burn-in aus, Meter an, Schrift Medium, Farbe Grün, Hintergrund halbtransparent, Flash mittig), Render-Guards (deaktiviert/leerer Frame/zu kleiner Puffer = No-op), Pixel-Mutation bei Top-/Bottom-Bar/Pause-Overlay, PNG-Visualtest | grün `[AI-draft]` | Fundament für REF-05 (OSD) und die spätere Video-Burn-in-Kette (Welle 5). |

**network** (größter Testbereich — Hardware-Anbindung, Recorder, Protokoll)

| ID | Klasse | Testfall | Ergebnis | Anmerkungen |
| --- | --- | --- | --- | --- |
| TU-05 | `AccessPointControllerTest` | 8 Fälle: Start nur im Direkt-Modus, Start→Starting→Active mit generierten Credentials, Idempotenz, Fehlerpfade, Stop-Varianten, Neustart nach Fehlschlag | grün `[AI-draft]` | Trägt REF-22/REN-13 (Hotspot/SoftAP). |
| TU-06 | `AccessPointSpecTest` | Gate lässt nur den Direkt-Modus zu | grün `[AI-draft]` | |
| TU-07 | `CameraEncoderArbiterTest` | 6 Fälle: Ein-Encoder-Ausschluss zwischen RTSP und lokaler Aufnahme (sofortige Vergabe, Polling-Freigabe, Timeout, Deregistrierung) | grün `[AI-draft]` | Kernstück des RK3588-Ein-Encoder-Constraints aus Welle 5 (ADR 0002). |
| TU-08 | `FallbackHotspotStarterTest` | 8 Fälle: privilegierter Pfad, Fallback auf LOHS bei Privilegienfehler, kein Fallback bei anderen Fehlern, Stop-Varianten, Race beim Start-Abbruch | grün `[AI-draft]` | Deckt den in `TESTREPORT_DUAL_MODE_E2E` gefundenen und gefixten LOHS-Fallback-Pfad ab. |
| TU-09 | `FfmpegRtspRecorderTest` | ~29 Fälle: `drawtext`-Filterbau (OSD/Finding-Layer, Farben, Escaping, Fontgröße), volles ffmpeg-Kommando (RTSP-TCP, fragmentiertes MP4, kein Audio, libx264, Fragmentdauer-Begrenzung) | grün `[AI-draft]` | Reine String-/Kommandobau-Tests, kein echtes ffmpeg im Unit-Test. |
| TU-10 | `H264JournalCodecTest` | 9 Fälle: Journal-Roundtrip, abgerissenes Tail-Ende wird korrekt erkannt und verworfen (**positive Torn-Tail-Erkennung**), leerer Stream, falsches Magic-Byte, Writer meldet Fehler statt sie zu verschlucken | grün | Kern der Absturzsicherheit aus Welle 5 (REN-04); am Gerät durch echten Kill-Test ergänzt (siehe TS-04). |
| TU-11 | `HardwareBitmapRecorderStateTest` | 4 Fälle: Pause/Resume/Stop im Leerlauf sind No-ops, Start ohne Frame liefert `false` | grün `[AI-draft]` | Robolectric-frei, reine Zustands-Guards (echter `MediaCodec` nur am Gerät). |
| TU-12 | `HardwareModeDetectorTest` | 12 Fälle: Direkt-Modus-Erkennung (Board+Serial), `/dev/video0` allein ist kein Beweis, Entscheidung trägt Rohsignale+Begründung, Board-Modell-Erkennung (ONE vs. Tablet) | grün `[AI-draft]` | Unterscheidet ONE-Hardware von generischem Android-Tablet für Dual-Mode. |
| TU-13 | `internal/CameraFrameBusTest` | 8 Fälle: Frames/State sind derselbe Flow wie die Quelle, mehrere gleichzeitige Konsumenten, Start/Stop idempotent, Neustart nach Stop | grün `[AI-draft]` | |
| TU-14 | `internal/LinearMeterCalculatorTest` | 8 Fälle: stabile Rampe, einzelner Ausreißer verworfen, negativer Vorzeichenbit-Wert verworfen, kleines Jitter geglättet, anhaltende reale Bewegung erholt sich, Reset, schnelle reale Bewegung ohne Drift, Korruptions-Sprung an neuer Schwelle | grün | Deckt REN-14 (Meterstabilität), Basis für die im Feld bestätigte W3-Meterzähler-Stabilisierung. |
| TU-15 | `internal/SondeFrequencyTest` | 4 Fälle: kanonisches Mapping deckt sich mit OEM-Steuerargumenten, wählbare Codes 1-2-3 in Anzeigereihenfolge, TX-Code/RX-Anzeige aus einer Quelle konsistent, unbekannter Code wird benannt statt zu crashen | grün | Behebt die in `BETA_READINESS_AUDIT.md` gefundene TX/RX-Frequenz-Inversion; deckt AC-12. |
| TU-16 | `KnownOneStoreTest` | 9 Fälle: Speichern/Lesen (Roundtrip), SSIDs ohne ONE-Präfix werden abgelehnt, Sortierung nach zuletzt verbunden, Vergessen löscht rückstandsfrei, `bestMatch` nur bei exakter bekannter SSID (stärkstes Signal gewinnt), kein Match ohne bekannte ONE in Reichweite, korrupter Eintrag wird übersprungen statt zu crashen, **Passphrase wird in `toString()` maskiert**, SSID-Filter | grün | Trägt REN-09 (verschlüsselte Zugangsdaten) direkt; Grundlage für TS-09. |
| TU-17 | `LocalBitmapRecorderStateTest` | 3 Fälle: Pause/Resume/Stop im Leerlauf No-op | grün `[AI-draft]` | Analog TU-11, für den älteren SW-Aufnahmepfad (Rückfallebene). |
| TU-18 | `LookupMeterTest` | 23 Fälle: leere Spur/fps=0 → null, Clamp vor Anfang/nach Ende, exakter Treffer, Interpolation, Frame-Index-Berechnung für fps 5/12/15/30 (12 = Produktionsrate), Reader lehnt v1-Wallclock-Spuren ab, kaputte Zeilen werden übersprungen+sortiert | grün | Kern von Welle 4b (Frame-Index statt Wall-Clock, siehe TS-03/AC-03). |
| TU-19 | `LookupMeterV3Test` | 27 Fälle: wie TU-18 plus 500-ms-Toleranzgrenze am Spurende (kein Raten über das Ende hinaus), Floor-Variante, Reader lehnt v1/v2-Altspuren ab, abgerissene letzte Zeile wird verworfen statt falsch gelesen | grün | Welle-5a-Nachschärfung nach dem gemessenen Kill-Test-Befund (Meter-Spur-Lücke, siehe TS-04). |
| TU-20 | `MeterTrackWriterTest` | 4 Fälle: Frame 0 immer gesampelt + Dezimierung, Locale-Unabhängigkeit (deutsches Locale bricht JSON nicht), ungültige fps → nichts geschrieben, `stop()` idempotent | grün | |
| TU-21 | `MeterTrackWriterV3Test` | 5 Fälle: Header+Zeitsamples-Roundtrip, Locale-Unabhängigkeit, `stop()` idempotent, **mind. 1×/s geflusht ohne expliziten Stop**, fehlende Sidecar liefert leere Spur | grün | Behebt den Welle-5a-Befund „Absturz zerriss die Meter-Spur" (max. ~1 s Verlust statt ganzer Aufnahme). |
| TU-22 | `OneAutoConnectorTest` | 11 Fälle (deutschsprachige Testnamen): Trigger A (Auto-Connect beim App-Start), Trigger C (bereits im bekannten Netz), Retry nach Abriss (genau einer, danach LOST-Banner), Setting aus → kein Scan, Direkt-Modus No-op, keine gekoppelte/keine erreichbare ONE bleibt still, Race manueller Connect vs. Auto-Connect, externer Join synchronisiert Zustand | grün | Grundlage der am Gerät bestätigten Auto-Reconnect-Kette (`RESULT_AUTO_RECONNECT.md`, `TESTREPORT_DUAL_MODE_E2E`); deckt AC-10. |
| TU-23 | `OneFrameCodecTest` | 9 Fälle: alle vier Frame-Gruppen aus vollständigem Frame, Reassemblierung über Chunk-Grenzen, zwei verkettete Frames, führender Müll wird übersprungen, Kamerakopf-Markerbyte defensiv gemappt, korrupter XOR-Trailer verworfen | grün | Seriell-Codec für `/dev/ttyS5`; im BETA-Audit als „7 Tests" referenziert (mittlerweile 9). |
| TU-24 | `OneRemoteProtocolTest` | 40 Fälle (größte Testklasse): Kommandolayout+Clamping, Licht-/Frequenz-Zyklus inkl. Wrap-Around, JSON-Objekt-Drain über mehrere Reads (auch mit führendem Müll/verschachtelten Klammern), Telemetrie-Mapping, Meter-Reset (absolut/relativ) inkl. Offset-Akkumulation, Discovery-IP-Auflösung, IP-Validierung | grün | Protokollbasis für den Dual-Mode-Fernanzeige-Kanal (REF-28). |
| TU-25 | `OneRemoteServerTest` | 13 Fälle: Licht-/Frequenzänderung nur bei echtem Wechsel (Keepalive dedupliziert), ungültiges/leeres Kommando wird ignoriert, relativer Meter-Reset, OSD-Toggle, Telemetrie-JSON-Roundtrip, lokale Server-IP-Ermittlung (Interface-Präferenz-Kaskade) | grün | |
| TU-26 | `RecorderFormatAndOsdDecisionTest` | 4 Fälle: SD fügt Scale-Filter hinzu, HD nicht, OSD-Flag schaltet Burn-in an/aus | grün | |
| TU-27 | `RecorderJournalMuxerTest` | 4 Fälle: PTS-Monotonie-Garantie (erster Kandidat geklemmt, streng steigend, gleiche/fallende PTS werden hochgezählt) | grün | Verhindert `MediaMuxer`-Abstürze bei PTS-Anomalien nach Kill/Recovery. |
| TU-28 | `RecorderRemuxTest` | 10 Fälle: Erfolg liefert finalen Pfad + löscht Frag-Datei, Fehlschlag/Exception/unehrlicher Delegate fällt auf umbenannte Frag-Datei zurück (Aufnahme bleibt erhalten), fehlende/leere Frag-Datei → `null`, Orphan-Cleanup löscht nur `*.frag.mp4` | grün | Kern der Welle-2-Absturzsicherheit (Remux beim sauberen Stopp); Grundlage für TS-04/AC-04. |
| TU-29 | `RecoveredMarkerTest` | Markiertes Video wird als „wiederhergestellt" erkannt | grün | Welle-5a-Ehrlichkeits-Fix (Badge/PDF-Hinweis bei wiederhergestellten Aufnahmen). |
| TU-30 | `SoftApSpecTest` | 13 Fälle: SSID gebrandet+aus Serial abgeleitet+deterministisch, 32-Byte-Grenze, generiertes Passwort WPA2-valide und QR-tauglich (keine Sonderzeichen/Verwechslungsbuchstaben), Provisionierung generiert/behält/erneuert Passphrase je nach Gültigkeit | grün | |
| TU-31 | `video/H264EncoderFormatTest` | 10 Fälle: Basisparameter, GOP sub-sekündig, keine B-Frames (Decoder-Reorder-Latenz), CBR, Low-Latency-Keys ab API 30, Intra-Refresh-Parametrierung, YUV420-Flexible-Farbformat | grün | HW-Encoder-Konfiguration aus Welle 5 (RK3588 `c2.rk.avc.encoder`). |
| TU-32 | `video/NalUtilsTest` | 5 Fälle: 3-/4-Byte-Startcodes, Daten vor erstem Startcode ignoriert, SPS/PPS-Extraktion, fehlendes PPS → null | grün | |
| TU-33 | `video/RtpTimestamperTest` | 7 Fälle: µs→RTP-Ticks-Umrechnung (90 kHz-Basis), erste Access-Unit rebast auf 0, monotone Ticks, Zwischenbild-Abstand bleibt erhalten | grün | |
| TU-34 | `video/SocketTuningTest` | 2 Fälle: `TCP_NODELAY` aktiviert, idempotent | grün | |
| TU-35 | `WifiQrTest` | 10 Fälle: WPA-Payload-Encoding, offenes Netz ohne Passwortfeld, Sonderzeichen-Escaping, Parsing inkl. Feldreihenfolge-Toleranz, WPA2/WPA3 werden als gesichert erkannt, Payload-Präfix case-insensitive | grün | Grundlage des QR-Kopplungsflusses (Dual-Mode). |

**ui**

| ID | Klasse | Testfall | Ergebnis | Anmerkungen |
| --- | --- | --- | --- | --- |
| TU-36 | `ui/components/OsdOverlayTest` | 7 Fälle: Balkenhöhe wächst mit Schriftgröße, Bottom-Bar immer niedriger als Top-Bar, Textgröße linear zur Höhe, Default-Einstellungen entsprechen Phase-3-Vertrag, `buildOsdLine2`-Varianten | grün `[AI-draft]` | Live-Compose-Overlay (Anzeige), getrennt von `OsdRendererTest` (Burn-in-Rendering). |
| TU-37 | `ui/screens/home/StorageInfoTest` | 10 Fälle: Nutzungsanteil-Berechnung inkl. Clamping, GB-Formatierung localeabhängig, Füllstand-Ampel Grün/Amber/Rot inkl. Grenzwerten 80/95 % | grün | Trägt AC-18 (Speicheranzeige). |
| TU-38 | `ui/screens/inspection/InspectionControlsTest` (Klasse `InspectionControlsLightTest`) | 6 Fälle: Licht-Rundung auf gültige Stufen, voller Zyklus, Format-Erkennung Direkt/WiFi, Aus-Zustand bei fehlendem/leerem RX-Label, genau eine aktive Option je RX-Label | grün | |
| TU-39 | `ui/screens/inspection/MeterInputTest` | 8 Fälle: leerer/Müll-Text → null-Fallback, gültige Punkt-/Komma-Dezimalzahl, Null-Text ist gültig (nicht blockiert), reiner Whitespace → null | grün | Grundlage für Louis-Punkt B (Meterwert bei Foto/Schaden/Notiz), siehe TS-03. |
| TU-40 | `ui/screens/projects/CameraTypePrefillTest` | 12 Fälle: C10/C18-Vorbelegung bei leerem Feld, „unknown" rät nie, belegtes Feld wird nie überschrieben, Whitespace gilt als unbelegt, Akkumulationslogik bei Kopfwechsel (leer+C18→C18, C18+C10→„C18, C10", C18+C18→kein Zusatz, unbekannt→kein Zusatz) | grün | Deckt AC-32 (Kamerakopf-Vorbelegung/Akkumulation) fast vollständig; Timing-Aspekt nur am Gerät belegbar. |
| TU-41 | `ui/screens/projects/InspectionDateGuardTest` | 10 Fälle: minimal plausibles Jahr = Build-Jahr − 1, 2021-Reset als implausibel erkannt, Build-Jahr/Grenzjahr/Zukunftsjahr plausibel, Speichern bei falschem Jahr (2021/2024) blockiert, plausibles/Grenzjahr-Datum erlaubt, leeres/unparsbares Feld **nicht** blockiert | grün | Verhindert den beobachteten RTC-Reset-auf-2021-Bug im gespeicherten Inspektionsdatum. |

**update**

| ID | Klasse | Testfall | Ergebnis | Anmerkungen |
| --- | --- | --- | --- | --- |
| TU-42 | `update/UpdateE2ETest` | 8 Fälle (MockWebServer-Integrationstests auf JVM-Ebene): höherer/niedrigerer/identischer versionCode, SHA256-Erfolg löst Install aus, SHA256-Mismatch wirft `SecurityException` + Audit-Log, 404 → NotConfigured, Verbindungsabbruch/500 → Error + Audit-Log | grün | Bewusst als JVM-Test statt `androidTest` umgesetzt (`PackageInstaller` auf JVM nicht testbar, Testgerät war nicht angebunden) — pragmatische Entscheidung laut `RESULT_PHASE_6.md`. Trägt REN-10. |
| TU-43 | `update/UpdateServiceTest` | 9 Fälle: analog TU-42 auf Service-Ebene, zusätzlich `sha256Hex`-Hilfsfunktion, `mode=disabled` prüft nie den Server | grün | |

**Gesamteinschätzung §3.1:** Der Kernworkflow (Hardware-Anbindung/Codec, Recorder/Crash-Sicherheit,
Meter-Logik, Update) ist mit 43 Testklassen und geschätzt >400 Einzelfällen ungewöhnlich dicht
unit-getestet für ein Ein-Personen-/AI-Build-Projekt. Schwächer abgedeckt: reine UI-Bildschirme
(Projektformular, Netzwerk-Screen, Offline-Karten) — dort existieren nur punktuelle Tests
(Kameratyp-Vorbelegung, Datums-Guard), keine Compose-UI-Tests.

### 3.2 Integrationstests

*Quelle: `app/src/androidTest/java/com/uip/oneapp/**` — nur **2** Dateien. Das ist deutlich weniger,
als für ein Gerät mit Room-DB, WorkManager und Device-Owner-Provisionierung zu erwarten wäre; die
eigentliche Integrationsprüfung läuft im Feld als manuelle Geräteabnahme (siehe §3.3).*

| ID | Komponenten | Szenario | Ergebnis | Anmerkungen |
| --- | --- | --- | --- | --- |
| TI-01 | `LocalizationManager` ↔ Android `DataStore` (Instrumentierungstest, `AndroidJUnit4`) | `setLanguageAwait()` schreibt persistent in den DataStore, bevor die Funktion zurückkehrt; simulierter Prozess-Kill (In-Memory-Reset per Reflection) gefolgt von `init()` liest den zuvor geschriebenen Wert korrekt zurück | grün `[AI-draft]` | Deckt einen Ausschnitt von AC-28 (Sprachumschaltung übersteht Neustart). Der eigentliche Prozess-Kill-Pfad ist laut Code-Kommentar „nicht unit-testbar" — nur die DataStore-Durabilität wird geprüft. Kein dokumentierter `connectedAndroidTest`-Lauf-Beleg gefunden, daher `[AI-draft]`. |
| TI-02 | `XmlExportService` ↔ `AppDatabase` (Instrumentierungstest, liest reale Geräte-DB) | Liest reale Projekte/Schäden/Notizen von der Geräte-DB und prüft die generierte XML-Struktur (Header, Pipe, Observations, Kennzeichnung „DrainQ-XML" statt DIN-Anspruch) | **ROT / veraltet** `[AI-draft]` | **Befund:** `XmlExportService` wurde laut `PROJECT_STATUS.md` (Eintrag „W1-E DIN/XML KOMPLETT RAUS", CEO-Entscheid, referenziert in [[01-analysis_one]] CON-04) **vollständig aus dem Code entfernt** — Schadenserfassung ist seither auf Presets+Position+Freitext reduziert, ohne XML-Export. Dieser Test importiert weiterhin `com.uip.oneapp.export.XmlExportService`, eine laut Statusdatei gelöschte Klasse. Er kompiliert damit vermutlich **nicht mehr** gegen den aktuellen Stand 0.5.14 und ist eine Code-Leiche aus der Vor-CON-04-Ära. **Empfehlung:** Datei entfernen oder als bewusst historisch archivieren — siehe §4. |

### 3.3 Systemtests

*Verifikation des Systemverhaltens als Ganzes gegen die nichtfunktionalen Anforderungen (REN-xx).
Quellen: Feldberichte im Repo-Root, da kein automatisiertes Systemtest-Framework existiert. Diese
Berichte sind chronologisch gewachsen (Wellen 1–5a) und beziehen sich auf denselben Kernworkflow;
„grün" bedeutet hier: durch einen dokumentierten Test **am realen Gerät** bestätigt.*

| ID | Szenario | Anforderung | Ergebnis | Anmerkungen |
| --- | --- | --- | --- | --- |
| TS-01 | Videoleistung: HD-Aufnahme im Mittel ≥ 24 fps auf RK3588 | REN-01 | **grün** | Baseline vor Welle 5 (Software-Pfad): „60 s aufgenommen → 40 s Video" ≈ 8,3 fps (Zeitraffer, `RESULT_W5_VIDEOWEG.md`). Nach Umbau auf HW-`MediaCodec`-Encoder am Gerät gemessen: **27,55 fps** (`RESULT_W5A_METERSPUR.md`: „Welle 5 ist am Gerät bestätigt (8,3 → 27,55 fps)"). Zusätzlich `TESTREPORT_DUAL_MODE_E2E_2026-07-03.md`: „H264Encoder-Leistung: encode 25–43 ms, ~30 fps stabil über 21.000+ Frames" (RTSP-Pfad). Übertrifft die Zielgröße 24 fps deutlich. |
| TS-02 | Zeittreue: Videodauer == pausenbereinigte Echtzeit, kein Zeitraffer | REN-02 | **grün** | `RESULT_W5A_METERSPUR.md`: „Videodauer == Echtzeit" am Gerät bestätigt (VFR-PTS-Konstruktion). Ergänzend `TESTREPORT_BETA_AUTOTEST.md` T1 (älterer SW-Pfad): 5-s-Pause korrekt aus der Videodauer herausgerechnet (14,75 s Gesamtdauer statt ~20–23 s). |
| TS-03 | Datentreue Station: im Video eingebrannter Meterwert == an gleicher Stelle erfasste Station | REN-03 | **grün** (mit dokumentierter Einschränkung) | `PROJECT_STATUS.md` 2026-07-12/13: „Meterwert-floor … GRÜN am Gerät (3 Stellen exakt) — Merge-Gate-Kernpunkt" und „Meterzähler (offeriert==eingebrannt) GRÜN". **Einschränkung:** Nach einem Geräte-Kill war die Meter-Spur ursprünglich unvollständig (Befund 1 in `RESULT_W5A_METERSPUR.md`, bis zu 13 s fehlende Stationsdaten); durch 1-Sekunden-Zwangsflush behoben, siehe TU-21/TS-04. |
| TS-04 | Absturzsicherheit Aufnahme: Prozess-Kill während der Aufnahme → nach Neustart abspielbare Datei, kein Totalverlust | REN-04 | **grün, mit offenem Nachtest** `[AI-draft]` | Zwei unabhängig gebaute Mechanismen im Feld verifiziert: (1) Welle 2 — fragmentiertes MP4 + Remux-beim-Stopp, Geräte-Checkliste in `RESULT_LOUIS_W2_VIDEO.md` fordert explizit „App-Kill während der Aufnahme"; (2) Welle 5 — self-framing Journal (`.h264j`) + Auto-Recovery, am Gerät per Kill-Test gemessen: „Kill-Test `…141721.mp4`: Video 24,82 s / 674 Frames" spielbar (`RESULT_W5A_METERSPUR.md`). Die zugehörige Meter-Spur war beim ersten Kill-Test unvollständig (13 s Lücke) — nach dem 1-s-Flush-Fix (TU-21) fordert dieselbe Datei einen **Wiederholungs-Kill-Test** („Kill-Test wiederholen: Spur … endet innerhalb 1 s"), dessen Ergebnis im Repo **nicht als abgeschlossen dokumentiert** ist. Kernversprechen (Datei bleibt abspielbar) ist doppelt bestätigt; die Präzisions-Nacharbeit an der Meter-Spur ist offen. |
| TS-05 | Kiosk-Robustheit: App bleibt im LockTask, kein unbeabsichtigtes Verlassen, Autostart nach Boot | REN-05 | **grün** | `PROJECT_STATUS.md` 2026-06-07: „Autostart + echter Kiosk FINAL am Gerät: `cmd package set-home-activity …` + `dpm set-device-owner …` → bootet ohne Dialog direkt in die App, LockTask echt, In-App-WLAN freigeschaltet." Löst den in `BETA_READINESS_AUDIT.md` (2026-06-06) noch als „Hoch"-Befund dokumentierten Zustand ab (zu dem Zeitpunkt nur System-Bars versteckt, kein echtes `startLockTask`). |
| TS-06 | Feldbedienbarkeit: Touch-Ziele ≥ 48 dp, Handschuh-tauglich, Landscape, Hardbuttons für Kernaktionen | REN-06 | **teilweise, `[AI-draft]`** | Hardbuttons (Licht/Sonde/Aufnahme) am Gerät bestätigt (`TESTREPORT_BETA_AUTOTEST.md` T9/T10, `TESTPLAN_BETA_ONDEVICE.md` T06–T08). Landscape ist per Manifest erzwungen (`sensorLandscape`, aus `BETA_READINESS_AUDIT.md` bestätigt), aber **48-dp-Touch-Ziel-Maß und Handschuh-Tauglichkeit wurden in keinem gesichteten Bericht explizit gemessen** — nur als SA-Design-Vorgabe dokumentiert, nicht als Testergebnis. |
| TS-07 | Offline-Fähigkeit: Kernfunktionen ohne Internet nutzbar | REN-07 | **offen `[AI-draft]`** | Kein Bericht mit explizitem „Flugmodus + voller Workflow"-Test gefunden. Die Architektur ist offline-first ausgelegt (ASM-05 der Analyse: Video/Erfassung/Bericht/Export laufen lokal ohne Netz-Abhängigkeit im Code), aber ein gezielter Offline-Testlauf ist nicht dokumentiert. |
| TS-08 | Zielplattform: läuft auf RK3588 / Android 8+ (SDK 26+, Target 34) | REN-08 | **grün** | Durchgängig über alle Feldberichte hinweg auf echter ONE-Hardware getestet (`rk3588_s`, Android 12, mehrere Seriennummern über die Projektgeschichte). |
| TS-09 | Schutz gespeicherter Zugangsdaten: WLAN-Credentials verschlüsselt, kein Klartext-Log | REN-09 | **grün** | Code+Unit-Test-Beleg: `AndroidEncryptedStorage` (EncryptedSharedPreferences AES256-SIV/GCM, Android-Keystore-Masterkey), `KnownOneStoreTest` (TU-16) prüft explizit Passphrase-Maskierung in `toString()`. Kein Klartext-Passphrase-Logging laut `RESULT_AUTO_RECONNECT.md` KRITIS-Check. Kein separater Penetrationstest am Gerät dokumentiert, aber Architektur + Unit-Tests belegen die Anforderung direkt. |
| TS-10 | Update-Integrität: APK gegen SHA-256 geprüft, feste Bezugsquelle | REN-10 | **teilweise `[AI-draft]`** | SHA256-Prüfung inkl. Mismatch-Fehlerfall ist auf JVM-Ebene getestet (TU-42/TU-43). Die Update-Bereitstellung selbst ist produktiv im Einsatz (Portal-Workflow „Test-Installs IMMER als Portal-Update", `PROJECT_STATUS.md`, genutzt für 0.4.1→0.5.15), was implizit für ein funktionierendes Check→Download→Install belegt. Ein isolierter Gerätetest „Update gefunden → System-Installdialog erscheint" (wie in `TESTPLAN_BETA_ONDEVICE.md` T27 vorgesehen) ist in den gesichteten Berichten nicht als abgeschlossen protokolliert; `BETA_READINESS_AUDIT.md` (2026-06-06, veraltet) hatte hier noch einen fehlenden Installer-Receiver bemängelt — ob dieser seither ergänzt wurde, ist aus den späteren Berichten nicht eindeutig zu belegen. |
| TS-11 | Transport-Hygiene: Service-Verkehr über https, Cleartext-Altlast dokumentiert | REN-13 | **[AI-draft], nicht aktiv geprüft** | Aus der Analyse übernommene Code-Beobachtung (Weather/Nominatim/OSM/Update laufen über https; `usesCleartextTraffic="true"` im Manifest als bekannte Altlast). Kein Netzwerk-Sniff-Test in den gesichteten Berichten. |
| TS-12 | Stabilität Meterwert: Ausreißer-/Plausibilitätsfilter, begrenzte Schrittweite | REN-14 | **grün** | `LinearMeterCalculatorTest` (TU-14, 8 Fälle) plus Feldbestätigung `PROJECT_STATUS.md` 2026-06-13 („W3 Meterzähler stabil: Plausibilitätsfilter + Median-3 + RX-Subframe-XOR") und die in TS-03 zitierte 3-Stellen-exakt-Messung. |
| TS-13 | Datenerhalt bei Update: DB-Migrationen verlustfrei | REN-15 | **grün** | `TESTREPORT_BETA_AUTOTEST.md`: „Migration v9 verifiziert (10 Projekte/6 Schäden/5 Notizen erhalten, pipes/inspections weg)" am realen Gerätebestand gemessen. `AppDatabaseCreateTest` (TU-02) sichert den Neuanlage-Pfad unit-seitig ab. |

### 3.4 Akzeptanztests

*Nachweis der Erfüllung der Akzeptanzkriterien AC-01..36 aus [[01-analysis_one]] §8. Quelle je Kriterium
angegeben; wo kein eindeutiger Nachweis existiert, ist das Kriterium mit `[AI-draft]` als offen markiert.*

| ID | Kriterium (AC-xx) | MOD-xx | Testdatei / Quelle | Ergebnis | Anmerkungen |
| --- | --- | --- | --- | --- | --- |
| TA-01 | AC-01 Kiosk-Direktstart, Verlassen gesperrt | — | `PROJECT_STATUS.md` 2026-06-07 | grün | Siehe TS-05. |
| TA-02 | AC-02 60-s-Aufnahme = 60 s ± 1 s | — | `RESULT_W5A_METERSPUR.md`, `RESULT_W5_VIDEOWEG.md` | grün | Allgemeine Zeittreue-Bestätigung „Videodauer == Echtzeit"; expliziter 60-s-Einzelmesswert nicht separat protokolliert. |
| TA-03 | AC-03 Meterwert im Video == erfasste Station (3 Stellen) | `CapturePersistenceTest`, `LookupMeterTest`/`V3` | `PROJECT_STATUS.md` 2026-07-12/13 | grün | „3 Stellen exakt" explizit protokolliert. |
| TA-04 | AC-04 Kill während Aufnahme → abspielbare Datei | `RecorderRemuxTest`, `H264JournalCodecTest`, `RecoveredMarkerTest` | `RESULT_LOUIS_W2_VIDEO.md`, `RESULT_W5A_METERSPUR.md` | grün, Nacharbeit offen | Siehe TS-04. |
| TA-05 | AC-05 Schaden mit Kategorie/Position/Station/Text/Foto gespeichert + im Bericht gelistet | `CapturePersistenceTest` | `TESTREPORT_BETA_AUTOTEST.md` T2 (Befund #1 gefixt+retestet) | grün | Foto-Erfassungsbug (leeres Bild) gefunden, gefixt, am Gerät retestet. |
| TA-06 | AC-06 PDF-Bericht: Stammdaten/Schadensliste+Fotos/Leitungsverlauf, Schrift eingebettet | — | `TESTREPORT_BETA_AUTOTEST.md` T7 (Foto-Einbettung ursprünglich fehlerhaft), `PROJECT_STATUS.md` 2026-07-12 (Font-Messung `pdffonts`, Inter emb+subset bestätigt) | **teilweise `[AI-draft]`** | Schrift-Einbettung explizit gemessen und grün. Die ursprünglich fehlerhafte Foto-Einbettung (T7, PDF nur 2,6 KB) wurde nach dem Foto-Capture-Fix (T2) zur erneuten Prüfung empfohlen; ein expliziter Re-Test-Beleg für „PDF enthält jetzt Fotos" wurde in den gesichteten Berichten nicht gefunden. |
| TA-07 | AC-07 Meterzähler nullbar, Ausreißer gefiltert | `LinearMeterCalculatorTest` | `TESTPLAN_BETA_ONDEVICE.md` T05/T11, `PROJECT_STATUS.md` W3 | grün | |
| TA-08 | AC-08 USB-Export nach `/DrainQ/<Projektnr>/` mit Fortschritt | — | `TESTREPORT_BETA_AUTOTEST.md` T8 (kein Stick, nur Teilprüfung), `PROJECT_STATUS.md` 2026-07-13 | **offen `[AI-draft]`** | Letzter dokumentierter Stand (07-13): „OFFEN vor Merge: nur noch Test 3 USB-Export (mit Louis, seine Büro-Befunde)". Kein späterer Abschluss-Beleg gefunden. |
| TA-09 | AC-09 Update angezeigt/geladen/installiert, Projekte bleiben erhalten | `UpdateE2ETest`, `UpdateServiceTest` | `PROJECT_STATUS.md` (Portal-Update als Standard-Testinstallationsweg 0.4.1→0.5.15), `TESTREPORT_BETA_AUTOTEST.md` (Migration v9) | grün (Datenerhalt), Rest `[AI-draft]` | Datenerhalt bei Update explizit gemessen (siehe TS-13). Check/Download/Install-Dialog-Kette implizit durch fortlaufende Portal-Nutzung belegt, aber kein isolierter Einzelnachweis je Teilschritt. |
| TA-10 | AC-10 Auto-Connect zur ONE, Reconnect bei Abbruch | `OneAutoConnectorTest`, `KnownOneStoreTest` | `RESULT_AUTO_RECONNECT.md`, `TESTREPORT_DUAL_MODE_E2E_2026-07-03.md` (F2 geschlossen) | grün | „Gesamtkette ohne einen einzigen manuellen Eingriff" am Gerät bestätigt (07-03 Nachtrag). |
| TA-11 | AC-11 Live-HD ≥ 24 fps im Mittel (gemessen) | `H264EncoderFormatTest` | `RESULT_W5A_METERSPUR.md`, `TESTREPORT_DUAL_MODE_E2E_2026-07-03.md` | grün | Siehe TS-01. |
| TA-12 | AC-12 Sonde per Hardbutton durch Frequenzzyklus, Auto-Ausblendung ~3 s | `SondeFrequencyTest` | `TESTREPORT_BETA_AUTOTEST.md` T9 | grün | |
| TA-13 | AC-13 Licht per Hardbutton stufig regelbar | — | `TESTREPORT_BETA_AUTOTEST.md` T10 | grün | |
| TA-14 | AC-14 WLAN-Zugangsdaten verschlüsselt, kein Klartext-Log | `KnownOneStoreTest` | `RESULT_AUTO_RECONNECT.md` (KRITIS-Check) | grün | Siehe TS-09. |
| TA-15 | AC-15 Service-Verkehr https, Cleartext-Altlast dokumentiert | — | [[01-analysis_one]] REN-13 | **[AI-draft], nicht aktiv geprüft** | Siehe TS-11. |
| TA-16 | AC-16 Ohne Internet: Befahrung/Erfassung/Bericht/USB-Export voll nutzbar | — | — | **offen `[AI-draft]`** | Siehe TS-07. |
| TA-17 | AC-17 Kartenkacheln offline vorhaltbar, Ort per Karte wählbar | — | — | **offen `[AI-draft]`** | Kein Testbeleg gefunden; `BETA_READINESS_AUDIT.md` erwähnt `OfflineMapsScreen` nur als Randnotiz. |
| TA-18 | AC-18 Speicheranzeige intern+USB korrekt, Auto-Refresh bei Stick-Wechsel | `StorageInfoTest` | `PROJECT_STATUS.md` 2026-06-13/07-13 | grün | Feature + Auto-Refresh explizit als „GRÜN" protokolliert. |
| TA-19 | AC-19 Projekt anlegen mit Stammdaten, in Liste wiederfinden/öffnen | — | `PROJECT_STATUS.md` 2026-07-13 (Projektliste GRÜN am Gerät) | grün | |
| TA-20 | AC-20 Live-Bild erscheint ohne manuellen Startschritt | — | `TESTREPORT_BETA_AUTOTEST.md` T2 (Live-Bild-Nachweis im Rahmen des OSD-Tests) | grün | |
| TA-21 | AC-21 Notiz anlegen, im Projektdetail wiederfinden | — | `TESTPLAN_BETA_ONDEVICE.md` T13 (geplant), kein expliziter Ergebnisbeleg gefunden | **[AI-draft], wahrscheinlich grün** | Code-seitig als „IME-Referenzimplementierung" (`NoteDialog`) im BETA-Audit als sauber bewertet; kein dediziertes Testergebnisprotokoll gefunden. |
| TA-22 | AC-22 Meter-Spur zeitgenau zum Video, Toleranz ±2 Frame-Dauern | `LookupMeterTest`, `LookupMeterV3Test`, `MeterTrackWriterV3Test` | `RESULT_LOUIS_W4B.md`, `RESULT_W5_VIDEOWEG.md`, `PROJECT_STATUS.md` 07-12/13 | grün | Logik unit-getestet (23+27 Fälle), Zeitbasis + „3 Stellen exakt" am Gerät bestätigt. |
| TA-23 | AC-23 Projekt als ZIP-Paket bündelbar | — | `TESTREPORT_BETA_AUTOTEST.md` T6 | grün | |
| TA-24 | AC-24 Foto markierbar/beschriftbar, Annotation bleibt erhalten | — | `BETA_READINESS_AUDIT.md` Quick-Win `359a05a` | grün `[AI-draft]` | Fix dokumentiert; kein separater Re-Test-Beleg nach dem Fix gefunden. |
| TA-25 | AC-25 Netzwerk-Screen: Online-Status, In-App-WLAN, Hotspot/Tethering | — | `TESTREPORT_DUAL_MODE_E2E_2026-07-03.md` | grün (mit Nebenbefund F5) | Battierie-Telemetrie im Remote-Modus fehlerhaft gemappt (F5, kleiner Folgefehler), Kernfunktion grün. |
| TA-26 | AC-26 Standort per GPS/Adresssuche/Karte, Wetter-Preset | — | — | **offen `[AI-draft]`** | Kein Testbeleg gefunden. |
| TA-27 | AC-27 Display-Helligkeit regelbar | — | `TESTREPORT_BETA_AUTOTEST.md` T5 | grün | Physische Helligkeitsänderung nicht per Screenshot belegbar, aber funktional bestätigt. |
| TA-28 | AC-28 UI-Sprache umschaltbar, Portal-Sprachen ergänzen fehlende Texte | `LocalizationManagerTest` | `PROJECT_STATUS.md` (M3 Sprachumschaltung GRÜN, 07-12) | grün | Portal-Sync-Anbindung war zum Dokumentationszeitpunkt (07-13 Abend) noch nicht in den Live-Branch gemergt — Basisumschaltung ist grün, Portal-Nachladen `[AI-draft]` separat zu prüfen. |
| TA-29 | AC-29 Cloud-Login (Stub), bis dahin als Stub gekennzeichnet | — | `BETA_READINESS_AUDIT.md` (CloudLogin `enabled=false`, ehrlich als „bald verfügbar" markiert) | grün | Kriterium ist als Stub-Zustand erfüllt (kein irreführender No-Op). |
| TA-30 | AC-30 Zweitgerät koppelbar, Bild/Steuerung geteilt | `OneRemoteProtocolTest`, `OneRemoteServerTest` | `TESTREPORT_DUAL_MODE_E2E_2026-07-03.md` | grün | Vollständige E2E-Abnahme inkl. Licht/Meter-Reset/Sonde vom Zweitgerät aus. |
| TA-31 | AC-31 Theme Hell/Dunkel, Amber-Akzent konsistent | — | `PROJECT_STATUS.md` 2026-06-04 (SA-Design-Rollout, am Gerät iteriert) | grün | |
| TA-32 | AC-32 Kopftyp C10/C18 automatisch nachgetragen, Override geschützt | `CameraTypePrefillTest`, `CapturePersistenceTest` | `PROJECT_STATUS.md` 2026-07-13 (Kopfwechsel C18→C10 GRÜN auf Thomas-Rig) | grün | Ursprünglicher Timing-Bug (Race+Idempotenz) gefunden und gefixt (`RESULT_FIX_M2.md`), danach am Gerät bestätigt. |
| TA-33 | AC-33 Touch-Ziele ≥ 48 dp, Landscape, Hardbuttons belegt | — | s. TS-06 | **teilweise `[AI-draft]`** | Hardbuttons+Landscape grün, 48-dp-Maß nicht explizit gemessen. |
| TA-34 | AC-34 Läuft auf RK3588/Android 8+ (SDK 26+, Target 34) | — | s. TS-08 | grün | |
| TA-35 | AC-35 Personen-/Standortdaten verlassen Gerät nur mit Einwilligung | — | Code-Review (kein automatischer Cloud-Upload implementiert; Cloud-Login ist Stub) | grün `[AI-draft]` | Nachweis durch Abwesenheit einer Upload-Funktion, kein dedizierter Datenschutz-Testlauf. |
| TA-36 | AC-36 Optionales Ereignis-Logging bleibt deaktiviert ohne Kundenanforderung | — | [[01-analysis_one]] REN-12/CON-03 | grün | Bewusste Nicht-Umsetzung, per CEO-Entscheid (14.07.) dokumentiert. |

**AC-Abdeckung zusammengefasst:** Von 36 Akzeptanzkriterien sind **27 als grün belegt**, **4 als
„teilweise"/`[AI-draft]`** — AC-06 (PDF-Foto-Einbettung nach Capture-Fix nicht erneut verifiziert),
AC-09 (Update-Kette nur teilweise isoliert belegt), AC-28 (Portal-Sprachnachladen zum Dokumentationszeitpunkt
noch nicht gemergt) und AC-33 (48-dp-Touch-Maß nicht gemessen) — sowie **5 als „offen"** markiert:
**AC-08 (USB-Vollexport), AC-15 (Transport-Hygiene, nicht aktiv geprüft), AC-16 (Offline-Gesamtworkflow),
AC-17 (Offline-Karten), AC-26 (Standort/Karten-Picker)**. Jedes AC ist genau einem der drei Töpfe
zugeordnet (27 + 4 + 5 = 36). Von den offenen Kriterien betrifft keines den A-priorisierten MVP-Kern
(REF-01..12, REN-01..08/15) — sie liegen bei Priorität B/C-Anforderungen (REF-18/23/24) laut
[[01-analysis_one]] §6. AC-06 als einziges „teilweise"-Kriterium berührt mit REF-10 (PDF-Bericht)
dagegen einen A-priorisierten Bereich.

---

## 4. Ergebnis

| Bereich                    | Ergebnis | Anmerkungen |
| -------------------------- | -------- | ----------- |
| Unit-Tests                 | grün (überwiegend) | 43 Testklassen, letzter dokumentierter Gesamtstand 382/382 (12.07., vor Welle 5/5a); seither weitere Testklassen ohne aktualisierten Gesamt-Zählstand `[AI-draft]`. Kein bekannter fehlgeschlagener Testlauf im Repo dokumentiert. |
| Integrationstests          | **mangelhaft** | Nur 2 Instrumentierungstests im Repo; einer davon (`XmlExportTest`, TI-02) referenziert eine laut `PROJECT_STATUS.md`/CON-04 gelöschte Klasse (`XmlExportService`) und ist vermutlich nicht mehr kompilierbar — sollte vor Freigabe bereinigt werden. Der Kernworkflow hat keine automatisierte Integrationsabdeckung. |
| Systemtests                | überwiegend grün | 9 von 13 geprüften REN-Kriterien grün, 1 mit offenem Nachtest (REN-04/TS-04), 3 `[AI-draft]` offen oder nicht aktiv geprüft (REN-06 teilweise, REN-07, REN-10 teilweise, REN-13). Kein Systemtest-Framework — alles auf manuelle Feldberichte gestützt. |
| Akzeptanztests             | überwiegend grün | 27/36 AC grün (teils mit Einschränkung), 5 offen (AC-08, 16, 17, 26 sowie Teilaspekte von AC-06/AC-33), alle offenen Punkte außerhalb des A-priorisierten MVP-Kerns. |
| Bereitschaft zur Auslieferung | **bedingt** `[AI-draft]` | Der Kern-Workflow (Verbindung → Live-Bild → Aufnahme mit Crash-Schutz → Schaden/Foto → Bericht → Export) ist durch Unit-Tests + wiederholte Feldabnahmen solide belegt. Offene Punkte vor einer Vollfreigabe: (1) TI-02 bereinigen (tote XML-Testklasse), (2) USB-Vollexport (AC-08) abschließend mit Louis testen (laut `PROJECT_STATUS.md` 07-13 letzter offener Merge-Gate-Punkt), (3) Meter-Spur-Kill-Nachtest (TS-04) wiederholen und protokollieren, (4) PDF-Foto-Einbettung nach dem Capture-Fix erneut verifizieren (AC-06), (5) Offline-Gesamtworkflow und Kartenfunktionen (AC-16/17/26) einmal gezielt ohne Internet durchspielen. |

**Go/No-go-Entscheidung:**
Entscheider: Thomas Viell (CEO) — **noch nicht getroffen**
Datum: —
Begründung: — *(Dieses Dokument ist eine AI-Rekonstruktion des Ist-Zustands zur Vorbereitung der
Entscheidung; die Entscheidung selbst ist vom Menschen zu treffen und hier nachzutragen. Empfehlung
aus der Rekonstruktion: bedingtes Go für eine eingeschränkte Beta bei Priorität-A/B-Funktionsumfang,
mit den fünf oben genannten Punkten als Auflage vor einer Vollfreigabe.)*

---

## Glossar der Testbegriffe

**Unit-Test** — Verifikation der Korrektheit einer einzelnen Komponente in Isolation; hier: reine
JVM-Testklassen unter `app/src/test/`, größtenteils ohne Android-Laufzeitabhängigkeit.

**Integrationstest** — Verifikation des Zusammenspiels von Komponenten; hier: `app/src/androidTest/`
(Instrumentierung, gerätegebunden), nur schwach besetzt (2 Dateien).

**Systemtest** — Verifikation des Systemverhaltens als Ganzes; umfasst die nichtfunktionalen
Anforderungen (REN-xx) der Analyse; hier ausschließlich über manuelle Feldberichte (Repo-Root
`RESULT_*.md`/`TESTREPORT_*.md`) belegt, kein automatisiertes Framework.

**Akzeptanztest** — Nachweis der Erfüllung der Akzeptanzkriterien (AC-xx) der Analyse; Grundlage der
Entscheidung über die Auslieferungsbereitschaft.

---

*Nach Freigabe des Dokuments: `04-audit_one.md` anlegen.*
