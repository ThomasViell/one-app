# Umstellung: Variante B (Hetzner-Mirror) → Variante A (direkter GitHub-Download)

**Datum:** 2026-05-12  
**Branch:** master  
**Commit:** `c41d4be` auf `master`

---

## Geänderte Dateien (Diff-Summary)

| Datei | Art | Änderung |
|---|---|---|
| `app/build.gradle.kts` | Geändert | `UPDATE_PROXY_URL`: `https://updates.drainq.de/one/` → `https://github.com/ThomasViell/one-app/releases/latest/download/` |
| `scripts/generate-release-manifest.py` | Geändert | `PROXY_BASE_URL` → `GITHUB_REPO`; APK-URL: `{base}{name}` → `{repo}/releases/download/v{ver}/{name}` |
| `app/src/main/java/com/uip/oneapp/update/HttpUpdateService.kt` | Geändert | OkHttp: `followRedirects(true)`, `followSslRedirects(true)` explizit; Interceptor mit `User-Agent: DrainQ.ONE/<version>` |
| `docs/adr/0001-update-process-android.md` | Geändert | Status aktualisiert; Abschnitt „Änderung 2026-05-12" hinzugefügt; `MARKER_HOSTING: SUBPATH → GITHUB_PUBLIC`; Variante A als aktive Entscheidung |
| `docs/UPDATE_PROCESS_CONCEPT.md` | Geändert | Variante A als gewählt markiert; Variante B als verworfen (2026-05-12); Architektur-Diagramm auf direkten GitHub-Download umgestellt |
| `docs/UPDATE_OPS_GUIDE.md` | Geändert | Hetzner-Mirror-Abschnitte entfernt; neuer Abschnitt „GitHub Release Direct"; Rollback über alte GitHub Releases; Troubleshooting-Tabelle aktualisiert |
| `HANDOVER.md` | Geändert | „Variante B" → „Variante A"; Mirror-Schritte entfernt; Repo als public markiert; `DRAINQ_RELEASE_PAT` als nicht mehr nötig markiert |
| `CHANGELOG.md` | Geändert | Neuer Abschnitt `[0.4.0] — Unreleased` mit Refactor-Eintrag |
| `README.md` | Geändert | Repo als public markiert; Update-Prozess-Snippet ohne Hetzner; `ops/hetzner-update-proxy/` aus Struktur entfernt |
| `docs/kritis/update-process.md` | Geändert | PROXY_URL-Referenz aktualisiert; neuer Abschnitt „Public-Repo-Konsequenzen"; DSGVO auf GitHub/Microsoft umgestellt; T1 (Hetzner → GitHub); KRITIS-Check-Block K9/K11 aktualisiert |
| `docs/UPDATE_PROCESS_PHASENPLAN.md` | Geändert | Titel, Übersicht-Tabelle: Phase 5 als OBSOLET markiert; Phase-5-Abschnitt mit Obsolet-Hinweis; Folge-Schritte: `DRAINQ_RELEASE_PAT` und Hetzner-Deployment gestrichen |

## Gelöschte Dateien

| Datei | Grund |
|---|---|
| `ops/hetzner-update-proxy/mirror-releases-one.sh` | Kein Mirror mehr — Variante A |
| `ops/hetzner-update-proxy/drainq-one-mirror.service` | Kein Mirror mehr — Variante A |
| `ops/hetzner-update-proxy/drainq-one-mirror.timer` | Kein Mirror mehr — Variante A |
| `ops/hetzner-update-proxy/nginx-snippet-one.conf` | Kein Mirror mehr — Variante A |
| `ops/hetzner-update-proxy/DEPLOYMENT.md` | Kein Mirror mehr — Variante A |
| `ops/hetzner-update-proxy/` (Ordner) | Leer nach Datei-Löschungen |

---

## Compile-Ergebnis

```
> Task :app:assembleDebug

BUILD SUCCESSFUL in 1m 22s
40 actionable tasks: 9 executed, 31 up-to-date
```

## Test-Ergebnis

```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 32s
31 actionable tasks: 6 executed, 25 up-to-date

Total: 88 tests | Failures: 0 | Errors: 0
```

`UpdateServiceTest` und `UpdateE2ETest` laufen grün — beide Tests sind Base-URL-agnostisch
(MockWebServer, nicht gegen eine feste URL gebunden).

---

## Commit-Hash auf master

```
c41d4be refactor(update): Variante B (Hetzner-Mirror) auf Variante A (public GitHub) umstellen
```

---

## Nächste Schritte

1. **Diesen Branch reviewen** (kein PR nötig — direkt auf master)
2. **GitHub Secrets prüfen** und ggf. aufräumen:
   - `DRAINQ_RELEASE_PAT` kann manuell gelöscht werden (blockiert keinen Build)
   - `DRAINQ_ONE_KEYSTORE_BASE64`, `_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD` müssen gesetzt sein vor erstem Tag-Push
3. **Erst-Release starten:**
   ```bash
   git tag v0.4.0
   git push --tags
   ```
   → GitHub Actions baut + signiert APK → Release ist sofort für Tablets verfügbar
4. **Smoke-Test auf SM-X610:**
   - Release-APK manuell sideloaden (einmaliger Wechsel Debug→Release-Keystore)
   - Einstellungen → „Nach Updates suchen" → sollte `v0.4.0` zeigen

---

## Hinweis: DRAINQ_RELEASE_PAT

Das GitHub Secret `DRAINQ_RELEASE_PAT` wird **nicht mehr benötigt**:
- Das Repo `ThomasViell/one-app` ist public — kein Token für Asset-Downloads
- Kein Mirror-Skript mehr vorhanden, das einen PAT liest

**Aktion:** Secret kann manuell unter GitHub → Settings → Secrets → Actions gelöscht werden.
Es blockiert keine laufenden Builds und hinterlässt keine Sicherheitslücke, wenn es verbleibt —
aber aufräumen ist sauberer.
