package com.uip.oneapp.update

import android.content.Context
import com.google.gson.Gson
import com.uip.oneapp.BuildConfig
import com.uip.oneapp.data.local.entity.UpdateEventType
import com.uip.oneapp.data.repository.UpdateEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class HttpUpdateService(
    private val context: Context?,
    val config: UpdateConfig,
    private val installer: UpdateInstaller,
    private val auditLog: UpdateEventRepository,
    private val installedVersionCode: Int = BuildConfig.VERSION_CODE,
    private val installedVersionName: String = BuildConfig.VERSION_NAME,
    httpClient: OkHttpClient? = null
) : UpdateService {

    private val client = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (config.mode == "disabled") return@withContext UpdateCheckResult.NotConfigured

        val url = config.manifestUrl
        auditLog.log(
            type = UpdateEventType.CHECK,
            fromVersion = installedVersionName,
            source = url
        )
        auditLog.pruneOldEvents()

        val manifest = try {
            fetchManifest(url)
        } catch (e: Exception) {
            auditLog.log(
                type = UpdateEventType.DOWNLOAD_FAIL,
                fromVersion = installedVersionName,
                source = url,
                errorMessage = "manifest fetch failed: ${e.message}"
            )
            return@withContext UpdateCheckResult.Error(e.message ?: "network error")
        }

        if (manifest == null) return@withContext UpdateCheckResult.NotConfigured

        return@withContext if (manifest.latest.versionCode > installedVersionCode &&
            manifest.latest.minSdk <= android.os.Build.VERSION.SDK_INT
        ) {
            UpdateCheckResult.Available(manifest.latest)
        } else {
            UpdateCheckResult.NoUpdate
        }
    }

    override suspend fun downloadAndInstall(release: ReleaseInfo): Unit = withContext(Dispatchers.IO) {
        auditLog.log(
            type = UpdateEventType.DOWNLOAD_START,
            fromVersion = installedVersionName,
            toVersion = release.version,
            source = config.manifestUrl
        )

        val apkFile = File(installer.cacheDir(), "drainq-one-${release.version}.apk")

        try {
            download(release.url, apkFile, release.size)
        } catch (e: Exception) {
            apkFile.delete()
            auditLog.log(
                type = UpdateEventType.DOWNLOAD_FAIL,
                fromVersion = installedVersionName,
                toVersion = release.version,
                source = config.manifestUrl,
                errorMessage = e.message
            )
            throw e
        }

        val actualHash = sha256Hex(apkFile)
        if (!actualHash.equals(release.sha256, ignoreCase = true)) {
            apkFile.delete()
            val msg = "SHA256 mismatch: expected=${release.sha256} actual=$actualHash"
            auditLog.log(
                type = UpdateEventType.DOWNLOAD_FAIL,
                fromVersion = installedVersionName,
                toVersion = release.version,
                source = config.manifestUrl,
                errorMessage = msg
            )
            throw SecurityException(msg)
        }

        auditLog.log(
            type = UpdateEventType.DOWNLOAD_OK,
            fromVersion = installedVersionName,
            toVersion = release.version,
            source = config.manifestUrl
        )
        auditLog.log(
            type = UpdateEventType.INSTALL_INITIATED,
            fromVersion = installedVersionName,
            toVersion = release.version,
            source = config.manifestUrl
        )

        try {
            installer.install(apkFile)
            auditLog.log(
                type = UpdateEventType.INSTALL_DONE,
                fromVersion = installedVersionName,
                toVersion = release.version,
                source = config.manifestUrl
            )
        } catch (e: Exception) {
            auditLog.log(
                type = UpdateEventType.DOWNLOAD_FAIL,
                fromVersion = installedVersionName,
                toVersion = release.version,
                source = config.manifestUrl,
                errorMessage = "install failed: ${e.message}"
            )
            throw e
        }
    }

    private fun fetchManifest(url: String): ReleaseManifest? {
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute()
        if (resp.code == 404) return null
        if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code} fetching manifest")
        val body = resp.body?.string() ?: return null
        return gson.fromJson(body, ReleaseManifest::class.java)
    }

    private fun download(url: String, dest: File, expectedSize: Long) {
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code} downloading APK")

        val body = resp.body ?: throw RuntimeException("Empty body downloading APK")
        val total = if (expectedSize > 0) expectedSize else body.contentLength()

        dest.outputStream().use { out ->
            body.byteStream().use { inp ->
                val buf = ByteArray(8192)
                var n: Int
                while (inp.read(buf).also { n = it } >= 0) {
                    out.write(buf, 0, n)
                }
            }
        }
        if (total > 0 && dest.length() != total) {
            throw RuntimeException("Download incomplete: got ${dest.length()} expected $total bytes")
        }
    }

    companion object {
        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buf = ByteArray(8192)
                var n: Int
                while (stream.read(buf).also { n = it } >= 0) {
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
