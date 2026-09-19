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
import com.uip.oneapp.network.HardwareMode
import com.uip.oneapp.system.AndroidClockPort
import com.uip.oneapp.system.SystemTimeSetter
import kotlinx.coroutines.NonCancellable
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
    // (DIRECT blendet den Verbindungs-/RTSP-Screen aus; WiFi blendet Kiosk-Zeile + Helligkeit aus).
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
    // Auto-Ausblenden der Bedienelemente in der Inspektion (Feedback Louis #2).
    // Default AUS: Bedienband bleibt dauerhaft sichtbar; AN = bisheriges Cinema-Auto-Hide.
    val controlsAutoHide: Boolean = false,
    // Bildschirmhelligkeit (CEO-Beschluss 2026-06-07, wie Original-App):
    // -1 = System/automatisch, 5..100 = manuell (Window-Brightness, keine Spezial-Permission).
    val screenBrightness: Int = -1,
    // Auto-Reconnect W1 (nur Tablet/WiFi sichtbar): beim App-Start/Abriss automatisch mit
    // einer bekannten ONE wiederverbinden. Default AN.
    val autoConnectOne: Boolean = true,
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

    // === Welle l10n-anschluss Z-3/Z-6: Sprachpakete + Rueckfall-Diagnose ===

    private val localePackStore = com.uip.oneapp.ui.localization.LocalePackStore(context)

    /** Z-5: die sichtbare Liste (nur was das Portal fuehrt); Flaggen/Namen/Zustand/Groesse. */
    val availableLanguages: StateFlow<List<com.uip.oneapp.ui.localization.AppLanguage>> =
        com.uip.oneapp.ui.localization.LocalizationManager.availableLanguages

    private val _l10nBusyCode = MutableStateFlow<String?>(null)
    val l10nBusyCode: StateFlow<String?> = _l10nBusyCode.asStateFlow()

    private val _l10nDiagToEnglish = MutableStateFlow(0)
    val l10nDiagToEnglish: StateFlow<Int> = _l10nDiagToEnglish.asStateFlow()
    private val _l10nDiagToKeyName = MutableStateFlow(0)
    val l10nDiagToKeyName: StateFlow<Int> = _l10nDiagToKeyName.asStateFlow()
    val l10nMissingKeys: StateFlow<List<String>> = com.uip.oneapp.ui.localization.FallbackCounter.missingKeys

    /** Beim Betreten der Einstellungen und nach einem Sprachwechsel (E-P5): Zaehler spiegeln. */
    fun refreshL10nDiagnostics() {
        val en = com.uip.oneapp.ui.localization.FallbackCounter.toEnglishCount
        val key = com.uip.oneapp.ui.localization.FallbackCounter.toKeyNameCount
        _l10nDiagToEnglish.value = en
        _l10nDiagToKeyName.value = key
        viewModelScope.launch(NonCancellable) { persistL10nDiagnostic(en, key) }
    }

    /** Z-5: Portalliste neu abrufen (Betreten der Einstellungen, Pull-Aktion). */
    fun refreshLanguageList() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.uip.oneapp.ui.localization.LocalizationManager.refreshAvailableLanguages(context)
        }
    }

    fun loadLanguagePack(code: String) {
        if (code == "de" || code == "en") return // im Paket, kein Laden noetig
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _l10nBusyCode.value = code
            val meta = localePackStore.loadMeta(code)
            when (val result = com.uip.oneapp.network.l10n.L10nPortalClient().fetchBundle(code, etag = meta?.etag)) {
                is com.uip.oneapp.network.l10n.BundleResult.Ok -> {
                    localePackStore.save(
                        code, result.values,
                        com.uip.oneapp.ui.localization.LocalePackMeta(result.etag, result.lastModified, result.bytes, System.currentTimeMillis())
                    )
                    com.uip.oneapp.ui.localization.LocalizationManager.loadPack(code, result.values)
                }
                is com.uip.oneapp.network.l10n.BundleResult.NotModified -> {
                    localePackStore.load(code)?.let { com.uip.oneapp.ui.localization.LocalizationManager.loadPack(code, it) }
                }
                else -> { /* nicht erreichbar/nicht verfuegbar -- Zustand bleibt NOT_LOADED */ }
            }
            com.uip.oneapp.ui.localization.LocalizationManager.refreshAvailableLanguages(context)
            _l10nBusyCode.value = null
        }
    }

    fun refreshLanguagePack(code: String) = loadLanguagePack(code)

    fun deleteLanguagePack(code: String) {
        if (code == "de" || code == "en") return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            localePackStore.delete(code)
            com.uip.oneapp.ui.localization.LocalizationManager.unloadPack(code)
            com.uip.oneapp.ui.localization.LocalizationManager.refreshAvailableLanguages(context)
        }
    }

    // === Welle geraetezeit Z-1 / zeitseite-nachzug: Systemzeit stellen ===

    // Lazy: Robolectric-/Paparazzi-Tests, die das ViewModel nur erzeugen, fassen dadurch
    // keine Android-Dienste an — der Setter entsteht erst beim ersten Setz-Versuch.
    private val timeSetter by lazy { SystemTimeSetter(AndroidClockPort(context.applicationContext)) }

    private val _dateTimeResult = MutableStateFlow<SystemTimeSetter.Result?>(null)
    val dateTimeResult: StateFlow<SystemTimeSetter.Result?> = _dateTimeResult.asStateFlow()

    /**
     * Welle zeitseite-nachzug Z-1a: der Vorab-Dialog braucht Zone+Zeit+Automatik-Zustand,
     * um nach Bestaetigung erneut setzen zu koennen — vor dem Setzen erkannt (Precheck).
     */
    data class PendingAutoConsent(
        val epochMs: Long,
        val zoneId: String,
        val autoTime: Boolean?,
        val autoZone: Boolean?,
    )

    private val _pendingAutoConsent = MutableStateFlow<PendingAutoConsent?>(null)
    val pendingAutoConsent: StateFlow<PendingAutoConsent?> = _pendingAutoConsent.asStateFlow()

    /** Z-4 + PLAN_NACHTRAG B-2: letzter Aufruf-Datensatz, waechst NICHT — wird ersetzt. */
    private val _lastDiagnostics = MutableStateFlow<List<SystemTimeSetter.CallRecord>>(emptyList())
    val lastDiagnostics: StateFlow<List<SystemTimeSetter.CallRecord>> = _lastDiagnostics.asStateFlow()

    /** Z-1c „datetime_checking": Knopf gesperrt, solange die zweite Ruecklese laeuft (P-5 a). */
    private val _dateTimeBusy = MutableStateFlow(false)
    val dateTimeBusy: StateFlow<Boolean> = _dateTimeBusy.asStateFlow()

    init {
        // PLAN_NACHTRAG B-1: die zuletzt geschriebene Diagnose ueberlebt einen Neustart —
        // sie wird aus derselben dauerhaften Ablage vorbelegt, bevor ein neuer Versuch laeuft.
        viewModelScope.launch {
            loadPersistedDiagnostic()?.let { _lastDiagnostics.value = listOf(it) }
        }
    }

    /** Datum+Zeit+Zone uebernehmen — Zone vor Zeit erledigt setZoneAndTime (E-4). */
    fun applyDateTime(epochMs: Long, zoneId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            _dateTimeBusy.value = true
            try {
                runSetZoneAndTime(epochMs, zoneId)
            } finally {
                _dateTimeBusy.value = false
            }
        }
    }

    /** Vorab-Dialog bestaetigt (Z-1a): Automatik abschalten (Nachtrag 2 Punkt 5), dann erneut stellen. */
    fun confirmAutoConsent() {
        val pending = _pendingAutoConsent.value ?: return
        _pendingAutoConsent.value = null
        disableAutoTimeAndRetry(pending.epochMs, pending.zoneId)
    }

    /**
     * E-3: nachtraeglicher Dialog (Sicherheitsnetz) UND Z-1a-Vorab-Dialog rufen denselben
     * Ablauf — Automatik abschalten, dann erneut setZoneAndTime. Wird die Automatik zwischen
     * Abschalten und erneutem Precheck wieder aktiv (Rennlage), erscheint der Vorab-Dialog
     * erneut statt stillen Nichts-Tuns.
     */
    fun disableAutoTimeAndRetry(epochMs: Long, zoneId: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            _dateTimeBusy.value = true
            try {
                val disabled = timeSetter.disableAutoTime()
                _dateTimeResult.value = if (disabled is SystemTimeSetter.Result.Applied) {
                    when (val result = timeSetter.setZoneAndTime(zoneId, epochMs, ::recordDiagnostic)) {
                        is SystemTimeSetter.Result.NeedsConsent -> {
                            _pendingAutoConsent.value = PendingAutoConsent(epochMs, zoneId, result.autoTime, result.autoZone)
                            null
                        }
                        else -> result
                    }
                } else {
                    disabled
                }
            } finally {
                _dateTimeBusy.value = false
            }
        }
    }

    /** Vorab-Dialog abgebrochen (Z-1a): NICHT gesetzt — eigener Zweig statt Stille (B-6-Lehre). */
    fun cancelAutoConsent() {
        _pendingAutoConsent.value = null
        _dateTimeResult.value = SystemTimeSetter.Result.ConsentCancelled
    }

    private suspend fun runSetZoneAndTime(epochMs: Long, zoneId: String) {
        when (val result = timeSetter.setZoneAndTime(zoneId, epochMs, ::recordDiagnostic)) {
            is SystemTimeSetter.Result.NeedsConsent ->
                _pendingAutoConsent.value = PendingAutoConsent(epochMs, zoneId, result.autoTime, result.autoZone)
            else -> _dateTimeResult.value = result
        }
    }

    /**
     * PLAN_NACHTRAG B-2: laeuft bereits nach der ersten Ruecklese — ueberlebt Coroutine-Abbruch.
     * NACHBESSERUNG Runde 3, N-1: die dauerhafte Ablage lief bisher als Kind-Coroutine
     * derselben `viewModelScope`, die beim Raeumen des ViewModels abgebrochen wird — genau
     * die Scope, gegen deren Abbruch B-2 schuetzen sollte. `NonCancellable` loest den
     * Schreibvorgang strukturell von `viewModelScope` (kein Kind mehr, kein Abbruch bei
     * `onCleared()`); der Flow-Wert `_lastDiagnostics` bleibt weiterhin synchron gesetzt,
     * der Bildschirm zeigt die Zeile also unveraendert sofort.
     */
    internal fun recordDiagnostic(record: SystemTimeSetter.CallRecord) {
        _lastDiagnostics.value = listOf(record)
        viewModelScope.launch(NonCancellable) { persistDiagnostic(record) }
    }

    /** Snackbar angezeigt → Ergebnis zuruecknehmen, damit derselbe Zweig erneut feuern kann. */
    fun clearDateTimeResult() {
        _dateTimeResult.value = null
    }

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
        val KEY_CONTROLS_AUTO_HIDE = booleanPreferencesKey("controls_auto_hide")
        val KEY_SCREEN_BRIGHTNESS = intPreferencesKey("screen_brightness")
        // Auto-Reconnect W1 — auch vom OneAutoConnector (DI) gelesen.
        val KEY_AUTO_CONNECT_ONE = booleanPreferencesKey("auto_connect_one")

        // PLAN_NACHTRAG B-1 (Welle zeitseite-nachzug): EIN Datensatz, ueberschrieben je Lauf,
        // im selben preferencesDataStore wie alle uebrigen Einstellungen — keine neue Tabelle.
        // `?` fuer nicht lesbare Automatik-Werte, leerer String fuer eine noch fehlende zweite
        // Ruecklese (B-2), damit der Datensatz auch nach einem harten Neustart lesbar bleibt.
        private val KEY_DIAG_FUN = stringPreferencesKey("time_diag_fun")
        private val KEY_DIAG_REQUESTED = stringPreferencesKey("time_diag_requested")
        private val KEY_DIAG_READ1 = stringPreferencesKey("time_diag_read1")
        private val KEY_DIAG_READ2 = stringPreferencesKey("time_diag_read2")
        private val KEY_DIAG_AUTO_TIME = stringPreferencesKey("time_diag_auto_time")
        private val KEY_DIAG_AUTO_ZONE = stringPreferencesKey("time_diag_auto_zone")
        private val KEY_DIAG_RESULT = stringPreferencesKey("time_diag_result")
        private val KEY_DIAG_TIMESTAMP = stringPreferencesKey("time_diag_timestamp")
        private val KEY_L10N_DIAG_EN = intPreferencesKey("l10n_diag_to_english")
        private val KEY_L10N_DIAG_KEY = intPreferencesKey("l10n_diag_to_keyname")
    }

    /**
     * PLAN_NACHTRAG B-1: schreibt den aktuellen Diagnose-Datensatz dauerhaft, EIN Datensatz.
     * `internal` statt `private` einzig fuer den Testzugriff (N-2) — keine neue Einspeisestelle/
     * Schnittstelle, nur Sichtbarkeit innerhalb desselben Moduls (Testquellen sind Freund-Pfad).
     */
    internal suspend fun persistDiagnostic(record: SystemTimeSetter.CallRecord) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_DIAG_FUN] = record.funName
            prefs[KEY_DIAG_REQUESTED] = record.requested
            prefs[KEY_DIAG_READ1] = record.read1 ?: ""
            prefs[KEY_DIAG_READ2] = record.read2 ?: ""
            prefs[KEY_DIAG_AUTO_TIME] = record.autoTime?.toString() ?: "?"
            prefs[KEY_DIAG_AUTO_ZONE] = record.autoZone?.toString() ?: "?"
            prefs[KEY_DIAG_RESULT] = record.resultBranch
            prefs[KEY_DIAG_TIMESTAMP] = record.timestampMs.toString()
        }
    }

    /**
     * PLAN_NACHTRAG B-1: liest den zuletzt abgelegten Datensatz, `null` wenn noch keiner da ist.
     * `internal` statt `private` einzig fuer den Testzugriff (N-2), siehe [persistDiagnostic].
     */
    internal suspend fun loadPersistedDiagnostic(): SystemTimeSetter.CallRecord? {
        val prefs = context.settingsStore.data.first()
        val funName = prefs[KEY_DIAG_FUN] ?: return null
        fun tri(v: String?): Boolean? = when (v) {
            "true" -> true
            "false" -> false
            else -> null
        }
        return SystemTimeSetter.CallRecord(
            funName = funName,
            requested = prefs[KEY_DIAG_REQUESTED] ?: "",
            read1 = prefs[KEY_DIAG_READ1]?.takeIf { it.isNotEmpty() },
            read2 = prefs[KEY_DIAG_READ2]?.takeIf { it.isNotEmpty() },
            autoTime = tri(prefs[KEY_DIAG_AUTO_TIME]),
            autoZone = tri(prefs[KEY_DIAG_AUTO_ZONE]),
            resultBranch = prefs[KEY_DIAG_RESULT] ?: "",
            timestampMs = prefs[KEY_DIAG_TIMESTAMP]?.toLongOrNull() ?: 0L,
        )
    }

    /** Z-6 (E-P5): Diagnosezeile uebersteht einen Neustart, Muster Welle 28 (persistDiagnostic). */
    internal suspend fun persistL10nDiagnostic(toEnglish: Int, toKeyName: Int) {
        context.settingsStore.edit { prefs ->
            prefs[KEY_L10N_DIAG_EN] = toEnglish
            prefs[KEY_L10N_DIAG_KEY] = toKeyName
        }
    }

    init {
        viewModelScope.launch {
            val prefs = context.settingsStore.data.first()
            _l10nDiagToEnglish.value = prefs[KEY_L10N_DIAG_EN] ?: 0
            _l10nDiagToKeyName.value = prefs[KEY_L10N_DIAG_KEY] ?: 0
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
                controlsAutoHide = prefs[KEY_CONTROLS_AUTO_HIDE] ?: false,
                screenBrightness = prefs[KEY_SCREEN_BRIGHTNESS] ?: -1,
                autoConnectOne = prefs[KEY_AUTO_CONNECT_ONE] ?: true,
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

    fun updateControlsAutoHide(value: Boolean) {
        _uiState.value = _uiState.value.copy(controlsAutoHide = value)
        saveBool(KEY_CONTROLS_AUTO_HIDE, value)
    }

    /** Auto-Reconnect W1: automatisch mit bekannter ONE verbinden (Default AN). */
    fun updateAutoConnectOne(value: Boolean) {
        _uiState.value = _uiState.value.copy(autoConnectOne = value)
        saveBool(KEY_AUTO_CONNECT_ONE, value)
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
