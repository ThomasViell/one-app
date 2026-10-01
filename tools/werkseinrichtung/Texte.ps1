<#
.SYNOPSIS
    Textkatalog des Werkseinrichtungs-Werkzeugs (Welle werkzeug-partner-en, 01.10.2026):
    alle Fenstertexte des Standardwegs an EINER Stelle, je Schluessel deutsch und englisch.

.DESCRIPTION
    Reine Funktionsbibliothek (kein Seiteneffekt beim Dot-Source). Benutzt von
    Werkseinrichtung.ps1, Invoke-DeviceSetup.ps1 (Geraetejob) und Update-WerkzeugApp.ps1.

    Fenstersprache: Windows-Anzeigesprache Deutsch (de-*) -> de, jede andere -> en; der
    Parameter -Sprache de|en an Werkseinrichtung.ps1 erzwingt eine Sprache.

    Protokolle (CSV, JSON, .log in logs\) bleiben IMMER deutsch: wer einen Text fuers Protokoll
    braucht, holt ihn ausdruecklich mit -Sprache 'de'.

    Platzhalter {0}, {1}, ... werden mit -f gefuellt; literale geschweifte Klammern kommen in
    keinem Text vor. Die deutschen Texte sind byte-gleich zu den frueheren Literalen in den drei
    Skripten (inkl. ae/ue-Schreibweise) - Ausnahmen: rot.konto_vorhanden und rot.app_vorhanden
    (keine Werksreset-Empfehlung mehr) sowie die neuen Schluessel allg.* und
    update.kein_lokaler_stand.

    Der Bestandsgeraet-Modus (-Bestandsgeraet) bleibt deutsch und steht NICHT in diesem
    Katalog (je eine #region Bestandsgeraet-nur-deutsch in Werkseinrichtung.ps1 und
    Invoke-DeviceSetup.ps1).
#>

function Get-WerkzeugTexte {
    <# Der Katalog: Schluessel -> @{ de; en }. Schluesselformat ^(allg|haupt|update|rot)\.[a-z0-9_]+$ #>
    return @{
        # --- allgemein ---
        'allg.hinweis_logs'           = @{ de = 'Ordner logs an service@uip.team senden.'; en = 'Send the logs folder to service@uip.team.' }
        'allg.grund_nur_protokoll'    = @{ de = '{0}'; en = 'Reason only available in German (see logs folder): {0}' }
        'allg.signatur_keine_datei'   = @{ de = 'Keine Signaturdatei (META-INF/*.RSA) in der App-Datei gefunden.'; en = 'No signature file (META-INF/*.RSA) found in the app file.' }

        # --- Hauptskript Werkseinrichtung.ps1 ---
        'haupt.titel'                 = @{ de = 'DrainQ.ONE - Werkseinrichtung'; en = 'DrainQ.ONE - Factory setup' }
        'haupt.adb_fehlt'             = @{ de = "FEHLER: adb.exe fehlt unter '{0}' - das Paket ist unvollständig. Bitte den ganzen Ordner neu kopieren."; en = "ERROR: adb.exe is missing at '{0}' - the package is incomplete. Please copy the whole folder again." }
        'haupt.taste_beenden'         = @{ de = 'Taste drücken zum Beenden'; en = 'Press Enter to exit' }
        'haupt.worker_fehlt'          = @{ de = 'FEHLER: Invoke-DeviceSetup.ps1 fehlt - das Paket ist unvollständig. Bitte den ganzen Ordner neu kopieren.'; en = 'ERROR: Invoke-DeviceSetup.ps1 is missing - the package is incomplete. Please copy the whole folder again.' }
        'haupt.update_uebersprungen_protokoll' = @{ de = 'Selbstaktualisierung übersprungen (-KeineSelbstaktualisierung).'; en = 'Self-update skipped (-KeineSelbstaktualisierung).' }
        'haupt.update_uebersprungen'  = @{ de = 'Selbstaktualisierung übersprungen (-KeineSelbstaktualisierung)'; en = 'Self-update skipped (-KeineSelbstaktualisierung)' }
        'haupt.pruefe_portal'         = @{ de = 'Prüfe Portal auf neueren freigegebenen Stand'; en = 'Checking the portal for a newer released version' }
        'haupt.config_warnung'        = @{ de = 'WARNUNG: autoupdate.config.json konnte nicht gelesen werden ({0}) - verwende Vorgaben.'; en = 'WARNING: autoupdate.config.json could not be read ({0}) - using defaults.' }
        'haupt.nichts_zum_einrichten' = @{ de = 'FEHLER: Weder eine mitgelieferte App-Datei noch eine Portal-Verbindung vorhanden - es gibt nichts, womit eingerichtet werden könnte.'; en = 'ERROR: Neither a supplied app file nor a portal connection is available - there is nothing to set up with.' }
        'haupt.pruefe_appdatei'       = @{ de = 'Prüfe die mitgelieferte App-Datei'; en = 'Checking the supplied app file' }
        'haupt.appdatei_anzahl'       = @{ de = "FEHLER: Es muss genau eine App-Datei nach dem Muster 'DrainQ-ONE_<Version>_<Code>_platform.apk' in '{0}' liegen (gefunden: {1})."; en = "ERROR: There must be exactly one app file matching the pattern 'DrainQ-ONE_<Version>_<Code>_platform.apk' in '{0}' (found: {1})." }
        'haupt.appdatei_name'         = @{ de = "FEHLER: Dateiname '{0}' folgt nicht dem Muster DrainQ-ONE_<Version>_<Code>_platform.apk - kann Soll-Version nicht bestimmen."; en = "ERROR: File name '{0}' does not follow the pattern DrainQ-ONE_<Version>_<Code>_platform.apk - cannot determine the target version." }
        'haupt.datei'                 = @{ de = 'Datei:   {0}'; en = 'File:    {0}' }
        'haupt.version'               = @{ de = 'Version: {0} (Code {1})'; en = 'Version: {0} (code {1})' }
        'haupt.signatur_unlesbar'     = @{ de = 'FEHLER: Signatur der App-Datei konnte nicht gelesen werden: {0}'; en = 'ERROR: The signature of the app file could not be read: {0}' }
        'haupt.signatur_falsch'       = @{ de = 'ABBRUCH: Die mitgelieferte App-Datei ist NICHT mit dem Plattformschlüssel signiert!'; en = 'ABORTED: The supplied app file is NOT signed with the platform key!' }
        'haupt.signatur_gefunden'     = @{ de = '  Gefunden:  {0}'; en = '  Found:     {0}' }
        'haupt.signatur_erwartet'     = @{ de = '  Erwartet:  {0}'; en = '  Expected:  {0}' }
        'haupt.kein_geraet_angefasst_1' = @{ de = 'Es wird KEIN Gerät angefasst. Ein falsch signierter Stand in der Serie bedeutet später'; en = 'NO device will be touched. A wrongly signed version in the series would later mean' }
        'haupt.kein_geraet_angefasst_2' = @{ de = 'für jedes betroffene Gerät eine Deinstallation. Bitte die richtige App-Datei einsetzen.'; en = 'an uninstallation on every affected device. Please put in the correct app file.' }
        'haupt.signatur_ok'           = @{ de = 'Signatur OK (Plattformschlüssel bestätigt).'; en = 'Signature OK (platform key confirmed).' }
        'haupt.fenstertitel'          = @{ de = 'DrainQ.ONE Werkseinrichtung — Version {0} (Code {1})'; en = 'DrainQ.ONE Factory setup — version {0} (code {1})' }
        'haupt.verwendete_version'    = @{ de = '*** Verwendete Version: {0} (Code {1}) ***'; en = '*** Version used: {0} (code {1}) ***' }
        'haupt.suche_geraete'         = @{ de = 'Suche angeschlossene Geräte'; en = 'Searching for connected devices' }
        'haupt.nicht_bereit'          = @{ de = 'Hinweis: folgende Geräte sind angeschlossen, aber noch nicht bereit: {0}'; en = 'Note: the following devices are connected but not ready yet: {0}' }
        'haupt.usb_debugging'         = @{ de = "Meist hilft: am Gerät den Dialog 'USB-Debugging erlauben' bestätigen, dann diese Datei erneut starten."; en = "Usually helps: confirm the 'Allow USB debugging' dialog on the device, then start this file again." }
        'haupt.kein_geraet'           = @{ de = 'Kein einsatzbereites Gerät gefunden. Bitte Tablet(s) per USB anschließen und diese Datei erneut starten.'; en = 'No ready device found. Please connect the tablet(s) via USB and start this file again.' }
        'haupt.gefunden'              = @{ de = 'Gefunden: {0} Gerät(e) - {1}'; en = 'Found: {0} device(s) - {1}' }
        'haupt.einrichtung_laeuft'    = @{ de = 'Einrichtung läuft für {0} Gerät(e) parallel'; en = 'Setup running for {0} device(s) in parallel' }
        'haupt.bestandsgeraet_zusatz' = @{ de = ' (BESTANDSGERAET-MODUS)'; en = ' (EXISTING-DEVICE MODE)' }
        'haupt.in_arbeit'             = @{ de = '... noch {0} von {1} Gerät(en) in Arbeit'; en = '... {0} of {1} device(s) still in progress' }
        'haupt.ergebnis_je_geraet'    = @{ de = 'Ergebnis je Gerät'; en = 'Result per device' }
        'haupt.geraet_gruen'          = @{ de = 'Gerät {0}: >>> GRUEN <<<'; en = 'Device {0}: >>> GRUEN (success) <<<' }
        'haupt.gruen_details'         = @{ de = '  Version {0}, Dauer {1} s, Modus {2}'; en = '  Version {0}, duration {1} s, mode {2}' }
        'haupt.geraet_rot'            = @{ de = 'Gerät {0}: >>> ROT <<<'; en = 'Device {0}: >>> ROT (failed) <<<' }
        'haupt.grund'                 = @{ de = '  Grund: {0}'; en = '  Reason: {0}' }
        'haupt.modus'                 = @{ de = '  Modus: {0}'; en = '  Mode: {0}' }
        'haupt.zusammenfassung'       = @{ de = 'Zusammenfassung'; en = 'Summary' }
        'haupt.erfolgreich'           = @{ de = '{0} von {1} Gerät(en) erfolgreich eingerichtet.'; en = '{0} of {1} device(s) set up successfully.' }
        'haupt.protokoll'             = @{ de = 'Protokoll: {0}'; en = 'Log: {0}' }

        # --- Geraete-Ergebnis ROT (Invoke-DeviceSetup.ps1 und Rueckfall im Hauptskript) ---
        'rot.konto_unlesbar'          = @{ de = 'Konnte den Konten-Status nicht auslesen (dumpsys account lieferte kein auswertbares Ergebnis).'; en = 'Could not read the account status (dumpsys account returned no usable result).' }
        'rot.konto_vorhanden'         = @{ de = 'Kein fabrikneues Gerät ({0} Benutzerkonto(en) vorhanden). Anlage NICHT zurücksetzen.'; en = 'Not a factory-fresh device ({0} user account(s) present). Do NOT reset the device.' }
        'rot.fremder_eigentuemer'     = @{ de = 'Geraet hat bereits einen ANDEREN Geraeteeigentuemer gesetzt. Nicht automatisch anfassen - bitte Rueckfrage vor jedem weiteren Schritt.'; en = 'The device already has a DIFFERENT device owner set. Do not touch it automatically - please check back before any further step.' }
        'rot.app_vorhanden'           = @{ de = 'Kein fabrikneues Gerät (App bereits installiert, Version {0}/{1}). Anlage NICHT zurücksetzen.'; en = 'Not a factory-fresh device (app already installed, version {0}/{1}). Do NOT reset the device.' }
        'rot.installation'            = @{ de = 'Installation fehlgeschlagen: {0}'; en = 'Installation failed: {0}' }
        'rot.version_falsch'          = @{ de = 'Nach der Installation stimmt die Version nicht: gefunden {0}/{1}, erwartet {2}/{3}.'; en = 'The version does not match after the installation: found {0}/{1}, expected {2}/{3}.' }
        'rot.werksapp'                = @{ de = '{0} (Werks-App) laesst sich nicht entfernen und ueberschreibt beim Neustart unseren Autostart. Ausgabe: {1}'; en = '{0} (factory app) cannot be removed and overrides our autostart on every reboot. Output: {1}' }
        'rot.startbildschirm'         = @{ de = 'Startbildschirm konnte nicht auf DrainQ.ONE gesetzt werden. Ausgabe: {0}'; en = 'The home screen could not be set to DrainQ.ONE. Output: {0}' }
        'rot.eigentuemer'             = @{ de = 'Geraeteeigentuemer konnte nicht gesetzt werden. Ausgabe: {0}'; en = 'The device owner could not be set. Output: {0}' }
        'rot.kiosk'                   = @{ de = 'Kiosk-Betrieb konnte nicht bestaetigt werden (LockTask nicht aktiv oder DrainQ.ONE nicht im Vordergrund).'; en = 'Kiosk mode could not be confirmed (LockTask not active or DrainQ.ONE not in the foreground).' }
        'rot.unerwartet'              = @{ de = 'Unerwarteter Fehler im Ablauf: {0}'; en = 'Unexpected error during the setup: {0}' }
        'rot.job_abgebrochen'         = @{ de = 'Der Einrichtungs-Vorgang wurde unerwartet abgebrochen (kein Ergebnis geschrieben) - Protokolldatei prüfen.'; en = 'The setup process was aborted unexpectedly (no result written) - check the log file.' }

        # --- Selbstaktualisierung Update-WerkzeugApp.ps1: Ablaufzeilen (nur Fenster) ---
        'update.log_mehrere'          = @{ de = 'Mehr als eine App-Datei im Ordner ({0}) - Selbstaktualisierung uebersprungen, die anschliessende Pflichtpruefung entscheidet.'; en = 'More than one app file in the folder ({0}) - self-update skipped, the subsequent mandatory check decides.' }
        'update.log_frage_manifest'   = @{ de = "Frage Portal-Manifest ab: {0} (Kanal '{1}')"; en = "Querying the portal manifest: {0} (channel '{1}')" }
        'update.log_portal_nicht_erreichbar' = @{ de = 'Portal NICHT erreichbar: {0}'; en = 'Portal NOT reachable: {0}' }
        'update.log_kanal_404'        = @{ de = "Portal antwortet, aber Kanal '{0}' ist dort nicht veroeffentlicht (HTTP 404)."; en = "The portal responds, but channel '{0}' is not published there (HTTP 404)." }
        'update.log_portal_meldet'    = @{ de = 'Portal meldet: {0} (Code {1}), veroeffentlicht {2}'; en = 'Portal reports: {0} (code {1}), published {2}' }
        'update.log_bootstrap'        = @{ de = 'Kein lokaler Stand vorhanden - bootstrap: hole den Portal-Stand direkt.'; en = 'No local file present - bootstrap: fetching the portal version directly.' }
        'update.log_lokaler_stand'    = @{ de = 'Lokaler Stand: {0}/{1}'; en = 'Local file: {0}/{1}' }
        'update.log_lokal_neuer'      = @{ de = 'Lokaler Stand ist NEUER als das Portal.'; en = 'The local file is NEWER than the portal.' }
        'update.log_aktuell'          = @{ de = 'Bereits aktuell - kein Update noetig.'; en = 'Already up to date - no update needed.' }
        'update.log_lade_herunter'    = @{ de = 'Portal ist neuer ({0} > {1}) - lade herunter: {2}'; en = 'The portal is newer ({0} > {1}) - downloading: {2}' }
        'update.log_download_fehler'  = @{ de = 'Download fehlgeschlagen: {0}'; en = 'Download failed: {0}' }
        'update.log_heruntergeladen'  = @{ de = 'Heruntergeladen. sha256 erwartet={0} tatsaechlich={1}'; en = 'Downloaded. sha256 expected={0} actual={1}' }
        'update.log_pruefsumme_falsch' = @{ de = 'PRUEFSUMME STIMMT NICHT - heruntergeladene Datei wird verworfen, bisheriger Stand bleibt aktiv.'; en = 'CHECKSUM MISMATCH - the downloaded file is discarded, the previous file stays active.' }
        'update.log_pruefsumme_ok'    = @{ de = 'Pruefsumme OK.'; en = 'Checksum OK.' }
        'update.log_signatur_unlesbar' = @{ de = 'Signatur konnte nicht gelesen werden: {0}'; en = 'The signature could not be read: {0}' }
        'update.log_fingerabdruck'    = @{ de = 'Signatur-Fingerabdruck: {0}'; en = 'Signature fingerprint: {0}' }
        'update.log_signatur_falsch'  = @{ de = 'SIGNATUR-FINGERABDRUCK STIMMT NICHT - heruntergeladene Datei wird verworfen, bisheriger Stand bleibt aktiv.'; en = 'SIGNATURE FINGERPRINT MISMATCH - the downloaded file is discarded, the previous file stays active.' }
        'update.log_signatur_ok'      = @{ de = 'Signatur-Fingerabdruck OK (Plattformschluessel bestaetigt).'; en = 'Signature fingerprint OK (platform key confirmed).' }
        'update.log_alte_datei'       = @{ de = 'Alte Datei zur Seite gelegt: {0}'; en = 'Old file moved aside: {0}' }
        'update.log_neue_datei'       = @{ de = 'Neue Datei eingesetzt: {0}'; en = 'New file put in place: {0}' }

        # --- Selbstaktualisierung: Ergebniszeile (SourceLabel Fenster, ProtokollLabel immer de) ---
        'update.label_mehrere'        = @{ de = 'Mehrere App-Dateien vorhanden - Selbstaktualisierung uebersprungen'; en = 'Several app files present - self-update skipped' }
        'update.label_kein_lokal_kein_portal' = @{ de = 'Kein lokaler Stand und Portal nicht erreichbar'; en = 'No local file and the portal is not reachable' }
        'update.label_portal_unerreichbar' = @{ de = 'Portal nicht erreichbar - lokaler Stand {0}/{1} vom {2}'; en = 'Portal not reachable - local file {0}/{1} from {2}' }
        'update.label_kanal_404_kein_lokal' = @{ de = "Kanal '{0}' im Portal nicht veroeffentlicht und keine lokale App-Datei vorhanden"; en = "Channel '{0}' not published in the portal and no local app file present" }
        'update.label_kanal_404'      = @{ de = "Kanal '{0}' im Portal nicht veroeffentlicht - lokaler Stand {1}/{2} vom {3}"; en = "Channel '{0}' not published in the portal - local file {1}/{2} from {3}" }
        'update.label_lokal_neuer'    = @{ de = 'ACHTUNG: lokaler Stand {0}/{1} ist NEUER als das Portal ({2}/{3}) - unveroeffentlichter Stand im Ordner'; en = 'ATTENTION: local file {0}/{1} is NEWER than the portal ({2}/{3}) - unpublished version in the folder' }
        'update.label_aktuell'        = @{ de = "Aktuell: {0}/{1} (Kanal '{2}', mit Portal abgeglichen)"; en = "Up to date: {0}/{1} (channel '{2}', matched with the portal)" }
        'update.label_download_fehler' = @{ de = 'Download von Portal-Version {0}/{1} fehlgeschlagen - bleibe bei {2}'; en = 'Download of portal version {0}/{1} failed - staying with {2}' }
        'update.label_pruefsumme_abgelehnt' = @{ de = 'ABGELEHNT: Pruefsumme der Portal-Datei {0}/{1} stimmt nicht - bleibe bei {2}'; en = 'REJECTED: checksum of the portal file {0}/{1} does not match - staying with {2}' }
        'update.label_signatur_unlesbar' = @{ de = 'ABGELEHNT: Signatur der Portal-Datei {0}/{1} nicht lesbar - bleibe bei {2}'; en = 'REJECTED: signature of the portal file {0}/{1} not readable - staying with {2}' }
        'update.label_signatur_falsch' = @{ de = 'ABGELEHNT: Signatur der Portal-Datei {0}/{1} stimmt nicht mit dem Plattformschluessel ueberein - bleibe bei {2}'; en = 'REJECTED: signature of the portal file {0}/{1} does not match the platform key - staying with {2}' }
        'update.label_aktualisiert'   = @{ de = 'AKTUALISIERT: Portal-Stand {0}/{1} uebernommen (vorher {2})'; en = 'UPDATED: portal version {0}/{1} taken over (previously {2})' }
        'update.kein_lokaler_stand'   = @{ de = '(kein lokaler Stand)'; en = '(no local file)' }

        # --- Selbstaktualisierung: Zusatzzeile Detail (nur Fenster) ---
        'update.detail_mehrere'       = @{ de = 'Mehr als eine Datei nach dem Muster DrainQ-ONE_<Version>_<Code>_platform.apk gefunden.'; en = 'More than one file matching the pattern DrainQ-ONE_<Version>_<Code>_platform.apk found.' }
        'update.detail_kein_lokal_kein_portal' = @{ de = 'Weder eine lokale App-Datei noch eine Portal-Verbindung vorhanden. Fehler: {0}'; en = 'Neither a local app file nor a portal connection is available. Error: {0}' }
        'update.detail_portal_fehler' = @{ de = 'Portal-Fehler: {0}'; en = 'Portal error: {0}' }
        'update.detail_404'           = @{ de = 'Manifest-URL {0} liefert HTTP 404.'; en = 'Manifest URL {0} returns HTTP 404.' }
        'update.detail_lokal_neuer'   = @{ de = 'Portal: {0}/{1} ({2}). Lokal: {3}/{4}.'; en = 'Portal: {0}/{1} ({2}). Local: {3}/{4}.' }
        'update.detail_aktuell'       = @{ de = 'Portal und lokaler Stand stimmen ueberein: {0}/{1}.'; en = 'Portal and local file match: {0}/{1}.' }
        'update.detail_download_fehler' = @{ de = 'Fehler beim Download: {0}'; en = 'Download error: {0}' }
        'update.detail_pruefsumme'    = @{ de = 'Erwartet sha256={0}, tatsaechlich={1}. Datei verworfen, NICHT verwendet.'; en = 'Expected sha256={0}, actual={1}. File discarded, NOT used.' }
        'update.detail_signatur_unlesbar' = @{ de = 'Fehler: {0}. Datei verworfen, NICHT verwendet.'; en = 'Error: {0}. File discarded, NOT used.' }
        'update.detail_signatur_falsch' = @{ de = 'Erwartet={0}, tatsaechlich={1}. Datei verworfen, NICHT verwendet.'; en = 'Expected={0}, actual={1}. File discarded, NOT used.' }
        'update.detail_aktualisiert'  = @{ de = "Kanal '{0}', veroeffentlicht {1}. Pruefsumme und Signatur-Fingerabdruck bestaetigt."; en = "Channel '{0}', published {1}. Checksum and signature fingerprint confirmed." }
    }
}

function Get-WerkzeugSprache {
    <#
    Fenstersprache bestimmen. -Erzwungen leer/nicht gesetzt -> automatisch nach der
    Windows-Anzeigesprache (Get-UICulture: de-* -> de, sonst en); 'de'/'en' -> genau das;
    alles andere -> Fehler.
    #>
    param([string]$Erzwungen = '')
    if ([string]::IsNullOrEmpty($Erzwungen)) {
        if ((Get-UICulture).Name -match '^de(-|$)') { return 'de' }
        return 'en'
    }
    if ($Erzwungen -eq 'de' -or $Erzwungen -eq 'en') { return $Erzwungen.ToLowerInvariant() }
    throw "Unbekannte Sprache / unknown language '$Erzwungen' (de, en)."
}

function Get-WerkzeugText {
    <# Text zu einem Katalogschluessel in der gewuenschten Sprache, Platzhalter mit -Werte gefuellt. #>
    param(
        [Parameter(Mandatory)] [string]$Key,
        [Parameter(Mandatory)] [string]$Sprache,
        [object[]]$Werte = @()
    )
    $texte = Get-WerkzeugTexte
    if (-not $texte.ContainsKey($Key)) { throw "Unbekannter Textschluessel '$Key'." }
    $eintrag = $texte[$Key]
    if (-not $eintrag.ContainsKey($Sprache)) { throw "Unbekannte Sprache '$Sprache' fuer Textschluessel '$Key'." }
    return ($eintrag[$Sprache] -f $Werte)
}

function Convert-BekannteMeldung {
    <#
    Uebersetzt eine bekannte deutsche Fehlermeldung aus Get-ApkSignatureFingerprint.ps1
    (Datei ausserhalb dieser Welle, wirft fest deutsch) fuers Fenster. Jede andere Meldung
    (z. B. .NET-Ausnahmen, die Windows selbst in der Systemsprache liefert) bleibt unveraendert.
    #>
    param(
        [string]$Meldung,
        [Parameter(Mandatory)] [string]$Sprache
    )
    if ($Meldung -ceq (Get-WerkzeugText -Key 'allg.signatur_keine_datei' -Sprache 'de')) {
        return (Get-WerkzeugText -Key 'allg.signatur_keine_datei' -Sprache $Sprache)
    }
    return $Meldung
}

function Select-FensterGrund {
    <#
    Ursache fuers Fenster: das letzte Objekt mit Eigenschaft GrundFenster (vom Geraetejob
    ausgegeben oder das Rueckfall-Objekt aus New-AbbruchErgebnis) gewinnt. Gibt es keins,
    zeigt das Fenster den deutschen Protokoll-Grund - auf Englisch mit einem Vorsatz, der das
    erklaert.
    #>
    param(
        [object[]]$JobAusgabe = @(),
        [string]$Grund = '',
        [Parameter(Mandatory)] [string]$Sprache
    )
    $treffer = $null
    foreach ($o in @($JobAusgabe)) {
        if ($null -ne $o -and $null -ne $o.PSObject.Properties['GrundFenster']) { $treffer = $o }
    }
    if ($null -ne $treffer) { return [string]$treffer.GrundFenster }
    return (Get-WerkzeugText -Key 'allg.grund_nur_protokoll' -Sprache $Sprache -Werte @($Grund))
}

function New-AbbruchErgebnis {
    <#
    Rueckfall-Ergebnis, wenn ein Geraetejob keine Ergebnisdatei geschrieben hat. Die sechs
    Protokollfelder wie in der JSON-Ergebnisdatei (Grund deutsch), dazu GrundFenster
    (Fenstersprache) als siebte Eigenschaft NUR im Objekt - die CSV-Zeile liest sie nicht.
    Im Standardmodus traegt die ROT-Meldung genau einmal den Hinweis allg.hinweis_logs.
    #>
    param(
        [Parameter(Mandatory)] [string]$Seriennummer,
        [Parameter(Mandatory)] [string]$Modus,
        [Parameter(Mandatory)] [string]$Sprache
    )
    $grund = Get-WerkzeugText -Key 'rot.job_abgebrochen' -Sprache 'de'
    $grundFenster = Get-WerkzeugText -Key 'rot.job_abgebrochen' -Sprache $Sprache
    if ($Modus -eq 'Standard') {
        $grund = $grund + ' ' + (Get-WerkzeugText -Key 'allg.hinweis_logs' -Sprache 'de')
        $grundFenster = $grundFenster + ' ' + (Get-WerkzeugText -Key 'allg.hinweis_logs' -Sprache $Sprache)
    }
    return [pscustomobject]@{
        Seriennummer  = $Seriennummer
        Ergebnis      = 'ROT'
        Grund         = $grund
        Version       = ''
        Modus         = $Modus
        DauerSekunden = 0
        GrundFenster  = $grundFenster
    }
}
