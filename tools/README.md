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

## Claude-Workflow

Wenn ein Bug auftritt:
1. `.\tablet-snapshot.ps1` ausfuehren — Pfad ist in der Zwischenablage
2. Claude sagen: *"Schau Dir den letzten Snapshot in debug-logs\snapshots an"*
3. Claude liest Bild + Log direkt aus dem Workspace

Bei laufender Session:
- Claude tailt `debug-logs\latest\errors.log`
- Oder Claude bekommt via computer-use Zugriff auf das scrcpy-Fenster
