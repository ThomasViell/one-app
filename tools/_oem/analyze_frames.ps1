<#
  analyze_frames.ps1 — liest RXFRAME=-Zeilen aus einem OneInternalHW-Logcat,
  walkt die TLV-Gruppen und meldet je Gruppe (23/24) pro Byte-Position, ob sie
  ueber alle Frames KONSTANT ist (+ Wertemenge). Speichert die Frames als capture.
#>
param(
  [Parameter(Mandatory=$true)][string]$InFile,
  [Parameter(Mandatory=$true)][string]$OutFile,
  [string]$Label = "?"
)
$frames = @()
foreach($line in Get-Content $InFile){
  $idx = $line.IndexOf("RXFRAME=")
  if($idx -lt 0){ continue }
  $hex = $line.Substring($idx + 8).Trim()
  $toks = [regex]::Matches($hex, '\b[0-9A-Fa-f]{2}\b') | ForEach-Object { [Convert]::ToByte($_.Value,16) }
  if($toks.Count -lt 7){ continue }
  # Ein RXFRAME kann mehrere Frames enthalten (consumed-slice). Per FA AF + len splitten.
  $b = @($toks); $i = 0
  while($i + 3 -lt $b.Count){
    if($b[$i] -eq 0xFA -and $b[$i+1] -eq 0xAF){
      $total = ($b[$i+2] -shl 8) -bor $b[$i+3]
      if($total -ge 7 -and ($i+$total) -le $b.Count){
        $frames += ,@($b[$i..($i+$total-1)]); $i += $total; continue
      }
    }
    $i++
  }
}
Write-Host "$Label : $($frames.Count) Frames"

# capture speichern
"### $Label App-reassembliert  Frames=$($frames.Count)" | Out-File -Encoding utf8 $OutFile
foreach($f in $frames){ (($f | ForEach-Object { $_.ToString('X2') }) -join ' ') | Out-File -Encoding utf8 -Append $OutFile }

# Gruppen 23/24 extrahieren (Payloads als Hex-String je Vorkommen)
$g23 = New-Object System.Collections.Generic.List[string]
$g24 = New-Object System.Collections.Generic.List[string]
foreach($f in $frames){
  $j = 6
  while($j + 1 -lt $f.Count){
    $glen = $f[$j]
    if($glen -le 1 -or ($j+$glen) -gt $f.Count){ break }
    $grp = $f[$j+1]
    $phex = if($glen -ge 2){ (($f[($j+2)..($j+$glen-1)] | ForEach-Object { $_.ToString('X2') }) -join ' ') } else { '' }
    if($grp -eq 23){ $g23.Add($phex) }
    if($grp -eq 24){ $g24.Add($phex) }
    $j += $glen
  }
}
function Report($name, $list){
  Write-Host "---- Gruppe $name ($($list.Count) Vorkommen) ----"
  if($list.Count -eq 0){ return }
  $arr = $list | ForEach-Object { ,($_ -split ' ') }
  $w = ($arr | ForEach-Object { $_.Count } | Measure-Object -Maximum).Maximum
  for($p=0;$p -lt $w;$p++){
    $vals = $arr | ForEach-Object { if($p -lt $_.Count){ $_[$p] } } | Where-Object { $_ }
    $d = $vals | Select-Object -Unique
    $tag = if($d.Count -eq 1){ "KONST" } else { "var  " }
    Write-Host ("Pos {0} : {1} werte={{{2}}}" -f $p,$tag,(($d|Select-Object -First 8) -join ','))
  }
}
Report 23 $g23
Report 24 $g24
