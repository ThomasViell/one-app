package com.uip.oneapp.maps

/**
 * Curated catalog of MapsForge .map files available on download.mapsforge.org.
 * Picked for the typical DrainQ user — DACH-heavy, neighbouring EU countries
 * second, plus a handful of common holiday destinations. More can be added on
 * demand without an app update (the URL pattern is stable).
 *
 * Add-it-yourself recipe:
 *   1. Find the file at https://download.mapsforge.org/maps/v5/<continent>/<...>
 *   2. Append an entry below with the path relative to /maps/v5/ (no ".map" suffix)
 *
 * Sizes are approximate snapshots — the real Content-Length is queried via HEAD
 * just before download so the user sees the current value.
 */
object OfflineMapCatalog {

    const val BASE_URL = "https://download.mapsforge.org/maps/v5/"

    data class Entry(
        val id: String,             // url path under /maps/v5/, no .map
        val displayName: String,    // shown in the picker
        val country: String,        // grouping label
        val continent: String,      // top-level grouping
        val approxSizeMB: Int       // rough size hint shown before HEAD response
    )

    val all: List<Entry> = listOf(
        // ── Deutschland ────────────────────────────────────────────────────
        Entry("europe/germany",                          "Deutschland (komplett)",  "Deutschland", "Europa", 540),
        Entry("europe/germany/baden-wuerttemberg",       "Baden-Württemberg",        "Deutschland", "Europa",  90),
        Entry("europe/germany/bayern",                   "Bayern",                   "Deutschland", "Europa", 130),
        Entry("europe/germany/berlin",                   "Berlin",                   "Deutschland", "Europa",   8),
        Entry("europe/germany/brandenburg",              "Brandenburg",              "Deutschland", "Europa",  30),
        Entry("europe/germany/bremen",                   "Bremen",                   "Deutschland", "Europa",   3),
        Entry("europe/germany/hamburg",                  "Hamburg",                  "Deutschland", "Europa",   6),
        Entry("europe/germany/hessen",                   "Hessen",                   "Deutschland", "Europa",  45),
        Entry("europe/germany/mecklenburg-vorpommern",   "Mecklenburg-Vorpommern",   "Deutschland", "Europa",  22),
        Entry("europe/germany/niedersachsen",            "Niedersachsen",            "Deutschland", "Europa",  70),
        Entry("europe/germany/nordrhein-westfalen",      "Nordrhein-Westfalen",      "Deutschland", "Europa", 110),
        Entry("europe/germany/rheinland-pfalz",          "Rheinland-Pfalz",          "Deutschland", "Europa",  45),
        Entry("europe/germany/saarland",                 "Saarland",                 "Deutschland", "Europa",   8),
        Entry("europe/germany/sachsen",                  "Sachsen",                  "Deutschland", "Europa",  40),
        Entry("europe/germany/sachsen-anhalt",           "Sachsen-Anhalt",           "Deutschland", "Europa",  28),
        Entry("europe/germany/schleswig-holstein",       "Schleswig-Holstein",       "Deutschland", "Europa",  28),
        Entry("europe/germany/thueringen",               "Thüringen",                "Deutschland", "Europa",  30),

        // ── Österreich ─────────────────────────────────────────────────────
        Entry("europe/austria",                          "Österreich (komplett)",    "Österreich",  "Europa",  85),

        // ── Schweiz ────────────────────────────────────────────────────────
        Entry("europe/switzerland",                      "Schweiz",                  "Schweiz",     "Europa",  60),

        // ── Nachbarländer (häufig benötigt) ────────────────────────────────
        Entry("europe/france",                           "Frankreich",               "Frankreich",  "Europa", 380),
        Entry("europe/italy",                            "Italien",                  "Italien",     "Europa", 260),
        Entry("europe/netherlands",                      "Niederlande",              "Niederlande", "Europa",  55),
        Entry("europe/belgium",                          "Belgien",                  "Belgien",     "Europa",  35),
        Entry("europe/luxembourg",                       "Luxemburg",                "Luxemburg",   "Europa",   5),
        Entry("europe/czech-republic",                   "Tschechien",               "Tschechien",  "Europa",  55),
        Entry("europe/poland",                           "Polen",                    "Polen",       "Europa", 180),
        Entry("europe/denmark",                          "Dänemark",                 "Dänemark",    "Europa",  30),

        // ── Häufige Reise-/Einsatzziele ────────────────────────────────────
        Entry("europe/spain",                            "Spanien",                  "Spanien",     "Europa", 250),
        Entry("europe/portugal",                         "Portugal",                 "Portugal",    "Europa",  50),
        Entry("europe/united-kingdom",                   "Großbritannien",           "Großbritannien", "Europa", 180),
        Entry("europe/croatia",                          "Kroatien",                 "Kroatien",    "Europa",  35),
        Entry("europe/slovenia",                         "Slowenien",                "Slowenien",   "Europa",  20),
    )

    fun byContinent(): Map<String, List<Entry>> = all.groupBy { it.continent }
    fun byCountry(continent: String): Map<String, List<Entry>> =
        all.filter { it.continent == continent }.groupBy { it.country }

    fun url(entry: Entry): String = "$BASE_URL${entry.id}.map"

    /** Stable local file name for an entry — "/" becomes "_". */
    fun fileName(entry: Entry): String = entry.id.replace('/', '_') + ".map"

    fun findById(id: String): Entry? = all.firstOrNull { it.id == id }
}
