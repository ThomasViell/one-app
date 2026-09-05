# KRITIS-Check: DrainQ.ONE Update-Prozess

**Stand:** 2026-09-05 (aktualisiert: Portalweg — DrainQ-Portal `license.drainq.com`)
**Scope:** In-App-Update-Mechanismus, Verteilung über das DrainQ-Portal
**Bezug:** ADR 0001 (Nachtrag 05.09.2026), `docs/UPDATE_PROCESS_CONCEPT.md`
**Wegwechsel:** 05.09.2026 (CEO) — der frühere GitHub-Transport (Variante A) gilt nicht mehr.
Werte, die aus dem Portal-Repo nicht belegbar sind (z. B. nginx-Log-Retention), stehen als
„offen, zu klären" — nicht als erfundener Wert.

---

## 1. Transport-Sicherheit

| Prüfpunkt | Befund | Bewertung |
|---|---|---|
| Protokoll | HTTPS-only gegen `license.drainq.com` (`BuildConfig.UPDATE_PROXY_URL`, `app/build.gradle.kts:61`) | ✅ OK |
| TLS-Version | OkHttp 4.x handelt TLS 1.2/1.3 mit der Android-TrustManager-Kette | ✅ OK |
| Cert-Pinning | OFF — bewusste Entscheidung, Folge-ADR offen | ⚠ Akzeptiert |
| Cleartext-Traffic | `android:usesCleartextTraffic="true"` im Manifest (für RTSP/TCP zur ONE) — gilt nicht für den Update-Pfad | ⚠ Hinweis |
| Manifest-/Download-Endpunkte | `GET /api/software/one/releases.beta.json`, `GET /api/software/download/{artifactId}` — Auslieferung nur wenn `IsPublished` (Portal: `SoftwareDistributionController.cs`) | ✅ OK |

**Cert-Pinning-Risiko:** Unverändert: ohne Pinning ist MitM mit gefälschtem Cert bei
kompromittierter CA denkbar. Mitigiert durch Plattformsignatur (siehe K4) und sha256 aus dem
Manifest.

---

## 2. Integrität

| Prüfpunkt | Befund | Bewertung |
|---|---|---|
| SHA256-Verifikation | `HttpUpdateService.sha256Hex()` prüft die APK vor Install; Hash rechnet der **Server** beim Upload (`SoftwareDistributionController.cs`, UploadArtifact) | ✅ Pflicht |
| Hash-Fehler | `SecurityException` + `DOWNLOAD_FAIL`-Event + APK-Löschung — keine Partial-Install | ✅ OK |
| APK-Signatur | Android `PackageInstaller.Session` prüft den Signing-Key; die App ist **plattformsigniert** (`bominwellalias`, ADR-0005) — ein Fremder ohne Herstellerschlüssel kann keine installierbare Fälschung bauen | ✅ Android-System |
| Manifest-Integrität | Kein separates Manifest-Signing — Vertrauen auf HTTPS-Transport zum Portal | ⚠ Akzeptiert |
| Downgrade-Schutz | `versionCode`-Vergleich: Manifest ≤ installed → `NoUpdate` (`HttpUpdateService.kt:67`); Paketverwaltung lehnt niedrigere Codes ab (`INSTALL_FAILED_VERSION_DOWNGRADE`) | ✅ OK |

---

## 3. Permission-Surface

| Permission | Zweck | Einschränkung |
|---|---|---|
| `INTERNET` | Manifest-Fetch + APK-Download | Bereits im Manifest (für RTSP) |
| `REQUEST_INSTALL_PACKAGES` | PackageInstaller-Session | **User-Bestätigung erforderlich** — System-Dialog |
| `POST_NOTIFICATIONS` | Update-Notification via UpdateWorker | Bereits im Manifest (für Map-Download) |

Unverändert: Download in `context.cacheDir/updates/` (App-privat), keine Silent-Installs
(kein `INSTALL_PACKAGES`).

---

## 4. Audit-Log

Geräteseitig unverändert: Tabelle `update_events` (Room), 90 Tage Aufbewahrung
(`UpdateEventRepository.pruneOldEvents()`), keine personenbezogenen Daten, keine
Token/Secrets im Log (`source` enthält nur die Portal-URL).

**Neu portal-seitig:** Freigeben und Veröffentlichen schreiben Audit-Einträge
(`ReleaseApproved`/`ReleasePublished`, Portal: `AdminReleases.razor`) — der 4-Augen-Akt ist
damit nachvollziehbar.

---

## 5. DSGVO-Auflagen

### Datenflüsse

| Datenfluss | Personenbezug | Rechtsgrundlage |
|---|---|---|
| `GET https://license.drainq.com/api/software/one/releases.beta.json` | IP-Adresse des Tablets im Server-Log (eigener nginx) | Berechtigtes Interesse (Software-Integrität) |
| `GET https://license.drainq.com/api/software/download/{artifactId}` | IP-Adresse des Tablets im Server-Log | Berechtigtes Interesse (Software-Update) |
| Lokales Audit-Log | Keine personenbezogenen Daten | — |

### Portal-Server-Logs (statt bisher GitHub/Microsoft)

Der Verteilserver steht in eigener Kontrolle (eigener nginx) — kein Drittanbieter-Log, kein
AVV-Bedarf gegenüber Microsoft/GitHub mehr für diesen Pfad. **Offen, zu klären:** konkrete
Log-Konfiguration und Retention des Portal-nginx — geht aus dem Portal-Repo nicht hervor.
Da die Tablets Betriebsmittel sind, ist der Personenbezug gering.

### Tablet-seitige Daten

Unverändert: `update_events` enthält keine IP-Adressen oder Nutzer-IDs. Kein Handlungsbedarf.

---

## 6. Veröffentlichung ist endgültig (neu, 05.09.2026)

Das Portal kennt keine Zurücknahme: kein Knopf (`AdminReleases.razor`: „Zurückziehen derzeit
nicht möglich"), kein Unpublish-Endpunkt. Der einzige Rückweg ist eine höhere `versionCode`
mit dem alten Stand (siehe `UPDATE_OPS_GUIDE.md`, Abschnitt Rückweg).

**KRITIS-Bewertung:** Betriebsrisiko ab dem ersten Kundengerät — eine schlechte Fassung
kostet Neubau plus Veröffentlichung unter Zeitdruck. Gegenmaßnahmen: Abnahme des Baus auf
einem Testgerät **vor** dem Veröffentlichen-Klick; Rückbau-Commit-Hash im Release-Bericht;
Unpublish-Endpunkt als Portal-Ausbau eingeordnet (eigener Entscheid, Portal-Repo).

---

## 7. Threat-Model

### T1: Portal-Kompromittierung (ersetzt: GitHub-Infrastruktur)

**Angriffsszenario:** Angreifer kompromittiert `license.drainq.com` und legt ein Release mit
manipulierter APK an oder tauscht ein Artefakt aus.

**Mitigationen:**
1. **Plattformsignatur** als Gegenmaßnahme: eine ersetzte APK ohne den Herstellerschlüssel
   wird von Android auf jedem Gerät abgelehnt — der Schlüssel liegt lokal beim CEO, nicht auf
   dem Server
2. sha256 im Manifest wird vom Client geprüft — Artefakt-Tausch erfordert auch
   Manifest-Manipulation (beides liegt auf derselben Server-Seite: Schwachstelle)
3. Anlegen/Upload brauchen den API-Schlüssel (`X-DrainQ-ApiKey`), Freigabe/Veröffentlichung
   ein Admin-Konto — zwei unabhängige Zugänge
4. **Restrisiko:** Server-Kompromittierung + Diebstahl des Plattformschlüssels zusammen.
   Der Plattformschlüssel verlässt die CEO-Konsole nie — Eintrittswahrscheinlichkeit niedrig.

### T2: Man-in-the-Middle (MitM)

Unverändert: HTTPS mit CA-Validierung; kein Cert-Pinning (Folge-ADR offen);
Signaturprüfung schlägt bei gefälschter APK fehl. Tablets im ONE-Hotspot ohne Internet führen
keinen Update-Check aus (WorkManager-Constraint `NetworkType.UNMETERED`).

### T3: Manifest-Manipulation (Downgrade-Angriff)

Unverändert: `versionCode`-Vergleich verhindert Downgrade; `latest` ist live der höchste
veröffentlichte Code — ein altes Manifest „zurückdrehen" gibt es serverseitig nicht.

### T4: Gestohlenes Tablet

Unverändert: keine Tokens/Credentials auf dem Gerät; die Portal-URL ist öffentlich bekannt.
Kein erhöhtes Risiko.

---

## 8. Offene Punkte (Folge-ADRs)

| Nr. | Punkt | Priorität | Folge-ADR |
|---|---|---|---|
| O1 | Cert-Pinning für `license.drainq.com` | Mittel | ADR 0002 |
| O2 | Manifest-Signatur (HMAC oder JWS) | Mittel | ADR 0002 |
| O3 | nginx-Log-Konfiguration und Retention des Portals dokumentieren (DSGVO) | Mittel | Operator-ToDo, Portal-Repo |
| O4 | Mandatory-Update-Mechanismus für Security-Fixes | Niedrig | ADR 0004 |
| O5 | Log-Export für Auditor (CSV/PDF aus update_events) | Niedrig | später |
| O6 | Unpublish/Rücknahme-Endpunkt im Portal | Hoch (ab Kundengerät) | Portal-Repo, CEO-Entscheid |

---

## KRITIS-Check-Block

```
KRITIS-CHECK — Update-Prozess (Stand: 2026-09-05, Portalweg)
==============================================================
K1  Transport-Security:    HTTPS-only gegen license.drainq.com, OkHttp 4.x   ✅ OK
K2  Cert-Pinning:          OFF (Folge-ADR offen)                             ⚠ Akzeptiert
K3  Integrität:            sha256-Pflichtprüfung, server-gerechnet           ✅ OK
K4  APK-Authentizität:     Plattformsignatur (bominwellalias, ADR-0005)      ✅ Android-System
K5  Downgrade-Schutz:      versionCode-Vergleich + Paketverwaltung           ✅ OK
K6  Permissions:           REQUEST_INSTALL_PACKAGES + User-Dialog            ✅ OK
K7  Audit-Log:             update_events (90 Tage) + Portal-Audit            ✅ OK
K8  Secrets im Log/Code:   keine Keys auf Gerät/Repo/Server                  ✅ OK
K9  DSGVO:                 eigener nginx; Log-Retention offen                ⚠ Offen
K10 Threat-Model:          T1 (Portal) mit Plattformsignatur mitigiert       ✅ OK
K11 Endgültigkeit:         keine Rücknahme; Rückweg = höhere Nummer          ⚠ Akzeptiert, O6 offen
```
