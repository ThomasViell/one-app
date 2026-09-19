package com.uip.oneapp.data.repository

import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.uip.oneapp.ui.localization.LocalizationManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Z-8 (RB-6): Standard-Schadensbezeichnungen muessen der Sprache folgen, vom Anwender
 * geaenderte oder ergaenzte Bezeichnungen bleiben stehen (AUFTRAG.md Abschnitt 3, Ziel Z-8).
 * `DamagePresetRepository.load` schrieb bisher beim ersten Start `getDefaultPresets()` als
 * **Texte** in den DataStore und las danach nur noch Texte -- ein Sprachwechsel aenderte
 * `presets` nicht (PLAN.md 1.4). Rot-zuerst gegen den Ausgangskopf.
 *
 * Jeder Test bekommt einen eigenen, frischen DataStore-Dateipfad (`DamagePresetRepository`,
 * Test-Konstruktor mit `testStoreName`) statt der App-weit einmaligen `context.damageStore`
 * -- reine Isolation zwischen Testmethoden (ihre Zustaende duerfen sich nicht vermischen).
 *
 * Root-Cause-Fund (Regel 5): der Migrationstest legte zusaetzlich eine ZWEITE, unabhaengige
 * `DataStore`-Instanz auf demselben Dateipfad an, um Altbestand vorzuschreiben (ueber einen
 * eigenen `PreferenceDataStoreFactory.create(...)`-Aufruf) -- genau die von androidx DataStore
 * verbotene Konstellation ("multiple instances of DataStore for this file",
 * SingleProcessDataStore.kt:433), die den Testlauf reproduzierbar zum Haengen brachte, auch
 * einzeln ausgefuehrt. Fix: `DamagePresetRepository.seedRawForTest` schreibt ueber die EINE
 * Instanz, die das Repository selbst besitzt (siehe dort). Seitdem laeuft die Klasse
 * zuverlaessig im vollen Testklassenlauf gruen (`belege/rb6_gruen_voll.txt`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DamagePresetRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val gson = Gson()
    private var counter = 0
    private var activeRepo: DamagePresetRepository? = null

    @After
    fun tearDown() {
        activeRepo?.close()
        LocalizationManager.setLanguage(context, "de")
    }

    private fun uniqueStoreName(): String = "test_damage_presets_${System.nanoTime()}_${counter++}"

    private fun newRepo(storeName: String = uniqueStoreName()): DamagePresetRepository =
        DamagePresetRepository(context, storeName).also { activeRepo = it }

    private fun setLangSync(lang: String) {
        // Muster ManualScreenshotTest: setLanguage() setzt den StateFlow synchron.
        LocalizationManager.setLanguage(context, lang)
    }

    @Test
    fun defaults_followLanguageChange() = runBlocking {
        setLangSync("de")
        val repo = newRepo()
        val firstDe = repo.presets.first { it.isNotEmpty() }
        assertEquals(LocalizationManager.getString("damage_type_crack", "de"), firstDe[0])

        setLangSync("en")
        val firstEn = repo.presets.first { it.isNotEmpty() && it[0] != firstDe[0] }
        assertEquals(
            "Standardbezeichnung muss dem Sprachwechsel folgen (Z-8)",
            LocalizationManager.getString("damage_type_crack", "en"),
            firstEn[0]
        )
    }

    @Test
    fun defaults_afterSaveStillFollowLanguage() = runBlocking {
        setLangSync("de")
        val repo = newRepo()
        repo.presets.first { it.isNotEmpty() }
        repo.addPreset("Eigen")
        val afterAdd = repo.presets.first { it.contains("Eigen") }
        val crackIndex = afterAdd.indexOf(LocalizationManager.getString("damage_type_crack", "de"))
        assertTrue("Standardbezeichnung muss nach dem ersten save() noch vorhanden sein", crackIndex >= 0)

        setLangSync("en")
        val afterLangChange = repo.presets.first { it.contains("Eigen") && it.getOrNull(crackIndex) != afterAdd[crackIndex] }
        assertEquals(
            "Standardbezeichnung muss auch nach einem save() der Sprache folgen (Z-8)",
            LocalizationManager.getString("damage_type_crack", "en"),
            afterLangChange[crackIndex]
        )
        assertTrue("Eigene Bezeichnung bleibt stehen", afterLangChange.contains("Eigen"))
    }

    @Test
    fun customAndEditedEntries_stayVerbatim() = runBlocking {
        // Sicherung: muss vorher wie nachher gruen sein.
        setLangSync("de")
        val repo = newRepo()
        repo.presets.first { it.isNotEmpty() }
        repo.updatePreset(0, "Mein Riss")
        val afterUpdate = repo.presets.first { it.getOrNull(0) == "Mein Riss" }
        assertEquals("Mein Riss", afterUpdate[0])

        setLangSync("en")
        val afterLangChange = repo.presets.first { it.isNotEmpty() }
        assertEquals("Editierter Text darf sich beim Sprachwechsel nicht aendern", "Mein Riss", afterLangChange[0])
    }

    @Test
    fun legacyTextList_isMigratedToKeys() = runBlocking {
        setLangSync("de")
        val legacyList = listOf(
            LocalizationManager.getString("damage_type_crack", "de"),
            LocalizationManager.getString("damage_type_fracture", "de"),
            LocalizationManager.getString("damage_type_roots", "de"),
            LocalizationManager.getString("damage_type_deposit", "de"),
            LocalizationManager.getString("damage_type_blockage", "de"),
            LocalizationManager.getString("damage_type_sag", "de"),
            LocalizationManager.getString("damage_type_other", "de"),
            "Eigen"
        )
        val repo = newRepo()
        repo.presets.first { it.isNotEmpty() } // erster (leerer Alt-)Zustand ist geladen
        repo.seedRawForTest(gson.toJson(legacyList))
        val loaded = repo.presets.first { it.size == legacyList.size }
        assertEquals(legacyList, loaded)

        setLangSync("en")
        val afterLangChange = repo.presets.first { it.size == legacyList.size && it[0] != legacyList[0] }
        assertEquals(LocalizationManager.getString("damage_type_crack", "en"), afterLangChange[0])
        assertTrue("Eigen-Eintrag bleibt aus der Migration unveraendert", afterLangChange.contains("Eigen"))
    }

    // A-3 (Z-1, Welle l10n-auflagen): ein beschaedigter oder unbekannter Preset-Bestand muss
    // auf die Standardliste zurueckfallen statt abzustuerzen. Die vom PLAN 4.1 vorgesehene
    // Form (addPreset + Warten + seedRawForTest) ist auf dieser Maschine nicht herstellbar:
    // unter Robolectric/Windows blockiert DataStore 1.0.0 jedes ZWEITE Schreiben auf
    // dieselbe Datei ("Unable to rename", belege/h1_platform_sonde.txt, H-1). Ersatz mit
    // derselben Aussage: seedUserStateInMemoryForTest setzt die Nutzerliste (Standardwerte
    // + "Eigen") im Speicher, ohne zu persistieren; seedRawForTest bleibt das einzige
    // Store-Schreiben und laedt neu -- nur so ist belegt, dass der Rueckfall wirklich die
    // Standardliste setzt und nicht bloss den vorherigen Zustand stehen laesst.
    // Rot-Beweis: am Ausgangskopf 24a18ee wirft load() die Ausnahme ungefangen bis in den
    // Test (seedRawForTest ruft load() direkt) -- belege/z1_rot_raw.txt.
    private suspend fun assertCorruptStoredFallsBack(repo: DamagePresetRepository, corrupt: String) {
        repo.presets.first { it.isNotEmpty() } // erster (leerer Alt-)Zustand ist geladen
        repo.seedUserStateInMemoryForTest(listOf("Eigen"))
        repo.seedRawForTest(corrupt)
        val loaded = repo.presets.first { it == DamagePresetRepository.getDefaultPresets() }
        assertEquals(DamagePresetRepository.getDefaultPresets(), loaded)
        assertTrue("Rueckfall darf die Nutzerliste nicht behalten", "Eigen" !in loaded)
    }

    @Test
    fun corruptStored_truncatedJson_fallsBackToDefaults() = runBlocking {
        setLangSync("de")
        assertCorruptStoredFallsBack(newRepo(), "{\"v\":2,\"entries\":[")
    }

    @Test
    fun corruptStored_missingEntries_fallsBackToDefaults() = runBlocking {
        setLangSync("de")
        assertCorruptStoredFallsBack(newRepo(), "{\"v\":2}")
    }

    @Test
    fun corruptStored_legacyNonStrings_fallsBackToDefaults() = runBlocking {
        setLangSync("de")
        assertCorruptStoredFallsBack(newRepo(), "[{\"a\":1}]")
    }
}
