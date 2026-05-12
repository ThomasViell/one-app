package com.uip.oneapp.update

import com.uip.oneapp.data.local.entity.UpdateEventType
import com.uip.oneapp.data.repository.UpdateEventRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Integration tests for the full update flow using MockWebServer.
 * Covers all 5 failure scenarios from Phase-6 spec plus the happy path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateE2ETest {

    private lateinit var server: MockWebServer
    private lateinit var auditLog: FakeUpdateEventRepository
    private lateinit var installer: FakeUpdateInstaller
    private lateinit var tmpDir: File

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        tmpDir = createTempDir("update-e2e")
        auditLog = FakeUpdateEventRepository()
        installer = FakeUpdateInstaller(tmpDir)
    }

    @After
    fun tearDown() {
        server.shutdown()
        tmpDir.deleteRecursively()
    }

    private fun buildService(installedVersionCode: Int = 10): HttpUpdateService {
        val config = FakeUpdateConfig(server.url("/").toString())
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        return HttpUpdateService(
            context = null,
            config = config,
            installer = installer,
            auditLog = auditLog,
            installedVersionCode = installedVersionCode,
            installedVersionName = "0.3.0",
            httpClient = client
        )
    }

    // ─── Test 1: Happy-Path ───────────────────────────────────────────────────

    @Test
    fun `checkForUpdate - higher versionCode returns Available`() = runTest {
        val apkBytes = "fake-apk-content".toByteArray()
        val sha256 = sha256Hex(apkBytes)
        server.enqueue(MockResponse().setBody(manifest("0.4.0", 20, server.url("/apk").toString(), sha256, apkBytes.size.toLong())))

        val result = buildService(installedVersionCode = 10).checkForUpdate()

        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("0.4.0", (result as UpdateCheckResult.Available).release.version)
        assertTrue(auditLog.hasEvent(UpdateEventType.CHECK))
    }

    @Test
    fun `downloadAndInstall - correct SHA256 succeeds and fires install`() = runTest {
        val apkBytes = "drainq-apk-payload".toByteArray(Charsets.ISO_8859_1)
        val sha256 = sha256Hex(apkBytes)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(apkBytes)))

        val release = release("0.4.0", 20, server.url("/apk").toString(), sha256, apkBytes.size.toLong())
        buildService().downloadAndInstall(release)

        assertTrue("install() must be called on hash match", installer.installCalled)
        assertTrue(auditLog.hasEvent(UpdateEventType.DOWNLOAD_OK))
        assertTrue(auditLog.hasEvent(UpdateEventType.INSTALL_DONE))
    }

    // ─── Test 2: SHA256-Mismatch → Abort ─────────────────────────────────────

    @Test
    fun `downloadAndInstall - SHA256 mismatch throws SecurityException and logs DOWNLOAD_FAIL`() = runTest {
        val apkBytes = "fake-apk-content".toByteArray(Charsets.ISO_8859_1)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(apkBytes)))

        val release = release("0.4.0", 20, server.url("/apk").toString(), "0".repeat(64), apkBytes.size.toLong())

        var caught: SecurityException? = null
        try {
            buildService().downloadAndInstall(release)
        } catch (e: SecurityException) {
            caught = e
        }

        assertTrue("Must throw SecurityException", caught != null)
        assertFalse("Installer must NOT be called on mismatch", installer.installCalled)
        assertTrue(auditLog.hasEvent(UpdateEventType.DOWNLOAD_FAIL))
        assertTrue(
            auditLog.events.any {
                it.type == UpdateEventType.DOWNLOAD_FAIL &&
                    it.errorMessage?.contains("SHA256 mismatch") == true
            }
        )
    }

    // ─── Test 3: 404 on manifest → NotConfigured ─────────────────────────────

    @Test
    fun `checkForUpdate - 404 manifest returns NotConfigured`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = buildService().checkForUpdate()
        assertEquals(UpdateCheckResult.NotConfigured, result)
    }

    // ─── Test 4: Connection abort during download ─────────────────────────────

    @Test
    fun `downloadAndInstall - disconnect during body logs DOWNLOAD_FAIL`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("partial-body")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        val release = release("0.4.0", 20, server.url("/apk").toString(), "0".repeat(64), 100_000L)

        var threw = false
        try {
            buildService().downloadAndInstall(release)
        } catch (_: Exception) {
            threw = true
        }

        assertTrue("Must throw on connection abort", threw)
        assertFalse("Installer must NOT be called", installer.installCalled)
        assertTrue(auditLog.hasEvent(UpdateEventType.DOWNLOAD_FAIL))
    }

    // ─── Test 5: Lower versionCode → NoUpdate ────────────────────────────────

    @Test
    fun `checkForUpdate - lower versionCode returns NoUpdate`() = runTest {
        server.enqueue(MockResponse().setBody(manifest("0.2.0", 5, server.url("/apk").toString(), "0".repeat(64), 100L)))

        assertEquals(UpdateCheckResult.NoUpdate, buildService(installedVersionCode = 10).checkForUpdate())
    }

    @Test
    fun `checkForUpdate - identical versionCode returns NoUpdate`() = runTest {
        server.enqueue(MockResponse().setBody(manifest("0.3.0", 10, server.url("/apk").toString(), "0".repeat(64), 100L)))

        assertEquals(UpdateCheckResult.NoUpdate, buildService(installedVersionCode = 10).checkForUpdate())
    }

    // ─── Test 6: 5xx → Error + audit ─────────────────────────────────────────

    @Test
    fun `checkForUpdate - 500 returns Error and logs DOWNLOAD_FAIL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = buildService().checkForUpdate()
        assertTrue(result is UpdateCheckResult.Error)
        assertTrue(auditLog.hasEvent(UpdateEventType.DOWNLOAD_FAIL))
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    // minSdk=0 bypasses the SDK_INT check (Build.VERSION.SDK_INT=0 in JVM tests)
    private fun manifest(version: String, versionCode: Int, url: String, sha256: String, size: Long) =
        """{"channel":"stable","latest":{"version":"$version","versionCode":$versionCode,"minSdk":0,"url":"$url","sha256":"$sha256","size":$size,"releasedAt":"2026-05-12T00:00:00Z","notes":"test","mandatory":false},"history":[]}"""

    private fun release(version: String, versionCode: Int, url: String, sha256: String, size: Long) =
        ReleaseInfo(version = version, versionCode = versionCode, minSdk = 0,
            url = url, sha256 = sha256, size = size,
            releasedAt = "2026-05-12T00:00:00Z", notes = "test")

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

// ─── Test Doubles ─────────────────────────────────────────────────────────────

data class AuditEntry(
    val type: UpdateEventType,
    val fromVersion: String?,
    val toVersion: String?,
    val source: String?,
    val errorMessage: String?
)

class FakeUpdateEventRepository : UpdateEventRepository(null) {
    val events = mutableListOf<AuditEntry>()

    fun hasEvent(type: UpdateEventType) = events.any { it.type == type }

    override suspend fun log(
        type: UpdateEventType,
        fromVersion: String?,
        toVersion: String?,
        source: String?,
        errorMessage: String?
    ): Long {
        events.add(AuditEntry(type, fromVersion, toVersion, source, errorMessage))
        return events.size.toLong()
    }

    override suspend fun pruneOldEvents() { /* no-op in tests */ }
}

class FakeUpdateConfig(private val baseUrl: String) : UpdateConfig(null) {
    override val mode: String get() = "proxy"
    override val manifestUrl: String get() = "${baseUrl}releases.stable.json"
}

class FakeUpdateInstaller(private val dir: File) : UpdateInstaller(null) {
    var installCalled = false
    var lastFile: File? = null

    override fun install(apkFile: File) {
        installCalled = true
        lastFile = apkFile
    }

    override fun cacheDir(): File = dir
}
