# Hardcoded String Audit Preview — Phase 0

**Scan-Datum:** 2026-05-20  
**Scope:** `ui/screens/**` und `ui/components/**`  
**Gefundene Fundstellen:** 19 (17 automatisch + 2 Toast manuell)  
**Betroffene Dateien:** 3  

> Hinweis: Diese Liste ist ein Vorab-Audit. Vollständige Behebung erfolgt in Phase 6.
> Strings die bereits `S()` / `LocalizationManager.t()` nutzen wurden übersprungen.
> Technische Strings (URLs, Konstanten, Format-Pattern) wurden gefiltert.

## Fundstellen

| # | Datei | Zeile | Typ | Text (gekürzt) |
|---|---|---|---|---|
| 1 | `ui\screens\inspection\InspectionScreen.kt` | 783 | `Text()` | Neu verbinden |
| 2 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 38 | `Text()` | Offline-Karten |
| 3 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 55 | `Text()` | Karte hinzufügen |
| 4 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 173 | `Text()` | Download starten? |
| 5 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 207 | `Text()` | Herunterladen |
| 6 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 212 | `Text()` | Abbrechen |
| 7 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 221 | `Text()` | Server nicht erreichbar |
| 8 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 252 | `Text()` | Karte löschen? |
| 9 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 258 | `Text()` | Löschen |
| 10 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 261 | `Text()` | Abbrechen |
| 11 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 318 | `Text()` | Region auswählen |
| 12 | `ui\screens\offlinemaps\OfflineMapsScreen.kt` | 398 | `Text()` | Schließen |
| 13 | `ui\screens\projectdetail\ProjectDetailScreen.kt` | 167 | `Text()` | $fileName ($fileSize) |
| 14 | `ui\screens\projectdetail\ProjectDetailScreen.kt` | 555 | `Text()` | Projekt unwiderruflich löschen? |
| 15 | `ui\screens\projectdetail\ProjectDetailScreen.kt` | 558 | `Text()` | Projekt: $pNum |
| 16 | `ui\screens\projectdetail\ProjectDetailScreen.kt` | 581 | `Text()` | Endgültig löschen |
| 17 | `ui\screens\projectdetail\ProjectDetailScreen.kt` | 694 | `Text()` | $count |
| 18 | `ui\screens\projectdetail\ProjectDetailScreen.kt` | 83 | `Toast` | Projekt gelöscht — ${r.filesRemoved} Dateien, ${kb} KB freigegeben |
| 19 | `ui\screens\projectdetail\ProjectDetailScreen.kt` | 92 | `Toast` | Löschen fehlgeschlagen: ${r.message} |

## Nächste Schritte (Phase 6)

1. Jeden Eintrag mit einem L10N-Key aus `phase0/keys_de_en.json` verknüpfen oder neuen Key anlegen
2. Composable auf `LocalizationManager.t("KEY")` / `S("KEY")` umstellen
3. Build + Tests grün halten

## Nicht erfasste Kategorien (manueller Review empfohlen)

- Toast-Strings mit `$`-Interpolation (z.B. `ProjectDetailScreen.kt` Zeilen 83, 92)
- Strings in `ViewModel`-Klassen (werden als State zur UI übergeben)
- Template-Strings mit `$variable`-Interpolation in Composables
- Strings in `DrawScope`/Canvas-Code (OSD-Overlay)
- Strings in Fehler-Logs (technisch, nicht user-facing)
- `OfflineMapsScreen.kt` vollständig auf L10N-Keys migrieren (kein bestehender Key-Set)
