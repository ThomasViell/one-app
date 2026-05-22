# Autorun L10N — Bedienungsanleitung

## Inhalt
- `PHASENPLAN_L10N.md` — Phasen 0–7 detailliert (Auftrag für Claude pro Phase)
- `start_phase0.ps1` — manueller Vorlauf (Inventur + ADR), einmalig
- `autorun_l10n.ps1` — Autorun Phasen 1–7, läuft über Nacht
- `docs/concepts/L10N_PORTAL_KONZEPT.md` — Konzept v1.1 (Single Source of Truth)

## Voraussetzungen einmalig prüfen

1. **GodMode-Permissions** in `%USERPROFILE%\.claude\settings.json`:

   ```json
   {
     "permissions": {
       "allow": ["Bash(*)", "Edit(*)", "Write(*)", "Read(*)", "Glob(*)", "Grep(*)"]
     }
   }
   ```

2. **claude CLI im PATH** — `claude --version` muss eine Version zurückgeben.

3. **Repos vorhanden:**
   - `C:\Projekte\drainq.one`
   - `C:\Projekte\Drainq\Drainq_Suite_repo`

4. **Branches aktuell:** in beiden Repos `git pull` auf Default-Branch.

5. **Skill `godmode`** liegt unter `C:\Projekte\drainq.one\.claude\skills\godmode\SKILL.md` — bereits vorhanden.

## Ablauf

### 1) Phase 0 manuell (heute Abend, vor dem Schlafen)

```powershell
cd C:\Projekte\drainq.one
.\start_phase0.ps1 *>&1 | Tee-Object -FilePath phase0.log
```

Erzeugt:
- `phase0/keys_de_en.json`
- `phase0/hardcoded_audit_preview.md`
- `docs/adr/0010-l10n-portal-as-sot.md`
- `RESULT_PHASE_0.md`

**Kontrolle nach Lauf:**
- `RESULT_PHASE_0.md` enthält die drei Marker `PHASE0-KEYS`, `PHASE0-SHARED-CANDIDATES`, `PHASE0-HARDCODED`
- Branch `feature/l10n-phase-0-inventur` gepusht

### 2) Autorun Phasen 1–7 (über Nacht)

```powershell
cd C:\Projekte\drainq.one
.\autorun_l10n.ps1 *>&1 | Tee-Object -FilePath autorun_l10n.log
```

Läuft ohne Eingriff. Bricht ab bei:
- Exit-Code ≠ 0 einer Phase
- fehlender `RESULT_PHASE_N.md`

**Schätzung Laufzeit:** Phase 1 ~15 min, Phase 2–4 (Web) je 45–90 min, Phase 5 (App-Refactor) 60–90 min, Phase 6 30 min, Phase 7 15 min. Insgesamt grob 5–8 Stunden.

### 3) Am Morgen prüfen

- `autorun_l10n.log` durchscrollen
- `RESULT_PHASE_1.md` … `RESULT_PHASE_7.md` lesen
- Marker prüfen (APK-Größe, gemappte Keys, behobene Hardcoded-Strings, Smoke-Test-Resultate)
- Pull-Requests reviewen in beiden Repos

## Modell-/Effort-Matrix

| Phase | Modell | Effort | Begründung |
|---|---|---|---|
| 0 | sonnet | think harder | Inventur ist Detail-Fleißarbeit, Heuristik für Shared-Detection muss sitzen |
| 1 | sonnet | think | Mapping-Heuristik mittelschwer |
| 2 | sonnet | think harder | DB-Schema + Migration + API-Design + Tests, hohe Folgekosten bei Fehlern |
| 3 | sonnet | think | UI-Aufbau gegen Phase-2-API |
| 4 | sonnet | think | DeepL-Integration + Glossar |
| 5 | sonnet | think harder | Kompletter Refactor des LocalizationManagers + Build-Hook |
| 6 | sonnet | think | Routine-Audit |
| 7 | haiku | — | Doku, Release-Notes, Smoke-Test-Protokoll |

## Notbremse

- **Phase abbrechen:** `Strg+C` im PowerShell-Fenster. Bisheriger Branch bleibt erhalten, nichts ist gepusht solange Phase nicht erfolgreich abgeschlossen.
- **Bestimmte Phase überspringen:** Im Skript-Array oben `$phases` den Eintrag auskommentieren UND eine künstliche `RESULT_PHASE_N.md` mit „skipped" anlegen.
- **Neustart ab Phase X:** Skript bricht bei erster fehlgeschlagener Phase ab. Nach Fix einfach erneut starten — alle vorher erfolgreichen Phasen werden übersprungen, sobald deren `RESULT_PHASE_N.md` existiert. (Aktuell nicht automatisch; manuell die Phasen-Liste oben kürzen.)

## Was passiert mit den 33 alten Sprachen?

In Phase 0 werden sie aus `LocalizationManager.kt` **ignoriert** (nur DE+EN extrahiert).
In Phase 5 wird der gesamte Block entfernt.
In Phase 7 werden auf dem Portal nur DE+EN als `core` aktiv, alle anderen Locales bekommen Status `inactive` und sind in der App nicht sichtbar.
