# DrainQ ONE Tablet-Debug Tools

Skripte um das Tablet vom PC aus zu inspizieren — fuer Live-Bug-Sessions mit Claude.

## Voraussetzungen

- Android USB-Debugging am Tablet aktiv
- `adb` und `scrcpy` installiert
  - Chocolatey: `choco install adb scrcpy -y`
  - Scoop:      `scoop install adb scrcpy`

Pre-Check: `.\tablet-debug.ps1 -Install`

## Skripte

### tablet-debug.ps1 — Dauerhafte Debug-Session

Startet **scrcpy-Mirror** + **Logcat-Capture** parallel. Alles wird unter
`C:\Projekte\drainq.one\debug-logs\<timestamp>\` gespeichert. Ein
Junction-Link `debug-logs\latest` zeigt immer auf den aktuellen Lauf, damit
Claude einen stabilen Pfad hat.

Output je Session:
- `screen-record.mp4` — komplette Bildschirmaufnahme
- `logcat.log` — App-Tags + alle Errors
- `errors.log` — nur Error-Level (schnelles Scannen)
- `crashes.log` — Crash-Buffer (Stacktraces)
- `device-info.txt` — Hersteller, Modell, Android-Version

Beispiele:
```
.\tablet-debug.ps1                                 # Standard: USB, erstes Geraet
.\tablet-debug.ps1 -NoScrcpy                       # nur Log, kein Mirror
.\tablet-debug.ps1 -Wireless -WirelessIp 192.168.1.50
```

Beenden mit `STRG+C` — schliesst alle Streams sauber.

### tablet-snapshot.ps1 — Einmal-Snapshot

Schnelle Momentaufnahme ohne laufende Session:
- Screenshot des aktuellen Bildschirms
- Letzte 200 Logcat-Zeilen
- Aktuell aktive Activity
- Crash-Buffer

Pfad zur Snapshot-Datei wird in die Zwischenablage gelegt — kann direkt
Claude mitgeteilt werden.

```
.\tablet-snapshot.ps1
```

### tablet-debug.cmd

Doppelklick-Wrapper fuer `tablet-debug.ps1` (umgeht ExecutionPolicy-Stress).

### l10n-import-to-portal.ps1 — Trockenlauf fuer den Portal-Nachzug

```
pwsh -File .\l10n-import-to-portal.ps1 -DryRun
```

Sendet nichts, vergleicht die Repo-Maps gegen das lebende Portal und schreibt den
Pruefgegenstand (`l10n_import.json`) in den Ausgabeordner. Genaueres im Kopfkommentar
des Skripts; der echte Upload ist CEO-Akt.

Der Trockenlauf holt vor dem Paketbau den Haupt-View `scope=one,shared`, `scope=shared`
und jeden Fremd-Bereich (hmx, app, web, catalog, manhole) in **de und en** plus die
SA-Sicht `sa/{lang}.json` (M-3): Schluessel, die dort liegen, fallen aus NEU heraus
(FREMD). Plausibilitaetssperren (Exit 4, kein Paket): Haupt-View unter 450 Schluesseln,
mehr als 200 NEU, ein Bereich mit `{}` ausser WEB, ein Bereich unter seinem
Mindestumfang, HMX ohne Pflicht-Schluessel `ok`. Der Abbruch laeuft vor dem Schreiben
der Listen und hinterlaesst kein Ausgabeverzeichnis (C-9). Tests:
`tools/l10n/L10nImportLib.Tests.ps1` (Pester 3.4, pwsh 7).

## Claude-Workflow

Wenn ein Bug auftritt:
1. `.\tablet-snapshot.ps1` ausfuehren — Pfad ist in der Zwischenablage
2. Claude sagen: *"Schau Dir den letzten Snapshot in debug-logs\snapshots an"*
3. Claude liest Bild + Log direkt aus dem Workspace

Bei laufender Session:
- Claude tailt `debug-logs\latest\errors.log`
- Oder Claude bekommt via computer-use Zugriff auf das scrcpy-Fenster
