<#
.SYNOPSIS
    Liest den SHA-256-Fingerabdruck des Signaturzertifikats einer APK — rein per .NET
    (kein keytool/JDK nötig, läuft auf einem Rechner, auf dem sonst nichts installiert ist).

.DESCRIPTION
    Gemeinsam genutzt von Werkseinrichtung.ps1 (Prüfung der mitgelieferten Datei) und
    Update-WerkzeugApp.ps1 (Prüfung einer frisch vom Portal heruntergeladenen Datei) —
    ein Codepfad statt zwei, damit beide Prüfungen garantiert dasselbe tun.
#>

function Get-ApkSignatureFingerprint {
    param(
        [Parameter(Mandatory)] [string]$ApkPath
    )
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.Security

    $zip = $null
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($ApkPath)
        $certEntry = $zip.Entries | Where-Object { $_.FullName -match '^META-INF/.*\.(RSA|DSA)$' } | Select-Object -First 1
        if (-not $certEntry) { throw 'Keine Signaturdatei (META-INF/*.RSA) in der App-Datei gefunden.' }
        $ms = New-Object System.IO.MemoryStream
        $certEntry.Open().CopyTo($ms)
        $certBytes = $ms.ToArray()
        $cms = New-Object System.Security.Cryptography.Pkcs.SignedCms
        $cms.Decode($certBytes)
        return ($cms.Certificates[0].GetCertHash('SHA256') | ForEach-Object { $_.ToString('X2') }) -join ':'
    } finally {
        if ($zip) { $zip.Dispose() }
    }
}
