# DrainQ.ONE 0.4.3-beta — Testanleitung

Diese Version läuft in **zwei Betriebsarten aus EINER App**:

- **Direkt auf der ONE** (wie gewohnt).
- **Auf einem separaten Tablet**, das sich per WLAN mit der ONE verbindet — mit voller Steuerung.

Die App erkennt beim Start selbst, wo sie läuft.

**Wichtig vorab:** Die App ist hier provisorisch installiert (noch nicht als Geräte-Owner registriert). Deshalb einmalig der kleine Standort-Schritt unter Punkt 3 — sonst startet der Tablet-Hotspot nicht.

## 1. Installieren (ONE und Tablet)

1. Falls schon eine ältere DrainQ.ONE drauf ist: zuerst **deinstallieren** (neue Signatur).
2. Datei `DrainQ-ONE_0.4.3-beta_405.apk` auf das Gerät kopieren, antippen, installieren. „Unbekannte Quellen / dieses Gerät zulassen" bestätigen.
3. Auf **beiden** Geräten installieren (ONE + Tablet).
4. Tipp ONE: „Kiosk-Modus" in den App-Einstellungen während des Tests **AUS** lassen. Kommt beim HOME-Druck ein Auswahldialog → den **System-Launcher** wählen (nicht DrainQ.ONE „immer").

## 2. Direkt auf der ONE testen

App auf der ONE starten → sie geht automatisch in den **Direkt-Modus**.

- Projekt anlegen, Haltung starten.
- Live-Video, Meterzähler, Licht AN/AUS, Schaden erfassen.

## 3. Einmalig Standort freigeben (für den Tablet-Hotspot)

Nur einmal nötig, danach merkt sich die ONE das:

1. Android-Standort einschalten (Schnelleinstellungen oben → „Standort" → AN).
2. In der App: Projekt anlegen/öffnen → den **GPS-/Standort-Knopf** antippen.
3. Android fragt nach Standort → **„Bei Nutzung der App"** + **„Genau"** wählen → Erlauben.

## 4. Tablet per WLAN mit der ONE verbinden

1. Auf der ONE: **Tablet-Hotspot-Schalter AN** → ein QR-Code erscheint.
2. Auf dem Tablet: App starten → sie geht automatisch in den **Tablet-/WLAN-Modus** → QR-Code scannen.
3. Tablet verbindet sich mit der ONE: Live-Video, Steuerung und Schadenserfassung laufen über WLAN.

Hinweis: Solange der Hotspot an ist, hat die ONE selbst **kein** WLAN-Internet (ein Funkmodul, entweder/oder).

## 5. Bekannte Punkte (schon auf der Liste)

- Licht-**Helligkeitsregler** im Tablet-Modus noch nicht sauber. Licht AN/AUS und Meter-Reset funktionieren.

## 6. Bitte zurückmelden

- Läuft die Installation auf ONE und Tablet?
- Direkt-Modus: Video / Meter / Licht / Schadenserfassung ok?
- Tablet-Modus: QR-Kopplung, Video, Steuerung, Schadenserfassung ok?
- Wo hakt es / was fehlt? Gern kurze Stichpunkte + Screenshots.
