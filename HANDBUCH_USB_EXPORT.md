# Handbuch-Abschnitt: Bericht & Projektdaten auf USB-Stick exportieren

*(Zum Einfügen in die ONE-Bedienungsanleitung. Anlass: Louis-Feedback 06.07. #9b — der USB-Export existiert, war aber nicht dokumentiert.)*

Die DrainQ.ONE kann ein komplettes Projekt (Berichte, Fotos, Videos, Audionotizen, Kartenbild) **ohne PC** direkt auf einen USB-Stick kopieren.

## Voraussetzungen

- **Stick-Format:** FAT32 oder exFAT. NTFS-formatierte Sticks werden von Android in der Regel nicht beschrieben — solche Sticks vorher am PC auf **exFAT** (für Dateien > 4 GB) oder **FAT32** formatieren.
- **Stick anstecken:** über den USB-Anschluss bzw. USB-OTG-Adapter der ONE. Warten, bis Android den Stick erkannt hat.
- **Einmalige Berechtigung „Alle Dateien":** Beim ersten Export fragt die App den Zugriff „Alle Dateien verwalten" an (nötig, um auf den Stick zu schreiben). Der Dialog bietet einen Knopf, der direkt in die Android-Einstellung springt — dort für **DrainQ.ONE** erlauben, zurück in die App, „Erneut prüfen" antippen. Diese Freigabe merkt sich das Gerät.

## Schritt für Schritt

1. Projekt in der Projektliste öffnen (Projekt-Detailansicht).
2. Auf **USB-Export** tippen (Download-Symbol).
3. Falls „Alle Dateien"-Zugriff fehlt: **Zugriff erteilen** → in den Android-Einstellungen erlauben → zurück → **Erneut prüfen**.
4. Falls „kein Stick erkannt": Stick-Format prüfen (FAT32/exFAT), neu anstecken, **Erneut prüfen**.
5. Ziel wählen (bei mehreren Sticks) und Modus:
   - **Komplettes Projekt** — alle Dateien.
   - **Einzelne Dateien** — gruppiert nach Fotos / Videos / Audio / Berichte einzeln ankreuzen.
6. **Export starten** — der Fortschritt wird angezeigt.
7. Fertig: Die Dateien liegen auf dem Stick unter **`DrainQ/<Projektnummer>/`** (Unterordner `fotos/`, `videos/`, `audio/`, `berichte/` sowie `map.jpg`).

## Wenn es hakt

| Meldung / Symptom | Ursache | Lösung |
|---|---|---|
| „Kein Stick erkannt" | Stick nicht gemountet oder NTFS | Neu anstecken; auf FAT32/exFAT formatieren; „Erneut prüfen" |
| Fragt nach „Alle Dateien"-Zugriff | Erst-Nutzung / Berechtigung entzogen | Zugriff in den Android-Einstellungen erteilen, „Erneut prüfen" |
| „Zielordner nicht beschreibbar" | Stick schreibgeschützt oder Dateisystem nicht unterstützt | Schreibschutz prüfen, auf exFAT/FAT32 formatieren |

---

### Quick steps (English, for the field)

1. Format the USB stick as **FAT32 or exFAT** (not NTFS). Plug it into the ONE.
2. Open the project → tap **USB export** (download icon).
3. First time only: tap **Grant access**, allow „All files" for DrainQ.ONE in Android settings, go back, tap **Re-check**.
4. Pick **whole project** or **single files** → **Start export**.
5. Files land on the stick under **`DrainQ/<project number>/`**.
