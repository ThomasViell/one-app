package com.uip.oneapp.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uip.oneapp.data.repository.DamagePresetRepository
import com.uip.oneapp.data.repository.WeatherPresetRepository
import com.uip.oneapp.export.OsdBackground
import com.uip.oneapp.export.OsdColor
import com.uip.oneapp.export.OsdFlashPosition
import com.uip.oneapp.export.OsdFontSize
import com.uip.oneapp.export.OsdSettings
import com.uip.oneapp.network.FeatureFlags
import com.uip.oneapp.network.HardwareMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

internal val Context.settingsStore by preferencesDataStore(name = "app_settings")

data class SettingsUiState(
    val brokerIp: String = "172.169.11.200",
    val brokerPort: String = "1883",
    val rtspUrl: String = "rtsp://172.169.11.200:554/stream",
    val companyName: String = "",
    val companyAddress: String = "",
    val companyLogoPath: String = "",
    // Dual-Modus (Welle 4): aktiver Laufzeit-Modus, steuert die modusabhängige Sichtbarkeit
    // (DIRECT blendet den Verbindungs-/RTSP-Screen aus; WiFi blendet Kiosk + Helligkeit aus).
    // Wird aus dem aufgelösten HardwareService abgeleitet (siehe AppModule), nicht persistiert.
    val hardwareMode: HardwareMode = HardwareMode.DIRECT,
    // OSD Burn-In settings
    val osdEnabled: Boolean = false,
    val osdShowMeter: Boolean = true,
    val osdShowDate: Boolean = true,
    val osdFontSize: OsdFontSize = OsdFontSize.Medium,
    val osdFontColor: OsdColor = OsdColor.Green,
    val osdBackground: OsdBackground = OsdBackground.SemiTransparent,
    val osdFlashPosition: OsdFlashPosition = OsdFlashPosition.Center,
    // Kiosk-Modus: blendet die Android-System-Bars aus (Vollbild am Feldgerät).
    // Default AUS, damit Entwicklung/Service immer auf die Android-Ebene kommt.
    val kioskMode: Boolean = false,
    // Auto-Ausblenden der Bedienelemente in der Inspektion (Feedback Louis #2).
    // Default AUS: Bedienband bleibt dauerhaft sichtbar; AN = bisheriges Cinema-Auto-Hide.
    val controlsAutoHide: Boolean = false,
    // Bildschirmhelligkeit (CEO-Beschluss 2026-06-07, wie Original-App):
    // -1 = System/automatisch, 5..100 = manuell (Window-Brightness, keine Spezial-Permission).
    val screenBrightness: Int = -1,
    // Auto-Reconnect W1 (nur Tablet/WiFi sichtbar): beim App-Start/Abriss automatisch mit
    // einer bekannten ONE wiederverbinden. Default AN.
    val autoConnectOne: Boolean = true,
    // Welle 5: Aufnahmeweg. true (Default) → HW-Encoder (25 fps, Echtzeit, absturzsicher);
    // false → alter LocalBitmapRecorder (Rückfallebene). Spiegelt FeatureFlags.useHardwareRecorder.
    val useHardwareRecorder: Boolean = true,
) {
    // Hardware-OSD wurde entfernt (CEO-Beschluss 2026-06-07): Die ONE rendert kein
    // Kamera-OSD; die App ist die einzige OSD-Quelle. Sonde/Neigung sind ebenfalls
    // raus — im eingebrannten Video haben sie keinen dokumentarischen Wert.
    fun toOsdSettings() = OsdSettings(
        enableOsdBurnIn = osdEnabled,
        showMeterValue = osdShowMeter,
        showDate = osdShowDate,
        fontSize = osdFontSize,
        fontColor = osdFontColor,
        background = osdBackground,
        findingFlashPosition = osdFlashPosition
    )
}

class SettingsViewModel(
    private val context: Context,
    private val weatherPresetRepository: WeatherPresetRepository,
    private val damagePresetRepository: DamagePresetRepository,
    private val hardwareMode: HardwareMode
) : ViewModel() {

    // Modus ist ab Konstruktion bekannt → initial setzen, damit die modusabhängige UI nicht
    // erst nach dem asynchronen Prefs-Laden umspringt.
    private val _uiState = MutableStateFlow(SettingsUiState(hardwareMode = hardwareMode))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Weather presets
    val weatherPresets: StateFlow<List<String>> = weatherPresetRepository.presets

    fun addWeatherPreset(name: String) = weatherPresetRepository.addPreset(name)
    fun removeWeatherPreset(index: Int) = weatherPresetRepository.removePreset(index)
    fun updateWeatherPreset(index: Int, newName: String) = weatherPresetRepository.updatePreset(index, newName)
    fun resetWeatherPresets() = weatherPresetRepository.resetToDefaults()

    // Damage presets
    val damagePresets: StateFlow<List<String>> = damagePresetRepository.presets

    fun addDamagePreset(name: String) = damagePresetRepository.addPreset(name)
    fun removeDamagePreset(index: Int) = damagePresetRepository.removePreset(index)
    fun updateDamagePreset(index: Int, newName: String) = damagePresetRepository.updatePreset(index, newName)
    fun resetDamagePresets() = damagePresetRepository.resetToDefaults()

    companion object {
        private val KEY_BROKER_IP = stringPreferencesKey("broker_ip")
        private val KEY_BROKER_PORT = stringPreferencesKey("broker_port")
        private val KEY_RTSP_URL = stringPreferencesKey("rtsp_url")
        private val KEY_COMPANY_NAME = stringPreferencesKey("company_name")
        private val KEY_COMPANY_ADDRESS = stringPreferencesKey("company_address")
        private val KEY_COMPANY_LOGO = stringPreferencesKey("company_logo_path")
        private val KEY_OSD_ENABLED = booleanPreferencesKey("osd_enabled")
        private val KEY_OSD_SHOW_METER = booleanPreferencesKey("osd_show_meter")
        private val KEY_OSD_SHOW_DATE = booleanPreferencesKey("osd_show_date")
        private val KEY_OSD_FONT_SIZE = stringPreferencesKey("osd_font_size")
        private val KEY_OSD_FONT_COLOR = stringPreferencesKey("osd_font_color")
        private val KEY_OSD_BACKGROUND = stringPreferencesKey("osd_background")
        private val KEY_OSD_FLASH_POSITION = stringPreferencesKey("osd_flash_position")
        val KEY_KIOSK_MODE = booleanPreferencesKey("kiosk_mode")
        val KEY_CONTROLS_AUTO_HIDE = booleanPreferencesKey("controls_auto_hide")
        val KEY_SCREEN_BRIGHTNESS = intPreferencesKey("screen_brightness")
        // Auto-Reconnect W1 — auch vom OneAutoConnector (DI) gelesen.
        val KEY_AUTO_CONNECT_ONE = booleanPreferencesKey("auto_connect_one")
        // Welle 5 — auch von OneApp.onCreate eager gelesen (FeatureFlags-Restore).
        val KEY_USE_HARDWARE_RECORDER = booleanPreferencesKey("use_hardware_recorder")
    }

    init {
        viewModelScope.launch {
            val prefs = context.settingsStore.data.first()
            _uiState.value = SettingsUiState(
                brokerIp = prefs[KEY_BROKER_IP] ?: "172.169.11.200",
                brokerPort = prefs[KEY_BROKER_PORT] ?: "1883",
                rtspUrl = prefs[KEY_RTSP_URL] ?: "rtsp://172.169.11.200:554/stream",
                companyName = prefs[KEY_COMPANY_NAME] ?: "",
                companyAddress = prefs[KEY_COMPANY_ADDRESS] ?: "",
                companyLogoPath = prefs[KEY_COMPANY_LOGO] ?: "",
                hardwareMode = hardwareMode,
                osdEnabled = prefs[KEY_OSD_ENABLED] ?: false,
                osdShowMeter = prefs[KEY_OSD_SHOW_METER] ?: true,
                osdShowDate = prefs[KEY_OSD_SHOW_DATE] ?: true,
                osdFontSize = OsdFontSize.entries.firstOrNull { it.name == prefs[KEY_OSD_FONT_SIZE] } ?: OsdFontSize.Medium,
                osdFontColor = OsdColor.entries.firstOrNull { it.name == prefs[KEY_OSD_FONT_COLOR] } ?: OsdColor.Green,
                osdBackground = OsdBackground.entries.firstOrNull { it.name == prefs[KEY_OSD_BACKGROUND] } ?: OsdBackground.SemiTransparent,
                osdFlashPosition = OsdFlashPosition.entries.firstOrNull { it.name == prefs[KEY_OSD_FLASH_POSITION] } ?: OsdFlashPosition.Center,
                kioskMode = prefs[KEY_KIOSK_MODE] ?: false,
                controlsAutoHide = prefs[KEY_CONTROLS_AUTO_HIDE] ?: false,
                screenBrightness = prefs[KEY_SCREEN_BRIGHTNESS] ?: -1,
                autoConnectOne = prefs[KEY_AUTO_CONNECT_ONE] ?: true,
                useHardwareRecorder = prefs[KEY_USE_HARDWARE_RECORDER] ?: true,
            )
            // Laufzeit-Flag mit dem persistierten Wert synchronisieren (OneApp init'd eager; hier
            // defensiv nachziehen, falls der Store nach dem App-Start geändert wurde).
            FeatureFlags.useHardwareRecorder = _uiState.value.useHardwareRecorder
        }
    }

    fun updateBrokerIp(value: String) {
        _uiState.value = _uiState.value.copy(brokerIp = value)
        save(KEY_BROKER_IP, value)
    }

    fun updateBrokerPort(value: String) {
        _uiState.value = _uiState.value.copy(brokerPort = value)
        save(KEY_BROKER_PORT, value)
    }

    fun updateRtspUrl(value: String) {
        _uiState.value = _uiState.value.copy(rtspUrl = value)
        save(KEY_RTSP_URL, value)
    }

    fun updateCompanyName(value: String) {
        _uiState.value = _uiState.value.copy(companyName = value)
        save(KEY_COMPANY_NAME, value)
    }

    fun updateCompanyAddress(value: String) {
        _uiState.value = _uiState.value.copy(companyAddress = value)
        save(KEY_COMPANY_ADDRESS, value)
    }

    fun updateOsdEnabled(value: Boolean) {
        _uiState.value = _uiState.value.copy(osdEnabled = value)
        saveBool(KEY_OSD_ENABLED, value)
    }

    fun updateOsdShowMeter(value: Boolean) {
        _uiState.value = _uiState.value.copy(osdShowMeter = value)
        saveBool(KEY_OSD_SHOW_METER, value)
    }

    fun updateOsdShowDate(value: Boolean) {
        _uiState.value = _uiState.value.copy(osdShowDate = value)
        saveBool(KEY_OSD_SHOW_DATE, value)
    }

    fun updateOsdFontSize(value: OsdFontSize) {
        _uiState.value = _uiState.value.copy(osdFontSize = value)
        save(KEY_OSD_FONT_SIZE, value.name)
    }

    fun updateOsdFontColor(value: OsdColor) {
        _uiState.value = _uiState.value.copy(osdFontColor = value)
        save(KEY_OSD_FONT_COLOR, value.name)
    }

    fun updateOsdBackground(value: OsdBackground) {
        _uiState.value = _uiState.value.copy(osdBackground = value)
        save(KEY_OSD_BACKGROUND, value.name)
    }

    fun updateOsdFlashPosition(value: OsdFlashPosition) {
        _uiState.value = _uiState.value.copy(osdFlashPosition = value)
        save(KEY_OSD_FLASH_POSITION, value.name)
    }

    fun updateKioskMode(value: Boolean) {
        _uiState.value = _uiState.value.copy(kioskMode = value)
        saveBool(KEY_KIOSK_MODE, value)
    }

    fun updateControlsAutoHide(value: Boolean) {
        _uiState.value = _uiState.value.copy(controlsAutoHide = value)
        saveBool(KEY_CONTROLS_AUTO_HIDE, value)
    }

    /** Auto-Reconnect W1: automatisch mit bekannter ONE verbinden (Default AN). */
    fun updateAutoConnectOne(value: Boolean) {
        _uiState.value = _uiState.value.copy(autoConnectOne = value)
        saveBool(KEY_AUTO_CONNECT_ONE, value)
    }

    /**
     * Welle 5: Aufnahmeweg umschalten (HW-Encoder ↔ alter Recorder). Setzt den Laufzeit-Flag
     * sofort; wirkt auf die NÄCHSTE Aufnahme (der Recorder wird beim Betreten der Inspektion
     * gewählt). Eine aktive Aufnahme kann nicht betroffen sein: das Verlassen der Inspektion (nötig
     * um in die Settings zu kommen) bricht sie ohnehin ab.
     */
    fun updateUseHardwareRecorder(value: Boolean) {
        _uiState.value = _uiState.value.copy(useHardwareRecorder = value)
        FeatureFlags.useHardwareRecorder = value
        saveBool(KEY_USE_HARDWARE_RECORDER, value)
    }

    /** -1 = System/automatisch, 5..100 = manuelle Helligkeit. Anwendung in MainActivity. */
    fun updateScreenBrightness(value: Int) {
        val v = if (value < 0) -1 else value.coerceIn(5, 100)
        _uiState.value = _uiState.value.copy(screenBrightness = v)
        viewModelScope.launch {
            context.settingsStore.edit { it[KEY_SCREEN_BRIGHTNESS] = v }
        }
    }

    fun setCompanyLogo(uri: Uri) {
        // IO-Dispatcher + runCatching wie setCompanyLogoFromFile: der Stream-Copy lief vorher
        // ungeschützt auf dem Main-Dispatcher (Jank + Crash bei nicht lesbarer Uri).
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val logoFile = File(context.filesDir, "company_logo.png")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    logoFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@launch
            }.onSuccess {
                val path = logoFile.absolutePath
                _uiState.value = _uiState.value.copy(companyLogoPath = path)
                save(KEY_COMPANY_LOGO, path)
            }
        }
    }

    /** Eigenes Logo aus Datei übernehmen (In-App-Picker: USB-Stick/Download/DCIM, kiosk-sicher). */
    fun setCompanyLogoFromFile(source: File) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val logoFile = File(context.filesDir, "company_logo.png")
            runCatching {
                source.inputStream().use { input ->
                    logoFile.outputStream().use { output -> input.copyTo(output) }
                }
            }.onSuccess {
                val path = logoFile.absolutePath
                _uiState.value = _uiState.value.copy(companyLogoPath = path)
                save(KEY_COMPANY_LOGO, path)
            }
        }
    }

    /** Eigenes Logo entfernen → zurück auf das mitgelieferte Standard-Logo (NSP3CT, pref=""). */
    fun removeCompanyLogo() {
        viewModelScope.launch {
            val logoFile = File(context.filesDir, "company_logo.png")
            if (logoFile.exists()) logoFile.delete()
            _uiState.value = _uiState.value.copy(companyLogoPath = "")
            save(KEY_COMPANY_LOGO, "")
        }
    }

    /** Bewusst KEIN Logo im Bericht (Sentinel "none", siehe export/ReportLogo). */
    fun useNoLogo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(companyLogoPath = com.uip.oneapp.export.ReportLogo.PREF_NONE)
            save(KEY_COMPANY_LOGO, com.uip.oneapp.export.ReportLogo.PREF_NONE)
        }
    }

    fun saveAll() {
        val state = _uiState.value
        viewModelScope.launch {
            context.settingsStore.edit { prefs ->
                prefs[KEY_BROKER_IP] = state.brokerIp
                prefs[KEY_BROKER_PORT] = state.brokerPort
                prefs[KEY_RTSP_URL] = state.rtspUrl
                prefs[KEY_COMPANY_NAME] = state.companyName
                prefs[KEY_COMPANY_ADDRESS] = state.companyAddress
                prefs[KEY_COMPANY_LOGO] = state.companyLogoPath
                prefs[KEY_OSD_ENABLED] = state.osdEnabled
                prefs[KEY_OSD_SHOW_METER] = state.osdShowMeter
                prefs[KEY_OSD_SHOW_DATE] = state.osdShowDate
                prefs[KEY_OSD_FONT_SIZE] = state.osdFontSize.name
                prefs[KEY_OSD_FONT_COLOR] = state.osdFontColor.name
                prefs[KEY_OSD_BACKGROUND] = state.osdBackground.name
                prefs[KEY_OSD_FLASH_POSITION] = state.osdFlashPosition.name
                prefs[KEY_KIOSK_MODE] = state.kioskMode
                prefs[KEY_CONTROLS_AUTO_HIDE] = state.controlsAutoHide
                prefs[KEY_SCREEN_BRIGHTNESS] = state.screenBrightness
            }
        }
    }

    private fun save(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        viewModelScope.launch {
            context.settingsStore.edit { it[key] = value }
        }
    }

    private fun saveBool(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            context.settingsStore.edit { it[key] = value }
        }
    }
}
