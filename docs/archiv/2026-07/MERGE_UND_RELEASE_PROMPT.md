# AUFTRAG: Zusammenführen, Version 0.9.0, Auslieferungspaket vervollständigen

ROLLE: Android-Ingenieur im Repo `C:\Projekte\drainq.one`.

CEO-Entscheidungen vom 30.07.2026. Reihenfolge einhalten.

---

## AP-1 — Zusammenführen und Tag
1. `feature/camera2-umstieg` und `feature/werkseinrichtung` nach `feature/dual-mode` zusammenführen.
2. `feature/dual-mode` nach `master`.
3. Tag setzen.

Vor jedem Schritt: Testsuite grün. Nach dem letzten Schritt: einmal am Gerät prüfen, dass die App startet und das Kamerabild kommt — der Zusammenführung wird nicht auf Zusage vertraut.

Bei einem Konflikt: **anhalten und melden**, nicht raten. Es sind zwei Wochen Arbeit, die hier zusammenkommen.

## AP-2 — Version 0.9.0
Versionsstand auf **0.9.0 / versionCode 900** setzen (Schema MAJOR×10000 + MINOR×100 + PATCH). Der Sprung von 0.6 ist gewollt und markiert die Nähe zur Beta.

## AP-3 — Zweiter Modus im Einrichtungswerkzeug
Das Werkzeug weist derzeit jedes Gerät ab, auf dem bereits eine App liegt. Das bleibt der Standard und ist richtig.

Ergänze einen **zweiten Modus für Bestandsgeräte ohne schützenswerte Daten**:
- Wird ausdrücklich angefordert, nie automatisch.
- Entfernt die vorhandene App und richtet danach normal ein.
- **Kein Werksreset** — der ist hier nicht nötig und würde die USB-Verbindung abschalten.
- Vor dem Entfernen eine unmissverständliche Rückfrage: „Alle Daten dieser App gehen verloren. Fortfahren?"
- Im Protokoll klar erkennbar, dass dieser Modus benutzt wurde.

Anlass: Ein Kollege hat ein Gerät mit alter Signatur, auf dem nichts gesichert werden muss und das nirgends registriert ist. Er soll das Paket bekommen und die Einrichtung selbst ausführen.

## AP-4 — Auslieferungspaket fertig machen
Das versendbare Paket soll ohne Rückfragen benutzbar sein: App in 0.9.0, adb, beide Modi, Anleitung. Prüfe, dass es auf einem Rechner läuft, auf dem nichts installiert ist.

## AP-5 — Kamera-Krücke: nur noch kennzeichnen, NICHT erneut messen
Die Messung ist am 30.07. bereits erfolgt. Ergebnis, auf zwei Geräten, jeweils nach einem Neustart und ohne den Inspektionsbildschirm zu öffnen:

| Gerät | `init.svc.vendor.camera-provider-2-4-ext` nach dem Booten | `dumpsys media.camera` |
|---|---|---|
| `e92df62d2dbd2143` | running | ADD device 100, **kein** REMOVE |
| `80cfaba8f63b8362` | running | ADD device 100, **kein** REMOVE |

Am 29.07., mit noch vorhandener Hersteller-App, kam rund 13 Sekunden nach dem ADD ein REMOVE. Ohne sie bleibt der Dienst durchgehend laufen. Starkes Indiz, dass die Hersteller-App der gesuchte Mechanismus war — **kein letztgültiger Beweis**, weil kein A/B-Test auf demselben Gerät stattfand und die Ursache im Detail unbekannt bleibt.

**Zu tun ist nur noch eines:** `CameraServiceSelfStarter` im Code neu kennzeichnen. Bisher steht dort, sie kompensiere einen unbekannten Mechanismus. Ersetze das durch: bewusstes Sicherheitsnetz für Geräte mit abweichender Firmware, mit der Messung vom 30.07. als Begründung und dem ausdrücklichen Hinweis, dass der Zusammenhang ein starkes Indiz und kein Beweis ist.

**Die Klasse wird nicht ausgebaut** (CEO-Entscheid 30.07.). Kein erneuter Messlauf.

## AP-6 — Release vorbereiten, NICHT veröffentlichen
Den Release für den Portal-Kanal vorbereiten (Bau, Signaturprüfung, Hochladen über `tools\publish-one-release.ps1`). **Das Freischalten im Admin macht der CEO selbst** — wie immer. Melde, wenn der Stand hochgeladen und bereit ist.

---

## HARTE REGELN
1. Kein `git add -A`, keine repo-weiten Git-Befehle.
2. Nur die freigegebenen Testgeräte. Fremde Geräte werden nicht angefasst.
3. Der Plattform-Keystore kommt nicht ins Paket — nur die fertig signierte App.
4. Keine echten Zugangsdaten in committete Dateien.
5. Nach jedem Arbeitspaket committen und pushen.
6. Bei einem Blocker: anhalten und melden, nicht drumherum bauen.

## BERICHT
`RESULT_MERGE_0_9_0_2026-07-30.md`: Merge-Verlauf mit Hashes, Tag, Ergebnis der Geräteprüfung, AP-5-Messwerte, Stand des Pakets, Stand des Release-Uploads, und was nicht geprüft werden konnte. Danach STOPP.
