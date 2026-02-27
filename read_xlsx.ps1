$excel = New-Object -ComObject Excel.Application
$excel.Visible = $false
$excel.DisplayAlerts = $false
$workbook = $excel.Workbooks.Open('C:\projekte\one.app\ONE_APP_Translations.xlsx')
$sheet = $workbook.Sheets.Item(1)
$maxRow = $sheet.UsedRange.Rows.Count
$maxCol = $sheet.UsedRange.Columns.Count

Write-Host "Rows: $maxRow, Cols: $maxCol"

# Header
$header = @()
for ($c = 1; $c -le $maxCol; $c++) {
    $header += $sheet.Cells.Item(1, $c).Value2
}
Write-Host "Header: $($header -join ' | ')"

$workbook.Close($false)
$excel.Quit()
[System.Runtime.Interopservices.Marshal]::ReleaseComObject($excel) | Out-Null
