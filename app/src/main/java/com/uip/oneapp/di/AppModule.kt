package com.uip.oneapp.di

import android.app.Application
import com.uip.oneapp.data.local.AppDatabase
import com.uip.oneapp.data.repository.DamageRepository
import com.uip.oneapp.data.repository.InspectionRepository
import com.uip.oneapp.data.repository.NoteRepository
import com.uip.oneapp.data.repository.PipeRepository
import com.uip.oneapp.data.repository.ProjectRepository
import com.uip.oneapp.data.repository.DamagePresetRepository
import com.uip.oneapp.data.repository.WeatherPresetRepository
import androidx.datastore.preferences.core.stringPreferencesKey
import com.uip.oneapp.export.ProjectExportService
import com.uip.oneapp.network.DeviceType
import com.uip.oneapp.network.HardwareService
import com.uip.oneapp.network.LocationService
import com.uip.oneapp.network.NetworkDiscoveryService
import com.uip.oneapp.network.OneHardwareService
import com.uip.oneapp.network.RtspStreamTester
import com.uip.oneapp.network.NominatimService
import com.uip.oneapp.network.OsmStaticMapService
import com.uip.oneapp.network.TwoHardwareConfig
import com.uip.oneapp.network.TwoHardwareService
import com.uip.oneapp.network.WeatherApiService
import com.uip.oneapp.ui.screens.settings.settingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.uip.oneapp.ui.screens.connection.ConnectionViewModel
import com.uip.oneapp.ui.screens.projectdetail.ProjectDetailViewModel
import com.uip.oneapp.ui.screens.projects.ProjectFormViewModel
import com.uip.oneapp.ui.screens.projects.ProjectsViewModel
import com.uip.oneapp.ui.screens.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { NetworkDiscoveryService(androidContext()) }
    single { RtspStreamTester() }
    single<HardwareService> {
        val context = androidContext()
        val prefs = runBlocking { context.settingsStore.data.first() }
        val deviceType = try {
            DeviceType.valueOf(prefs[stringPreferencesKey("device_type")] ?: "ONE")
        } catch (_: Exception) { DeviceType.ONE }

        when (deviceType) {
            DeviceType.ONE -> OneHardwareService()
            DeviceType.TWO -> {
                val cameraIp = prefs[stringPreferencesKey("two_camera_ip")] ?: TwoHardwareConfig().cameraIp
                val cameraUser = prefs[stringPreferencesKey("two_camera_user")] ?: TwoHardwareConfig().cameraUser
                val cameraPassword = prefs[stringPreferencesKey("two_camera_password")] ?: TwoHardwareConfig().cameraPassword
                TwoHardwareService(
                    TwoHardwareConfig(
                        cameraIp = cameraIp,
                        cameraUser = cameraUser,
                        cameraPassword = cameraPassword
                    )
                )
            }
        }
    }

    // Database
    single { AppDatabase.create(androidContext()) }
    single { get<AppDatabase>().projectDao() }
    single { get<AppDatabase>().damageDao() }
    single { get<AppDatabase>().noteDao() }
    single { get<AppDatabase>().pipeDao() }
    single { get<AppDatabase>().inspectionDao() }
    single { ProjectRepository(get()) }
    single { DamageRepository(get()) }
    single { NoteRepository(get()) }
    single { PipeRepository(get()) }
    single { InspectionRepository(get()) }

    // Export
    single { ProjectExportService(androidContext()) }

    // Weather & Damage Presets
    single { WeatherPresetRepository(androidContext()) }
    single { DamagePresetRepository(androidContext()) }
    single { WeatherApiService() }
    single { LocationService(androidContext()) }
    single { NominatimService() }
    single { OsmStaticMapService() }

    viewModel { ConnectionViewModel(get(), get(), get(), androidContext()) }
    viewModel { SettingsViewModel(androidContext(), get(), get()) }
    viewModel { ProjectFormViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { ProjectsViewModel(get()) }
    viewModel { ProjectDetailViewModel(get(), get(), get(), get(), androidContext() as Application) }
}
