# Analyse-Auftrag: drainq.one — ALPHA → BETA

> Prompt für einen Voll-Analyse-Lauf (Claude Opus „Ultracode" / Claude Code) direkt im Repo `C:\Projekte\drainq.one`.
> Modus: **Audit + Quick-Win-Fixes**. Ziel: belastbarer BETA-Readiness-Befund + sofortige Reparatur trivialer Lücken.

---

## Rolle & Mission

Du bist Senior-Android-Ingenieur und technischer Auditor für **drainq.one**, die DrainQ-Schiebekamera-App. Deine Mission: die App vollständig analysieren und sauber von **ALPHA auf BETA** bringen. Im Zentrum stehen drei Fragen, in genau dieser Priorität:

1. **Was ist nicht verdrahtet?** — UI-Elemente, Schalter, Einstellungen, Menüpunkte, die existieren, aber an keine Logik/keinen Service/keine Hardware angebunden sind (reines Metadatum, toter Toggle, Setting ohne Wirkung).
2. **Was läuft ins Leere?** — Buttons ohne Handler, Navigationsziele die nichts tun, stille `return`-Guards, Stubs/Platzhalter, `TODO`/`FIXME`, in DI nicht registrierte oder als `null`/No-Op verdrahtete Services, Delegates die nie gesetzt werden, Flows die niemand sammelt, Export der keine valide Datei erzeugt.
3. **Was fehlt für BETA?** — priorisierte BETA-Blocker mit Aufwandsschätzung.

**Maßstab BETA:** Der Kern-Workflow läuft am Gerät ohne Sackgassen und ohne stille Fehlschläge: Live-Bild → erfassen (Foto/Video/Schaden/Notiz) → speichern → wiederfinden → exportieren/teilen. Jede sichtbare Bedien-Affordanz hat eine echte Wirkung oder ist entfernt. Keine Funktion, die „so aussieht als ginge sie", aber ins Leere läuft.

---

## Wichtig: KRITIS / NIS2 spielen hier KEINE relevante Rolle

drainq.one läuft bei **Handwerkern im Sanitärbereich**, nicht an kritischer Infrastruktur. Behandle KRITIS/NIS2/ISO-27001 daher **nur am Rande**: Erwähne grobe Sicherheits-Hygiene (z. B. Klartext-Credentials, Secrets im Repo, offene Datei-Permissions) als normale Qualitätsbefunde, aber **nicht** als BETA-Gate und **ohne** ausführliche Compliance-Blöcke. Kein KRITIS-Schwerpunkt, keine NIS2-Maßnahmenkataloge. Wenn ein Compliance-Skill automatisch anspringt: auf eine knappe Randnotiz beschränken.

---

## Kontext zur App (Stand verifizieren, nicht blind übernehmen)

- **Was es ist:** ONE-Schiebekamera-App, läuft **direkt auf der ONE-Hardware** (RK3588, Android), nicht als Tablet-Slave.
- **Hardware-Anbindung:** Steuerung seriell über `/dev/ttyS5`, Video über V4L2 `/dev/video0`. Native Bridge `app/src/main/cpp/v4l2bridge.c`. Serielles Protokoll in `network/internal/` (`OneInternalHardwareService.kt`, `OneFrameCodec.kt`).
- **Stack:** Kotlin / Jetpack Compose, Room (KSP), Koin (DI), Media3/ExoPlayer, iText7 (PDF), Coil-SVG. `compileSdk 35`, `minSdk 26`, `targetSdk 34`, `applicationId com.uip.drainq.one`, `versionName` aktuell `0.3.0`.
- **Paketwurzel:** `app/src/main/java/com/uip/oneapp/` mit Schichten: `bootstrap`, `di`, `data` (`local/dao`, `local/entity`, `repository`), `network` (+ `network/internal` = serielle/V4L2-Anbindung), `export` (+ `export/model`), `cloud`, `maps`, `update`, `ui` (`components`, `hardware`, `localization`, `navigation`, `screens/*`, `theme`, `utils`).
- **Aktiver Branch:** `feature/network-settings` (Superset: SA-Design-Rollout + Device-Fixes + Netzwerk-Feature + Pager).
- **Umfang:** ~110 Kotlin-Dateien in `main`, nur **6 Testdateien**, **~108 TODO/FIXME/STUB-Marker** im Code.

**Bekannte offene Punkte aus `PROJECT_STATUS.md` (gezielt nachprüfen, ob real noch offen):**
- Kamerakopf-Erkennung **C10/C18** — Stand Status seriell nicht erkennbar; laut Auto-Memory am 05.06. via Stream-Reassembly gelöst. Prüfen, was im Code wirklich verdrahtet ist (Enum, Live-Chip, Mapping) und ob Reste/Diagnose-Logs übrig sind.
- **SD/HD-Toggle** — laut Status „nicht verdrahtet" (nur Metadatum, kein Recorder liest es, Köpfe HD-only). Klären: Toggle entfernen oder echt anbinden.
- **DrainQ-Cloud-Login** — als **Stub** markiert (`cloud/`, Netzwerk-Screen). Kein echtes OAuth/Token gegen drainq.web. Prüfen, wie weit der Stub ins Leere läuft.
- **Geräte-Gate Welle 1** (OSD-Einbrennung/Recording + PDF-Overlay) — vor Merge zu verifizieren.
- **Branch-Konsolidierung** offen; Tag-Ziel `v0.4.0`.

**Feldtest-Befunde aus `FEEDBACK_Jakob_2026-06-02_Analyse.md` (jeden Punkt am aktuellen Code gegenprüfen — manches wurde seither evtl. gefixt):**
- Kiosk/Immersive-Mode fehlte (Wischen → Android-Homescreen, Gerät hängt) — *Showstopper*.
- Capture-Pfade brachen ohne Projekt still ab (`if (projectId == null) return`) → „Schnellaufnahme"-Modus war beschlossen.
- Sonde an/aus + Frequenz ohne sichtbare Wirkung (Bedienung im Slide-Panel versteckt / HW-Connect).
- Tastatur fährt nicht ein (kein IME-Dismiss in Dialogen).
- Hardware-Tasten `btn1..btn6` aus `GROUP_STATUS` wurden in `foldFrames()` verworfen → physische Tasten wirkungslos; gemeinsame Aktionsliste für Softbutton-Leiste + Hardtasten war beschlossen.
- Kein Zurück aus `InspectionScreen`; „Gallery" = Projektverzeichnis (Benennung).

Diese Liste ist **Ausgangsverdacht, kein Ergebnis**. Verifiziere am Code, was heute tatsächlich verdrahtet ist und was noch ins Leere läuft.

---

## Vorgehen (systematisch, schichtweise)

Arbeite das Repo Schicht für Schicht durch. Für **jede sichtbare Bedien-Affordanz** verfolge die Kette: **UI-Element → onClick/Handler → ViewModel/Repository → Service → Hardware/DB/Datei** und markiere jede Stelle, die ohne echte Wirkung endet.

1. **Inventur & Marker-Sweep.** Alle `TODO`/`FIXME`/`STUB`/`not implemented`/`placeholder`/leere `catch`/`return@`-Guards/No-Op-Lambdas erfassen (~108 Treffer als Startpunkt). Jeden Marker bewerten: harmlos, Politur oder echter Dead-End/BETA-Blocker.
2. **DI-Graph (Koin).** Jedes Modul in `di/` prüfen: Wird jeder deklarierte Service auch injiziert und genutzt? Gibt es Interfaces, die auf eine No-Op-/Null-Implementierung zeigen (analog zum SA-Fund „lief in NullExportService")? Wird ein `persistDelegate`/Callback in DI nie gesetzt?
3. **Daten (Room).** Entities ↔ DAOs ↔ Repositories: Werden alle DAOs benutzt? Fehlen Migrationen? Schreibt die App wirklich persistent, oder hängt etwas in-memory? Geht bei Feldeinsatz/Prozess-Tod etwas verloren?
4. **Hardware (`network/internal`).** Serielles Protokoll (`OneFrameCodec`, `OneInternalHardwareService`): Welche RX-Gruppen werden geparst, welche verworfen (z. B. Tasten-Bytes)? Welche TX-Kommandos (Sonde, Licht, Meter-0, Frequenz, Recording) erreichen die HW, welche enden in „TX failed"/ohne Permission? V4L2-Recording/Capture: erzeugt es echte Dateien? Trenne „am Gerät zu verifizieren" sauber von „im Code definitiv kaputt/fehlend".
5. **Export (`export/`).** ISYBAU/XML/PDF: Erzeugt der Export valide, vollständige Dateien (gegen Schema/Erwartung), oder ist es ein Fake-Schema/Teilexport? (SA hatte hier einen Fake-Schema-Fund.) Werden alle erfassten Artefakte einbezogen?
6. **Update (`update/`).** Proxy-Update gegen GitHub-Releases (`UPDATE_PROXY_URL`): Greift der Pfad wirklich, oder zeigt er auf ein Release, das es (noch) nicht gibt? Fehlerfälle abgedeckt?
7. **Cloud (`cloud/`) + Netzwerk-Screen.** Wie weit ist der Login-Stub? Was tut der „Login"-Button real? Klar als „kommt später" kennzeichnen vs. „sieht funktionsfähig aus, ist es aber nicht".
8. **Navigation (`ui/navigation`).** Jedes Ziel im NavGraph: erreichbar? führt zurück? Sackgassen (Screen ohne Rückweg)? Tabs/Buttons, die auf nicht-implementierte Routen zeigen?
9. **Screens (`ui/screens/*`).** Pro Screen: tote Buttons, Settings ohne Effekt (SD/HD-Toggle!), Dialoge ohne IME-Dismiss, Hardtasten-Anbindung, Schnellaufnahme-Pfad.
10. **Lokalisierung.** Fehlende Keys / Roh-Keys sichtbar in der UI? (`missing_keys.txt`, `_l10n_bundle` als Hinweis.)
11. **Build & Tests.** `assembleDebug` bauen; Warnings sichten. Die 6 Tests laufen lassen. Test-Lücken um die Kern-Erfassung benennen (nicht alles testen — nur BETA-kritische Pfade vorschlagen).

**Verifikationsdisziplin:** Jeden behaupteten „läuft ins Leere"-Befund am tatsächlichen Code belegen (Datei + Zeile + zitierte Stelle), nicht aus Namen/Annahmen ableiten. Wo nur ein Geräte-Test Gewissheit bringt, das ausdrücklich als „on-device zu verifizieren" kennzeichnen statt als Fehler zu werten.

---

## Modus: Audit + Quick-Win-Fixes

- **Audit:** vollständiger Befund (s. Deliverables).
- **Quick-Wins direkt fixen** auf einem **eigenen Branch** `feature/beta-hardening` (von `feature/network-settings` abzweigen): nur **triviale** Dead-Ends/Verdrahtungslücken mit klarer, risikoarmer Lösung (z. B. fehlende IME-Dismiss-Action, toter Toggle entfernen/anbinden, fehlende Zurück-Affordanz, nicht registrierter Service in Koin nachziehen, leeres `catch` mit Log). 
- **Alles Größere** (Architektur, HW-Protokoll-Erweiterung, echtes Cloud-OAuth, Export-Neubau) **nicht** umsetzen — nur als priorisierten BETA-Blocker mit Lösungsskizze + Aufwand beschreiben.
- Pro Quick-Win **ein** fokussierter Commit mit aussagekräftiger Message. **Kein Merge**, **kein** `git add -A` — gezielt stagen. Branch unverändert lassen, wenn unsicher.

---

## Repo-/Umgebungs-Regeln (zwingend beachten)

- **Git nur lokal im Terminal.** Niemals `git add -A` (CRLF-Mount-Churn ~199 Dateien ist kein echter Stand) — gezielt einzelne Dateien stagen.
- **Mount-Reads über das Cowork-Laufwerk können lügen** (Zeilenenden/Churn). Den echten Stand host-/terminalseitig prüfen.
- **Kein `su`/Root** auf dem Gerät; HW-Serial wird nativ angesprochen.
- **Build:** `JAVA_HOME="C:\Android\jdk17"`, dann `.\gradlew assembleDebug`. Installation aufs Gerät (falls vorhanden): `ANDROID_SERIAL` setzen, `.\gradlew installDebug`.
- CLI-Befehle, die der CEO selbst ausführt, **immer mit vollem Pfad / `--project`-Pfad** angeben.

---

## Deliverables

Lege im Repo-Root an: **`BETA_READINESS_AUDIT.md`** mit:

1. **Executive Summary** (½ Seite): BETA ja/nein, Anzahl Blocker, Gesamteinschätzung.
2. **BETA-Blocker — priorisiert** (Tabelle): `Nr | Befund | Ort (Datei:Zeile) | Kategorie (nicht verdrahtet / läuft ins Leere / fehlt) | Schweregrad (Showstopper/Hoch/Mittel) | Quick-Win-fixbar (ja/nein) | Aufwand (S/M/L)`.
3. **Befunde im Detail** — je Befund: was, belegte Ursache (Code-Zitat + Pfad/Zeile), Auswirkung, Lösungsskizze. Gruppiert nach Schicht (s. Vorgehen).
4. **„Nicht verdrahtet / tote Affordanzen"** — eigene, vollständige Liste aller UI-Elemente/Settings ohne Wirkung.
5. **Bereits gefixte Quick-Wins** — was auf `feature/beta-hardening` repariert wurde, je mit Commit-Hash + einer Zeile Begründung.
6. **On-device zu verifizieren** — separater Block: Befunde, die nur am Gerät abschließend bestätigt/widerlegt werden können (HW-Tasten-Mapping, Sonde, V4L2-Recording, Kamerakopf-Erkennung), mit konkreter Prüfanleitung (Logcat-Tag, Schritt).
7. **Empfohlene Reihenfolge bis BETA** — knappe Wellen-/Schrittliste mit Aufwand.
8. **Test-Lücken** — nur BETA-kritische Pfade, mit Vorschlag für minimale Absicherung.

Format: deutsch, knapp, faktenbasiert, keine Floskeln. Jede Behauptung mit Datei:Zeile belegen.

---

## Definition of Done für diesen Lauf

- `BETA_READINESS_AUDIT.md` vollständig, jede Aussage code-belegt.
- Quick-Wins auf `feature/beta-hardening` committet (einzeln, kein Merge), `assembleDebug` grün.
- Klare Trennung: „im Code definitiv offen" vs. „nur am Gerät verifizierbar".
- Priorisierte BETA-Blocker-Liste, an der der CEO ohne Rückfragen die nächste Welle starten kann.
