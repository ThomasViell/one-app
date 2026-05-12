$ids = @(
    "europe/germany",
    "europe/germany/baden-wuerttemberg",
    "europe/germany/bayern",
    "europe/germany/berlin",
    "europe/germany/brandenburg",
    "europe/germany/bremen",
    "europe/germany/hamburg",
    "europe/germany/hessen",
    "europe/germany/mecklenburg-vorpommern",
    "europe/germany/niedersachsen",
    "europe/germany/nordrhein-westfalen",
    "europe/germany/rheinland-pfalz",
    "europe/germany/saarland",
    "europe/germany/sachsen",
    "europe/germany/sachsen-anhalt",
    "europe/germany/schleswig-holstein",
    "europe/germany/thueringen",
    "europe/austria",
    "europe/switzerland",
    "europe/france",
    "europe/italy",
    "europe/netherlands",
    "europe/belgium",
    "europe/luxembourg",
    "europe/czech-republic",
    "europe/poland",
    "europe/denmark",
    "europe/spain",
    "europe/portugal",
    "europe/united-kingdom",
    "europe/croatia",
    "europe/slovenia"
)
foreach ($id in $ids) {
    $url = "https://download.mapsforge.org/maps/v5/$id.map"
    try {
        $r = Invoke-WebRequest -Method Head -Uri $url -UseBasicParsing -ErrorAction Stop
        $bytes = [long]($r.Headers['Content-Length'] | Select-Object -First 1)
        $mb = [math]::Round($bytes / 1MB, 0)
        "{0,-45} {1,6} MB" -f $id, $mb
    } catch {
        "{0,-45} MISSING ({1})" -f $id, $_.Exception.Message
    }
}
