> ÜBERHOLT am 2026-08-05 durch OFFENE_PUNKTE.md. Nur noch als Nachweis aufbewahrt.

# Offene To-dos — Stand 09.07.2026 (Feierabend)

Branch `feature/dual-mode`, **gepusht** (`6a51673..ec52c0b`, 14 Commits, am Host verifiziert), kein Merge, kein Tag.
Ausgelieferter Testbuild: **0.5.5-beta / versionCode 505** (Portal, Channel beta). Test-Mail an Louis ist raus.

## Merge-Gate `feature/dual-mode` → master

- [ ] **Station mit laufendem Meterzähler prüfen.** Der einzige nie verifizierte Punkt: Foto/Schaden aus dem fertigen Video muss den **eingebrannten** Meterwert tragen. An der Test-ONE hing kein Fahrwagen/keine Haspel, die Spur war durchgehend `0.00`. Louis' erster Prüfpunkt — oder Haspel anschließen und selbst messen.
- [ ] Lange Aufnahme ≥ 5 min mit Pause: Dauer == Echtzeit minus Pause.
- [ ] Kill-Test auf 0.5.5 wiederholen: Meter-Spur endet **innerhalb 1 s** der Videodauer (vorher 13 s Lücke).
- [ ] Foto **hinter** dem Spurende im geretteten Video → Station bleibt **leer**, keine stille Zahl.
- [ ] Wiederhergestelltes Video ist markiert (Badge in der Liste, Hinweis im PDF-Bericht).
- [ ] Rückfallschalter: Hardware-Recorder aus → alter Aufnahmeweg unverändert.
- [ ] W3 am Gerät: Kamerakopf-Vorbelegung; Durchmesser/Länge/Start/Ende direkt in HD über drei Projektanlagen.

## Entscheidungen (CEO)

- [ ] **Signing-Key.** Die Flotte ist in zwei Signatur-Welten gespalten (Release-Keystore `18f9dadb…` vs. Debug-Key `0a03f9ca…`). Der Debug-Key hängt am Windows-Benutzerprofil; geht er verloren, ist jedes Feldgerät nur noch per Deinstallation aktualisierbar. Entscheidungsvorlage zugesagt: fester Key, sichere Ablage, Off-Machine-Backup, Umstellung der bestehenden Geräte. **Vor der nächsten Kundenauslieferung.**
- [ ] **RTSP-Freeze während der Aufnahme.** Das Ein-Encoder-Gate (`CameraEncoderArbiter`) friert das Tablet-Live-Bild ein, solange die ONE aufnimmt. Für Louis irrelevant, im Tablet-Betrieb ein Funktionsverlust. Zu prüfen: erlaubt der RK3588 zwei Encoder-Instanzen?

## Welle 6 (nach Louis' Rückmeldung)

- [ ] **USB-Export:** kryptische Dateinamen, Fotos/Videos lassen sich nicht öffnen. Louis testet im Büro, dann Befunde einarbeiten.
- [ ] **Speicher-/Kapazitätsanzeige** (intern + USB-Stick) — Louis' Feature-Wunsch, noch nicht gebaut.
- [ ] Golden-Image neu aufsetzen (Werksreset + `dpm set-device-owner`) — erst mit dem finalen Signing-Key, sonst zweimal Arbeit.

## Backlog / niedrige Priorität

- [ ] Zero-Copy-Aufnahmeweg (V4L2-MJPEG → HW-Decoder → Surface → HW-Encoder). Laut `H264Encoder.kt` Z. 27 Voraussetzung für „volle 30 fps". Mit gemessenen 27,55 fps derzeit nicht dringend.
- [ ] Dedizierter Publish-API-Key mit eigener Rotation (statt geteiltem `DrainQCloud:ApiKey`) — Review-Empfehlung, in drainq.web/DECISIONS.md vermerkt.
- [ ] PDF-Handbuch neu rendern (Schrift barlow→inter).

## Gemessen am 09.07. (Beleg, nicht Schätzung)

| | Frames | Dauer | echte fps | Wiedergabe |
|---|---|---|---|---|
| vor Welle 5 | 498 | 41,5 s | **8,3** | 1,45× zu schnell |
| nach Welle 5 | 1721 | 62,47 s | **27,55** | Echtzeit |

Auflösung 1280×720. „HD" heißt in diesem Produkt 720p.
