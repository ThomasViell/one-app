package com.uip.oneapp.ui.localization

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Z-2: Zwischenspeicher fuer nachgeladene Sprachpakete (`filesDir/l10n/<code>.json`).
 * Ueberlebt Neustart, weil er auf dem Dateisystem liegt statt im Speicher (Muster der
 * Mai-Vorlage `drainq.one-localization`, PLAN.md 1.3 — Feld uebernommen). Flugmodus- und
 * Geraete-Ueberleben sind mit diesem Klassentest nicht hergestellt (kein Geraet, H-4).
 */
data class LocalePackMeta(
    val etag: String?,
    val lastModified: String?,
    val bytes: Int,
    val fetchedAt: Long
)

class LocalePackStore(private val context: Context) {

    private fun dir(): File = File(context.filesDir, "l10n").also { it.mkdirs() }
    private fun dataFile(code: String) = File(dir(), "$code.json")
    private fun metaFile(code: String) = File(dir(), "$code.meta.json")
    private fun localesFile() = File(dir(), "locales.json")

    fun save(code: String, values: Map<String, String>, meta: LocalePackMeta) {
        val obj = JSONObject()
        values.forEach { (k, v) -> obj.put(k, v) }
        dataFile(code).writeText(obj.toString(), Charsets.UTF_8)

        val metaObj = JSONObject()
        metaObj.put("etag", meta.etag ?: JSONObject.NULL)
        metaObj.put("lastModified", meta.lastModified ?: JSONObject.NULL)
        metaObj.put("bytes", meta.bytes)
        metaObj.put("fetchedAt", meta.fetchedAt)
        metaFile(code).writeText(metaObj.toString(), Charsets.UTF_8)
    }

    fun load(code: String): Map<String, String>? {
        val f = dataFile(code)
        if (!f.exists()) return null
        val obj = JSONObject(f.readText(Charsets.UTF_8))
        val result = mutableMapOf<String, String>()
        obj.keys().forEach { key -> result[key] = obj.getString(key) }
        return result
    }

    fun loadMeta(code: String): LocalePackMeta? {
        val f = metaFile(code)
        if (!f.exists()) return null
        val obj = JSONObject(f.readText(Charsets.UTF_8))
        return LocalePackMeta(
            etag = if (obj.isNull("etag")) null else obj.optString("etag"),
            lastModified = if (obj.isNull("lastModified")) null else obj.optString("lastModified"),
            bytes = obj.optInt("bytes", 0),
            fetchedAt = obj.optLong("fetchedAt", 0L)
        )
    }

    fun delete(code: String) {
        dataFile(code).delete()
        metaFile(code).delete()
    }

    fun listLoaded(): List<String> =
        dir().listFiles { f -> f.name.endsWith(".json") && !f.name.endsWith(".meta.json") && f.name != "locales.json" }
            ?.map { it.name.removeSuffix(".json") }
            ?.sorted()
            ?: emptyList()

    fun sizeOf(code: String): Long = dataFile(code).let { if (it.exists()) it.length() else 0L }

    fun saveLocalesList(codes: List<String>) {
        val arr = JSONArray()
        codes.forEach { arr.put(it) }
        localesFile().writeText(arr.toString(), Charsets.UTF_8)
    }

    fun loadLocalesList(): List<String>? {
        val f = localesFile()
        if (!f.exists()) return null
        val arr = JSONArray(f.readText(Charsets.UTF_8))
        return (0 until arr.length()).map { arr.getString(it) }
    }
}
