package com.uip.oneapp.ui.help

data class HelpElement(val id: String, val label: String, val text: String)

data class HelpScreen(
    val id: String,
    val route: String,
    val title: String,
    val intro: String,
    val screenshot: String,
    val elements: List<HelpElement>
)

class HelpRepository(private val context: android.content.Context) {

    // Cache per language to avoid re-parsing on every call
    private val textCache = mutableMapOf<String, Map<String, String>>()
    private val structCache = mutableMapOf<String, List<HelpScreen>>()

    fun getHelpForRoute(route: String, lang: String): HelpScreen? {
        val screens = getScreens(lang)
        // Parametrized routes: "inspection/123" matches "inspection/{id}"
        return screens.firstOrNull { screenMatchesRoute(it.route, route) }
    }

    private fun getScreens(lang: String): List<HelpScreen> {
        structCache[lang]?.let { return it }
        val texts = getTexts(lang)
        val fallback = if (lang != "de") getTexts("de") else emptyMap()

        val result = try {
            val json = context.assets.open("help/help_$lang.json").bufferedReader().readText()
            val obj = org.json.JSONObject(json)
            val arr = obj.getJSONArray("screens")
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                val elemArr = s.optJSONArray("elements") ?: org.json.JSONArray()
                HelpScreen(
                    id = s.getString("id"),
                    route = s.getString("route"),
                    title = resolveKey(s.getString("title"), texts, fallback),
                    intro = resolveKey(s.getString("intro"), texts, fallback),
                    screenshot = s.optString("screenshot", ""),
                    elements = (0 until elemArr.length()).map { j ->
                        val e = elemArr.getJSONObject(j)
                        HelpElement(
                            id = e.getString("id"),
                            label = resolveKey(e.getString("label"), texts, fallback),
                            text = resolveKey(e.getString("text"), texts, fallback),
                        )
                    }
                )
            }
        } catch (e: Exception) {
            if (lang != "de") getScreens("de") else emptyList()
        }
        structCache[lang] = result
        return result
    }

    private fun getTexts(lang: String): Map<String, String> {
        textCache[lang]?.let { return it }
        return try {
            val json = context.assets.open("i18n/$lang.json").bufferedReader().readText()
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key -> map[key] = obj.getString(key) }
            textCache[lang] = map
            map
        } catch (e: Exception) {
            emptyMap<String, String>().also { textCache[lang] = it }
        }
    }

    private fun resolveKey(key: String, texts: Map<String, String>, fallback: Map<String, String>): String =
        texts[key] ?: fallback[key] ?: key

    private fun screenMatchesRoute(pattern: String, actual: String): Boolean {
        if (pattern == actual) return true
        // Match parametrized: "inspection/{id}" matches "inspection/42"
        val patternParts = pattern.split("/")
        val actualParts = actual.split("/")
        if (patternParts.size != actualParts.size) return false
        return patternParts.zip(actualParts).all { (p, a) -> p.startsWith("{") || p == a }
    }
}
