> **ÜBERHOLT (30.07.2026):** Der QR-Weg wurde am 29.07.2026 geprüft und scheidet aus — die
> ONE durchläuft nach einem Werksreset keinen Android-Einrichtungsassistenten, es gibt also
> keinen Willkommensbildschirm zum Scannen. Zusätzlich gilt seit dem 30.07.2026 die Regel:
> eine fabrikneue ONE wird NIEMALS zurückgesetzt (Werksreset schaltet die USB-Verbindung ab,
> siehe `docs/WERKSEINRICHTUNG.md`). Aktueller Weg: `tools/werkseinrichtung/` (per USB, ein
> Skript, siehe `WERKSEINRICHTUNG_PROMPT.md` und `RESULT_WERKSEINRICHTUNG_2026-07-30.md`).
> Diese Datei bleibt nur als Beleg für den geprüften und verworfenen Ansatz stehen.

# AUFTRAG: Geräteeinrichtung per QR-Code vorbereiten und erproben (ÜBERHOLT, siehe Hinweis oben)

ROLLE: Android-Ingenieur im Repo `C:\Projekte\drainq.one`.

CEO-Entscheid 29.07.2026: Die Einrichtung neuer bzw. zurückgesetzter ONE-Geräte soll **ohne PC** laufen — Werksreset, dann QR-Code auf dem Willkommensbildschirm scannen, das Gerät richtet sich selbst ein. Dieser Auftrag bereitet das vor und erprobt es.

**Dieser Lauf hat ein hartes Tor gleich am Anfang (Phase 0). Fällt es, wird nichts gebaut.**

---

## AUSGANGSLAGE — bekannt, nicht neu prüfen
- Gerät: ONE, RK3588, Android 12, `userdebug`, SELinux permissive.
- Die App ist plattformsigniert (Alias `bominwellalias`) und trägt `sharedUserId="android.uid.system"` (ADR-0005, angenommen).
- Ein `DeviceAdminReceiver` existiert bereits: `com.uip.oneapp.bootstrap.OneDeviceAdminReceiver` — er wurde bisher über `dpm set-device-owner` per adb genutzt.
- Der Kiosk-Betrieb (Device-Owner) ist nur auf einem kontenfreien Gerät einrichtbar. Ein Werksreset ist Voraussetzung.
- Die Deinstallation/Neuinstallation beim Signaturwechsel löscht Device-Owner-Status und Startbildschirm-Zuordnung (belegt 29.07.).
- Aktuelle Version: 0.6.1 / 601 auf `feature/camera2-umstieg`.

---

## PHASE 0 — DAS TOR: Beherrscht dieses Gerät die QR-Einrichtung überhaupt?

Hersteller-Systemabbilder lassen den Einrichtungsassistenten manchmal weg oder ersetzen ihn. Ohne den funktionierenden Assistenten ist der ganze Weg tot.

**Zu messen, an einem Gerät, das zurückgesetzt werden darf:**
1. Werksreset durchführen (das Testgerät ist dafür freigegeben, Daten sind entbehrlich).
2. Auf dem ersten Willkommensbildschirm mehrfach auf dieselbe Stelle tippen (der übliche Weg, um die Firmen-Einrichtung zu öffnen).
3. Erscheint eine QR-Scan-Aufforderung oder ein Firmen-Einrichtungsmenü — ja oder nein?

**Ergebnis JA** → weiter mit Phase 1.
**Ergebnis NEIN** → **STOPP und melden.** Nicht basteln, nicht nach Umwegen suchen. Dann fällt die Entscheidung auf den PC-Weg zurück, das ist eine CEO-Entscheidung.

Miss außerdem bei derselben Gelegenheit und halte es fest, weil es für den PC-Weg gebraucht wird:
- **Ist die USB-Wartungsverbindung (adb) nach einem Werksreset von allein aktiv?**

Beide Ergebnisse gehören in den Bericht, unabhängig davon, wie es weitergeht.

---

## PHASE 1 — Was gebraucht wird, zusammenstellen

1. **Die App muss aus dem Netz erreichbar sein.** Kläre und schlage vor, wo die Datei liegen soll — naheliegend ist das bestehende Portal (`license.drainq.com`), das die Auslieferung ohnehin macht. **Lege nichts ohne Rückfrage im Portal ab.** Wenn eine Entscheidung nötig ist: fragen, nicht entscheiden.
2. **Prüfsumme der App** für den QR-Inhalt berechnen (die Einrichtung lädt die Datei und vergleicht sie).
3. **QR-Inhalt bauen** mit: Verweis auf `OneDeviceAdminReceiver`, Downloadadresse, Prüfsumme. WLAN-Zugangsdaten optional mit hineinlegen, damit das Gerät ohne Handeingabe ins Netz kommt — **aber niemals echte Kundenpasswörter in eine committete Datei.** Vorlage mit Platzhaltern ins Repo, echte Werte nur zur Laufzeit.
4. **Erzeuger-Skript** unter `tools/`, das aus Version, Adresse und Prüfsumme einen fertigen QR-Code als Bilddatei ausgibt. Ein Aufruf, ein Bild.

## PHASE 2 — Am Gerät erproben
Zurückgesetztes Gerät, QR-Code scannen, durchlaufen lassen. Am Ende muss stehen:
- App installiert, in der richtigen Version.
- App ist Startbildschirm und startet nach dem Einschalten von allein — **mit zwei vollständigen Neustarts belegt**, nicht mit einem.
- Kiosk-Betrieb aktiv (Device-Owner gesetzt).
- Kamerabild kommt ohne jeden Eingriff.

Jeder Punkt mit Beleg. Scheitert einer: anhalten, Fehlermeldung wörtlich festhalten, melden. Keine Reparaturversuche ins Blaue.

## PHASE 3 — Anleitung
Eine Seite, die ein Mitarbeiter ohne Vorkenntnisse befolgen kann, in `docs/`:
- **Schritt 1 ist immer: Projekte per USB-Export sichern.** Danach sind sie weg. Diesen Schritt so schreiben, dass man ihn nicht überliest.
- Dann Werksreset, dann QR scannen, dann die Erfolgskontrolle.
- Und der Hinweis, dass dies nach jeder Signaturänderung erneut nötig wird.

---

## HARTE REGELN
1. Kein `git add -A`, keine repo-weiten Git-Befehle.
2. Eigener Branch ab `feature/camera2-umstieg`. Kein Merge nach master, kein Tag.
3. Nur das freigegebene Testgerät. Louis' Gerät wird nicht angefasst.
4. Nichts im Portal veröffentlichen ohne ausdrückliche Freigabe.
5. Keine echten Zugangsdaten in committete Dateien.
6. Zwischenstände regelmäßig committen und pushen.
7. Bei einem Blocker: anhalten und melden.

## BERICHT
`RESULT_QR_EINRICHTUNG_2026-07-30.md`: Ergebnis von Phase 0 (beide Messungen), was gebaut wurde, Phase-2-Belege, die Anleitung, und was nicht geprüft werden konnte. Danach STOPP zur CEO-Abnahme.
