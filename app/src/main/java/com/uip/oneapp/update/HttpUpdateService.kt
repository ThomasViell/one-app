package com.uip.oneapp.update

import android.content.Context
import com.google.gson.Gson
import com.uip.oneapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class HttpUpdateService(
    private val context: Context,
    private val config: UpdateConfig,
    private val installer: UpdateInstaller,
    // Injected for testing; defaults to the real installed versionCode at runtime
    private val installedVersionCode: Int = BuildConfig.VERSION_CODE
) : UpdateService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val _events = MutableStateFlow<List<UpdateEvent>>(emptyList())

    override fun getUpdateEvents(): Flow<List<UpdateEvent>> = _events.asStateFlow()

    override suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (config.mode == "disabled") return@withContext UpdateCheckResult.NotConfigured

        emit(UpdateEventType.CHECK, "Checking ${config.manifestUrl}")

        val manifest = try {
            fetchManifest(config.manifestUrl)
        } catch (e: Exception) {
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
        emit(UpdateEventType.DOWNLOAD_START, "Downloading ${release.version}")

        val apkFile = File(installer.cacheDir(), "drainq-one-${release.version}.apk")

        try {
            download(release.url, apkFile, release.size)
        } catch (e: Exception) {
            emit(UpdateEventType.DOWNLOAD_FAIL, e.message ?: "download failed")
            throw e
        }

        val actualHash = sha256Hex(apkFile)
        if (!actualHash.equals(release.sha256, ignoreCase = true)) {
            apkFile.delete()
            emit(UpdateEventType.DOWNLOAD_FAIL, "SHA256 mismatch: expected=${release.sha256} actual=$actualHash")
            throw SecurityException("SHA256 mismatch for ${release.version}")
        }

        emit(UpdateEventType.DOWNLOAD_OK, "Verified ${release.version}")
        emit(UpdateEventType.INSTALL_INITIATED, "Installing ${release.version}")

        try {
            installer.install(apkFile)
            emit(UpdateEventType.INSTALL_DONE, "Install session committed for ${release.version}")
        } catch (e: Exception) {
            emit(UpdateEventType.DOWNLOAD_FAIL, "Install failed: ${e.message}")
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
                var downloaded = 0L
                var n: Int
                while (inp.read(buf).also { n = it } >= 0) {
                    out.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) {
                        emit(UpdateEventType.DOWNLOAD_PROGRESS, "$downloaded/$total")
                    }
                }
            }
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

    private fun emit(type: UpdateEventType, message: String) {
        val event = UpdateEvent(System.currentTimeMillis(), type, message)
        _events.value = _events.value + event
    }
}
