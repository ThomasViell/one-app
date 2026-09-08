# Autorun L10n-Portal — Master-Plan

**Version:** 2.0 · 2026-05-20
**Status:** bereit zum Start (Preflight ausstehend)
**Arbeitskopie:** `C:\Projekte\drainq.one-localization` (Branch `feature/l10n-portal`)
**Original:** `C:\Projekte\drainq.one` bleibt unangetastet

---

## Worum es geht

Die DrainQ.ONE-App hat heute **10.578 Zeilen statische Kotlin-Maps** für 35 Sprachen
in `LocalizationManager.kt`. Pflege durch Händler ist praktisch unmöglich —
sie müssten Kotlin-Quellcode anfassen.

Ziel: gleiche L10n-Architektur wie DrainQ.Windows. **Portal als Single Source of
Truth**, App holt Sprachpakete on-demand vom Portal, Händler pflegen pro Land
über Web-UI (DeepL fügt Basisübersetzung, Partner reviewt).

**Das gesamte Konzept ist bereits ausgearbeitet** in:

- `docs/concepts/L10N_PORTAL_KONZEPT.md` (v1.1) — Architektur, Entscheidungen
- `PHASENPLAN_L10N.md` (v1.0) — 8 Phasen, je mit Definition-of-Done

Dieses Dokument hier ist **nur die Bedienungsanleitung** für den Autorun.

---

## Warum diese Variante (Arbeitskopie statt In-Place)

Original-Repo wird **nicht verändert**. Wenn morgen festgestellt wird, dass
die Migration nicht passt — alter Stand komplett intakt. Wenn sie passt —
Merge zurück nach `drainq.one`.

---

## Phasen-Übersicht (aus PHASENPLAN_L10N.md)

| # | Titel | Repo | Manuell/Auto | Effort |
|---|---|---|---|---|
| 0 | Inventur DE+EN, Key-Extrakt, ADR | drainq.one-localization | **MANUELL** | think harder |
| 1 | Key-Mapping snake → UPPER_SNAKE + Shared-Detection | drainq.one-localization | Autorun | think |
| 2 | Portal-Backend: Locale-Status, Scope, Partner, API | drainq.web | Autorun | think harder |
| 3 | Portal-Frontend: Tab DrainQ.ONE + Sprachen/Partner | drainq.web | Autorun | think |
| 4 | DeepL + Glossar + Partner-Review | drainq.web | Autorun | think |
| 5 | App: LocalizationManager neu (Bundle DE+EN, Lazy-Download) | drainq.one-localization | Autorun | think harder |
| 6 | App: Hardcoded-Audit + alle Composables auf `t("KEY")` | drainq.one-localization | Autorun | think |
| 7 | Cutover: 33 Sprachen löschen, DE/EN importieren, Smoke + Doku | beide | Autorun | — |

**Geschätzte Laufzeit Autorun (P1–P7):** ~4 Stunden netto Modellzeit (Sonnet),
unbeaufsichtigt über Nacht.

---

## Preflight (vor Phase 0)

Einmalig prüfen mit:

```powershell
cd C:\Projekte\drainq.one-localization
.\preflight_l10n.ps1
```

Der Preflight checkt:

1. **Arbeitskopie sauber?** Branch `feature/l10n-portal`, kein uncommitted state.
2. **Original-Repo unangetastet?** Diff gegen Original = 0 produktive Files.
3. **`claude` CLI im PATH** (`claude --version`).
4. **GodMode-Permissions** in `%USERPROFILE%\.claude\settings.json`.
5. **Suite-Repo erreichbar:** `C:\Projekte\DrainQ\drainq_suite_repo` existiert,
   `git status` clean.
6. **Portal-Repo erreichbar:** `C:\Projekte\DrainQ\drainq.web` existiert,
   `git status` clean.
7. **Telegram-Config:** `telegram-config.local.ps1` vorhanden mit Token + ChatId.
8. **Gradle-Wrapper** ausführbar (`gradlew.bat assembleDebug` startet).
9. **Skill `godmode` vorhanden** unter `.claude/skills/godmode/SKILL.md`.
10. **Disk-Space** ≥ 5 GB frei auf C:.

Exit-Code 0 = alle grün. Exit-Code 1 = mindestens ein Check rot, Liste oben.

---

## Phase 0 — MANUELL, vor dem Autorun

Phase 0 ist die Inventur. Manueller Start, weil das ADR vor dem Loslaufen
durch Dich abgesegnet sein muss.

```powershell
cd C:\Projekte\drainq.one-localization
.\start_phase0.ps1 *>&1 | Tee-Object -FilePath phase0.log
```

Erzeugt:

- `phase0/keys_de_en.json` — Extrakt der existierenden DE+EN aus dem alten LocalizationManager
- `phase0/hardcoded_audit_preview.md` — Liste aller hardcoded Text(…)-Stellen
- `docs/adr/0010-l10n-portal-as-sot.md` — ADR mit den 4 Kern-Entscheidungen
- `RESULT_PHASE_0.md` — Mengengerüst, Shared-Kandidaten-Statistik

**Nach dem Lauf prüfen:**

- `RESULT_PHASE_0.md` enthält die drei Marker `PHASE0-KEYS`, `PHASE0-SHARED-CANDIDATES`, `PHASE0-HARDCODED`
- Branch `feature/l10n-phase-0-inventur` gepusht
- ADR-Datei stimmt inhaltlich

Wenn das passt → Autorun starten.

---

## Autorun Phasen 1–7

Ein einziger Befehl, läuft über Nacht:

```powershell
cd C:\Projekte\drainq.one-localization
.\autorun_l10n_portal.ps1 *>&1 | Tee-Object -FilePath autorun_l10n_portal.log
```

**Was läuft ab pro Phase:**

1. Phase-spezifischer Branch angelegt
2. Skill `godmode` wird vom Claude konsultiert
3. Phase-Aufgabe wird ausgeführt
4. Build + Tests grün
5. Commit mit Conventional Commits Message
6. `RESULT_PHASE_<N>.md` geschrieben
7. **Telegram-Update gesendet:** Phase OK/FAIL + Auszug aus RESULT
8. Nächste Phase

**Fail-Fast:** Exit-Code ≠ 0 oder RESULT-File fehlt → Autorun bricht ab,
Telegram-Alarm, Du übernimmst manuell.

---

## Telegram-Updates

Konfiguration in `telegram-config.local.ps1` (wird vom Autorun gelesen):

```powershell
$env:TELEGRAM_BOT_TOKEN = "..."
$env:TELEGRAM_CHAT_ID   = "..."
```

(Datei ist bereits im Repo vorhanden und in `.gitignore` gelistet.)

**Nachrichten-Schema:**

```
[START] L10n-Portal Migration
        Repo: drainq.one-localization
        Phasen: 1..7
```

```
[P3 OK] Portal-Frontend (Tab DrainQ.ONE + Partner)
        Dauer: 18m 32s
        Branch: feature/l10n-phase-3-frontend @ a1b2c3d
        Marker: P3-SCREENS=4 P3-E2E=GREEN
```

```
[P5 FAIL] App LocalizationManager Refactor
          Build rot nach 24m
          Letzter Fehler: app/.../LocalizationManager.kt:142 Type mismatch
```

---

## Was nach dem Lauf passiert

1. **Du prüfst** über Telegram-Verlauf, ob alle Phasen OK sind.
2. **Smoke-Test auf dem Tablet:** APK aus `drainq.one-localization` bauen
   und installieren, durch alle Screens klicken in DE + EN.
3. **Entscheidung morgen:** wenn alles passt → Merge `feature/l10n-portal`
   in `drainq.one/master`. Wenn nicht → Arbeitskopie bleibt liegen,
   Original ist unverändert.

---

## Notbremse

Wenn während des Autoruns etwas schiefläuft und Du eingreifen musst:

```powershell
# In einer zweiten PowerShell:
Get-Process | Where-Object { $_.ProcessName -like "claude*" } | Stop-Process -Force
```

Telegram zeigt sofort den letzten Status. Die Arbeitskopie ist immer in
einem konsistenten Branch-Zustand — letzte Phase entweder vollständig
committed oder gar nicht begonnen.

---

## Referenzen

- `docs/concepts/L10N_PORTAL_KONZEPT.md` — Architektur-Konzept v1.1
- `PHASENPLAN_L10N.md` — vollständige Phasen-Definition mit DoD je Phase
- `AUTORUN_L10N_README.md` — Vorgänger-Bedienungsanleitung (für die alte In-Place-Variante)
- DrainQ.Suite Vorlage (Client-Loader): `C:\Projekte\DrainQ\drainq_suite_repo\src\DrainQ.Core\Services\Localization\LocalizationService.cs`
- DrainQ.Suite Vorlage (Sync-Tool): `C:\Projekte\DrainQ\drainq_suite_repo\tools\SyncTranslations\`
- Portal-Backend (Server-API): `C:\Projekte\DrainQ\drainq.web\src\DrainQ.Web\Controllers\TranslationController.cs`
- Portal-Modell (Scope `APP|WEB|CATALOG` → `ONE` ergänzen): `C:\Projekte\DrainQ\drainq.web\src\DrainQ.Web\Models\Translation\TranslationReference.cs`
