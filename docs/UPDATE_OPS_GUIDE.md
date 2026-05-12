# DrainQ.ONE — Update-Operations-Guide

**Version:** 0.4.0  
**Stand:** 2026-05-12  
**Zielgruppe:** Ops-Team, Release-Manager, Support-Techniker  
**Referenzen:** `ADR 0001`, `UPDATE_PROCESS_CONCEPT.md`, `UPDATE_PROCESS_PHASENPLAN.md`

---

## Übersicht

Dieser Guide beschreibt den Prozess für:
1. **Erst-Release-Schritte** — vom Code bis zur Verfügbarkeit auf `updates.drainq.de`
2. **Hetzner-Mirror überwachen** — sicherstellen, dass die APK verfügbar ist
3. **Rollback-Verfahren** — falls kritische Fehler gefunden werden
4. **Notfall-Sideload** — wenn In-App-Update-Funktion ausfällt

---

## Phase 1: Erst-Release-Vorbereitung

Vor dem **ersten Tag-Push** für v0.4.0 müssen diese Voraussetzungen erfüllt sein:

### 1. GitHub Secrets setzen

Im Repository `ThomasViell/one-app` (Settings → Secrets → Actions):

```
DRAINQ_ONE_KEYSTORE_BASE64      = base64 < oneapp-release.keystore
DRAINQ_ONE_KEYSTORE_PASSWORD    = [Store-Passwort]
DRAINQ_ONE_KEY_ALIAS            = [Key-Alias im Keystore]
DRAINQ_ONE_KEY_PASSWORD         = [Key-Passwort]
DRAINQ_RELEASE_PAT              = [Read-only PAT auf repo]
```

**Prüf-Befehle (lokal):**
```powershell
cd C:\Projekte\drainq.one
keytool -list -v -keystore oneapp-release.keystore
# Liefert: Owner, Issuer, Serial, Valid, Fingerprint, Alias-Name
```

Verwenden Sie die Daten für die Secret-Einträge.

### 2. Release-Keystore-Backup

Der `oneapp-release.keystore` ist **einzigartig** und darf **nicht verloren gehen**:
- Backup-Kopie in 1Password/Vault anlegen
- Beim Team dokumentieren: „Falls Thomas den Keystore verliert, kann im Vault nachgesehen werden"
- Passwörter im Vault hinterlegen

**Keystore-Verlust = keine Updates mehr für bereits installierte Tablets!**

### 3. Test auf SM-X610

Vor dem ersten Release sollte die Update-Mechanik auf der Pilot-Hardware getestet sein:
```bash
adb -s R52Y303GEZH shell am start -n com.uip.drainq.one/.MainActivity
# App sollte starten und in Settings "Nach Updates suchen" Button zeigen
```

---

## Phase 2: Tag pushen und Workflow triggern

```bash
cd C:\Projekte\drainq.one
git tag v0.4.0
git push --tags
```

Das triggert automatisch `.github/workflows/release-apk.yml`:

1. ✅ APK wird gebaut und signiert mit Release-Keystore
2. ✅ SHA256-Hash wird berechnet
3. ✅ Release-Manifest `releases.stable.json` wird generiert
4. ✅ GitHub Release wird publiziert (privat)
5. → Warte auf Workflow-Abschluss (meist 5–10 min)

**Workflow-Status prüfen:** GitHub → Actions → letzte Run

---

## Phase 3: Hetzner-Mirror überwachen

Der Hetzner-Server mirror-releases.sh läuft alle 5 Minuten und spiegelt die neuen Release-Artefakte nach `/var/www/drainq-updates/one/`.

### A. Mirror-Status prüfen (lokal)

```bash
curl -I https://updates.drainq.de/one/releases.stable.json
# HTTP/2 200 — OK
```

### B. APK-Verfügbarkeit testen

```bash
curl -I https://updates.drainq.de/one/drainq-one-0.4.0.apk
# HTTP/2 200 — OK
# Falls 404: Mirror läuft noch, 5 min warten
```

### C. Mirror-Logs auf Hetzner prüfen

SSH auf Hetzner und Logs ansehen:
```bash
ssh ops@updates.drainq.de

# Mirror-Timer-Status
systemctl status drainq-one-mirror.timer
systemctl status drainq-one-mirror.service

# Mirror-Logs
journalctl -u drainq-one-mirror.service -n 30 --no-pager
# Erwartete Ausgabe: „Mirrored drainq-one-0.4.0.apk", „Updated releases.stable.json"

# Dateien überprüfen
ls -la /var/www/drainq-updates/one/
# drainq-one-0.4.0.apk
# releases.stable.json
# releases.beta.json
```

---

## Phase 4: Tablet-Test

Nach Hetzner-Mirror-Bestätigung (oder nach max. 10 min warten):

### Test auf SM-X610

```bash
adb -s R52Y303GEZH shell settings put global airplane_mode_on 0
adb -s R52Y303GEZH shell am start -n com.uip.drainq.one/.MainActivity

# Einstellungen öffnen → Update-Einstellungen
# Button „Nach Updates suchen" tippen
# → Sollte Update v0.4.0 zeigen
```

Falls immer noch „aktuelle Version":
1. Mirror-Logs erneut prüfen (möglich: GitHub-API-Rate-Limit getroffen)
2. Mirror manuell auslösen:
   ```bash
   ssh ops@updates.drainq.de
   systemctl start drainq-one-mirror.service
   journalctl -u drainq-one-mirror.service -f
   ```
3. 1 min warten, dann auf Tablet erneut „Nach Updates suchen" tipp

---

## Rollback-Verfahren

Falls ein kritischer Bug in v0.4.0 entdeckt wird, kann ein Rollback durchgeführt werden:

### Schnelles Rollback (wenn alte APK noch am Hetzner)

```bash
ssh ops@updates.drainq.de
cd /var/www/drainq-updates/one/

# releases.stable.json aktualisieren, um auf alte Version zu zeigen
cat > releases.stable.json <<'EOF'
{
  "channel": "stable",
  "latest": {
    "version": "0.3.0",
    "versionCode": 11,
    ...
    "url": "https://updates.drainq.de/one/drainq-one-0.3.0.apk",
    ...
  },
  ...
}
EOF

# Bestätigung: curl -s https://updates.drainq.de/one/releases.stable.json | jq .latest.version
# → 0.3.0
```

Tablets zeigen dann Update auf 0.3.0 beim nächsten Check.

### Notfall-Rollback (wenn APK gelöscht)

Falls die alte APK `drainq-one-0.3.0.apk` nicht mehr am Hetzner ist:

```bash
# GitHub Release 0.3.0 downloaden (privat)
git clone https://github.com/ThomasViell/one-app.git
cd one-app && git checkout v0.3.0
./gradlew assembleRelease --offline
# oder von GitHub Release direkt ziehen
curl -O https://github.com/ThomasViell/one-app/releases/download/v0.3.0/drainq-one-0.3.0.apk

# In Hetzner hochladen
scp drainq-one-0.3.0.apk ops@updates.drainq.de:/var/www/drainq-updates/one/

# releases.stable.json aktualisieren (siehe oben)
```

---

## Notfall-Sideload via ADB

Falls die Update-Mechanik komplett ausgefallen ist:

### APK lokal bereitstellen

```bash
# Von GitHub Release oder lokalem Build
# curl -O https://github.com/ThomasViell/one-app/releases/download/v0.4.0/drainq-one-0.4.0.apk
```

### Installation auf Tablet

```bash
adb -s R52Y303GEZH install -r drainq-one-0.4.0.apk
# Ausgabe: "Success" nach 10–20 sec
```

**Hinweis:** Die `-r` Flag (reinstall) erlaubt, eine bereits installierte App zu ersetzen.

---

## Update-Fehlerdiagnose

### Update-Check funktioniert nicht (Tablet zeigt immer „aktuell")

1. **Hetzner-Verbindung prüfen:**
   ```bash
   adb -s R52Y303GEZH shell ping -c 3 8.8.8.8
   # Falls kein Pong: Tablet hat keine Internet-Verbindung
   ```

2. **DNS-Auflösung:**
   ```bash
   adb -s R52Y303GEZH shell getprop ro.config.nocheckin  # Prüfen ob Netzwerk aktiv
   adb -s R52Y303GEZH shell nslookup updates.drainq.de
   # Falls Fehler: DNS nicht verfügbar
   ```

3. **HTTP-Response testen:**
   ```bash
   adb -s R52Y303GEZH shell wget -O- https://updates.drainq.de/one/releases.stable.json
   # Sollte JSON-Manifest anzeigen
   ```

4. **App-Logcat:**
   ```bash
   adb logcat | grep -i "update\|check\|drainq"
   # Reproduziert Update-Fehler und sucht nach relevanten Logs
   ```

### Download-Fehler (SHA256-Mismatch)

```bash
# Auf Hetzner SHA256 überprüfen
sha256sum /var/www/drainq-updates/one/drainq-one-0.4.0.apk
# Vergleichen mit releases.stable.json Field "sha256"

# Falls unterschiedlich: APK auf Hetzner ist beschädigt — neu hochladen
scp drainq-one-0.4.0.apk ops@updates.drainq.de:/var/www/drainq-updates/one/
```

---

## Best Practices

### 1. Release-Notes vor Tag-Push finalisieren
Siehe `CHANGELOG.md`, Abschnitt v0.4.0:
- Welche Features sind neu?
- Welche Bugs wurden behoben?
- Gibt es Breaking Changes?

Diese Notes werden im Tablet-Update-Dialog angezeigt.

### 2. Beta-Channel vorab testen
Falls Änderungen unsicher sind:
1. Release mit Tag `v0.4.0-beta.1` pushen (oder in `releases.beta.json` testen)
2. Interne Tester vor dem stable Release validieren
3. Erst nach Green-Light stabilen Release pushen

### 3. Update-Größe dokumentieren
Große Updates (> 200 MB) sollten den Endkunden vorher kommuniziert werden:
- Tablets im Feld sollten WLAN mit gutem Signal nutzen
- Download-Zeit: grob 1 MB = 1–2 Sekunden (abhängig von Uplink)

### 4. DSGVO-Compliance: Nginx-Logs
Tablets fragen Manifest ab → `updates.drainq.de` protokolliert die IP im Nginx-Log. Diese Logs sind in der **AVV mit Hetzner** dokumentiert (existiert bereits für Suite, gilt auch für ONE).

Falls neue AVV-Ergänzung nötig: mit Hetzner-Support absprechen.

---

## Troubleshooting Checkliste

| Problem | Diagnose | Lösung |
|---|---|---|
| Mirror zeigt 404 | `curl -I https://updates.drainq.de/one/drainq-one-0.4.0.apk` | Mirror läuft noch — 5 min warten oder `systemctl start drainq-one-mirror.service` |
| SHA256-Mismatch | `sha256sum /var/www/.../drainq-one-*.apk` | APK beschädigt — neu hochladen |
| Tablet sieht kein Update | `adb shell wget https://updates.drainq.de/one/releases.stable.json` | Internet-Verbindung prüfen |
| Installation auf Tablet blockiert | `adb shell pm list packages \| grep com.uip` | Alte APK noch installiert — `-r` Flag nutzen |
| Workflow läuft endlos | GitHub Actions → release-apk.yml Logs | Secrets falsch oder Keystore-Passwort ungültig |

---

**Support-Kontakt:** t.viell@uip.team  
**Ops-Kontakt (Hetzner):** [SSH-Key in 1Password]  
**Dokumentversion:** 0.4.0 (Phase 7)
