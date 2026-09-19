package com.uip.oneapp.ui.localization

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * N-1 (Runde 2, B-1): ein einmal geladenes Sprachpaket steht nach einem Prozessneustart
 * wieder in der Nachschlagekette -- ohne Nutzeraktion und ohne Netz. "Neustart" heisst hier:
 * der Speicherzustand des Managers wird geleert, nur die Ablage (LocalePackStore) bleibt.
 * Echter Geraeteneustart und Flugmodus sind damit nicht hergestellt (H-4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LocalizationManagerRestartTest {

    private val code = "zz"
    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun tearDown() {
        LocalePackStore(context()).delete(code)
        LocalizationManager.clearPack(code)
    }

    @Test
    fun storedPack_isBackInChainAfterProcessRestart() {
        LocalePackStore(context()).save(
            code, mapOf("restart_probe" to "Paketwert"), LocalePackMeta("\"e\"", null, 1, 0L)
        )
        LocalizationManager.clearPack(code) // frischer Prozess: nichts im Speicher

        LocalizationManager.init(context(), refreshPortal = false)

        assertEquals("Paketwert", LocalizationManager.getString("restart_probe", code))
    }

    @Test
    fun startWithStoredPack_needsNoNetworkAndIsFast() {
        LocalePackStore(context()).save(
            code, (0 until 500).associate { "k$it" to "v$it" }, LocalePackMeta(null, null, 1, 0L)
        )
        LocalizationManager.clearPack(code)

        val t0 = System.nanoTime()
        LocalizationManager.restoreStoredPacks(context())
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        println("N1_RESTORE_MS=$ms")

        assertEquals("v499", LocalizationManager.getString("k499", code))
        assertTrue("Wiedereinhaengen darf den Start nicht spuerbar bremsen (H-5, 50 ms): $ms ms", ms < 50.0)
    }
}
