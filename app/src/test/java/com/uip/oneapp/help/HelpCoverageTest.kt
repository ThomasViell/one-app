package com.uip.oneapp.help

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * W-H5 Phase 1: Coverage-Gate — Build bricht ohne vollständige Hilfe-Dokumentation.
 *
 * Quelle der Wahrheit: tools/manual/scenes.json (die 20 deklarierten Szenen).
 * Für jede Szene (außer Ausnahmen) muss gelten:
 *   1. Eintrag in assets/help/help_de.json (Baustein vorhanden)
 *   2. Eintrag in assets/help/help_en.json (Baustein vorhanden)
 *   3. Alle referenzierten help.*-Keys in assets/i18n/de.json UND en.json vorhanden und nicht leer.
 */
class HelpCoverageTest {

    // Findet den Projekt-Root (das Verzeichnis, das gradlew.bat enthält).
    // Gradle-Tests laufen mit CWD = Modul-Root (app/); wir steigen maximal 3 Ebenen auf.
    private fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(4) {
            if (File(dir, "gradlew.bat").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }

    private fun readText(relPath: String): String {
        val f = File(projectRoot(), relPath)
        assertTrue("Datei nicht gefunden: $relPath (Root: ${projectRoot()})", f.exists())
        return f.readText(Charsets.UTF_8)
    }

    private fun json(relPath: String) = JSONObject(readText(relPath))

    @Test
    fun helpCoverage_allScenesHaveHelpBausteinAndKeys() {
        val scenesJson   = json("tools/manual/scenes.json")
        val helpDe       = json("app/src/main/assets/help/help_de.json")
        val helpEn       = json("app/src/main/assets/help/help_en.json")
        val deI18n       = json("app/src/main/assets/i18n/de.json")
        val enI18n       = json("app/src/main/assets/i18n/en.json")

        val deScreens    = buildScreenIndex(helpDe.getJSONArray("screens"))
        val enScreens    = buildScreenIndex(helpEn.getJSONArray("screens"))
        val deKeys       = keySet(deI18n)
        val enKeys       = keySet(enI18n)

        // scr01_splash hat kein NavGraph-Ziel; per adb nicht isoliert ansteuerbar (PLAN §W-H2).
        val exemptions = setOf("scr01_splash")

        val failures = mutableListOf<String>()
        val scenesArray = scenesJson.getJSONArray("scenes")

        for (i in 0 until scenesArray.length()) {
            val sceneId = scenesArray.getJSONObject(i).getString("name")
            if (sceneId in exemptions) continue

            val deBaustein = deScreens[sceneId]
            if (deBaustein == null) {
                failures += "[$sceneId] Kein Baustein in help_de.json"
                continue
            }
            if (!enScreens.containsKey(sceneId)) {
                failures += "[$sceneId] Kein Baustein in help_en.json"
                continue
            }

            for (key in collectHelpKeys(deBaustein)) {
                if (!key.startsWith("help.")) continue
                if (!deKeys.contains(key) || deI18n.optString(key, "").isBlank())
                    failures += "[$sceneId] DE-Key fehlt/leer: $key"
                if (!enKeys.contains(key) || enI18n.optString(key, "").isBlank())
                    failures += "[$sceneId] EN-Key fehlt/leer: $key"
            }
        }

        if (failures.isNotEmpty()) {
            fail("HelpCoverage FAIL — ${failures.size} Problem(e):\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun negativprobe_fakeSceneMissingFromHelp_mustFail() {
        // Beweist, dass das Gate wirklich anschlägt, wenn eine Szene keinen Baustein hat.
        val fakeScenes = JSONObject(
            """{"schema":1,"scenes":[{"name":"fake_scene_xyz_W_H5","route":"fake"}]}"""
        )
        val emptyHelp = JSONObject("""{"schema":1,"screens":[]}""")
        val deScreens = buildScreenIndex(emptyHelp.getJSONArray("screens"))

        val failures = mutableListOf<String>()
        val arr = fakeScenes.getJSONArray("scenes")
        for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).getString("name")
            if (!deScreens.containsKey(id)) failures += "[$id] Kein Baustein in help_de.json"
        }

        assertTrue(
            "NEGATIVPROBE: Fake-Szene muss Coverage-Failure auslösen (Gate wäre nicht blockierend!)",
            failures.isNotEmpty()
        )
        println("NEGATIVPROBE-BEWEIS (W-H5): ${failures.joinToString()}")
    }

    // ── Hilfsfunktionen ─────────────────────────────────────────────────────────

    private fun buildScreenIndex(arr: JSONArray): Map<String, JSONObject> =
        (0 until arr.length()).associate { i ->
            val s = arr.getJSONObject(i)
            s.getString("id") to s
        }

    private fun keySet(obj: JSONObject): Set<String> = buildSet {
        val it = obj.keys(); while (it.hasNext()) add(it.next())
    }

    private fun collectHelpKeys(screen: JSONObject): List<String> = buildList {
        screen.optString("title",  "").takeIf { it.startsWith("help.") }?.let { add(it) }
        screen.optString("intro",  "").takeIf { it.startsWith("help.") }?.let { add(it) }
        screen.optJSONArray("elements")?.let { elems ->
            for (i in 0 until elems.length()) {
                val e = elems.getJSONObject(i)
                e.optString("label", "").takeIf { it.startsWith("help.") }?.let { add(it) }
                e.optString("text",  "").takeIf { it.startsWith("help.") }?.let { add(it) }
            }
        }
    }
}
