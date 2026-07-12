package com.uip.oneapp.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageInfoTest {

    @Test
    fun usedFraction_zeroTotal_returnsZero() {
        assertEquals(0f, VolumeUsage(0L, 0L).usedFraction)
    }

    @Test
    fun usedFraction_halfFull() {
        assertEquals(0.5f, VolumeUsage(500L, 1000L).usedFraction, 0.001f)
    }

    @Test
    fun usedFraction_clampedToOne() {
        // freeBytes > totalBytes wäre pathologisch — clamp auf 1
        assertEquals(0f, VolumeUsage(2000L, 1000L).usedFraction, 0.001f)
    }

    @Test
    fun formatGb_locale_us() {
        assertEquals("1.5 GB", formatGb(1_500_000_000L))
    }

    @Test
    fun formatGb_zero() {
        assertEquals("0.0 GB", formatGb(0L))
    }

    @Test
    fun fillLevel_green_below80() {
        assertEquals(StorageFillLevel.OK, storageFillLevel(0.5f))
    }

    @Test
    fun fillLevel_amber_between80and95() {
        assertEquals(StorageFillLevel.WARN, storageFillLevel(0.85f))
    }

    @Test
    fun fillLevel_red_above95() {
        assertEquals(StorageFillLevel.CRITICAL, storageFillLevel(0.97f))
    }

    @Test
    fun fillLevel_exactBoundary80_isOk() {
        // > 0.80 → WARN, genau 0.80 → OK
        assertEquals(StorageFillLevel.OK, storageFillLevel(0.80f))
    }

    @Test
    fun fillLevel_exactBoundary95_isWarn() {
        // > 0.95 → CRITICAL, genau 0.95 → WARN
        assertEquals(StorageFillLevel.WARN, storageFillLevel(0.95f))
    }
}
