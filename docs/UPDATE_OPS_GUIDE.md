# DrainQ.ONE — Update-Operations-Guide

**Version:** 0.4.0  
**Stand:** 2026-05-12  
**Zielgruppe:** Ops-Team, Release-Manager, Support-Techniker  
**Referenzen:** `ADR 0001`, `UPDATE_PROCESS_CONCEPT.md`, `UPDATE_PROCESS_PHASENPLAN.md`

---

## Übersicht

Dieser Guide beschreibt den Prozess für:
1. **Erst-Release-Schritte** — vom Code bis zur Verfügbarkeit auf GitHub
2. **GitHub Release Direct** — wie Tag-Push → GitHub Actions → Tablet funktioniert
3. **Rollback-Verfahren** — falls kritische Fehler gefunden werden
4. **Notfall-Sideload** — wenn In-App-Update-Funktion ausfällt

> **Variante A aktiv:** Tablets ziehen direkt von GitHub Release Assets — kein Mirror-Server, kein Hetzner-Deployment erforderlich.

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
```

> **Hinweis:** `DRAINQ_RELEASE_PAT` wird nicht mehr benötigt — das Repo ist public. Falls das Secret noch vorhanden ist, kann es manuell gelöscht werden (blockiert keinen Build).

**Prüf-Befehle (lokal):**
```powershell
cd C:\Projekte\drainq.one
keytool -list -v -keystore oneapp-release.keystore
# Liefert: Owner, Issuer, Serial, Valid, Fingerprint, Alias-Name
```

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

## Phase 2: GitHub Release Direct — Tag pushen und Workflow triggern

```bash
cd C:\Projekte\drainq.one
git tag v0.4.0
git push --tags
```

Das triggert automatisch `.github/workflows/release-apk.yml`:

1. ✅ APK wird gebaut und signiert mit Release-Keystore
2. ✅ SHA256-Hash wird berechnet
3. ✅ Release-Manifest `releases.stable.json` wird generiert (APK-URL zeigt auf GitHub Asset)
4. ✅ GitHub Release wird publiziert (**public** — kein PAT erforderlich)
5. → Warte auf Workflow-Abschluss (meist 5–10 min)

**Workflow-Status prüfen:** GitHub → Actions → letzte Run

Nach Workflow-Abschluss sind die Assets sofort verfügbar:
```
https://github.com/ThomasViell/one-app/releases/latest/download/releases.stable.json
https://github.com/ThomasViell/one-app/releases/download/v0.4.0/drainq-one-0.4.0.apk
```

### Manifest-Verfügbarkeit prüfen

```bash
curl -L -I "https://github.com/ThomasViell/one-app/releases/latest/download/releases.stable.json"
# HTTP/2 200 nach GitHub-Redirect → OK
```

### APK-Verfügbarkeit prüfen

```bash
curl -L -I "https://github.com/ThomasViell/one-app/releases/download/v0.4.0/drainq-one-0.4.0.apk"
# HTTP/2 200 → OK
```

> GitHub leitet `/latest/download/` auf die konkrete Asset-URL der neuesten Release weiter. `-L` folgt dem Redirect.

---

## Phase 3: Tablet-Test

Nach Workflow-Abschluss (oder nach max. 10 min warten):

### Test auf SM-X610

```bash
adb -s R52Y303GEZH shell settings put global airplane_mode_on 0
adb -s R52Y303GEZH shell am start -n com.uip.drainq.one/.MainActivity

# Einstellungen öffnen → Update-Einstellungen
# Button „Nach Updates suchen" tippen
# → Sollte Update v0.4.0 zeigen
```

Falls immer noch „aktuelle Version":
1. Workflow-Status auf GitHub prüfen (evtl. noch laufend)
2. Manifest-URL direkt auf dem Tablet testen:
   ```bash
   adb -s R52Y303GEZH shell wget -O- \
     "https://github.com/ThomasViell/one-app/releases/latest/download/releases.stable.json"
   ```
3. Internet-Verbindung des Tablets sicherstellen (kein ONE-Hotspot, echtes WLAN)

---

## Rollback-Verfahren

Falls ein kritischer Bug in v0.4.0 entdeckt wird:

### Schnelles Rollback (bevorzugt)

Da alle GitHub Releases dauerhaft erhalten bleiben, kann ein Rollback durch ein korrigiertes Manifest erfolgen:

1. Ein neues GitHub Release erstellen (z.B. `v0.3.1-hotfix`)
2. Das Manifest darin zeigt auf die alte stabile APK (`v0.3.0`)
3. Oder: APK des Hotfix bauen + taggen als `v0.3.1`, dann normaler Release-Flow

GitHub `/latest/download/` zeigt immer auf das neueste Release-Tag. Tablets holen sich automatisch das neue Manifest beim nächsten Check.

### Rollback via korrigiertem Manifest (manuell)

Falls schnell ohne neuen Tag nötig:
```bash
# releases.stable.json manuell bearbeiten und als Release-Asset hochladen:
# gh release upload v0.4.0 releases.stable.json --clobber
# oder neues Release-Tag erstellen:
git tag v0.3.1
git push origin v0.3.1
```

### Notfall-Rollback: alte APK ist noch verfügbar

Ältere Releases bleiben auf GitHub dauerhaft erhalten:
```bash
# Alte APK direkt downloaden (kein PAT nötig — Repo public):
curl -L -O "https://github.com/ThomasViell/one-app/releases/download/v0.3.0/drainq-one-0.3.0.apk"
# Auf Tablet sideloaden:
adb -s R52Y303GEZH install -r drainq-one-0.3.0.apk
```

---

## Notfall-Sideload via ADB

Falls die Update-Mechanik komplett ausgefallen ist:

### APK lokal bereitstellen

```bash
# Von GitHub Release (kein PAT nötig — Repo public):
curl -L -O "https://github.com/ThomasViell/one-app/releases/download/v0.4.0/drainq-one-0.4.0.apk"
# oder lokaler Build:
.\gradlew assembleRelease
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

1. **Internet-Verbindung prüfen:**
   ```bash
   adb -s R52Y303GEZH shell ping -c 3 8.8.8.8
   # Falls kein Pong: Tablet hat keine Internet-Verbindung (im ONE-Hotspot?)
   ```

2. **GitHub-Erreichbarkeit:**
   ```bash
   adb -s R52Y303GEZH shell wget -O- \
     "https://github.com/ThomasViell/one-app/releases/latest/download/releases.stable.json"
   # Sollte JSON-Manifest anzeigen
   ```

3. **App-Logcat:**
   ```bash
   adb logcat | grep -i "update\|check\|drainq"
   ```

### Download-Fehler (SHA256-Mismatch)

```bash
# SHA256 der lokal heruntergeladenen APK prüfen
sha256sum drainq-one-0.4.0.apk
# Vergleichen mit releases.stable.json Field "sha256"
# Falls unterschiedlich: erneut downloaden — evtl. Netzwerkfehler beim ersten Download
```

---

## Troubleshooting Checkliste

| Problem | Diagnose | Lösung |
|---|---|---|
| Manifest nicht erreichbar | `curl -L -I https://github.com/.../releases.stable.json` | Workflow noch laufend? Internet-Verbindung Tablet? |
| SHA256-Mismatch | APK erneut downloaden + prüfen | Netzwerkfehler beim Download — nochmals versuchen |
| Tablet sieht kein Update | `adb shell wget <manifest-url>` | Internet-Verbindung prüfen (nicht im ONE-Hotspot) |
| Installation auf Tablet blockiert | `adb shell pm list packages \| grep com.uip` | Alte APK noch installiert — `-r` Flag nutzen |
| Workflow läuft endlos | GitHub Actions → release-apk.yml Logs | Secrets falsch oder Keystore-Passwort ungültig |

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
1. Release mit Tag `v0.4.0-beta.1` pushen (GitHub Release als Prerelease markieren)
2. Interne Tester vor dem stable Release validieren
3. Erst nach Green-Light stabilen Release pushen

### 3. DSGVO-Compliance: GitHub-Download-Logs
Tablets fragen Manifest ab und laden APK direkt von GitHub (Microsoft/GitHub-Infrastruktur). GitHub protokolliert IP-Adressen in Access-Logs. Da die Tablets Betriebsmittel sind (keine privaten Geräte), ist der Personenbezug gering.

**ToDo:** In bestehende AVV mit GitHub / Microsoft ergänzen (oder prüfen ob GitHub Enterprise Agreement der UIP diese Nutzung abdeckt).

---

**Support-Kontakt:** t.viell@uip.team  
**Dokumentversion:** 0.4.0 (2026-05-12, Variante A)
