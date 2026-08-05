# AUFTRAG: Einrichtungswerkzeug holt sich die App selbst aus dem Lizenzportal

ROLLE: Android-/Windows-Ingenieur im Repo `C:\Projekte\drainq.one`.

**Anlass:** Das Werkzeug `tools/werkseinrichtung/` geht an die Produktion (Kollege Sven richtet den Produktionsrechner ein). Der Rechner ist **dauerhaft online**. Es muss sichergestellt sein, dass dort immer der aktuelle, freigegebene App-Stand eingerichtet wird — ohne dass jemand von Hand Ordner kopiert.

---

## ZIEL
Beim Start prüft das Werkzeug das Lizenzportal auf einen neueren freigegebenen Stand, lädt ihn bei Bedarf, prüft ihn und ersetzt die lokale Datei. Danach läuft die Einrichtung wie bisher.

**Quelle ist dasselbe Manifest, aus dem sich die Geräte aktualisieren** — nicht ein zweiter, eigener Weg. Der Kanal ist der, in den auch veröffentlicht wird. Mach den Kanal konfigurierbar, mit dem heute genutzten als Vorgabe.

## ABLAUF, den du bauen sollst
1. Manifest vom Portal holen und die dort angegebene Version mit der lokal vorliegenden App vergleichen.
2. **Portal neuer:** herunterladen, **Prüfsumme aus dem Manifest verifizieren**, **zusätzlich den Signatur-Fingerabdruck prüfen** (`2D:37:0C:21:F5:DF:D5:53:D2:A7:96:31:4B:70:92:5F:B3:8A:DE:EF:90:86:4C:92:0B:BB:BB:12:88:7D:35:22`). Erst wenn beides stimmt, die lokale Datei ersetzen — die alte vorher zur Seite legen, nicht überschreiben.
3. **Stimmt eine der beiden Prüfungen nicht:** heruntergeladene Datei verwerfen, mit der bisherigen weiterarbeiten, **deutlich sichtbar melden**. Niemals eine ungeprüfte Datei auf ein Gerät bringen.
4. **Portal nicht erreichbar:** mit der vorhandenen Datei weiterarbeiten, aber gut sichtbar anzeigen, welche Version das ist und von wann. Kein stiller Rückfall auf einen alten Stand.
5. **Lokal ist neuer als das Portal:** ebenfalls melden — das bedeutet, jemand hat einen unveröffentlichten Stand im Ordner, und das muss auffallen.
6. In jedem Fall die verwendete Version gut sichtbar oben im Fenster anzeigen und ins Protokoll je Gerät schreiben.

**Keine Zugangsdaten nötig:** Das Herunterladen des veröffentlichten Stands muss ohne Schlüssel funktionieren, so wie es die Geräte auch tun. Falls das nicht geht, **anhalten und melden** — dann ist es eine eigene Entscheidung und kein Bastelweg.

## ZUSATZ — Auslieferungspaket bei jeder Veröffentlichung
Erweitere `tools\publish-one-release.ps1` so, dass bei jeder Veröffentlichung zusätzlich ein fertiges Paket entsteht: der komplette Ordner `werkseinrichtung` mit der aktuellen App, ohne `logs`, als Zip mit Versionsnummer im Dateinamen. Das ist der Stand, den man weitergibt, wenn ein Rechner doch einmal offline ist.

## PRÜFEN, mit Belegen
- Portal hat neuere Version → wird geholt, geprüft, ersetzt, Einrichtung läuft durch.
- Manipulierte Datei (Prüfsumme absichtlich falsch) → wird abgelehnt, alte Datei bleibt, Meldung erscheint. **Diese Negativprobe ist Pflicht** — eine Prüfung, die nie ausgelöst hat, ist keine Prüfung.
- Portal nicht erreichbar (Netz trennen) → Werkzeug läuft weiter, Version und Datum werden angezeigt.
- Ein vollständiger Einrichtungslauf am Gerät nach dem Selbstaktualisieren, grün.

## HARTE REGELN
1. Kein `git add -A`, keine repo-weiten Git-Befehle.
2. Eigener Branch ab `master`. Kein Merge, kein Tag, kein Publish.
3. Der Plattform-Keystore kommt nicht ins Paket.
4. Keine echten Zugangsdaten in committete Dateien.
5. Bei einem Blocker: anhalten und melden.

## BERICHT
`RESULT_WERKZEUG_AUTOUPDATE_2026-07-30.md`: was gebaut wurde, die vier Prüfungen mit Belegen, und was nicht geprüft werden konnte. Danach STOPP.
