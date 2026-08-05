# Release-Runbook — DrainQ.ONE Beta mit Louis-Fixes W1 + W2

**Ziel:** Den Stand mit Louis-Feedback 06.07. (Welle 1 Quick Wins + Welle 2 Video) als **Beta** übers DrainQ-Portal verteilen, damit die ONE ihn per Self-Update zieht — und an der ONE die offene Abnahme (inkl. #8) in einer Sitzung abhaken.
**Quelle:** `feature/dual-mode` · Berichte `RESULT_LOUIS_W1_QUICKWINS.md`, `RESULT_LOUIS_W2_VIDEO.md`
**Stand:** vorbereitet 2026-07-07

---

## Reihenfolge

1. **Build aus `feature/dual-mode`** (W1+W2 liegen dort, kein Merge nach master — für Beta ok).
2. Signierte Release-APK bauen → Portal-Release anlegen → am Gerät self-updaten.
3. **Geräte-Abnahme** nach der Checkliste unten (W1, W2 und #8-Repro).

---

## Schritte (lokal auf dem Build-Rechner)

**1. Version setzen** (`build.gradle.kts` liest Umgebungsvariablen; Schema `MAJOR*10000 + MINOR*100 + PATCH`):
```powershell
$env:APP_VERSION_CODE="501"; $env:APP_VERSION_NAME="0.5.1"
```
*(Vorschlag 0.5.1 / 501 — folgt auf das vorhandene 0.5.0-alpha_500. Falls anders gewünscht, hier ändern.)*

**2. APK bauen — OHNE Keystore/Signing** (aktueller Stand: es wird `assembleDebug` gebaut, KEIN Release-Signing):
```powershell
cd C:\Projekte\drainq.one; .\gradlew.bat assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. (Debug-signiert = alle Beta-Builds teilen denselben Debug-Key, Self-Update untereinander funktioniert.)

**3. sha256 + Größe** (beides exakt ins Manifest):
```powershell
$apk="app/build/outputs/apk/debug/app-debug.apk"
(Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
(Get-Item $apk).Length
```

**4. Im DrainQ-Portal-Admin (drainq.web/Hetzner) Release anlegen:** Produkt **one**, Channel **beta**, Version **0.5.1**, versionCode **501**, APK hochladen, sha256 + size eintragen. Notes-Vorschlag:
> „Louis-Fixes: Schadensart wählbar, Licht 100 %, Sonde-Frequenz farbig, Info-Panel schließbar, Datum/Uhrzeit stellbar; Video-Wiedergabe mit Timer und voller Länge auch nach Pause."

**5. Verifizieren:**
```powershell
curl.exe -sL https://license.drainq.com/api/software/one/releases.beta.json
# HTTP 200 + JSON, versionCode 501.
```

**6. Self-Update am Gerät:** Einstellungen → „Nach Updates suchen" → Installieren → auf 0.5.1.

---

## Geräte-Abnahme-Checkliste (an der ONE)

### Welle 1 — Quick Wins
- [ ] **Licht 100 %:** Licht-Taste mehrfach → OSD zeigt nacheinander 30/60/100 %, nicht bei 90 % gedeckelt.
- [ ] **Sonde-Frequenz:** Frequenz wählen → im Popup ist der aktive Wert **grün + fett**. Auch im **Tablet-/WLAN-Modus** prüfen (dort anderes Label-Format).
- [ ] **Datum/Uhrzeit:** Einstellungen → „Datum & Uhrzeit stellen" öffnet die Android-Seite; Uhr korrigieren → neues Projekt zeigt automatisch das **heutige** Datum (nicht 01.01.2021).
- [ ] **Info-Panel:** per Tipp aufs Video öffnen, per **X am Panel** schließen — ohne den Inspektions-Screen zu verlassen.
- [ ] **Schadensart:** Dialog „Schaden erfassen" → Schadensart lässt sich auf **jeden** Katalogwert ändern und wird gespeichert (der ursprüngliche Louis-Bug).

### Welle 2 — Video
- [ ] **Ohne Pause:** kurz aufnehmen → stoppen → Wiedergabe: **Timer läuft mit**, korrekte Länge, seekbar (nicht nur Endzeit).
- [ ] **Mit Pause:** aufnehmen → Pause → Resume → weiter → stoppen → Wiedergabe: **volles Video**, korrekte Dauer, **kein Abbruch nach ~2 s**.
- [ ] **Absturzsicherheit (optional):** App während Aufnahme killen → im Ordner bleibt eine spielbare `*.frag.mp4`.
- [ ] Beide Modi prüfen: **lokal (V4L2, direkt an der ONE)** und **RTSP (ONE.PRO/Remote)**, falls verfügbar.

### #8 — Durchmesser/Länge (Repro, noch offen)
- [ ] Neues Projekt: **Durchmesser** und **Länge** eintippen → speichern → Projekt erneut öffnen: Werte noch da?
- [ ] Report erzeugen: stehen **Durchmesser (DN …)** und **Länge (… m)** im Bericht?
- [ ] **Genau notieren, was bei Louis scheiterte:** Feld nicht antippbar? Tastatur öffnet nicht? Wert verschwindet nach Speichern? Report-Zeile leer? → das entscheidet, ob es ein echter Bug oder ein Bedien-/Tastatur-Thema ist.

---

## Was ich von hier nicht kann
APK bauen (Android-SDK), Portal-Upload (Login), Self-Update-Test und die Geräte-Abnahme laufen auf Deinem Rechner / an der ONE.

🔒 **KRITIS:** Aktuell Debug-Build ohne Release-Signing (Beta-Phase). Integrität über `sha256`/`size` im Manifest (App lehnt bei Abweichung ab). Auslieferung über TLS (`https://license.drainq.com`). Keine neuen Endpunkte/Dependencies. Vor Produktiv-Release: echtes Release-Signing nachziehen.
