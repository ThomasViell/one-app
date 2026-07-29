# L10n-Portal-Testballon — Stand 2026-07-13 Abend

Ziel: App-UI-Sprachen aus dem Lizenz-Portal (`license.drainq.com`) beziehen, DeepL pro Sprache, Händler-Review pro Land. Testballons: Französisch (fr) + Norwegisch (nb). Goldenes Image bewusst verschoben.

## Erledigt heute
- Portal-Sprachsync ist beidseitig gebaut (Portal `L10nApiController`+`TranslationController`; App-Lazy-Download in Branch `feature/l10n-portal` unter `C:\Projekte\drainq.one-localization`, NICHT in feature/dual-mode gemergt, `l10n.portal.url` leer).
- Live-Portal: nur DE+EN mit Inhalt; kein Norwegisch; alte ONE-Referenzen gemischt/veraltet.
- Skript `C:\Projekte\drainq.one-localization\publish-one-l10n.ps1` gebaut (Import res/raw Scope ONE, `-PurgeOne`, optional DeepL). Import lief: 429 Keys (68 neu, 361 upd).
- Portal-Purge-Endpoint gebaut: `POST /api/admin/l10n/translations/purge?scope=ONE&confirm=DELETE` (ApiKey, Scope-Whitelist ONE/HMX, Confirm-Guard, Audit-Log). Auf master `8f37af8` gepusht, Deploy ausgelöst.

## BLOCKER (morgen zuerst)
Purge-Endpoint liefert live weiter **404**. GitHub-Action „Deploy to Hetzner" (drainq.web) prüfen: durch? gescheitert? Route greift nicht? Erst wenn Endpoint live:
1. `.\publish-one-l10n.ps1 -PurgeOne -SkipDeepL` (löscht alte ONE-Texte + lädt 429 neu).
2. In `/admin/translations`: Norwegisch als Sprache `nb` anlegen; DeepL für fr und nb auslösen; für ONE aktivieren.
3. Prüfen: `license.drainq.com/api/translations/fr.json?scope=one`.

## Wichtig
- DeepL-Trigger über generischen API-Endpoint = noop (übersetzt nur bestehende Einträge). Der ONE-Weg (`L10nDeeplService.RunDeeplPassAsync`) legt Einträge aus Referenzen an — läuft über Admin-UI oder Pending-Hintergrunddienst.
- Norwegisch bei DeepL = `nb` (Bokmål), nicht `no`.
- ApiKey: `$env:DRAINQ_PUBLISH_APIKEY` (wie Beta-Publish).
