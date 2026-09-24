# l10n-import-to-portal.ps1 - spielt die deutschen ONE-Begriffe ins DrainQ-Portal ein
# (Welle portal-nachzug, CEO-Entscheide 17.09./21.09.2026, PLAN Abschnitt 3.3):
#
# 1. Quelle ist NUR der deutsche Block (deTranslations) in LocalizationManager.kt.
#    Englisch entsteht im Portal (DeepL/Partner), Uebersetzungen werden nicht
#    hochgeladen. Hilfe-Texte (help.*) werden nicht gelesen - deren Portalanschluss
#    ist eine eigene Welle.
# 2. Vor dem Paketbau holt das Skript das lebende Portal: den Haupt-View
#    scope=one,shared plus scope=shared (Sperre e3) und jeden weiteren Bereich
#    in de und en plus die SA-Sicht sa/{lang}.json (N-1: FREMD-Schluessel fallen
#    aus NEU heraus, CEO-Entscheid 21.09.2026; M-3: Positivliste, Mindestumfang
#    je Sprache, Pflicht-Schluessel). Dann teilt es die Schluessel in
#    NEU / GLEICH / ABWEICHEND / NUR-PORTAL (Listen im Ausgabeverzeichnis).
# 3. Gesendet werden NUR NEU plus die per -AbweichendFreigabe freigegebenen
#    ABWEICHEND (R-3). GLEICH wird nie gesendet. Uebrige ABWEICHEND bleiben im
#    Portal unberuehrt - der CEO entscheidet je Fall (R-2), Ergebnis ist die
#    Freigabedatei (ein Schluessel je Zeile, Kommentare mit #).
# 4. Kotlin-Escapes (uXXXX, n, t, ", \, $) werden dekodiert; Doppelschluessel
#    ergeben den letzten Wert (mapOf-Semantik), mit Warnung samt Dateizeile.
# 5. Sperren vor jedem Senden: kein SHARED-Schluessel (e3), kein GLEICH (e6),
#    kein unfreigegebener ABWEICHEND (e4), kein woertliches \uXXXX im Wert (e5),
#    kein EN-Feld im Paket (e1), keine hilfe-Schluessel (e2), kein Schluessel aus
#    einem fremden Bereich (e7, auch fuer Freigaben), kein zurueckgehaltener
#    Schluessel (e8). Verstoss -> Exit 2.
# 6. Plausibilitaet (N-3, M-3): weniger als 460 Schluessel im Haupt-View (D-7) oder
#    mehr als 200 NEU -> Exit 4, kein Paket; ebenso ein Fremd-Bereich mit {} oder
#    unter seinem Mindestumfang, ein Bereich ausserhalb der Positivliste oder HMX ohne
#    'ok'. Leerer/null/{}/nicht parsebarer Portal-Koerper -> Exit 4. Der Abbruch
#    laeuft vor dem Schreiben der Listen und hinterlaesst kein NEUES Verzeichnis
#    (C-9); ein vorhandenes Verzeichnis mit altem JSON wird schon vor dem ersten GET
#    umbenannt, nie stillschweigend stehen gelassen (D-8).
# 7. -DryRun laeuft ohne Schluessel und schreibt das vollstaendige JSON (der
#    Pruefgegenstand dieser Welle). Der Lauf OHNE -DryRun schreibt in das
#    Live-Portal, das alle Produkte bedient - er gehoert dem CEO (Admin-Schluessel),
#    nie dem Bauer dieser Welle.
#
# Aufruf:  pwsh -NoProfile -File tools/l10n-import-to-portal.ps1 -DryRun
#          pwsh -NoProfile -File tools/l10n-import-to-portal.ps1 -DryRun -AbweichendFreigabe <datei>
#          pwsh -NoProfile -File tools/l10n-import-to-portal.ps1 -ApiKey "<DrainQCloud:ApiKey>" [-AbweichendFreigabe <datei>]
# Optional: -PortalUrl "https://license.drainq.com"   (Default)
#           -OutDir    (Default tools/_autotest/l10n-import, gitignored)
#           -AbweichendFreigabe (Freigabedatei R-2)

param(
    [string]$ApiKey,
    [string]$PortalUrl = "https://license.drainq.com",
    [switch]$DryRun,
    [string]$OutDir,
    [string]$AbweichendFreigabe
)

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$root = Split-Path $here -Parent

if (-not $DryRun -and [string]::IsNullOrWhiteSpace($ApiKey)) {
    Write-Host "Kein -ApiKey und kein -DryRun: Abbruch. Der Lauf gegen das Live-Portal gehoert dem CEO (Admin-Schluessel); ein Trockenlauf braucht keinen Schluessel." -ForegroundColor Red
    exit 2
}

if (-not $OutDir) { $OutDir = Join-Path $here "_autotest\l10n-import" }

. (Join-Path $here "l10n\L10nImportLib.ps1")

# D-8: schon vor jedem GET und vor jedem moeglichen Exit 4 aus dem Weg raeumen
# (Sichere-VorhandenesOutDir, L10nImportLib.ps1), damit kein altes l10n_import.json
# nach einem Abbruch fuer frisches Ergebnis gehalten werden kann.
$altesOutDir = Sichere-VorhandenesOutDir -OutDir $OutDir
if ($altesOutDir) { Write-Host "Vorhandenes Ausgabeverzeichnis war da - umbenannt nach $altesOutDir (D-8)." -ForegroundColor Yellow }

$lm = Join-Path $root "app\src\main\java\com\uip\oneapp\ui\localization\LocalizationManager.kt"
if (-not (Test-Path $lm)) { Write-Host "LocalizationManager.kt nicht gefunden: $lm" -ForegroundColor Red; exit 1 }

# ---- 1) deutschen Block lesen (kein en, keine Hilfe-Assets) -------------------
$src = [IO.File]::ReadAllText($lm, [Text.Encoding]::UTF8)
$block = Get-KotlinLangBlock -Source $src -Lang "de"
if ($null -eq $block) { Write-Host "Block deTranslations nicht gefunden." -ForegroundColor Red; exit 1 }
$blockStart = $src.IndexOf("fun deTranslations(")
$zeilenVersatz = ($src.Substring(0, $blockStart).Split("`n")).Count - 1
$map = ConvertFrom-KotlinPairs -Block $block -ZeilenVersatz $zeilenVersatz
Write-Host "Gelesen aus LocalizationManager.kt (nur de): $($map.Count) Begriffe."
if ($map.Count -lt 300) { Write-Host "WARNUNG: de-Block unerwartet klein - bitte melden." -ForegroundColor Yellow }

# Bereichsliste (N-1, M-3): die Fremd-Scopes aus der Positivliste der Lib
# (ScopeClassifier.cs:12-18; one und shared deckt der Haupt-View ab). 'sa' ist
# kein Bereich (M-3.1): die SA-Sicht sa/{lang}.json holt Get-PortalBereiche
# selbst. Grenze L-213: Bereiche, die kein Code-Pfad nennt, sind ohne Admin-Sicht
# nicht erhebbar (im Beleg benannt).
$bereiche = @("hmx", "app", "web", "catalog", "manhole")

# Zurueckgehalten (N-2, B-6): benannte Rueckhalteliste, CEO-Entscheid 21.09.2026.
# Leer seit 23.09.2026 (W-33f): 'logo_default_label' traegt kein NSP3CT mehr; Mechanik (e8) bleibt.
$Zurueckgehalten = @{}

# ---- 2) lebendes Portal holen und vergleichen ----------------------------------
$portal = Get-PortalDe -PortalUrl $PortalUrl
if ($null -eq $portal) {
    Write-Host "Portal nicht erreichbar oder liefert keinen verwertbaren Koerper - kein Paket, kein Senden (Exit 4)." -ForegroundColor Red
    exit 4
}
$bereicheInfo = Get-PortalBereiche -PortalUrl $PortalUrl -Bereiche $bereiche -Shared @($portal.RohShared)
if ($null -eq $bereicheInfo) {
    Write-Host "Bereichs-GET gescheitert - kein Paket, kein Senden (Exit 4)." -ForegroundColor Red
    exit 4
}
$vergleich = Compare-L10nKeys -Map $map.Map -Portal $portal.Map -RohPortal @($portal.RohKeys) `
    -Fremd $bereicheInfo.Fremd -Zurueckgehalten $Zurueckgehalten

# Freigaben lesen (R-2): nur diese ABWEICHEND werden mit dem Repo-Wert gesendet.
$freigaben = @()
if ($AbweichendFreigabe) {
    if (-not (Test-Path $AbweichendFreigabe)) {
        Write-Host "Freigabedatei nicht gefunden: $AbweichendFreigabe" -ForegroundColor Red
        exit 2
    }
    foreach ($zeile in [IO.File]::ReadAllLines($AbweichendFreigabe, [Text.Encoding]::UTF8)) {
        $name = $zeile.Trim()
        if (-not $name -or $name.StartsWith("#")) { continue }
        $abw = $vergleich.Abweichend | Where-Object { $_.Key -eq $name }
        if ($null -eq $abw) {
            Write-Host "Freigabe '$name' steht nicht in der ABWEICHEND-Liste - Abbruch (Freigabedatei pruefen)." -ForegroundColor Red
            exit 2
        }
        $freigaben += [pscustomobject]@{ Key = $name; Repo = $abw.Repo }
    }
}

# ---- 3) Plausibilitaet (N-3.2, M-3.4) - vor dem Schreiben der Listen (C-9) -----
# Der Abbruch verlaeuft hier: Exit 4 hinterlaesst kein Listenverzeichnis, und ein
# Freigabefehler (Exit 2, oben) behaelt Vorrang.
$plausi = @(Test-L10nPlausibilitaet -PortalSchluessel $portal.RohKeys.Count -NeuSchluessel $vergleich.Neu.Count)
if ($plausi.Count -gt 0) {
    Write-Host "Plausibilitaet verletzt - kein Paket, kein Senden (Exit 4):" -ForegroundColor Red
    $plausi | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 4
}

# ---- 3b) Listen + ZUSAMMENFASSUNG schreiben -------------------------------------
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$zweig = (git rev-parse --abbrev-ref HEAD 2>$null | Out-String).Trim()
$kopf  = (git rev-parse --short HEAD 2>$null | Out-String).Trim()
if (-not $zweig) { $zweig = "unbekannt" }
if (-not $kopf)  { $kopf  = "unbekannt" }

$enc = New-Object System.Text.UTF8Encoding($false)
function Schreibe-Zeilen([string]$Pfad, [string[]]$Zeilen) {
    [IO.File]::WriteAllLines($Pfad, [string[]]$Zeilen, $enc)
}

Schreibe-Zeilen (Join-Path $OutDir "neu.txt") (@("Bezug: $zweig $kopf") + $vergleich.Neu)
Schreibe-Zeilen (Join-Path $OutDir "gleich.txt") (@("Bezug: $zweig $kopf") + $vergleich.Gleich)
Schreibe-Zeilen (Join-Path $OutDir "nur_portal.txt") (@("Bezug: $zweig $kopf") + $vergleich.NurPortal)
$abwZeilen = @("Bezug: $zweig $kopf")
foreach ($abw in $vergleich.Abweichend) {
    $repoText   = (Normalisiere-Zeilenenden $abw.Repo).Replace("`n", "\n")
    $portalText = (Normalisiere-Zeilenenden $abw.Portal).Replace("`n", "\n")
    $abwZeilen += "$($abw.Key)`tRepo: $repoText`tPortal: $portalText"
}
Schreibe-Zeilen (Join-Path $OutDir "abweichend.txt") $abwZeilen

$fremdZeilen = @("Bezug: $zweig $kopf", "FREMD: Schluessel, die in einem Portal-Bereich ausser ONE existieren (SHARED eingeschlossen) - fallen aus NEU heraus und werden nicht gesendet (CEO-Entscheid 21.09.2026); sie bleiben im anderen Produkt unberuehrt.")
foreach ($f in $vergleich.Fremd) { $fremdZeilen += "$($f.Key)`tBereiche: $($f.Bereiche -join ', ')" }
Schreibe-Zeilen (Join-Path $OutDir "z2_fremd.txt") $fremdZeilen

$zurueckZeilen = @("Bezug: $zweig $kopf", "Zurueckgehalten: benannte Rueckhalteliste (N-2) - fallen aus NEU heraus und werden nicht gesendet.")
foreach ($z in $vergleich.Zurueckgehalten) { $zurueckZeilen += "$($z.Key)`tGrund: $($z.Grund)" }
Schreibe-Zeilen (Join-Path $OutDir "z2_zurueckgehalten.txt") $zurueckZeilen

$zeilen = @()
$zeilen += "Bezug: $zweig $kopf"
$zeilen += "Portal: $PortalUrl - ETag $($portal.ETag), Last-Modified $($portal.LastModified), Datum $($portal.Datum)"
$zeilen += "GET-Zeit (L-220): $($portal.AnzahlGet) Aufrufe, $($portal.DauerMs) ms gesamt - kalt = erster Aufruf dieses Laufs auf $PortalUrl, warm = unmittelbare Wiederholung; Maschine: $(hostname)"
$zeilen += "Map (deTranslations): $($map.Count) eindeutige Schluessel"
$zeilen += "Portal (scope=one,shared): $($portal.RohKeys.Count) Schluessel roh (case-sensitive, eindeutige Schreibweisen; das Portal fuehrt cancel/CANCEL und save/SAVE als eigene Datensaetze), Kartensicht (-AsHashtable): $($portal.Map.Count); davon SHARED: $($portal.RohShared.Count)"
$zeilen += "Bereichs-GETs (N-1, M-3): $($bereicheInfo.AnzahlGet) Aufrufe ($($bereiche -join ', ') je de und en + sa/de.json + sa/en.json), $($bereicheInfo.DauerMs) ms; Schluessel je Abfrage: $(($bereicheInfo.Zaehler.Keys | Sort-Object | ForEach-Object { "$_=$($bereicheInfo.Zaehler[$_])" }) -join ', ')"
$zeilen += "NEU: $($vergleich.Neu.Count) (nach Abzug FREMD und Zurueckgehalten) - namentlich in neu.txt"
$zeilen += "FREMD: $($vergleich.Fremd.Count) - namentlich in z2_fremd.txt (fallen aus NEU heraus, werden nicht gesendet, bleiben im anderen Produkt unberuehrt)"
$zeilen += "Zurueckgehalten: $($vergleich.Zurueckgehalten.Count) - namentlich in z2_zurueckgehalten.txt"
$zeilen += "GLEICH: $($vergleich.Gleich.Count) - namentlich in gleich.txt (wird nie gesendet, R-3)"
$zeilen += "ABWEICHEND: $($vergleich.Abweichend.Count) - namentlich mit beiden Werten in abweichend.txt (bleibt ohne Freigabe unberuehrt)"
if ($freigaben.Count -gt 0) {
    $zeilen += "Freigaben ($AbweichendFreigabe): $($freigaben.Key -join ', ')"
} else {
    $zeilen += "Freigaben: 0 (keine -AbweichendFreigabe angegeben)"
}
$zeilen += "NUR-PORTAL: $($vergleich.NurPortal.Count) - namentlich in nur_portal.txt (wird nicht geloescht)"
$zeilen += "Paket: NEU + Freigaben = $($vergleich.Neu.Count + $freigaben.Count) Schluessel"
Schreibe-Zeilen (Join-Path $OutDir "ZUSAMMENFASSUNG.txt") $zeilen

# ---- 4) Paket bauen und Sperren pruefen ----------------------------------------
$neuHashtable = @{}
foreach ($k in $vergleich.Neu) { $neuHashtable[$k] = $map.Map[$k] }
$paket = New-L10nImportBody -Neu $neuHashtable -Freigegeben $freigaben

$freigegebeneNamen = @($freigaben | ForEach-Object { $_.Key })
$verstoesse = @(Test-L10nImportBody -Body $paket.Body -Portal $portal.Shared -PortalRoh @($portal.RohShared) `
    -Unveraendert @($vergleich.Gleich) `
    -Abweichend @($vergleich.Abweichend | ForEach-Object { $_.Key }) `
    -Freigegeben $freigegebeneNamen `
    -Fremd $bereicheInfo.Fremd -Zurueckgehalten $Zurueckgehalten)
if ($verstoesse.Count -gt 0) {
    Write-Host "Sperren verletzt - kein Senden, auch nicht im Trockenlauf:" -ForegroundColor Red
    $verstoesse | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 2
}

if ($DryRun) {
    $jsonPfad = Join-Path $OutDir "l10n_import.json"
    [IO.File]::WriteAllText($jsonPfad, $paket.Json + "`n", $enc)
    $abwOhneFreigabe = @($vergleich.Abweichend | Where-Object { $_.Key -notin $freigegebeneNamen })
    Write-Host ""
    Write-Host "DryRun: JSON liegt unter $jsonPfad - kein Senden." -ForegroundColor Yellow
    Write-Host "Paket: $($paket.Count) Schluessel (NEU $($vergleich.Neu.Count) + Freigaben $($freigaben.Count))."
    Write-Host "NEU namentlich:"
    $vergleich.Neu | ForEach-Object { Write-Host "  $_" }
    if ($vergleich.Fremd.Count -gt 0) {
        Write-Host "FREMD (liegen in einem anderen Bereich, fallen aus NEU, werden nicht gesendet):"
        $vergleich.Fremd | ForEach-Object { Write-Host "  $($_.Key)  [$($_.Bereiche -join ', ')]" }
    }
    if ($vergleich.Zurueckgehalten.Count -gt 0) {
        Write-Host "Zurueckgehalten (werden nicht gesendet):"
        $vergleich.Zurueckgehalten | ForEach-Object { Write-Host "  $($_.Key)  [$($_.Grund)]" }
    }
    if ($abwOhneFreigabe.Count -gt 0) {
        Write-Host "ABWEICHEND ohne Freigabe (bleibt im Portal unberuehrt):"
        $abwOhneFreigabe | ForEach-Object { Write-Host "  $($_.Key)" }
    }
    if ($freigegebeneNamen.Count -gt 0) {
        Write-Host "Freigegebene ABWEICHEND (werden gesendet, Repo-Wert):"
        $freigegebeneNamen | ForEach-Object { Write-Host "  $_" }
    }
    exit 0
}

# ---- 5) Senden (gehoert dem CEO) ------------------------------------------------
$uri = "$PortalUrl/api/admin/l10n/translations/import"
Write-Host "Sende an $uri ..."
try {
    $resp = Invoke-RestMethod -Method Post -Uri $uri `
        -Headers @{ "X-DrainQ-ApiKey" = $ApiKey } `
        -ContentType "application/json; charset=utf-8" `
        -Body ([Text.Encoding]::UTF8.GetBytes($paket.Json))
    Write-Host "ERFOLG: $($resp.created) neu angelegt, $($resp.updated) aktualisiert (von $($resp.total))." -ForegroundColor Green
    Write-Host "Paketgroessen: de=$($resp.deByteSize) Bytes, en=$($resp.enByteSize) Bytes."
    Write-Host ""
    # K-1 (D-2, NACHBESSERUNG Runde 4): Alarm gegen die Zahlen des GEBAUTEN Pakets
    # (Test-L10nAlarmKriterium, L10nImportLib.ps1), nicht gegen feste Werte.
    if (-not (Test-L10nAlarmKriterium -Created $resp.created -Updated $resp.updated -NeuAnzahl $vergleich.Neu.Count -FreigabenAnzahl $freigaben.Count)) {
        Write-Host "ALARM: created=$($resp.created) (Soll $($vergleich.Neu.Count)), updated=$($resp.updated) (Soll $($freigaben.Count)) - weicht vom gebauten Paket ab. Sofort die Fremd-Sperren gegenpruefen (Schritt 3 unten) und Rueckfrage an den CEO, kein Weiterlaufen." -ForegroundColor Red
    } else {
        Write-Host "Alarmkriterium: created=$($resp.created) = NEU ($($vergleich.Neu.Count)), updated=$($resp.updated) = Freigaben ($($freigaben.Count)) - stimmt." -ForegroundColor Green
    }
    Write-Host "Danach im Portal pruefen (https://license.drainq.com):" -ForegroundColor Cyan
    Write-Host " 1. created muss der NEU-Zahl entsprechen, updated der Zahl der Freigaben (oben bereits geprueft)."
    Write-Host " 2. GET de.json?scope=one,shared zaehlt Haupt-View vor dem Lauf + created ($($portal.RohKeys.Count + $resp.created) erwartet)."
} catch {
    Write-Host "FEHLER: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    Write-Host "Hinweise: 401 = ApiKey falsch (DrainQCloud:ApiKey). 404 = Portal-Stand ohne L10n-API, erst deployen."
    exit 1
}
