package com.uip.oneapp.update

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

// Use plain Application to prevent OneApp from starting Koin during Robolectric setup.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpdateServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var config: UpdateConfig
    private lateinit var installer: UpdateInstaller

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        context = ApplicationProvider.getApplicationContext()
        config = UpdateConfig(context)
        // Point config at MockWebServer
        config.overrideProxyUrl(server.url("/").toString())
        installer = UpdateInstaller(context)
    }

    @After
    fun tearDown() {
        server.shutdown()
        config.reset()
        runCatching { stopKoin() }
    }

    // ── versionCode comparison ────────────────────────────────────────────────

    @Test
    fun `lower versionCode in manifest returns NoUpdate`() = runBlocking {
        val installedCode = 999
        server.enqueue(MockResponse().setBody(manifestJson(versionCode = installedCode - 1)))
        val svc = HttpUpdateService(context, config, installer, installedVersionCode = installedCode)
        val result = svc.checkForUpdate()
        assertTrue("lower versionCode must yield NoUpdate", result is UpdateCheckResult.NoUpdate)
    }

    @Test
    fun `identical versionCode returns NoUpdate`() = runBlocking {
        val installedCode = 300
        server.enqueue(MockResponse().setBody(manifestJson(versionCode = installedCode)))
        val svc = HttpUpdateService(context, config, installer, installedVersionCode = installedCode)
        val result = svc.checkForUpdate()
        assertTrue("equal versionCode must yield NoUpdate", result is UpdateCheckResult.NoUpdate)
    }

    @Test
    fun `higher versionCode returns Available with correct release`() = runBlocking {
        val installedCode = 300
        server.enqueue(MockResponse().setBody(manifestJson(versionCode = installedCode + 1)))
        val svc = HttpUpdateService(context, config, installer, installedVersionCode = installedCode)
        val result = svc.checkForUpdate()
        assertTrue("higher versionCode must yield Available", result is UpdateCheckResult.Available)
        assertEquals(installedCode + 1, (result as UpdateCheckResult.Available).release.versionCode)
    }

    // ── network error ─────────────────────────────────────────────────────────

    @Test
    fun `server 500 on manifest returns Error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val svc = HttpUpdateService(context, config, installer, installedVersionCode = 300)
        val result = svc.checkForUpdate()
        assertTrue("HTTP 500 must yield Error", result is UpdateCheckResult.Error)
    }

    @Test
    fun `server 404 on manifest returns NotConfigured`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val svc = HttpUpdateService(context, config, installer, installedVersionCode = 300)
        val result = svc.checkForUpdate()
        assertTrue("HTTP 404 must yield NotConfigured", result is UpdateCheckResult.NotConfigured)
    }

    @Test
    fun `connection refused returns Error`() = runBlocking {
        // Shut down server before request
        server.shutdown()
        val svc = HttpUpdateService(context, config, installer, installedVersionCode = 300)
        val result = svc.checkForUpdate()
        assertTrue("connection refused must yield Error", result is UpdateCheckResult.Error)
    }

    // ── SHA256 mismatch ───────────────────────────────────────────────────────

    @Test
    fun `sha256Hex companion produces correct hash`() {
        val tmpFile = File.createTempFile("update_test", ".bin")
        tmpFile.writeBytes(ByteArray(1024) { it.toByte() })
        val expected = reference_sha256(tmpFile)
        val actual = HttpUpdateService.sha256Hex(tmpFile)
        assertEquals("sha256Hex must match reference implementation", expected, actual)
        tmpFile.delete()
    }

    @Test
    fun `downloadAndInstall throws SecurityException on SHA256 mismatch`() = runBlocking {
        val fakeApkBytes = "not-a-real-apk".toByteArray()
        val wrongHash = "0".repeat(64) // intentionally wrong — actual hash differs

        server.enqueue(MockResponse().setBody(okio.Buffer().write(fakeApkBytes)))

        val release = ReleaseInfo(
            version = "9.9.9",
            versionCode = 999,
            minSdk = 26,
            url = server.url("/drainq-one-9.9.9.apk").toString(),
            sha256 = wrongHash,
            size = fakeApkBytes.size.toLong(),
            releasedAt = "2026-05-14T08:00:00Z",
            notes = "test release",
            mandatory = false
        )
        val svc = HttpUpdateService(context, config, installer, installedVersionCode = 300)

        var threw = false
        try {
            svc.downloadAndInstall(release)
        } catch (e: SecurityException) {
            threw = true
        }
        assertTrue("SHA256 mismatch must throw SecurityException", threw)
    }

    // ── disabled mode ─────────────────────────────────────────────────────────

    @Test
    fun `mode=disabled returns NotConfigured without hitting server`() = runBlocking {
        config.overrideChannel("stable")
        val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
        prefs.edit().putString("mode", "disabled").commit()
        val svc = HttpUpdateService(context, config, installer, installedVersionCode = 300)
        val result = svc.checkForUpdate()
        assertTrue("disabled mode must yield NotConfigured", result is UpdateCheckResult.NotConfigured)
        assertEquals("server must not be called", 0, server.requestCount)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun manifestJson(versionCode: Int, version: String = "0.4.0"): String = """
        {
          "channel": "stable",
          "latest": {
            "version": "$version",
            "versionCode": $versionCode,
            "minSdk": 26,
            "url": "${server.url("/drainq-one-$version.apk")}",
            "sha256": "${"a".repeat(64)}",
            "size": 12345,
            "releasedAt": "2026-05-14T08:00:00Z",
            "notes": "Test release",
            "mandatory": false
          },
          "history": []
        }
    """.trimIndent()

    private fun reference_sha256(file: File): String {
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
