# DrainQ.ONE — Update-Benutzerhandbuch

**Version:** 0.4.0  
**Stand:** 2026-05-12  
**Sprache:** Deutsch (mit English fallbacks)

---

## Überblick

DrainQ.ONE wird regelmäßig mit neuen Funktionen, Bugfixes und Sicherheits-Updates aktualisiert. Diese Anleitung erklärt, wie Updates funktionieren und was Sie dabei tun müssen.

---

## Automatische Update-Suche

### Wie funktioniert die automatische Suche?

- **Zeitplan:** Die App prüft automatisch täglich nach neuen Updates — **aber nur, wenn das Tablet mit einem WLAN mit Internetverbindung verbunden ist** (nicht im ONE-Hotspot).
- **Stille Prüfung:** Im Hintergrund, ohne Benachrichtigung falls keine neue Version vorhanden ist.
- **Benachrichtigung:** Falls ein Update verfügbar ist, zeigt die App eine Benachrichtigung im Status-Bereich oben auf dem Bildschirm.

### Warum kein Update im ONE-Hotspot?

Der ONE-Hotspot (`ONE_01`) verbindet das Tablet ausschließlich mit der ONE-Hardware — es gibt keine Internetverbindung. Die automatische Update-Prüfung ist auf externe WLAN-Netzwerke beschränkt, um Bandbreite zu sparen und Fehlermeldungen zu vermeiden.

Falls Sie Updates im Feld benötigen: Bringen Sie das Tablet mit nach Hause oder in ein Büro mit Internet-WLAN, um Updates einzuspielen.

---

## Manuell nach Updates suchen

1. Öffnen Sie **Einstellungen** (Icon unten rechts oder in Navigation)
2. Scrollen Sie zu **Update-Einstellungen**
3. Tippen Sie auf **„Nach Updates suchen"** Button
4. Die App kontaktiert den Update-Server — dies kann 5–30 Sekunden dauern
5. Mögliche Ergebnisse:
   - ✅ **„Du hast die aktuelle Version"** — Sie laufen bereits auf der neuesten Version
   - 📦 **„Update verfügbar: [Version]"** — Ein neues Update ist bereit zum Installieren

---

## Update installieren

### Update-Banner

Falls ein Update verfügbar ist, sehen Sie ein Banner mit:
- Versionsnummer (z.B. `v0.4.0`)
- Kurze Beschreibung der Änderungen
- Größe der Update-Datei
- Buttons: **„Jetzt installieren"** oder **„Später"**

### Installation durchführen

1. Tippen Sie auf **„Jetzt installieren"**
2. Die App zeigt einen **Download-Fortschrittsbalken** (Beispiel: `45 / 150 MB`)
3. Nach erfolgreichem Download wird die Datei **überprüft** (SHA256-Hash-Verifikation)
4. Falls erfolgreich: **Android-Bestätigungsdialog** — tippen Sie **„Installieren"**
5. Android installiert das Update und **startet die App neu**
6. Die neue Version lädt beim nächsten Start

### Installation verschieben

- Tippen Sie **„Später"** → Das Update bleibt im Banner sichtbar
- Sie können die Inspektion fortsetzen und später installieren
- **Hinweis:** Für kritische Sicherheits-Updates sollten Sie nicht zu lange warten

---

## Update-Kanäle

DrainQ.ONE hat zwei Versions-Kanäle:

| Kanal | Zweck | Für wen |
|---|---|---|
| **Stable (Standard)** | Stabile, getestete Versionen | Alle Endkunden |
| **Beta (Versteckt)** | Vorgelagerte Versionen zum Testen | Nur für Entwickler/Power-User |

### Zum Beta-Kanal wechseln (für Tester)

1. Öffnen Sie **Einstellungen**
2. Suchen Sie die **Versionsnummer** (oben, unter „App-Info")
3. Tippen Sie **7× schnell hintereinander** auf die Versionsnummer
4. Ein verstecktes **Menü** erscheint mit Kanal-Auswahl
5. Wählen Sie **„Beta"** und bestätigen Sie
6. Die App fordert Sie auf, die App **neu zu starten**
7. Ab sofort prüft die App auf Beta-Versionen

### Zurück zum Stable-Kanal

Wiederholen Sie die gleichen Schritte (7× auf Versionsnummer tippen) und wählen Sie **„Stable"** aus.

---

## Versionsnummern verstehen

Versionsnummern folgen dem Schema `MAJOR.MINOR.PATCH`, z.B. `0.4.1`:

- **0** = Hauptversion (Major Release mit großen Änderungen)
- **4** = Nebenversion (Minor Release mit neuen Features)
- **1** = Patch (kleine Bugfixes und Sicherheits-Updates)

**Beispiele:**
- `0.3.0` → `0.4.0` = größere Features hinzugekommen
- `0.4.0` → `0.4.1` = kleine Bugfixes

---

## Was tun bei Update-Fehlern?

### Fehler: „Netzwerkfehler. Bitte später erneut versuchen."

**Ursache:** Keine Internetverbindung oder Verbindung unterbrochen  
**Lösung:**
1. Stellen Sie sicher, dass das Tablet mit einem **Internet-WLAN verbunden** ist (nicht ONE-Hotspot)
2. Warten Sie ein paar Sekunden
3. Tippen Sie erneut auf **„Nach Updates suchen"**
4. Falls Problem bleibt: Starten Sie das Tablet neu (Netzwerkstack zurücksetzen)

### Fehler: „Update-Datei beschädigt. Bitte später erneut versuchen."

**Ursache:** Die heruntergeladene Datei war fehlerhaft (Übertragungsfehler)  
**Lösung:**
1. Das Tablet versucht automatisch, das Update erneut zu laden
2. Falls immer noch fehlerhaft: Warten Sie einige Stunden (möglicherweise Netzwerkverzögerung)
3. Kontaktieren Sie den Support mit der **Logcat-Ausgabe** (siehe unten)

### Fehler: „Installation fehlgeschlagen. Bitte später erneut versuchen."

**Ursache:** Android konnte das Update nicht installieren (Speicherplatz, Signaturmismatch, etc.)  
**Lösung:**
1. Prüfen Sie den **freien Speicher** (Einstellungen → Speicher) — mindestens 500 MB sollten frei sein
2. Starten Sie das Tablet neu
3. Versuchen Sie das Update erneut
4. Falls immer noch fehlerhaft: Melden Sie an Support mit **Logcat** (siehe unten)

---

## Logcat-Ausgabe für Support

Falls ein Update-Fehler auftritt und Support benötigt wird, können Sie die App-Logs sammeln:

### Logs über ADB sammeln (Gerät direkt anschließen)

```bash
adb logcat -c                          # Logs löschen
# Jetzt Update-Fehler reproduzieren
adb logcat > update_error.log          # Logs erfassen (Strg+C zum Stoppen)
```

Die Datei `update_error.log` enthält die Fehler-Details und können an Support gesendet werden.

### Logs direkt auf dem Tablet

1. Installieren Sie die App **"Material Logcat"** aus dem Play Store
2. Öffnen Sie die App
3. Wählen Sie **"Filter by package"** → `com.uip.drainq.one`
4. Reproduzieren Sie den Update-Fehler
5. Tippen Sie **"Share Logs"** und senden Sie per Mail/Cloud

---

## FAQ

**Q: Warum prüft die App nicht im ONE-Hotspot auf Updates?**  
A: Der ONE-Hotspot hat keine Internetverbindung. Nur externe WLANs mit Internet ermöglichen die Update-Prüfung.

**Q: Kann ich Updates im Hintergrund (ohne Dialog) installieren?**  
A: Nein. Android erfordert eine explizite Benutzerbestätigung zum Installieren von Apps — das ist ein Sicherheits-Feature.

**Q: Was passiert mit meinen Daten, wenn ich ein Update installiere?**  
A: Nichts. Alle Ihre Inspektions-Projekte, Fotos, Videos und Notizen bleiben erhalten. Das Update ändert nur die App-Version.

**Q: Wie lange dauert die Installation?**  
A: 2–5 Minuten, abhängig von Tablet-Hardware und Internet-Geschwindigkeit.

**Q: Kann ich eine Update-Installation abbrechen?**  
A: Während des Downloads: ja (tippen Sie „Abbrechen"). Während der Installation: nein (Android übernimmt dann).

**Q: Wo kann ich ältere Versionen finden?**  
A: Nicht direkt in der App. Falls Sie zu einer älteren Version zurückkehren möchten (rollback), kontaktieren Sie Support — es können alte APKs bereitgestellt werden.

---

## Notfall: Sideload über ADB

Falls die In-App-Update-Funktion nicht funktioniert, kann ein Techniker eine APK direkt über ADB installieren:

```bash
adb install -r drainq-one-0.4.0.apk
```

Die Option `-r` (reinstall) erlaubt, eine bereits installierte App zu ersetzen.

---

**Support-Kontakt:** t.viell@uip.team  
**Dokumentversion:** 0.4.0 (Phase 7)
