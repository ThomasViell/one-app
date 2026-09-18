package com.uip.oneapp.network.l10n

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class L10nPortalClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: L10nPortalClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = L10nPortalClient(baseUrl = server.url("").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchLocales_parsesCodeNameNativeStatusBytes() {
        server.enqueue(
            MockResponse().setBody(
                """[{"code":"de","displayName":"Deutsch","nativeName":"Deutsch","oneStatus":"core","bytesSize":300056}]"""
            )
        )
        val result = client.fetchLocales()
        assertTrue(result is LocalesResult.Ok)
        val ok = result as LocalesResult.Ok
        assertEquals(1, ok.locales.size)
        assertEquals("de", ok.locales[0].code)
        assertEquals("Deutsch", ok.locales[0].displayName)
        assertEquals("Deutsch", ok.locales[0].nativeName)
        assertEquals("core", ok.locales[0].oneStatus)
        assertEquals(300056L, ok.locales[0].bytesSize)
    }

    @Test
    fun fetchBundle_requestsScopeOneAndShared() {
        server.enqueue(MockResponse().setBody("""{"a":"b"}"""))
        client.fetchBundle("de")
        val recorded = server.takeRequest()
        assertTrue(recorded.path?.contains("scope=one,shared") == true)
    }

    @Test
    fun fetchBundle_sendsIfNoneMatch_when304_returnsNotModified() {
        server.enqueue(MockResponse().setResponseCode(304))
        val result = client.fetchBundle("de", etag = "\"abc\"")
        val recorded = server.takeRequest()
        assertEquals("\"abc\"", recorded.getHeader("If-None-Match"))
        assertTrue(result is BundleResult.NotModified)
    }

    @Test
    fun fetchBundle_404_returnsNotAvailable() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.fetchBundle("pl")
        assertTrue(result is BundleResult.NotAvailable)
    }

    @Test
    fun fetchBundle_keepsKeyCase() {
        server.enqueue(MockResponse().setBody("""{"CANCEL":"Abbrechen","Camera.Cable.Down":"Runter"}"""))
        val result = client.fetchBundle("de")
        assertTrue(result is BundleResult.Ok)
        val ok = result as BundleResult.Ok
        assertTrue(ok.values.containsKey("CANCEL"))
        assertTrue(ok.values.containsKey("Camera.Cable.Down"))
    }

    @Test
    fun fetchBundle_networkError_returnsUnavailable_noThrow() {
        server.shutdown()
        val result = client.fetchBundle("de")
        assertTrue(result is BundleResult.Unavailable)
    }
}
