package com.converterplus.app.ads

import com.converterplus.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdUnitsTest {

    @Test
    fun `demo unit ids are valid`() {
        AdUnits.DEMO_UNIT_IDS.forEach { id ->
            assertTrue("$id должен считаться допустимым", AdUnits.isValidUnitId(id))
        }
    }

    @Test
    fun `real rsya ids are recognised`() {
        assertTrue(AdUnits.isValidUnitId("R-M-1234567-1"))
        assertTrue(AdUnits.isValidUnitId("R-M-987654-12"))
    }

    @Test
    fun `broken ids are rejected`() {
        assertFalse(AdUnits.isValidUnitId(""))
        assertFalse(AdUnits.isValidUnitId("   "))
        assertFalse(AdUnits.isValidUnitId("R-M--1"))
        assertFalse(AdUnits.isValidUnitId("ca-app-pub-123"))
        assertFalse(AdUnits.isValidUnitId("banner"))
    }

    @Test
    fun `configuration is valid when all three ids are filled`() {
        val units = AdUnits(
            testMode = true,
            bannerUnitId = "demo-banner-yandex",
            interstitialUnitId = "demo-interstitial-yandex",
            rewardedUnitId = "demo-rewarded-yandex",
        )
        assertTrue(units.isValid)
        assertFalse(units.hasDemoUnitsInRelease)
    }

    @Test
    fun `demo ids in release are flagged`() {
        val units = AdUnits(
            testMode = false,
            bannerUnitId = "demo-banner-yandex",
            interstitialUnitId = "R-M-1234567-1",
            rewardedUnitId = "R-M-1234567-2",
        )
        assertTrue(units.hasDemoUnitsInRelease)
    }

    @Test
    fun `real ids in release are not flagged`() {
        val units = AdUnits(
            testMode = false,
            bannerUnitId = "R-M-1234567-1",
            interstitialUnitId = "R-M-1234567-2",
            rewardedUnitId = "R-M-1234567-3",
        )
        assertTrue(units.isValid)
        assertFalse(units.hasDemoUnitsInRelease)
    }

    @Test
    fun `build config values are wired`() {
        val units = AdUnits.fromBuildConfig()
        assertEquals(BuildConfig.ADS_TEST_MODE, units.testMode)
        assertEquals(BuildConfig.ADS_BANNER_UNIT_ID, units.bannerUnitId)
        assertEquals(BuildConfig.ADS_INTERSTITIAL_UNIT_ID, units.interstitialUnitId)
        assertEquals(BuildConfig.ADS_REWARDED_UNIT_ID, units.rewardedUnitId)
    }

    @Test
    fun `debug build runs in test mode by default`() {
        // В gradle.properties ads.testMode=true — до релиза показы идут в тестовом режиме.
        assertEquals(true, BuildConfig.ADS_TEST_MODE)
    }
}
