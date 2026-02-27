package com.uip.oneapp.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uip.oneapp.data.repository.DamagePresetRepository
import com.uip.oneapp.data.repository.WeatherPresetRepository
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
    val companyLogoPath: String = ""
)

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
                companyLogoPath = prefs[KEY_COMPANY_LOGO] ?: ""
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
            }
        }
    }

    private fun save(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        viewModelScope.launch {
            context.settingsStore.edit { it[key] = value }
        }
    }
}
