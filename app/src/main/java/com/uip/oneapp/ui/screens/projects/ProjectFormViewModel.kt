package com.uip.oneapp.ui.screens.projects

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uip.oneapp.data.local.entity.ProjectEntity
import com.uip.oneapp.data.repository.ProjectRepository
import com.uip.oneapp.data.repository.WeatherPresetRepository
import com.uip.oneapp.network.LocationService
import com.uip.oneapp.network.NominatimService
import com.uip.oneapp.network.OsmStaticMapService
import com.uip.oneapp.network.WeatherApiService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ProjectFormViewModel(
    private val repository: ProjectRepository,
    private val weatherPresetRepository: WeatherPresetRepository,
    private val weatherApiService: WeatherApiService,
    private val locationService: LocationService,
    private val nominatimService: NominatimService,
    private val osmMapService: OsmStaticMapService
) : ViewModel() {

    // Editing state
    private var editingProjectId: Long? = null
    private var editingProject: ProjectEntity? = null
    var isEditing by mutableStateOf(false)
        private set

    // Allgemeine Angaben
    var auftraggeber by mutableStateOf("")
    var standortAdresse by mutableStateOf("")
    var inspektionsdatum by mutableStateOf(
        LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    )
    var inspektor by mutableStateOf("")
    var wetter by mutableStateOf("")

    // Weather
    val weatherPresets: StateFlow<List<String>> = weatherPresetRepository.presets
    var isFetchingWeather by mutableStateOf(false)
        private set
    var weatherError by mutableStateOf<String?>(null)
        private set

    fun fetchWeatherFromGps() {
        if (isFetchingWeather) return
        isFetchingWeather = true
        weatherError = null
        viewModelScope.launch {
            Log.d("WeatherGPS", "Starting location fetch...")
            locationService.getCurrentLocation()
                .onSuccess { (lat, lon) ->
                    Log.d("WeatherGPS", "Location: $lat, $lon - fetching weather...")
                    weatherApiService.fetchWeather(lat, lon)
                        .onSuccess { result ->
                            Log.d("WeatherGPS", "Weather OK: ${result.toFormattedString()}")
                            wetter = result.toFormattedString()
                        }
                        .onFailure { e ->
                            Log.e("WeatherGPS", "Weather API error: ${e.message}", e)
                            weatherError = e.message
                        }
                }
                .onFailure { e ->
                    Log.e("WeatherGPS", "Location error: ${e.message}", e)
                    weatherError = e.message
                }
            isFetchingWeather = false
        }
    }

    // GPS & Map
    var capturedLat by mutableStateOf<Double?>(null)
        private set
    var capturedLon by mutableStateOf<Double?>(null)
        private set
    var mapImagePath by mutableStateOf<String?>(null)
    var isLoadingLocation by mutableStateOf(false)
        private set
    var locationError by mutableStateOf<String?>(null)
        private set

    // Address search (forward geocoding)
    var isSearchingAddress by mutableStateOf(false)
        private set

    // Fullscreen map picker
    var showMapPicker by mutableStateOf(false)
        private set
    fun openMapPicker() {
        // Only meaningful if we have coords to center on; otherwise the picker
        // can fall back to a default location set in the Composable.
        showMapPicker = true
    }
    fun closeMapPicker() { showMapPicker = false }

    // Context reference for map file path (set during loadProject or fetchLocationAndMap)
    private var appFilesDir: File? = null

    fun setFilesDir(dir: File) {
        appFilesDir = dir
    }

    fun clearLocationError() { locationError = null }

    /**
     * Forward-geocode the current address field text. On success: writes
     * coords, triggers a map render, and reverse-geocodes for a clean display.
     * On failure: surfaces a locationError code the screen can translate.
     */
    fun searchAddress() {
        val query = standortAdresse.trim()
        if (query.isEmpty() || isSearchingAddress) return
        isSearchingAddress = true
        locationError = null
        viewModelScope.launch {
            nominatimService.searchAddress(query)
                .onSuccess { (lat, lon, display) ->
                    capturedLat = lat
                    capturedLon = lon
                    if (display.isNotEmpty()) standortAdresse = display
                    renderMapFor(lat, lon)
                }
                .onFailure { e ->
                    locationError = when (e.message) {
                        "NO_MATCH" -> "ADDRESS_NOT_FOUND"
                        else -> "ADDRESS_SEARCH_NO_INTERNET"
                    }
                }
            isSearchingAddress = false
        }
    }

    /** Applies a marker picked on the fullscreen map. Triggers reverse-geocode + map re-render. */
    fun applyPickedLocation(lat: Double, lon: Double) {
        capturedLat = lat
        capturedLon = lon
        viewModelScope.launch {
            nominatimService.reverseGeocode(lat, lon)
                .onSuccess { addr -> if (addr.isNotEmpty()) standortAdresse = addr }
                .onFailure { /* keep user-entered text */ }
            renderMapFor(lat, lon)
        }
        showMapPicker = false
    }

    private suspend fun renderMapFor(lat: Double, lon: Double) {
        val filesDir = appFilesDir ?: return
        val mapDir = File(filesDir, "maps")
        mapDir.mkdirs()
        val mapFile = File(mapDir, "map_project_${System.currentTimeMillis()}.jpg")
        osmMapService.downloadAndSaveMap(lat, lon, mapFile)
            .onSuccess { file ->
                mapImagePath?.let { old -> File(old).takeIf { it.exists() }?.delete() }
                mapImagePath = file.absolutePath
            }
            .onFailure { e -> Log.w("GPS", "Map render failed: ${e.message}") }
    }

    fun fetchLocationAndMap() {
        if (isLoadingLocation) return
        isLoadingLocation = true
        locationError = null
        viewModelScope.launch {
            locationService.getCurrentLocation()
                .onSuccess { (lat, lon) ->
                    capturedLat = lat
                    capturedLon = lon
                    val coordsFallback = String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon)

                    // Reverse geocode address — needs internet. Tablets on a camera-only
                    // Wi-Fi often can't reach nominatim.openstreetmap.org. In that case
                    // still give the user a useful result: at least the raw coordinates
                    // land in the address field so the GPS click isn't a no-op.
                    var addressOk = false
                    nominatimService.reverseGeocode(lat, lon)
                        .onSuccess { address ->
                            if (address.isNotEmpty()) {
                                standortAdresse = address
                                addressOk = true
                            }
                        }
                        .onFailure { e ->
                            Log.w("GPS", "Nominatim failed: ${e.message}")
                        }
                    if (!addressOk && standortAdresse.isEmpty()) {
                        // Don't overwrite anything the user typed — only fill empty field
                        standortAdresse = coordsFallback
                    }

                    // Download OSM map — also needs internet; non-fatal if it fails.
                    val filesDir = appFilesDir
                    var mapOk = true
                    if (filesDir != null) {
                        val mapDir = File(filesDir, "maps")
                        mapDir.mkdirs()
                        val mapFile = File(mapDir, "map_project_${System.currentTimeMillis()}.jpg")
                        osmMapService.downloadAndSaveMap(lat, lon, mapFile)
                            .onSuccess { file ->
                                mapImagePath?.let { oldPath ->
                                    val oldFile = File(oldPath)
                                    if (oldFile.exists()) oldFile.delete()
                                }
                                mapImagePath = file.absolutePath
                            }
                            .onFailure { e ->
                                Log.w("GPS", "OSM map download failed: ${e.message}")
                                mapOk = false
                            }
                    }

                    // Surface a single info message so the user knows GPS worked but
                    // online lookups didn't — instead of a silent half-success.
                    if (!addressOk || !mapOk) {
                        locationError = "GPS_OK_NO_INTERNET"
                    }
                }
                .onFailure { e ->
                    Log.e("GPS", "Location error: ${e.message}", e)
                    locationError = e.message ?: "LOCATION_FAILED"
                }
            isLoadingLocation = false
        }
    }

    // Leitungsdaten
    var leitungstyp by mutableStateOf("")
    var material by mutableStateOf("")
    var durchmesser by mutableStateOf("")
    var inspektionslaenge by mutableStateOf("")
    var startpunkt by mutableStateOf("")
    var endpunkt by mutableStateOf("")

    // Inspektionsmethode
    var kameratyp by mutableStateOf("")
    var formVisuell by mutableStateOf(false)
    var formVideo by mutableStateOf(true)
    var formFoto by mutableStateOf(true)

    // Video-Einstellungen (nach Anlage nicht änderbar)
    var videoQuality by mutableStateOf("HD")
    var videoOverlay by mutableStateOf(true)

    // Save state
    var isSaving by mutableStateOf(false)
        private set
    var savedProjectId by mutableStateOf<Long?>(null)
        private set

    fun loadProject(projectId: Long) {
        viewModelScope.launch {
            repository.getProject(projectId)?.let { p ->
                editingProjectId = p.id
                editingProject = p
                isEditing = true
                auftraggeber = p.auftraggeber
                standortAdresse = p.standortAdresse
                inspektionsdatum = p.inspektionsdatum
                inspektor = p.inspektor
                wetter = p.wetter
                leitungstyp = p.leitungstyp
                material = p.material
                durchmesser = p.durchmesser
                inspektionslaenge = p.inspektionslaenge
                startpunkt = p.startpunkt
                endpunkt = p.endpunkt
                kameratyp = p.kameratyp
                formVisuell = p.formVisuell
                formVideo = p.formVideo
                formFoto = p.formFoto
                videoQuality = p.videoQuality
                videoOverlay = p.videoOverlay
                capturedLat = p.latitude
                capturedLon = p.longitude
                mapImagePath = p.mapImagePath
            }
        }
    }

    fun saveProject() {
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            try {
                if (isEditing && editingProject != null) {
                    val updated = editingProject!!.copy(
                        auftraggeber = auftraggeber,
                        standortAdresse = standortAdresse,
                        inspektionsdatum = inspektionsdatum,
                        inspektor = inspektor,
                        wetter = wetter,
                        leitungstyp = leitungstyp,
                        material = material,
                        durchmesser = durchmesser,
                        inspektionslaenge = inspektionslaenge,
                        startpunkt = startpunkt,
                        endpunkt = endpunkt,
                        kameratyp = kameratyp,
                        formVisuell = formVisuell,
                        formVideo = formVideo,
                        formFoto = formFoto,
                        // Video-Einstellungen: Originalwerte beibehalten (nicht änderbar)
                        videoQuality = editingProject!!.videoQuality,
                        videoOverlay = editingProject!!.videoOverlay,
                        latitude = capturedLat,
                        longitude = capturedLon,
                        mapImagePath = mapImagePath
                    )
                    repository.updateProject(updated)
                    savedProjectId = updated.id
                } else {
                    val project = ProjectEntity(
                        auftraggeber = auftraggeber,
                        standortAdresse = standortAdresse,
                        inspektionsdatum = inspektionsdatum,
                        inspektor = inspektor,
                        wetter = wetter,
                        leitungstyp = leitungstyp,
                        material = material,
                        durchmesser = durchmesser,
                        inspektionslaenge = inspektionslaenge,
                        startpunkt = startpunkt,
                        endpunkt = endpunkt,
                        kameratyp = kameratyp,
                        formVisuell = formVisuell,
                        formVideo = formVideo,
                        formFoto = formFoto,
                        videoQuality = videoQuality,
                        videoOverlay = videoOverlay,
                        latitude = capturedLat,
                        longitude = capturedLon,
                        mapImagePath = mapImagePath
                    )
                    val id = repository.saveProject(project)
                    savedProjectId = id
                }
            } finally {
                isSaving = false
            }
        }
    }
}
