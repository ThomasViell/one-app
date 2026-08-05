package com.uip.oneapp.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.uip.oneapp.data.repository.ProjectRepository
import com.uip.oneapp.data.repository.WeatherPresetRepository
import com.uip.oneapp.network.LocationService
import com.uip.oneapp.network.NominatimService
import com.uip.oneapp.network.OsmStaticMapService
import com.uip.oneapp.network.WeatherApiService
import com.uip.oneapp.ui.screens.projects.ProjectFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Fix Datumsdrift (05.08.2026): ScreenshotTestModule überschreibt den Koin-Single für
 * java.time.Clock mit einem festen Datum, damit die Handbuch-Screenshots nicht mehr mit
 * dem tatsächlichen Testlauf-Tag driften. Dieser Test belegt, dass der PRODUKTIONSPFAD davon
 * unberührt ist: ohne einen explizit übergebenen Clock (genau wie in AppModule, wo Koin den
 * Clock-Single auf Clock.systemDefaultZone() registriert) füllt ProjectFormViewModel das
 * Inspektionsdatum weiterhin mit dem echten heutigen Datum vor.
 */
class ProjectFormViewModelDefaultClockTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `inspektionsdatum ist ohne injizierten Clock das echte heutige Datum`() {
        val viewModel = ProjectFormViewModel(
            repository = ProjectRepository(FakeProjectDao()),
            weatherPresetRepository = WeatherPresetRepository(paparazzi.context),
            weatherApiService = WeatherApiService(),
            locationService = LocationService(paparazzi.context),
            nominatimService = NominatimService(),
            osmMapService = OsmStaticMapService()
            // clock bewusst NICHT übergeben — testet den Default-Parameter Clock.systemDefaultZone(),
            // exakt der Pfad, den AppModule.kt in der echten App verwendet.
        )

        val expected = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        assertEquals(expected, viewModel.inspektionsdatum)
    }
}
