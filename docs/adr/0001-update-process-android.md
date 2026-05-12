# ADR 0001 — Update-Prozess für DrainQ.ONE (Android)

**Status:** Accepted (aktualisiert 2026-05-12 — Wechsel auf Variante A)
**Datum:** 2026-05-12
**Entscheider:** Thomas Viell
**Bezug:** `docs/UPDATE_PROCESS_CONCEPT.md`, `docs/UPDATE_PROCESS_PHASENPLAN.md`
**Referenz-Implementierung:** `drainq_suite_repo` (Velopack-basiert, Variante B — nicht übertragen)

---

## Kontext

DrainQ.ONE muss In-App-Updates ausliefern können:
- Pilot-Tablets im Feld (Samsung SM-X610, ggf. weitere)
- Tablets sind häufig nur im ONE-Hotspot (kein Internet) → Update-Check muss netzfehler-tolerant sein
- Keine Distribution über Play Store (Hardware-/Hotspot-Bindung, KRITIS-Compliance, eigener Release-Takt)
- Repo `ThomasViell/one-app` wurde auf **public** umgestellt

## Entscheidung

~~**Variante B aus Konzept** wird umgesetzt~~ (ursprüngliche Entscheidung, revidiert — siehe Änderung 2026-05-12)

**Variante A aus Konzept** ist die aktive Implementierung:

1. **Source-of-Truth:** public GitHub-Repo `ThomasViell/one-app`, GitHub-Releases per Tag-Push
2. **Mirror:** keiner — Tablets ziehen direkt von GitHub Release Assets
3. **Client:** eigener Kotlin-Updater (kein Velopack, da Windows-only)
4. **Signatur:** dedizierter Release-Keystore, ausschließlich in GitHub Secrets, niemals im Repo
5. **Vertrauen:** SHA256-Hash im Manifest + Android-Signaturprüfung beim Install

## Änderung 2026-05-12: Wechsel auf Variante A

**Begründung:**
- Der Hetzner-Server betreibt einen Container-Stack, kein Bare-Metal-Nginx
- Der Suite-Mirror (`updates.drainq.de`) existiert dort nicht; der aufwand, ihn im Container-Stack aufzusetzen (Nginx-Container, Volume-Mounts, PAT-Verwaltung) ist unverhältnismäßig zu dem gebotenen Mehrwert
- Das Repo wurde auf **public** umgestellt, damit ist ein PAT für Download nicht mehr erforderlich
- GitHub Release Assets sind ohne Authentifizierung über stabile URLs abrufbar

**Konsequenz:**
- Kein Hetzner-Mirror, kein DNS-Aufwand, keine PAT-Rotation, kein Eigen-Server-Betrieb
- Manifest-URL: `https://github.com/ThomasViell/one-app/releases/latest/download/releases.<channel>.json`
- APK-URL: `https://github.com/ThomasViell/one-app/releases/download/v<ver>/drainq-one-<ver>.apk`
- GitHub redirectet `/latest/download/` auf die konkrete Asset-URL — OkHttp folgt dem Redirect (Default)
- User-Agent `DrainQ.ONE/<version>` gesetzt, damit GitHub Downloads nicht gegen Rate-Limit laufen

**KRITIS-Bewertung:** APK-Signatur + SHA256-Prüfung bleiben vollständig aktiv — der fehlende Mirror reduziert nicht die Integritätssicherung. Das Tamperingrisiko ist durch Android-Signaturprüfung (letzte Linie) ausreichend mitigiert.

**DRAINQ_RELEASE_PAT:** Das GitHub Secret ist nicht mehr nötig (kein Mirror-Skript, kein privates Repo). Es kann manuell gelöscht werden — es blockiert aber keinen Build wenn es verbleibt.

## Fixierte Marker (aktualisiert)

```
MARKER_HOSTING:      GITHUB_PUBLIC
MARKER_MANDATORY:    NO
MARKER_CHANNEL_UI:   HIDDEN
MARKER_VERSIONCODE:  FROM_TAG
MARKER_AUTOCHECK:    DAILY_WIFI
MARKER_CERT_PINNING: OFF
```

### Begründungen

- **GITHUB_PUBLIC** statt SUBPATH: Repo ist public, kein PAT, kein Mirror, kein DNS. Direkter Download von GitHub Release Assets.
- **MANDATORY NO**: für Phase 1 keine erzwungenen Updates. Mandatory-Feld bleibt im Manifest-Schema reserviert, Client respektiert es UI-seitig (Banner statt Block). Falls später kritischer Security-Fix nötig: nachrüsten als eigener Branch.
- **CHANNEL_UI HIDDEN**: für Endkunden cleaner. Beta-Channel-Switch hinter 7-fach-Tap auf Versions-String in Settings (Android-Convention). Verhindert versehentliches Umschalten.
- **VERSIONCODE FROM_TAG**: deterministisch aus Git-Tag `vMAJOR.MINOR.PATCH` als `MAJOR*10000 + MINOR*100 + PATCH`. v0.4.1 → 401, v1.0.0 → 10000. Damit kein Drift zwischen Tag, `versionName`, `versionCode` möglich.
- **AUTOCHECK DAILY_WIFI**: WorkManager-Periodic, 24h-Intervall, NetworkType.UNMETERED. Tablets im ONE-Hotspot lösen kein Check aus.
- **CERT_PINNING OFF**: in v1 deaktiviert (Risiko: Cert-Renewal blockt Updates wenn Pinning falsch). Kann später nachgezogen werden, sobald Renewal-Routine etabliert.

## Annahmen, die VOR Erst-Tag-Push final geklärt sein müssen

Diese Punkte werden im Autorun NICHT angefasst — sie sind manuelle Vorbereitungsschritte für den ersten Release-Tag:

1. **Release-Keystore** `oneapp-release.keystore`:
   - Existiert lokal (Repo-Root, gitignored).
   - **Annahme:** wurde noch nicht für signierte Builds genutzt — Alias und Passwörter sind zu vergeben/neu zu erstellen.
   - Falls schon genutzt: Passwörter aus Tresor + Alias dokumentieren.
   - Falls Alias unbekannt: `keytool -list -v -keystore oneapp-release.keystore` (interaktiv).
   - **Sicherheits-Anforderung:** Backup-Kopie in 1Password/Vault, niemals als einzige Kopie auf Build-PC.

2. **Bisher installierte APKs sind Debug-signiert** (`signingConfig = signingConfigs.getByName("debug")` in `app/build.gradle.kts:27`).
   - Konsequenz: Wechsel auf Release-Keystore bricht Update-Pfad für bereits installierte Tablets.
   - **Annahme:** Im Feld nur SM-X610 (Dein Dev-Gerät). Bei Roll-out: einmaliger De-Install + Frisch-Install, lokale Daten vorher per Export-Funktion sichern.
   - Falls mehr Tablets im Feld: Migrations-Strategie als Folge-ADR.

3. **GitHub Secrets** im Repo `ThomasViell/one-app` zu setzen (Settings → Secrets → Actions):
   - `DRAINQ_ONE_KEYSTORE_BASE64` — `base64 -w 0 oneapp-release.keystore`
   - `DRAINQ_ONE_KEYSTORE_PASSWORD` — Store-Password
   - `DRAINQ_ONE_KEY_ALIAS` — Key-Alias
   - `DRAINQ_ONE_KEY_PASSWORD` — Key-Password
   - ~~`DRAINQ_RELEASE_PAT`~~ — **nicht mehr nötig** (Repo public, kein Mirror)

## Konsequenzen

### Positiv
- Kein PAT auf Tablets, kein PAT im Repo, kein PAT in Logs.
- Kein eigener Server für Update-Hosting — kein Betriebsaufwand.
- Keine DNS-Einträge, keine Cert-Verwaltung für Update-Infra.
- SHA256 + Android-Signaturprüfung = doppelte Integrität.
- Rollback: alte GitHub Releases bleiben dauerhaft erhalten.

### Negativ
- GitHub-Verfügbarkeit ist Voraussetzung für Updates (Mitigation: 24h-Cache-Toleranz im Client + ADB-Sideload als Fallback).
- Keystore-Verlust = keine Updates mehr für existierende Installationen (Mitigation: Backup-Strategie in 1Password).
- Erste Migration vom Debug- auf Release-Keystore bricht bestehende Installationen (Mitigation: aktuell nur 1 Dev-Tablet betroffen).
- Code ist öffentlich sichtbar (BWELL-Protokoll-Wissen in Code). Bewertung: vertretbar, da nur dekompiliertes Protokoll-Wissen; falls später nötig, kann Repo wieder auf private umgestellt werden.

### Neutral
- Stable + Beta-Channel parallel pflegbar (1 Manifest pro Channel als GitHub Release Asset).
- Rollback-Mechanismus: Manifest-URL zeigt via `/latest/download/` immer auf neueste Version; Rollback erfordert manuelles Hochladen eines korrigierten Manifests oder Sideload via ADB.

## Alternativen, die verworfen wurden

- **Variante B (Hetzner-Mirror):** verworfen am 2026-05-12, weil Hetzner-Container-Stack keinen Nginx-Mirror hat und Setup-Aufwand unverhältnismäßig ist. Siehe Änderung 2026-05-12.
- **Variante C (PAT im APK):** verworfen wegen Token-Rotation und Tablet-Diebstahl-Risiko.
- **Play Store:** verworfen wegen KRITIS-Kontrolle, B2B-Hardware-Bindung, eigenem Release-Takt.
- **F-Droid-eigenes-Repo:** Overhead unverhältnismäßig.

## Offene Punkte für Folge-ADRs

- ADR 0002 — Cert-Pinning-Strategie (sobald Cert-Renewal-Routine etabliert)
- ADR 0003 — Mandatory-Update-Mechanik (sobald erster Security-Fix dies erfordert)
- ADR 0004 — Multi-Tablet-Migration vom Debug- auf Release-Keystore (sobald >1 Tablet im Feld)
