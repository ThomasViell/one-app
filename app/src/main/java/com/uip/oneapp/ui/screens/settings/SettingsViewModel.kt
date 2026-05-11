package com.uip.oneapp.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import com.uip.oneapp.network.DeviceType
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
    // Device type
    val deviceType: DeviceType = DeviceType.ONE,
    val twoCameraIp: String = "172.169.10.65",
    val twoCameraUser: String = "admin",
    val twoCameraPassword: String = "",
    // OSD Burn-In settings
    val osdEnabled: Boolean = false,
    val osdShowMeter: Boolean = true,
    val osdShowDate: Boolean = true,
    val osdShowInclination: Boolean = false,
    val osdFontSize: OsdFontSize = OsdFontSize.Medium,
    val osdFontColor: OsdColor = OsdColor.Green,
    val osdBackground: OsdBackground = OsdBackground.SemiTransparent,
    val osdFlashPosition: OsdFlashPosition = OsdFlashPosition.Center,
    // Phase 4: feature flag — switches InspectionScreen to FfmpegVideoPlayer with OSD overlay
    val useFfmpegOsdPlayer: Boolean = false,
    // Phase 5: feature flag — uses FfmpegRtspRecorder for recording with OSD burned in during capture
    val useFfmpegRecording: Boolean = false,
) {
    fun toOsdSettings() = OsdSettings(
        enableOsdBurnIn = osdEnabled,
        showMeterValue = osdShowMeter,
        showDate = osdShowDate,
        showInclination = osdShowInclination,
        fontSize = osdFontSize,
        fontColor = osdFontColor,
        background = osdBackground,
        findingFlashPosition = osdFlashPosition
    )
}

class SettingsViewModel(
    private val context: Context,
    private val weatherPresetRepository: WeatherPresetRepository,
    private val damagePresetRepository: DamagePresetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
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
        private val KEY_DEVICE_TYPE = stringPreferencesKey("device_type")
        private val KEY_TWO_CAMERA_IP = stringPreferencesKey("two_camera_ip")
        private val KEY_TWO_CAMERA_USER = stringPreferencesKey("two_camera_user")
        private val KEY_TWO_CAMERA_PASSWORD = stringPreferencesKey("two_camera_password")
        private val KEY_OSD_ENABLED = booleanPreferencesKey("osd_enabled")
        private val KEY_OSD_SHOW_METER = booleanPreferencesKey("osd_show_meter")
        private val KEY_OSD_SHOW_DATE = booleanPreferencesKey("osd_show_date")
        private val KEY_OSD_SHOW_INCLINATION = booleanPreferencesKey("osd_show_inclination")
        private val KEY_OSD_FONT_SIZE = stringPreferencesKey("osd_font_size")
        private val KEY_OSD_FONT_COLOR = stringPreferencesKey("osd_font_color")
        private val KEY_OSD_BACKGROUND = stringPreferencesKey("osd_background")
        private val KEY_OSD_FLASH_POSITION = stringPreferencesKey("osd_flash_position")
        private val KEY_USE_FFMPEG_OSD_PLAYER = booleanPreferencesKey("use_ffmpeg_osd_player")
        private val KEY_USE_FFMPEG_RECORDING = booleanPreferencesKey("use_ffmpeg_recording")
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
                deviceType = DeviceType.entries.firstOrNull { it.name == prefs[KEY_DEVICE_TYPE] } ?: DeviceType.ONE,
                twoCameraIp = prefs[KEY_TWO_CAMERA_IP] ?: "172.169.10.65",
                twoCameraUser = prefs[KEY_TWO_CAMERA_USER] ?: "admin",
                twoCameraPassword = prefs[KEY_TWO_CAMERA_PASSWORD] ?: "",
                osdEnabled = prefs[KEY_OSD_ENABLED] ?: false,
                osdShowMeter = prefs[KEY_OSD_SHOW_METER] ?: true,
                osdShowDate = prefs[KEY_OSD_SHOW_DATE] ?: true,
                osdShowInclination = prefs[KEY_OSD_SHOW_INCLINATION] ?: false,
                osdFontSize = OsdFontSize.entries.firstOrNull { it.name == prefs[KEY_OSD_FONT_SIZE] } ?: OsdFontSize.Medium,
                osdFontColor = OsdColor.entries.firstOrNull { it.name == prefs[KEY_OSD_FONT_COLOR] } ?: OsdColor.Green,
                osdBackground = OsdBackground.entries.firstOrNull { it.name == prefs[KEY_OSD_BACKGROUND] } ?: OsdBackground.SemiTransparent,
                osdFlashPosition = OsdFlashPosition.entries.firstOrNull { it.name == prefs[KEY_OSD_FLASH_POSITION] } ?: OsdFlashPosition.Center,
                useFfmpegOsdPlayer = prefs[KEY_USE_FFMPEG_OSD_PLAYER] ?: false,
                useFfmpegRecording = prefs[KEY_USE_FFMPEG_RECORDING] ?: false,
            )
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

    fun updateDeviceType(value: DeviceType) {
        _uiState.value = _uiState.value.copy(deviceType = value)
        save(KEY_DEVICE_TYPE, value.name)
    }

    fun updateTwoCameraIp(value: String) {
        _uiState.value = _uiState.value.copy(twoCameraIp = value)
        save(KEY_TWO_CAMERA_IP, value)
    }

    fun updateTwoCameraUser(value: String) {
        _uiState.value = _uiState.value.copy(twoCameraUser = value)
        save(KEY_TWO_CAMERA_USER, value)
    }

    fun updateTwoCameraPassword(value: String) {
        _uiState.value = _uiState.value.copy(twoCameraPassword = value)
        save(KEY_TWO_CAMERA_PASSWORD, value)
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

    fun updateOsdShowInclination(value: Boolean) {
        _uiState.value = _uiState.value.copy(osdShowInclination = value)
        saveBool(KEY_OSD_SHOW_INCLINATION, value)
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

    fun updateUseFfmpegOsdPlayer(value: Boolean) {
        _uiState.value = _uiState.value.copy(useFfmpegOsdPlayer = value)
        saveBool(KEY_USE_FFMPEG_OSD_PLAYER, value)
    }

    fun updateUseFfmpegRecording(value: Boolean) {
        _uiState.value = _uiState.value.copy(useFfmpegRecording = value)
        saveBool(KEY_USE_FFMPEG_RECORDING, value)
    }

    fun setCompanyLogo(uri: Uri) {
        viewModelScope.launch {
            val logoFile = File(context.filesDir, "company_logo.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                logoFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val path = logoFile.absolutePath
            _uiState.value = _uiState.value.copy(companyLogoPath = path)
            save(KEY_COMPANY_LOGO, path)
        }
    }

    fun removeCompanyLogo() {
        viewModelScope.launch {
            val logoFile = File(context.filesDir, "company_logo.png")
            if (logoFile.exists()) logoFile.delete()
            _uiState.value = _uiState.value.copy(companyLogoPath = "")
            save(KEY_COMPANY_LOGO, "")
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
                prefs[KEY_DEVICE_TYPE] = state.deviceType.name
                prefs[KEY_TWO_CAMERA_IP] = state.twoCameraIp
                prefs[KEY_TWO_CAMERA_USER] = state.twoCameraUser
                prefs[KEY_TWO_CAMERA_PASSWORD] = state.twoCameraPassword
                prefs[KEY_OSD_ENABLED] = state.osdEnabled
                prefs[KEY_OSD_SHOW_METER] = state.osdShowMeter
                prefs[KEY_OSD_SHOW_DATE] = state.osdShowDate
                prefs[KEY_OSD_SHOW_INCLINATION] = state.osdShowInclination
                prefs[KEY_OSD_FONT_SIZE] = state.osdFontSize.name
                prefs[KEY_OSD_FONT_COLOR] = state.osdFontColor.name
                prefs[KEY_OSD_BACKGROUND] = state.osdBackground.name
                prefs[KEY_OSD_FLASH_POSITION] = state.osdFlashPosition.name
                prefs[KEY_USE_FFMPEG_OSD_PLAYER] = state.useFfmpegOsdPlayer
                prefs[KEY_USE_FFMPEG_RECORDING] = state.useFfmpegRecording
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
