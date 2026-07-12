<#
  reframe.ps1 — rekonstruiert aus dem OEM-Roh-Logcat (MiniPushControlHelper hexByte=...)
  den seriellen Byte-Stream und zerlegt ihn in vollstaendige Frames (FA AF | len16 | ...).
  Gibt saubere Frames (ein Frame/Zeile) aus und extrahiert Gruppe 23 (0x17) + 24 (0x18).
#>
param(
  [Parameter(Mandatory=$true)][string]$InFile,
  [Parameter(Mandatory=$true)][string]$OutFile,
  [string]$Label = "?"
)

# 1) Alle Hex-Tokens nach "hexByte" in Reihenfolge einsammeln
$bytes = New-Object System.Collections.Generic.List[byte]
foreach($line in Get-Content $InFile){
  $idx = $line.IndexOf("hexByte")
  if($idx -lt 0){ continue }
  $rest = $line.Substring($idx)
  foreach($m in [regex]::Matches($rest, '\b[0-9A-Fa-f]{2}\b')){
    $bytes.Add([Convert]::ToByte($m.Value,16))
  }
}
$buf = $bytes.ToArray()
Write-Host "Stream-Bytes: $($buf.Count)"

# 2) Frames walken: FA AF suchen, 16-bit Gesamtlaenge bei +2..+3, Frame slicen
$frames = New-Object System.Collections.Generic.List[string]
$g23 = New-Object System.Collections.Generic.List[string]
$g24 = New-Object System.Collections.Generic.List[string]
$i = 0
while($i + 3 -lt $buf.Count){
  if($buf[$i] -eq 0xFA -and $buf[$i+1] -eq 0xAF){
    $len = ($buf[$i+2] -shl 8) -bor $buf[$i+3]
    if($len -ge 6 -and ($i + $len) -le $buf.Count){
      $frame = $buf[$i..($i+$len-1)]
      $hex = ($frame | ForEach-Object { $_.ToString('X2') }) -join ' '
      $frames.Add($hex)
      # TLV ab Offset 6 walken
      $j = 6
      while($j + 1 -lt $frame.Count){
        $glen = $frame[$j]
        if($glen -le 0 -or ($j + $glen) -gt $frame.Count){ break }
        $grp = $frame[$j+1]
        $payload = if($glen -ge 2){ $frame[($j+2)..($j+$glen-1)] } else { @() }
        $phex = ($payload | ForEach-Object { $_.ToString('X2') }) -join ' '
        if($grp -eq 0x17){ $g23.Add($phex) }
        if($grp -eq 0x18){ $g24.Add($phex) }
        $j += $glen
      }
      $i += $len
      continue
    }
  }
  $i++
}

# 3) Ausgabe
$header = "### $Label OEM-Frames (reassembliert)  Frames=$($frames.Count)"
$header | Out-File -Encoding utf8 $OutFile
$frames | Out-File -Encoding utf8 -Append $OutFile
Write-Host "Frames: $($frames.Count)  -> $OutFile"

function Show-Const($name, $list){
  Write-Host "---- $name ($($list.Count) Vorkommen) ----"
  $arr = $list | ForEach-Object { ,($_ -split ' ') }
  if($arr.Count -eq 0){ return }
  $width = ($arr | ForEach-Object { $_.Count } | Measure-Object -Maximum).Maximum
  $out = @()
  for($p=0; $p -lt $width; $p++){
    $vals = $arr | ForEach-Object { if($p -lt $_.Count){ $_[$p] } } | Where-Object { $_ }
    $distinct = $vals | Select-Object -Unique
    $const = if($distinct.Count -eq 1){ "KONST" } else { "var" }
    $sample = ($distinct | Select-Object -First 6) -join ','
    $out += "Pos $p : $const  werte={$sample}"
  }
  $out | ForEach-Object { Write-Host $_ }
}
Show-Const "Gruppe 23 (0x17) payload" $g23
Show-Const "Gruppe 24 (0x18) payload" $g24
