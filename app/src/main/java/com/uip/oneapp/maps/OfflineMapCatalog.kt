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

    // Sizes verified against HEAD response from download.mapsforge.org on 2026-05-12.
    // OSM data grew significantly over the years — these are NOT my-old-memory guesses
    // anymore. Refresh occasionally with scripts/check_mapsforge_sizes.ps1.
    val all: List<Entry> = listOf(
        // ── Deutschland ────────────────────────────────────────────────────
        Entry("europe/germany",                          "Deutschland (komplett)",   "Deutschland", "Europa", 3040),
        Entry("europe/germany/baden-wuerttemberg",       "Baden-Württemberg",         "Deutschland", "Europa",  399),
        Entry("europe/germany/bayern",                   "Bayern",                    "Deutschland", "Europa",  544),
        Entry("europe/germany/berlin",                   "Berlin",                    "Deutschland", "Europa",   51),
        Entry("europe/germany/brandenburg",              "Brandenburg",               "Deutschland", "Europa",  190),
        Entry("europe/germany/bremen",                   "Bremen",                    "Deutschland", "Europa",   13),
        Entry("europe/germany/hamburg",                  "Hamburg",                   "Deutschland", "Europa",   28),
        Entry("europe/germany/hessen",                   "Hessen",                    "Deutschland", "Europa",  215),
        Entry("europe/germany/mecklenburg-vorpommern",   "Mecklenburg-Vorpommern",    "Deutschland", "Europa",   95),
        Entry("europe/germany/niedersachsen",            "Niedersachsen",             "Deutschland", "Europa",  342),
        Entry("europe/germany/nordrhein-westfalen",      "Nordrhein-Westfalen",       "Deutschland", "Europa",  581),
        Entry("europe/germany/rheinland-pfalz",          "Rheinland-Pfalz",           "Deutschland", "Europa",  176),
        Entry("europe/germany/saarland",                 "Saarland",                  "Deutschland", "Europa",   34),
        Entry("europe/germany/sachsen",                  "Sachsen",                   "Deutschland", "Europa",  163),
        Entry("europe/germany/sachsen-anhalt",           "Sachsen-Anhalt",            "Deutschland", "Europa",  121),
        Entry("europe/germany/schleswig-holstein",       "Schleswig-Holstein",        "Deutschland", "Europa",  108),
        Entry("europe/germany/thueringen",               "Thüringen",                 "Deutschland", "Europa",  111),

        // ── Österreich ─────────────────────────────────────────────────────
        Entry("europe/austria",                          "Österreich (komplett)",     "Österreich",  "Europa",  522),

        // ── Schweiz ────────────────────────────────────────────────────────
        Entry("europe/switzerland",                      "Schweiz",                   "Schweiz",     "Europa",  312),

        // ── Nachbarländer (häufig benötigt) ────────────────────────────────
        Entry("europe/france",                           "Frankreich",                "Frankreich",  "Europa", 3292),
        Entry("europe/italy",                            "Italien",                   "Italien",     "Europa", 1579),
        Entry("europe/netherlands",                      "Niederlande",               "Niederlande", "Europa",  890),
        Entry("europe/belgium",                          "Belgien",                   "Belgien",     "Europa",  486),
        Entry("europe/luxembourg",                       "Luxemburg",                 "Luxemburg",   "Europa",   32),
        Entry("europe/czech-republic",                   "Tschechien",                "Tschechien",  "Europa",  573),
        Entry("europe/poland",                           "Polen",                     "Polen",       "Europa", 1486),
        Entry("europe/denmark",                          "Dänemark",                  "Dänemark",    "Europa",  317),

        // ── Häufige Reise-/Einsatzziele ────────────────────────────────────
        Entry("europe/spain",                            "Spanien",                   "Spanien",     "Europa", 1095),
        Entry("europe/portugal",                         "Portugal",                  "Portugal",    "Europa",  343),
        // mapsforge.org has no "united-kingdom" — the correct slug is "great-britain"
        Entry("europe/great-britain",                    "Großbritannien",            "Großbritannien", "Europa", 1530),
        Entry("europe/croatia",                          "Kroatien",                  "Kroatien",    "Europa",  164),
        Entry("europe/slovenia",                         "Slowenien",                 "Slowenien",   "Europa",  225),
    )

    fun byContinent(): Map<String, List<Entry>> = all.groupBy { it.continent }
    fun byCountry(continent: String): Map<String, List<Entry>> =
        all.filter { it.continent == continent }.groupBy { it.country }

    fun url(entry: Entry): String = "$BASE_URL${entry.id}.map"

    /** Stable local file name for an entry — "/" becomes "_". */
    fun fileName(entry: Entry): String = entry.id.replace('/', '_') + ".map"

    fun findById(id: String): Entry? = all.firstOrNull { it.id == id }
}
