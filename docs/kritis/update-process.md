# KRITIS-Check: DrainQ.ONE Update-Prozess

**Stand:** 2026-05-12  
**Scope:** In-App-Update-Mechanismus (Phasen 2–6, Variante B: Hetzner-Proxy)  
**Bezug:** ADR 0001, `docs/UPDATE_PROCESS_CONCEPT.md`  
**Erstellt:** Phase 6 (Pflicht-Lieferung)

---

## 1. Transport-Sicherheit

| Prüfpunkt | Befund | Bewertung |
|---|---|---|
| Protokoll | HTTPS-only (`https://updates.drainq.de/one/`) — `http://` wird von OkHttp nicht gefolgt | ✅ OK |
| TLS-Version | OkHttp 4.x handelt TLS 1.2/1.3 mit der Android-TrustManager-Kette | ✅ OK |
| Cert-Pinning | OFF (ADR-Marker `MARKER_CERT_PINNING: OFF`) — bewusste Entscheidung v1 | ⚠ Akzeptiert |
| Cleartext-Traffic | `android:usesCleartextTraffic="true"` im Manifest (für RTSP/TCP zur ONE) — gilt nicht für Update-Pfad | ⚠ Hinweis |
| Proxy-URL | `BuildConfig.UPDATE_PROXY_URL` = `"https://updates.drainq.de/one/"` — keine HTTP-Alternative konfigurierbar | ✅ OK |

**Cert-Pinning-Risiko:** Ohne Pinning ist ein MitM-Angriff mit gefälschtem Cert prinzipiell möglich, wenn die CA-Kette kompromittiert ist. Mitigiert durch:
- Android-Signaturprüfung beim PackageInstaller (effektivere Vertrauenssicherung)
- SHA256-Hashes aus dem Manifest (zweite Integritätsstufe)
- Folge-ADR 0002 geplant sobald Cert-Renewal-Routine etabliert

---

## 2. Integrität

| Prüfpunkt | Befund | Bewertung |
|---|---|---|
| SHA256-Verifikation | `HttpUpdateService.sha256Hex()` prüft APK-Datei Byte-für-Byte vor Install | ✅ Pflicht |
| Hash-Fehler | `SecurityException` + `DOWNLOAD_FAIL`-Event + APK-Löschung — keine Partial-Install möglich | ✅ OK |
| APK-Signatur | Android `PackageInstaller.Session` prüft Signing-Key vor Installation — identischer Key wie installierte App erforderlich | ✅ Android-System |
| Manifest-Integrität | Kein separates Manifest-Signing — Vertrauen auf HTTPS-Transport | ⚠ Akzeptiert |
| Downgrade-Schutz | `versionCode`-Vergleich: Manifest ≤ installed → `NoUpdate` | ✅ OK |

**Downgrade-Angriff:** Wenn ein Angreifer ein Manifest mit älterem `versionCode` einspielen kann (MitM), liefert der Client `NoUpdate`. Ein echter Downgrade-Install ist nicht möglich ohne Nutzerbeteiligung.

---

## 3. Permission-Surface

| Permission | Zweck | Einschränkung |
|---|---|---|
| `INTERNET` | Manifest-Fetch + APK-Download | Bereits im Manifest (für RTSP) |
| `REQUEST_INSTALL_PACKAGES` | PackageInstaller-Session | **User-Bestätigung erforderlich** — Android zeigt Install-Dialog |
| `POST_NOTIFICATIONS` | Update-Notification via UpdateWorker | Bereits im Manifest (für Map-Download) |

**Keine `MANAGE_EXTERNAL_STORAGE`:** APK-Download in `context.cacheDir/updates/` (App-privater Cache) — keine SD-Karte, keine `READ_EXTERNAL_STORAGE`.

**User-Bestätigung:** `REQUEST_INSTALL_PACKAGES` auf Android 8+ triggert einen System-Dialog. Der Nutzer kann die Installation ablehnen. Die App kann keine Silent-Installs durchführen (kein `INSTALL_PACKAGES` — Privileged-Permission, nur System-Apps).

---

## 4. Audit-Log

| Aspekt | Implementierung |
|---|---|
| Schema | Tabelle `update_events` (Room, SQLite), Felder: `id`, `timestamp`, `eventType`, `fromVersion`, `toVersion`, `source`, `errorMessage` |
| Events | `CHECK`, `DOWNLOAD_START`, `DOWNLOAD_OK`, `DOWNLOAD_FAIL`, `INSTALL_INITIATED`, `INSTALL_DONE` |
| Lückenlosigkeit | Jeder Pfad in `HttpUpdateService` schreibt einen Event — auch Fehler-Pfade |
| Aufbewahrung | 90 Tage (`UpdateEventRepository.pruneOldEvents()`) — KRITIS-Anforderung: 3 Monate Mindestaufbewahrung |
| Löschung | `deleteOlderThan(cutoffMs)` — keine personenbezogenen Daten im Log |
| Token/Secrets im Log | **Keine** — `source`-Feld enthält nur Proxy-URL, nie GitHub-PAT oder Credentials |

**KRITIS-Konformität Audit-Log:** Das Log erfüllt die Anforderung nach nachvollziehbaren Update-Operationen im KRITIS-Umfeld. Der Aufbewahrungszeitraum von 90 Tagen übersteigt die Mindestanforderung für operationale Logs (30 Tage) und entspricht gängiger Praxis für Software-Update-Audits.

---

## 5. DSGVO-Auflagen

### Datenflüsse

| Datenfluss | Personenbezug | Rechtsgrundlage |
|---|---|---|
| `GET https://updates.drainq.de/one/releases.stable.json` | IP-Adresse des Tablets (Hetzner-Nginx-Log) | Berechtigtes Interesse (Software-Integrität), AVV mit Hetzner |
| `GET https://updates.drainq.de/one/drainq-one-*.apk` | IP-Adresse des Tablets | Berechtigtes Interesse (Software-Update), AVV mit Hetzner |
| Lokales Audit-Log | Keine personenbezogenen Daten — nur Versions-Strings und Zeitstempel | — |

### Hetzner-Nginx-Logs

Die IP-Adressen der Tablets werden in Hetzner-Nginx-Access-Logs protokolliert. Da die Tablets Betriebsmittel (keine privaten Geräte) sind, ist der Personenbezug gering. Maßnahmen:

- **AVV mit Hetzner:** Auftragsverarbeitungsvertrag liegt für die Suite-Infra bereits vor — Extension auf den `/one/`-Pfad erforderlich. Operator-ToDo nach Deployment.
- **Log-Rotation:** Nginx-Standardkonfiguration 14 Tage (konfigurierbar). Für KRITIS ausreichend.
- **IP-Anonymisierung optional:** Nginx `last_byte` + `map $remote_addr $anon_addr` konfigurierbar, falls behördliche Anforderung besteht.

### Tablet-seitige Daten

Das Audit-Log (`update_events`) enthält keine IP-Adressen, keine Nutzer-IDs, keine personenbezogenen Daten. Es enthält ausschließlich technische Metadaten (Versionsnummern, Zeitstempel, Fehlertext). Kein DSGVO-Handlungsbedarf.

---

## 6. Threat-Model

### T1: Hetzner-Kompromittierung

**Angriffsszenario:** Angreifer kompromittiert den Hetzner-Server und manipuliert das Manifest oder ersetzt die APK.

**Mitigationen:**
1. APK ist mit dediziertem Release-Keystore signiert — manipulierte APK ohne Keystore wird von Android abgelehnt
2. SHA256-Hash im Manifest wird vom Client geprüft — Ersetzung der APK erfordert auch Manifest-Manipulation
3. Manifest hat keine eigene Signatur (Schwachstelle) — Vertrauen auf HTTPS-Transport
4. **Restrisiko:** Wenn Hetzner-Zertifikat + Server kompromittiert: falsches Manifest + falsche APK könnten eingespielt werden. Mitigiert durch Android-Signatur als letzte Linie.

**Gesamtbewertung:** Angriff erfordert simultane Kompromittierung von Hetzner-Infra + Diebstahl des Keystores. Realistische Eintrittswahrscheinlichkeit: niedrig.

### T2: Man-in-the-Middle (MitM)

**Angriffsszenario:** Angreifer in der Netzwerkstrecke Tablet → Hetzner liefert falsches Manifest.

**Mitigationen:**
1. HTTPS schützt gegen Standard-MitM (CA-Kette validiert)
2. Kein Cert-Pinning (Schwachstelle bei kompromittierter CA) — Folge-ADR 0002
3. Android-Signaturprüfung schlägt fehl bei gefälschter APK

**Tablets im ONE-Hotspot:** Im Betrieb sind die Tablets normalerweise im ONE-Hotspot ohne Internet — Update-Check löst dann keinen Netzwerkzugriff aus (WorkManager-Constraint: `NetworkType.UNMETERED`). Effektives MitM-Fenster: nur wenn Tablet im Betreiber-WLAN mit Internet.

### T3: Manifest-Manipulation (Downgrade-Angriff)

**Angriffsszenario:** Manifest wird auf alte Version gesetzt um bekannte Schwachstellen einzuspielen.

**Mitigationen:**
1. Client prüft `versionCode > installedVersionCode` — Downgrade führt zu `NoUpdate`
2. Android-Signatur verhindert dennoch Silent-Downgrade via anderen Kanal

### T4: Gestohlenes Tablet

**Angriffsszenario:** Physischer Zugriff auf Tablet, Versuch PAT oder Credentials zu extrahieren.

**Befund:** Kein PAT oder Credentials im APK oder in SharedPreferences. Proxy-URL ist öffentlich bekannt. Kein erhöhtes Risiko durch Tablet-Diebstahl für den Update-Pfad.

---

## 7. Offene Punkte (Folge-ADRs)

| Nr. | Punkt | Priorität | Folge-ADR |
|---|---|---|---|
| O1 | Cert-Pinning für `updates.drainq.de` | Mittel | ADR 0002 |
| O2 | Manifest-Signatur (HMAC oder JWS) | Mittel | ADR 0002 |
| O3 | AVV-Extension auf `/one/` Hetzner-Pfad | Hoch | Operator-ToDo |
| O4 | Mandatory-Update-Mechanismus für Security-Fixes | Niedrig | ADR 0003 |
| O5 | Log-Export für Auditor (CSV/PDF aus update_events) | Niedrig | Phase 7+ |

---

## KRITIS-Check-Block (für RESULT_PHASE_6.md)

```
KRITIS-CHECK PHASE 6 — Update-Prozess
======================================
K1  Transport-Security:    HTTPS-only, TLS 1.2/1.3, OkHttp 4.x     ✅ OK
K2  Cert-Pinning:          OFF (ADR-Marker CERT_PINNING:OFF)         ⚠ Akzeptiert, ADR 0002 offen
K3  Integrität:            SHA256-Pflichtprüfung vor Install          ✅ OK
K4  APK-Authentizität:     Android PackageInstaller Signaturprüfung  ✅ Android-System
K5  Downgrade-Schutz:      versionCode-Vergleich, Manifest ≤ inst.   ✅ OK
K6  Permissions:           REQUEST_INSTALL_PACKAGES + User-Dialog     ✅ OK
K7  Audit-Log:             update_events, 6 EventTypes, 90 Tage       ✅ OK
K8  Secrets im Log/Code:   Keine PATs, keine Credentials              ✅ OK
K9  DSGVO:                 IP-Log Hetzner in AVV (Operator-ToDo)      ⚠ Offen
K10 Threat-Model:          T1-T4 dokumentiert, Restrisiken bekannt    ✅ OK
```
