# Reparaturauftrag — Leitungsverlauf: Meter-Zahlen überdrucken sich bei engen Befunden

Quelle: Thomas-Selbst-Testrunde 12.07.2026 (0.5.7 Beta, Thomas-ONE). M4-Route-Pfeil OK, aber im
PDF-Leitungsverlauf drucken sich zwei nah beieinanderliegende Meter-Werte übereinander.
Branch: `feature/dual-mode`. Kein Merge, kein Tag.
Empfohlenes Modell: **Sonnet / mittel** (einzeiliger Kern-Fix; Diagnose erledigt).

---

## Befund (am Gerät gemessen)

PDF-Bericht, Seite „Leitungsverlauf": Die linke Textspalte (Schadenstyp/Beschreibung/Thumbnails)
steht sauber gestaffelt. Die **Meter-Zahlen rechts am Rohr** sind bei zwei eng beieinanderliegenden
Befunden (~1,5 m) **übereinander gedruckt** → unlesbares Doppelbild. Rote Marker/Kreise am Rohr
selbst sind korrekt an ihrer wahren Position.

Hinweis (kein Bug, nicht anfassen): „unten (0.0 m)" oben resultiert aus benutzerdefinierten
Start-/Endpunkt-Namen. Start wird konstruktiv oben gezeichnet, Meter wachsen nach unten; Start-unten
ist der `reversed`-Export-Schalter.

## Ursache [Sicher — Code verifiziert]

`export/ProjectExportService.kt`, `addPipeProfilePages`. Jeder Befund hat zwei Y-Werte:
- `idealY` — wahre Rohrposition (roter Tick + Kreis).
- `adjustedY` — kollisionsentzerrt (`minSpacing = 55f`).

Die Entzerrung (`adjustedY`) wird auf Schadenstyp/Beschreibung/Thumbnails angewendet. Das
**Meter-Label wird jedoch auf `idealY` gesetzt — ohne Kollisionsschutz**:
```kotlin
// Meter label - positioned at the pipe line (right side)
document.add(
    Paragraph(entry.meterLabel)
        .setFixedPosition(pageNum, pipeX - 55, idealY - 4, 50f)   // <-- idealY: keine Entzerrung
        .setFontSize(8f).setBold()
        .setTextAlignment(TextAlignment.RIGHT)
)
```
Liegen zwei Befunde enger als `minSpacing`, sind ihre `idealY` fast gleich → die Meter-Zahlen
überdrucken sich. Der Schadenstyp direkt darunter nutzt bereits `adjY` und steht deshalb sauber.

## Fix [eindeutig]

Das Meter-Label ebenfalls auf `adjY` (= `entry.adjustedY`) setzen. Tick/Kreis bleiben auf `idealY`
(wahre Position); der gestrichelte Connector verbindet `adjustedY`-Labelbereich → `idealY`-Tick
bereits und überbrückt die Differenz — genau wie beim Schadenstyp.

**Datei:** `export/ProjectExportService.kt`, `addPipeProfilePages`, Meter-Label-Block:
```kotlin
document.add(
    Paragraph(entry.meterLabel)
        .setFixedPosition(pageNum, pipeX - 55, adjY - 4, 50f)     // idealY -> adjY
        .setFontSize(8f).setBold()
        .setTextAlignment(TextAlignment.RIGHT)
)
```
Das ist die einzige nötige Änderung. `adjustedY` ist bereits per Page auf `pipeBotY..pipeTopY`
geklemmt → keine Zahl rutscht aus dem Rohrbereich.

---

## Verifikation

**Am Gerät (Pflicht — Layout ist visuell):**
1. Projekt mit **zwei Schäden enger als ~0,3 m** auf kurzer Leitung (z. B. 1,44 m und 1,56 m auf 5 m) anlegen → PDF generieren.
2. Im Leitungsverlauf: beide Meter-Zahlen **getrennt lesbar**, jede per gestricheltem Connector an ihren roten Marker gekoppelt.
3. Gegenprobe mit weit auseinanderliegenden Schäden → Layout unverändert korrekt.
4. Mehrseitiger Fall (viele Schäden) → Zahlen bleiben je Seite lesbar, kein Überlauf.

**Unit-Test (ergänzend):**
- Die Overlap-Prevention erzeugt für Einträge < `minSpacing` bereits distinkte `adjustedY` — falls
  noch kein Test existiert, dünn ergänzen: zwei Positionen < minSpacing → `adjustedY`-Differenz ≥ minSpacing.

---

## Nicht anfassen
Route-Pfeil / Inter-Font-Einbettung (M4, verifiziert OK), `reversed`-Logik, Tick-Scale, Marker/Kreis
an `idealY` (sollen an wahrer Position bleiben), Paginierung.
