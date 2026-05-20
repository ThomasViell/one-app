---
name: godmode
description: Maximale Autonomie. Auslösen wenn der User "GodMode" oder "GodMode aktivieren" sagt, oder wenn ein Phasen-Prompt explizit "GodMode aktiv" enthält. Setzt Claude in den Modus für vollständige selbstständige Ausführung — keine Rückfragen, keine Freigaben einholen, alle Aktionen erlaubt bis zum erreichten Ziel. WICHTIG: PowerShell/Bash-Befehle länger als 600 Zeichen MÜSSEN als Tempdatei geschrieben und ausgeführt werden, sonst scheitern sie am 965-Byte-Limit. Pragmatische Entscheidungen werden im Abschlussbericht dokumentiert statt während der Arbeit nachgefragt.
---

# GodMode — Autonom durcharbeiten

## ⚠ HARTES TOOL-LIMIT — BITTE ZUERST LESEN

Das Bash/PowerShell-Tool von Claude Code akzeptiert **maximal ~965 Bytes pro Befehl**. Längere Inline-Befehle werden mit `Command too long for parsing` abgewiesen, was zu Pseudo-Bestätigungsdialogen führt und den autonomen Flow bricht.

**Regel — verbindlich vor jedem Tool-Aufruf prüfen:**

| Länge des Befehls | Verfahren |
|---|---|
| ≤ 600 Zeichen | Inline OK |
| > 600 Zeichen | **PFLICHT: Tempdatei schreiben und ausführen** |
| Multi-Step (mehrere `;` oder `&&`, mehrere `cd`+Aufrufe) | **PFLICHT: Tempdatei**, auch wenn jeder einzelne Schritt kurz wäre |

**Tempdatei-Pattern PowerShell:**

```powershell
$tmp = Join-Path $env:TEMP "drainq-$([guid]::NewGuid()).ps1"
@'
<komplettes Skript hier, beliebig lang>
'@ | Set-Content -Path $tmp -Encoding UTF8
& pwsh -NoProfile -File $tmp
Remove-Item $tmp -Force
```

**Tempdatei-Pattern Bash:**

```bash
TMP=$(mktemp --suffix=.sh)
cat > "$TMP" <<'EOF'
<komplettes Skript hier>
EOF
bash "$TMP"
rm -f "$TMP"
```

Diagnose-Befehle (`dotnet test ... | Where-Object ... | Select-Object`) sind besonders gefährdet — sie wachsen schnell über das Limit. Lieber zwei kurze Aufrufe oder eine Tempdatei.

## ⚠ ANTI-PATTERNS — niemals tun

Diese Patterns triggern Tool-Fehlermeldungen (auch wenn der sichtbare Befehl kurz ist), weil Claude Code intern beim Quoting/Escaping/Wrapping in pwsh stolpert:

1. **Shell-Mix von Windows-CMD-Syntax in Bash-Tool**
   ```
   if not exist "C:\..." mkdir "C:\..." || true   ← BREAKS in /usr/bin/bash
   ```
   → Stattdessen: File-Tool direkt nutzen.

2. **PowerShell-Pipes mit `| Out-Null` oder mehreren `;` über das Bash-Tool aufgerufen**
   ```
   New-Item ... | Out-Null; Write-Host "created"   ← BREAKS bei pwsh-Wrapping
   ```
   → Stattdessen: zwei einzelne Aufrufe oder .ps1-Tempdatei.

3. **Verzeichnisse via Shell anlegen**
   → IMMER vermeiden. Stattdessen: `Write`-Tool für irgendeine Datei im Zielverzeichnis aufrufen. Parent-Verzeichnisse werden automatisch erzeugt.

4. **Datei-Existenz via Shell prüfen** (`if`, `test -f`, `Test-Path`)
   → Stattdessen: `Glob`-Tool mit dem Pfad-Pattern.

5. **Datei-Inhalte via Shell lesen** (`cat`, `Get-Content`, `type`)
   → Stattdessen: `Read`-Tool.

6. **Multi-Step-Befehle in einer Zeile** (`cd X && dotnet Y && git Z`)
   → Stattdessen: einzelne Bash-Aufrufe oder .ps1-Tempdatei. Jeder Bash-Aufruf bekommt sein eigenes Working Directory via `cd` AM ANFANG, kein Aneinanderketten.

**Goldene Regel:** Wenn der Shell-Befehl mehr als ein einzelnes Programm aufruft (`dotnet test`, `git status`, `dotnet build`), prüfe zuerst, ob die File-Tools (`Read`, `Write`, `Edit`, `Glob`, `Grep`) den gleichen Zweck erfüllen. Sie sind robuster gegen Tool-Wrapping-Probleme.

---

Ab Aktivierung dieses Skills gelten für den Rest der Session bzw. Aufgabe folgende Regeln. Sie überschreiben das Default-Verhalten, NICHT aber die Engineering-Regeln aus dem `rules`-Skill (Build-Verifikation, Conventional Commits, keine Symptom-Patches usw. — die bleiben verbindlich).

## Voraussetzung: Tool-Permissions

GodMode wirkt auf Modell-Ebene (keine "Soll ich…?"-Rückfragen). Damit auch Claude Code selbst keine Bestätigungen für Bash/Edit/Write einholt, muss in `~/.claude/settings.json` eine Allow-Liste hinterlegt sein:

```json
{
  "permissions": {
    "allow": ["Bash(*)", "Edit(*)", "Write(*)", "Read(*)", "Glob(*)", "Grep(*)"]
  }
}
```

Wenn das fehlt, fragt Claude Code trotz GodMode-Skill bei jedem Tool-Aufruf nach. Setup einmalig via `setup-godmode-permissions.ps1` oder manuell über `/permissions → Add a new rule → User settings`.

---

## 1. Keine Rückfragen

- **Keine Klärungsfragen mehr.** Wo Default-Verhalten zurückfragen würde, wird stattdessen die pragmatischste Entscheidung getroffen.
- **Keine "Soll ich…?"-Sätze.** Keine "Möchten Sie…?"-Sätze. Keine "Bevor ich weitermache, möchte ich kurz…"-Sätze.
- **Bei echter Mehrdeutigkeit:** Annahme dokumentieren, weiterarbeiten, am Ende im Bericht hervorheben.
- **Ausnahme:** Wenn eine Aktion irreversibel und unklar ist (z.B. `git push --force` auf `main`, `DROP DATABASE`, Löschen von Produktivdaten) — DANN halten und fragen. Sonst nicht.

## 2. Keine Freigaben einholen

- **Keine Pausen vor Commits/Pushes** zum "Ist das so OK?"-Fragen.
- **Keine "Approval"-Schritte** zwischen logischen Arbeits-Etappen.
- Direkter Flow: lesen → analysieren → planen → bauen → testen → committen → pushen → nächste Etappe.

## 3. Alle Aktionen erlaubt

- Volle Schreibrechte auf den gesamten Repo-Code.
- Git-Operationen (commit, push, branch, merge, rebase) ohne Nachfrage erlaubt — solange Regel 1 (Force-Push/Reset-Hard nur auf explizite Anweisung) eingehalten wird.
- Dependencies hinzufügen/aktualisieren ohne Nachfrage erlaubt.
- Neue Migrationen anlegen ohne Nachfrage erlaubt.
- Neue Dateien, Ordner, Module, Services, Controller, Razor-Pages anlegen.
- Bestehende Code-Strukturen umbauen wenn nötig.
- Tools (ripgrep, dotnet ef, git, npm, etc.) ohne Bestätigung aufrufen.
- Web-Recherche, Doku-Lesen, Library-Vergleiche eigenständig ohne Nachfrage.

## 4. Pragmatische Entscheidungen werden dokumentiert, nicht erfragt

Wenn unklar ist, welcher von mehreren Wegen sinnvoller ist:

1. Den pragmatischsten wählen — das ist meist der mit weniger Abhängigkeiten, weniger neuem Risiko, näher an bestehenden Patterns im Repo.
2. Die Entscheidung im Endbericht unter "Abweichungen vom Plan" festhalten.
3. Wenn die Entscheidung das nächste Mal anders sein sollte, kann der User das im Folgeschritt korrigieren.

## 5. Aufgabe als beendet melden — nicht "Bereit für nächsten Schritt?"

- **Nicht:** "Soll ich jetzt mit dem nächsten Punkt weitermachen?"
- **Stattdessen:** Weitermachen, bis die definierte Aufgabe (Definition of Done aus dem Prompt) komplett erfüllt ist.
- Erst dann den finalen Bericht abgeben.

## 6. Was bleibt unverändert

Diese Regeln aus `rules`-Skill und Repo-Konventionen werden NICHT überschrieben:

- Build muss grün sein (`dotnet build` 0 errors, 0 warnings)
- Tests müssen grün sein (`dotnet test`)
- Conventional Commits
- Keine Symptom-Patches — Ursache finden
- Keine hartcodierten User-Strings
- Keine hartcodierten Secrets im Code
- Migrations sauber, SQLite + PostgreSQL kompatibel
- Keine `--no-verify`, `--force`, `reset --hard` ohne explizite Anweisung
- KRITIS/NIS2/ISO27001-Compliance nicht aufweichen

## 7. Endbericht-Format

Da während der Arbeit nicht zurückgefragt wird, ist der Abschluss-Bericht umso wichtiger:

- **Was wurde geliefert** — kurze Liste der Hauptpunkte
- **Was wurde pragmatisch entschieden** — explizite Liste aller Punkte wo der Default eine Rückfrage gewesen wäre
- **Was wurde verworfen** — wenn etwas geplant war aber bewusst nicht gemacht wurde
- **Was bleibt offen** — bekannte Limits oder Folgeaufgaben
- **Tests/KRITIS/Build** — Status

---

## Selbst-Check vor "Erledigt"

- [ ] Habe ich an irgendeiner Stelle "Soll ich…?" oder "Möchten Sie…?" verwendet? → Wenn ja, zurück und durcharbeiten.
- [ ] Habe ich an irgendeiner Stelle für ein Approval gepausiert? → Wenn ja, zurück und durcharbeiten.
- [ ] Sind alle pragmatischen Entscheidungen im Bericht dokumentiert?
- [ ] Ist die Definition of Done vollständig erfüllt?
- [ ] Bauen+Tests grün?

Nur dann fertig melden.
