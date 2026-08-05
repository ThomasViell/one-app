# AUFTRAG: Werkseinrichtungs-Werkzeug für die ONE (USB, Serienbetrieb)

ROLLE: Android-Ingenieur im Repo `C:\Projekte\drainq.one`.

**Hinweis:** `QR_EINRICHTUNG_PROMPT.md` im Repo-Root ist **hinfällig** — der QR-Weg wurde am 29.07.2026 geprüft und scheidet aus (die ONE durchläuft nach einem Werksreset keinen Android-Einrichtungsassistenten). Kennzeichne die Datei als überholt, lösche sie nicht.

**Mengengerüst:** 10–20 Anlagen pro Woche. Jede gesparte Minute pro Gerät zählt, aber eine halb eingerichtete Anlage beim Kunden kostet ein Vielfaches davon. Zuverlässigkeit vor Tempo.

---

## ★ DIE ENTSCHEIDENDE REGEL, am 29.07. gemessen

| Gerätezustand | `adb devices` | Folge |
|---|---|---|
| **fabrikneu, unangetastet** (`e92df62d2dbd2143`) | **meldet sich** | Einrichtung sofort möglich, kein Handgriff am Gerät |
| **nach Werksreset** | meldet sich **nicht** | Entwicklermodus muss von Hand freigeschaltet werden |

**Der Werksreset ist es, der die USB-Verbindung abschaltet — nicht das Gerät an sich.**

Daraus folgt die harte Regel für den Serienbetrieb:

> **Auf einer fabrikneuen ONE wird NIEMALS ein Werksreset durchgeführt.**
> Das Gerät geht direkt in die Einrichtung. Der Reset ist ausschließlich der Sonderfall für Anlagen, die aus dem Feld zurückkommen und bereits ein Benutzerkonto tragen.

Diese Regel gehört in das Skript (Abbruch mit Erklärung, wenn jemand sie verletzen will), in die Anleitung und in `docs/PROVISIONING_GOLDEN_IMAGE.md`. Sie ist kontraintuitiv — ohne deutlichen Hinweis setzt jemand aus Gewohnheit zurück und legt sich das Problem selbst.

---

## ZIEL
Gerät per USB an den PC, **ein Befehl**, fertig. Die Routine spielt die App auf, setzt sie als Startbildschirm, aktiviert den Kiosk-Betrieb, prüft **jeden** Schritt einzeln nach und schreibt ein Protokoll mit Seriennummer, Version, Zeitstempel und Gesamtergebnis grün/rot.

**Serienbetrieb ist Pflicht, kein Zusatz:** Das Werkzeug muss **mehrere gleichzeitig angesteckte Geräte parallel** abarbeiten (USB-Verteiler). Vier Anlagen sollen kaum länger brauchen als eine. Je Gerät eine eigene Protokollzeile, am Ende eine Übersicht.

**Als versendbares Paket bauen:** ein Ordner mit App, mitgeliefertem adb, Skript und einer Seite Anleitung. Muss auf einem Rechner laufen, auf dem nichts installiert ist — auch beim Mitarbeiter.

---

## PHASE 0 — zwei kurze Messungen am fabrikneuen Gerät `e92df62d2dbd2143`
Vor dem Bauen, weil sie den Zuschnitt bestimmen:
1. **Ist das Gerät kontenfrei, lässt sich der Kiosk-Betrieb also direkt setzen?** Prüfen, ob ein Benutzerkonto existiert und ob `dpm set-device-owner` durchgeht.
2. **Braucht die App beim allerersten Start ein Netz** (Lizenz, Portal)? Falls ja, muss das WLAN Teil der Einrichtung werden — dann als eigener kurzer Schritt, **nicht** als Umweg für den App-Download. Die App liegt im Paket und geht über das Kabel; das ist schneller und offlinefähig.

Ergebnis beider Messungen in den Bericht. Scheitert Punkt 1: anhalten und melden.

## PHASE 1 — Werkzeug bauen
Unter `tools/werkseinrichtung/`:
- Startskript (PowerShell), das ohne Vorkenntnisse per Doppelklick läuft.
- Mitgeliefertes adb.
- Die plattformsignierte App-Datei.
- Parallelverarbeitung aller angesteckten Geräte.
- Bildschirmausgabe in **deutscher Alltagssprache**, kein Fachjargon; am Ende je Gerät ein großes GRÜN oder ROT.
- Protokolldatei je Lauf, eine Zeile je Gerät.

**Sicherheitsnetze, nicht verhandelbar:**
- Erkennt das Skript ein Gerät, das **nicht** fabrikneu ist (App bereits installiert, Konto vorhanden, Nutzdaten vorhanden), bricht es für dieses Gerät ab und sagt verständlich: erst Projekte per USB-Export sichern, dann Werksreset, dann Entwicklermodus freischalten. **Vor einem Werksreset sind die Projekte unwiederbringlich weg, wenn sie nicht vorher gesichert wurden.**
- Jeder Schritt wird nachgeprüft, nicht nur abgeschickt. Ein Befehl ohne Fehlermeldung ist kein Beweis, dass er gewirkt hat.
- Kein Schritt, der ein Gerät löscht, läuft ohne ausdrückliche Bestätigung.

## PHASE 2 — Erprobung
Am fabrikneuen Gerät vollständig durchlaufen lassen, danach mit **mindestens zwei Geräten gleichzeitig**. Belege:
- App installiert, richtige Version.
- App ist Startbildschirm und startet nach dem Einschalten von allein — **mit zwei vollständigen Neustarts belegt**.
- Kiosk-Betrieb aktiv.
- Kamerabild kommt ohne jeden Eingriff.
- Protokoll liegt vor, je Gerät eine Zeile, verständlich.
- Gemessene Zeit je Gerät bei einzelner und bei paralleler Abarbeitung — die Zahl braucht der CEO für die Planung.

Scheitert ein Punkt: anhalten, Fehlermeldung wörtlich festhalten, melden.

## PHASE 3 — Anleitung
Eine Seite in `docs/`, für jemanden ohne Vorkenntnisse, mit zwei getrennten Wegen:
- **Neugerät (Regelfall):** auspacken, anstecken, Datei starten, Erfolgskontrolle. **Kein Werksreset.**
- **Rückläufer aus dem Feld (Sonderfall):** zuerst Projekte sichern, dann Werksreset, dann Entwicklermodus freischalten (Schritte wörtlich beschreiben), dann wie oben.
- Hinweis, dass die Einrichtung nach jeder Signaturänderung erneut nötig wird.

---

## HARTE REGELN
1. Kein `git add -A`, keine repo-weiten Git-Befehle.
2. Eigener Branch ab `feature/camera2-umstieg`. Kein Merge nach master, kein Tag, kein Publish.
3. Nur freigegebene Testgeräte. Louis' Gerät wird nicht angefasst.
4. Der Plattform-Keystore kommt **nicht** ins Paket — nur die fertig signierte App.
5. Keine echten Zugangsdaten in committete Dateien.
6. Zwischenstände regelmäßig committen und pushen.
7. Bei einem Blocker: anhalten und melden.

## BERICHT
`RESULT_WERKSEINRICHTUNG_2026-07-30.md`: Ergebnis Phase 0, was gebaut wurde, Phase-2-Belege inklusive der gemessenen Zeiten, die Anleitung, und was nicht geprüft werden konnte. Danach STOPP zur CEO-Abnahme.
