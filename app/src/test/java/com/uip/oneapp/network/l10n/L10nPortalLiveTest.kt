package com.uip.oneapp.network.l10n

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * E-11 / E-P8: echter Lauf gegen `license.drainq.com`. Aktiv nur mit `-Dl10n.live=true`
 * (siehe app/build.gradle.kts, `systemProperty("l10n.live", ...)`). Ohne die Eigenschaft
 * wird der Test uebersprungen (skipped) statt gegen einen Mock zu laufen — ein Mock waere
 * kein Beleg fuer S-1/S-12 (PLAN.md Abschnitt 5).
 */
class L10nPortalLiveTest {

    private val client = L10nPortalClient(baseUrl = "https://license.drainq.com")

    @Before
    fun requireLiveFlag() {
        assumeTrue(System.getProperty("l10n.live") == "true")
    }

    @Test
    fun fetchLocales_containsDeAndEn() {
        val result = client.fetchLocales()
        assertTrue("Portal nicht erreichbar oder Antwort unbrauchbar: $result", result is LocalesResult.Ok)
        val codes = (result as LocalesResult.Ok).locales.map { it.code }
        println("L10nPortalLiveTest.fetchLocales: codes=$codes")
        assertTrue(codes.contains("de"))
        assertTrue(codes.contains("en"))
    }

    @Test
    fun fetchBundle_de_oneSharedSupersetOfOne_containsAllDamageTypePresets() {
        val one = client.fetchBundle("de")
        // fetchBundle fragt immer scope=one,shared ab (E-P1); zum Vergleich holen wir
        // scope=one separat, wie die Portalmessung 1.1.
        val oneOnlyClient = L10nPortalClient(baseUrl = "https://license.drainq.com")
        val oneOnly = oneOnlyClient.fetchBundleScopeOne("de")

        assertTrue("scope=one,shared nicht erreichbar: $one", one is BundleResult.Ok)
        assertTrue("scope=one nicht erreichbar: $oneOnly", oneOnly is BundleResult.Ok)
        val okShared = one as BundleResult.Ok
        val okOne = oneOnly as BundleResult.Ok
        println("L10nPortalLiveTest.fetchBundle: one=${okOne.values.size} one,shared=${okShared.values.size}")

        assertTrue(okShared.values.keys.containsAll(okOne.values.keys))
        listOf(
            "damage_type_crack", "damage_type_root", "damage_type_offset",
            "damage_type_deposit", "damage_type_infiltration", "damage_type_collapse",
            "damage_type_other"
        ).forEach { key ->
            assertTrue("Preset-Schluessel $key fehlt im Portalpaket", okShared.values.containsKey(key))
        }
    }

    @Test
    fun fetchBundle_de_etagRoundTrip_returnsNotModified() {
        val first = client.fetchBundle("de")
        assertTrue("erster Abruf nicht erreichbar: $first" , first is BundleResult.Ok)
        val etag = (first as BundleResult.Ok).etag
        assertTrue("kein ETag geliefert", etag != null)
        val second = client.fetchBundle("de", etag = etag)
        println("L10nPortalLiveTest.etagRoundTrip: etag=$etag second=$second")
        assertTrue(second is BundleResult.NotModified)
    }
}
