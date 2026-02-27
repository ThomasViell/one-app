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
import com.uip.oneapp.network.WeatherApiService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ProjectFormViewModel(
    private val repository: ProjectRepository,
    private val weatherPresetRepository: WeatherPresetRepository,
    private val weatherApiService: WeatherApiService,
    private val locationService: LocationService
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
                        videoOverlay = editingProject!!.videoOverlay
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
                        videoOverlay = videoOverlay
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
