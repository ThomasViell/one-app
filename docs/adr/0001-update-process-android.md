# ADR 0001 — Update-Prozess für DrainQ.ONE (Android)

**Status:** Accepted
**Datum:** 2026-05-12
**Entscheider:** Thomas Viell
**Bezug:** `docs/UPDATE_PROCESS_CONCEPT.md`, `docs/UPDATE_PROCESS_PHASENPLAN.md`
**Referenz-Implementierung:** `drainq_suite_repo` (Velopack-basiert, Variante B)

---

## Kontext

DrainQ.ONE muss In-App-Updates ausliefern können:
- Pilot-Tablets im Feld (Samsung SM-X610, ggf. weitere)
- Tablets sind häufig nur im ONE-Hotspot (kein Internet) → Update-Check muss netzfehler-tolerant sein
- Keine Distribution über Play Store (Hardware-/Hotspot-Bindung, KRITIS-Compliance, eigener Release-Takt)
- Identisches Vertrauensmodell wie Drainq Suite (Tag → Build → privates GitHub-Release → Mirror → Client)

## Entscheidung

**Variante B aus Konzept** wird umgesetzt:

1. **Source-of-Truth:** privates GitHub-Repo `ThomasViell/one-app`, GitHub-Releases per Tag-Push
2. **Mirror:** Hetzner-Update-Proxy `updates.drainq.de` mit zusätzlichem Subpfad `/one/`
3. **Client:** eigener Kotlin-Updater (kein Velopack, da Windows-only)
4. **Signatur:** dedizierter Release-Keystore, ausschließlich in GitHub Secrets, niemals im Repo
5. **Vertrauen:** SHA256-Hash im Manifest + Android-Signaturprüfung beim Install

## Fixierte Marker (für Autorun-Phasen 2-7)

```
MARKER_HOSTING:      SUBPATH
MARKER_MANDATORY:    NO
MARKER_CHANNEL_UI:   HIDDEN
MARKER_VERSIONCODE:  FROM_TAG
MARKER_AUTOCHECK:    DAILY_WIFI
MARKER_CERT_PINNING: OFF
```

### Begründungen

- **SUBPATH** statt SUBDOMAIN: nutzt bestehendes Let's-Encrypt-Cert auf `updates.drainq.de`, kein DNS-A-Record, kein zweiter certbot-Lauf. Trennung zur Suite über Pfad `/one/` ausreichend.
- **MANDATORY NO**: für Phase 1 keine erzwungenen Updates. Mandatory-Feld bleibt im Manifest-Schema reserviert, Client respektiert es UI-seitig (Banner statt Block), Auto-Exit bei Ablehnung wird **nicht** implementiert. Falls später kritischer Security-Fix nötig: nachrüsten als eigener Branch.
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
   - `DRAINQ_RELEASE_PAT` — Read-Only-PAT auf das Repo (für Mirror auf Hetzner). Falls vorhanden vom Suite-Setup wiederverwendbar, sofern Scope `repo` umfasst.

4. **Hetzner-Server** (gleicher Host wie Suite-Mirror):
   - PAT-Datei `/etc/drainq/github-pat-one` neu anlegen ODER bestehende `/etc/drainq/github-pat` wiederverwenden, falls Scope ausreicht.
   - Webroot `/var/www/drainq-updates/one/` muss von `www-data` schreibbar sein.
   - Phase 5 liefert die exakten One-Shot-Befehle.

## Konsequenzen

### Positiv
- Kein PAT auf Tablets, kein PAT im Repo, kein PAT in Logs.
- Bestehende Hetzner-Infra wird genutzt — keine neue VM, kein neues Cert.
- Update-Pipeline identisch zur Suite — gemeinsamer Ops-Runbook möglich.
- SHA256 + Android-Signaturprüfung = doppelte Integrität.

### Negativ
- Hetzner-Verfügbarkeit ist Single-Point-of-Failure für Updates (mitigation: 24h-Cache im Client + Tablet-side-load via ADB als Fallback).
- Keystore-Verlust = keine Updates mehr für existierende Installationen (mitigation: Backup-Strategie in 1Password).
- Erste Migration vom Debug- auf Release-Keystore bricht bestehende Installationen (mitigation: aktuell nur 1 Dev-Tablet betroffen).

### Neutral
- Stable + Beta-Channel parallel pflegbar (1 Manifest pro Channel im Webroot).
- Rollback-Mechanismus über Manifest-URL-Switch in `update.config` (`releases.beta.json` → fix-Build).

## Alternativen, die verworfen wurden

- **Variante A (public Repo):** verworfen, weil BWELL-Reverse-Engineering-Details (`HANDOVER.md`, `OneHardwareService.kt`, dekompilierte minipush-Referenzen) nicht öffentlich gemacht werden sollen.
- **Variante C (PAT im APK):** verworfen wegen Token-Rotation und Tablet-Diebstahl-Risiko. Suite-Code (`migrate-to-proxy.ps1`) zeigt, dass Suite genau diesen Migrationspfad bereits durchgemacht hat.
- **Play Store:** verworfen wegen KRITIS-Kontrolle, B2B-Hardware-Bindung, eigenem Release-Takt.
- **F-Droid-eigenes-Repo:** Overhead unverhältnismäßig.

## Offene Punkte für Folge-ADRs

- ADR 0002 — Cert-Pinning-Strategie (sobald Cert-Renewal-Routine etabliert)
- ADR 0003 — Mandatory-Update-Mechanik (sobald erster Security-Fix dies erfordert)
- ADR 0004 — Multi-Tablet-Migration vom Debug- auf Release-Keystore (sobald >1 Tablet im Feld)
