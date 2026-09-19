package com.uip.oneapp.network.l10n

import com.uip.oneapp.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Z-1: Client fuer das Uebersetzungsportal (`license.drainq.com`).
 *
 * Nur ETag: `If-Modified-Since` wirkt am Portal nicht (Messung PLAN.md 1.1 — trotz
 * gleichem `Last-Modified` liefert es 200 statt 304; die Ursache liegt am Server,
 * die Zeile geht als H-1 in die Queue, nicht in diesen Client).
 */

sealed class LocalesResult {
    data class Ok(val locales: List<PortalLocale>) : LocalesResult()
    data class Unavailable(val reason: String) : LocalesResult()
}

data class PortalLocale(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val oneStatus: String,
    val bytesSize: Long
)

sealed class BundleResult {
    data class Ok(
        val values: Map<String, String>,
        val etag: String?,
        val lastModified: String?,
        val bytes: Int
    ) : BundleResult()
    object NotModified : BundleResult()
    object NotAvailable : BundleResult()
    data class Unavailable(val reason: String) : BundleResult()
}

class L10nPortalClient(
    private val baseUrl: String = BuildConfig.L10N_PORTAL_URL,
    httpClient: OkHttpClient? = null
) {
    private val client = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "DrainQ.ONE/${BuildConfig.VERSION_NAME}")
                    // E-P2/H-1-Familie, gemessen 19.09.2026: das Portal liefert fuer
                    // gzip-komprimierte Antworten einen SCHWACHEN ETag (W/"..."), der beim
                    // Rueckspielen per If-None-Match nicht als Treffer erkannt wird (curl-
                    // Gegenprobe: derselbe schwache ETag -> 200 statt 304; derselbe Wert ohne
                    // "W/" mit Accept-Encoding: identity -> 304, wie erwartet). OkHttp fordert
                    // Gzip sonst transparent an; "identity" erzwingt den starken ETag und macht
                    // den 304-Pfad wieder verlaesslich -- Client-seitige Umgehung eines
                    // Server-Fehlers, den `drainq.web` beheben muesste (QUEUE-Zeile).
                    .header("Accept-Encoding", "identity")
                    .build()
            )
        }
        .build()

    fun fetchLocales(): LocalesResult {
        val req = Request.Builder().url("$baseUrl/api/locales?app=one").build()
        return runCatchingIo({ LocalesResult.Unavailable(it) }) {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use LocalesResult.Unavailable("HTTP ${resp.code}")
                val body = resp.body?.string() ?: return@use LocalesResult.Unavailable("leere Antwort")
                val arr = JSONArray(body)
                val locales = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    PortalLocale(
                        code = o.getString("code"),
                        displayName = o.optString("displayName", o.getString("code")),
                        nativeName = o.optString("nativeName", o.getString("code")),
                        oneStatus = o.optString("oneStatus", ""),
                        bytesSize = o.optLong("bytesSize", 0L)
                    )
                }
                LocalesResult.Ok(locales)
            }
        }
    }

    fun fetchBundle(code: String, etag: String? = null, noCache: Boolean = false): BundleResult {
        val url = "$baseUrl/api/translations/$code.json?scope=one,shared"
        val builder = Request.Builder().url(url)
        if (etag != null) builder.header("If-None-Match", etag)
        if (noCache) builder.header("Cache-Control", "no-cache")
        return runCatchingIo({ BundleResult.Unavailable(it) }) {
            client.newCall(builder.build()).execute().use { resp ->
                when {
                    resp.code == 304 -> BundleResult.NotModified
                    resp.code == 404 -> BundleResult.NotAvailable
                    !resp.isSuccessful -> BundleResult.Unavailable("HTTP ${resp.code}")
                    else -> {
                        val body = resp.body?.string() ?: return@use BundleResult.Unavailable("leere Antwort")
                        val obj = JSONObject(body)
                        if (obj.has("error")) return@use BundleResult.Unavailable(obj.getString("error"))
                        val values = mutableMapOf<String, String>()
                        obj.keys().forEach { key -> values[key] = obj.getString(key) }
                        BundleResult.Ok(
                            values = values,
                            etag = resp.header("ETag"),
                            lastModified = resp.header("Last-Modified"),
                            bytes = body.toByteArray(Charsets.UTF_8).size
                        )
                    }
                }
            }
        }
    }

    /**
     * Nur fuer Messungen (Live-Test, S-12): scope=one statt scope=one,shared, damit sich
     * die Obermengen-Eigenschaft von E-P1 gegenpruefen laesst. Der Produktivpfad ruft
     * ausschliesslich [fetchBundle] auf.
     */
    fun fetchBundleScopeOne(code: String): BundleResult {
        val url = "$baseUrl/api/translations/$code.json?scope=one"
        return runCatchingIo({ BundleResult.Unavailable(it) }) {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use BundleResult.Unavailable("HTTP ${resp.code}")
                val body = resp.body?.string() ?: return@use BundleResult.Unavailable("leere Antwort")
                val obj = JSONObject(body)
                val values = mutableMapOf<String, String>()
                obj.keys().forEach { key -> values[key] = obj.getString(key) }
                BundleResult.Ok(values, resp.header("ETag"), resp.header("Last-Modified"), body.length)
            }
        }
    }

    private inline fun <T> runCatchingIo(onError: (String) -> T, block: () -> T): T =
        try {
            block()
        } catch (e: IOException) {
            onError(e.message ?: "network error")
        } catch (e: Exception) {
            onError(e.message ?: "parse error")
        }
}
