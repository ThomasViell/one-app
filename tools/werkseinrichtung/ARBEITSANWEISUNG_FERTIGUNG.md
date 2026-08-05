# Arbeitsanweisung — DrainQ.ONE einrichten

**Gilt für:** fabrikneue ONE-Anlagen vor der Auslieferung
**Dauer:** rund 15 Sekunden je Anlage · mehrere gleichzeitig möglich
**Stand:** 30.07.2026 · App-Version 0.9.0

---

## ⛔ DIE EINE REGEL

# Eine fabrikneue Anlage wird NIEMALS zurückgesetzt.

Kein Werksreset, keine Werkseinstellungen, nichts löschen. Auspacken und direkt anstecken.
Ein Werksreset schaltet die Verbindung zum PC ab — danach geht nichts mehr ohne Handarbeit am Gerät.

---

## Ablauf

**1. Anschließen**
Anlage auspacken, USB-Kabel an den Einrichtungsrechner.
Mehrere Anlagen gleichzeitig über einen USB-Verteiler sind ausdrücklich erwünscht — vier dauern nicht länger als eine.

**2. Starten**
Im Ordner `werkseinrichtung` die Datei **`Start-Werkseinrichtung.cmd`** doppelklicken.

**3. Nur beim allerersten Anschließen eines Geräts**
Erscheint auf dem Tablet die Frage „USB-Debugging zulassen?" → bestätigen, dann die Datei erneut starten.

**4. Warten**
Nach etwa 15 Sekunden steht je Anlage **GRÜN** oder **ROT** auf dem Bildschirm.

**5. Erfolgskontrolle — nicht überspringen**
Kabel abziehen. Anlage **zweimal** aus- und wieder einschalten.
Nach jedem Start muss gelten:
- DrainQ.ONE erscheint von allein — kein fremder Startbildschirm
- Das Kamerabild ist da, ohne dass jemand etwas antippt

Erst wenn das zweimal stimmt, ist die Anlage fertig.

**6. Protokoll ablegen**
Die Protokolldatei aus dem Ordner `logs` ablegen unter:

> **_______________________________________________**
> *(Ablageort eintragen)*

Darin stehen Seriennummer, Version und Zeitpunkt. Das ist der Nachweis, welche Software auf welcher Anlage war.

---

## Wenn ROT erscheint

**Anlage sofort zur Seite legen. Nicht auspacken zum Versand, nicht weiterreichen.**

Ein zweiter Versuch ist erlaubt — meist ist ein Kabel oder der Verteiler schuld.
Bleibt es rot, entscheidet:

> **_______________________________________________**
> *(Name / Funktion eintragen)*

Steht in der Meldung **„kein fabrikneues Gerät"**, ist es kein Neugerät. Solche Anlagen laufen über ein anderes Verfahren — Rückfrage halten, nicht selbst entscheiden.

---

## Vor jeder Charge kurz prüfen

Steht im Fenster beim Start die erwartete App-Version?
Aktuell erwartet: **0.9.0**

Stimmt sie nicht, ist der Paketordner veraltet. Dann nicht einrichten, sondern den aktuellen Ordner holen bei:

> **_______________________________________________**
> *(Bezugsquelle eintragen)*

---

## Was dieses Werkzeug erledigt

App aufspielen · als Startbildschirm setzen · Kiosk-Betrieb aktivieren · vorinstallierte Fremd-App entfernen · jeden Schritt einzeln nachprüfen · Protokoll schreiben.

Vor der Installation vergleicht es außerdem die Prüfsumme der App. Ein falsch signierter Stand kommt gar nicht erst auf ein Gerät.

---

*Ausführliche Fassung mit Sonderfällen (Rückläufer aus dem Feld, Bestandsgeräte, Rückholweg): `Anleitung.txt` im selben Ordner.*
