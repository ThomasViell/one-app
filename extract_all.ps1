$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$excel.DisplayAlerts = $false
$workbook = $excel.Workbooks.Open('C:\projekte\one.app\ONE_APP_Translations.xlsx')
$sheet = $workbook.Sheets.Item(1)
$maxRow = $sheet.UsedRange.Rows.Count
$maxCol = $sheet.UsedRange.Columns.Count

# Build column-to-code mapping
$colCodes = @()
for ($c = 5; $c -le $maxCol; $c++) {
    $header = [string]$sheet.Cells.Item(1, $c).Value2
    if ($header -match '\((\w+)\)') {
        $code = $Matches[1].ToLower()
        $colCodes += @{ Col = $c; Code = $code; Header = $header }
    }
}

Write-Host "Found $($colCodes.Count) languages"
foreach ($lc in $colCodes) { Write-Host "  Col $($lc.Col): $($lc.Code) ($($lc.Header))" }

$outPath = "C:\projekte\one.app\translations_raw.txt"
$writer = New-Object System.IO.StreamWriter($outPath, $false, (New-Object System.Text.UTF8Encoding($false)))

foreach ($lc in $colCodes) {
    $col = $lc.Col
    $code = $lc.Code
    $writer.WriteLine("===LANG:$code===")

    for ($r = 2; $r -le $maxRow; $r++) {
        $key = $sheet.Cells.Item($r, 1).Value2
        if ([string]::IsNullOrEmpty($key)) { continue }
        $value = $sheet.Cells.Item($r, $col).Value2
        if ($null -eq $value) { $value = "" }
        $value = [string]$value
        $writer.WriteLine("$key=$value")
    }
    $writer.WriteLine("===END===")
}

$writer.Close()
$writer.Dispose()

Write-Host "Done. Output: $outPath"

$workbook.Close($false)
$excel.Quit()
[System.Runtime.Interopservices.Marshal]::ReleaseComObject($excel) | Out-Null
