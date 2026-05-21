package com.uip.oneapp.network.l10n

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "L10nApi"
private const val MAX_RETRIES = 2

data class LocaleInfo(
    val code: String,
    val displayName: String,
    val bytesSize: Long
)

data class TranslationsResponse(
    val translations: Map<String, String>,
    val lastModified: String?
)

class L10nApi(private val portalBaseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getLocales(): List<LocaleInfo>? {
        val url = "${portalBaseUrl.trimEnd('/')}/api/locales?app=one"
        val json = getWithRetry(url) ?: return null
        return parseLocales(json)
    }

    fun getTranslations(localeCode: String, ifModifiedSince: String? = null): TranslationsResponse? {
        val url = "${portalBaseUrl.trimEnd('/')}/api/translations/$localeCode.json?scope=one,shared"
        return getTranslationsWithEtag(url, ifModifiedSince)
    }

    private fun getWithRetry(url: String, headers: Map<String, String> = emptyMap()): String? {
        repeat(MAX_RETRIES + 1) { attempt ->
            runCatching {
                val reqBuilder = Request.Builder().url(url)
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                val response = client.newCall(reqBuilder.build()).execute()
                if (response.isSuccessful) return response.body?.string()
                if (response.code in 400..499) return null
            }.getOrElse { e ->
                Log.w(TAG, "attempt $attempt for $url failed: ${e.message}")
            }
        }
        return null
    }

    private fun getTranslationsWithEtag(url: String, ifModifiedSince: String?): TranslationsResponse? {
        repeat(MAX_RETRIES + 1) { attempt ->
            runCatching {
                val reqBuilder = Request.Builder().url(url)
                if (ifModifiedSince != null) reqBuilder.header("If-Modified-Since", ifModifiedSince)
                val response = client.newCall(reqBuilder.build()).execute()
                when (response.code) {
                    304 -> return TranslationsResponse(emptyMap(), null)
                    in 200..299 -> {
                        val body = response.body?.string() ?: return null
                        val lastMod = response.header("Last-Modified")
                        val map = parseTranslations(body)
                        return TranslationsResponse(map, lastMod)
                    }
                    in 400..499 -> return null
                    else -> Unit
                }
            }.getOrElse { e ->
                Log.w(TAG, "attempt $attempt for $url failed: ${e.message}")
            }
        }
        return null
    }

    private fun parseLocales(json: String): List<LocaleInfo> {
        return runCatching {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                LocaleInfo(
                    code = obj.getString("code"),
                    displayName = obj.optString("displayName", obj.getString("code")),
                    bytesSize = obj.optLong("bytesSize", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun parseTranslations(json: String): Map<String, String> {
        return runCatching {
            val obj = org.json.JSONObject(json)
            buildMap { obj.keys().forEach { k -> put(k, obj.getString(k)) } }
        }.getOrDefault(emptyMap())
    }
}
