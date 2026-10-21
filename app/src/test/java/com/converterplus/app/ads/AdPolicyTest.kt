package com.converterplus.app.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {

    private class FakeStore : AdPolicyStore {
        val longs = mutableMapOf<String, Long?>()
        val ints = mutableMapOf<String, Int>()

        override fun getLong(key: String): Long? = longs[key]

        override fun putLong(key: String, value: Long?) {
            if (value == null) longs.remove(key) else longs[key] = value
        }

        override fun getInt(key: String, defaultValue: Int): Int = ints[key] ?: defaultValue

        override fun putInt(key: String, value: Int) {
            ints[key] = value
        }
    }

    private class FakeClock(var now: Long = 0L) : Clock {
        override fun now(): Long = now
    }

    private val config = AdPolicyConfig(
        interstitialEveryNthConversion = 3,
        interstitialMinIntervalMillis = 120_000L,
        noAdsDurationMillis = 30L * 60_000L,
    )

    private fun newPolicy(
        store: FakeStore = FakeStore(),
        clock: FakeClock = FakeClock(),
    ): Triple<AdPolicy, FakeStore, FakeClock> = Triple(AdPolicy(store, clock, config), store, clock)

    @Test
    fun `no interstitial before three conversions`() {
        val (policy, _, _) = newPolicy()
        policy.noteConversionFinished()
        assertFalse(policy.canShowInterstitial())
        policy.noteConversionFinished()
        assertFalse(policy.canShowInterstitial())
    }

    @Test
    fun `interstitial allowed on third conversion`() {
        val (policy, _, _) = newPolicy()
        repeat(3) { policy.noteConversionFinished() }
        assertTrue(policy.canShowInterstitial())
    }

    @Test
    fun `interval of 120 seconds is enforced`() {
        val store = FakeStore()
        val clock = FakeClock(1_000_000L)
        val policy = AdPolicy(store, clock, config)

        repeat(3) { policy.noteConversionFinished() }
        assertTrue(policy.canShowInterstitial())
        policy.noteInterstitialShown()

        repeat(3) { policy.noteConversionFinished() }
        assertFalse("раньше 120 секунд показывать нельзя", policy.canShowInterstitial())

        clock.now += 119_999L
        assertFalse(policy.canShowInterstitial())

        clock.now += 1L
        assertTrue(policy.canShowInterstitial())
    }

    @Test
    fun `counter resets after interstitial shown`() {
        val (policy, _, _) = newPolicy()
        repeat(3) { policy.noteConversionFinished() }
        policy.noteInterstitialShown()
        assertEquals(0, policy.conversionsSinceLastInterstitial())
        assertFalse(policy.canShowInterstitial())
    }

    @Test
    fun `ads free period blocks interstitial and banner`() {
        val (policy, _, clock) = newPolicy()
        repeat(3) { policy.noteConversionFinished() }

        policy.activateAdsFreePeriod()
        assertTrue(policy.isAdsFreeActive())
        assertFalse(policy.isAdsAllowed())
        assertFalse(policy.canShowInterstitial())

        clock.now += 30L * 60_000L + 1L
        assertFalse(policy.isAdsFreeActive())
        assertTrue(policy.isAdsAllowed())
        assertTrue(policy.canShowInterstitial())
    }

    @Test
    fun `ads free minutes are rounded up`() {
        val (policy, _, clock) = newPolicy()
        policy.activateAdsFreePeriod()
        assertEquals(30, policy.adsFreeMinutesLeft())

        clock.now += 60_000L
        assertEquals(29, policy.adsFreeMinutesLeft())

        clock.now += 29L * 60_000L + 1L
        assertEquals(0, policy.adsFreeMinutesLeft())
    }

    @Test
    fun `no sentinel zero - absent timestamp is null`() {
        val store = FakeStore()
        val clock = FakeClock(5_000L)
        val policy = AdPolicy(store, clock, config)

        assertNull("метки ещё не было", store.getLong(AdPolicy.KEY_LAST_INTERSTITIAL_AT))
        assertNull(policy.adsFreeUntil())

        policy.noteInterstitialShown()
        assertEquals(5_000L, store.getLong(AdPolicy.KEY_LAST_INTERSTITIAL_AT))

        store.putLong(AdPolicy.KEY_LAST_INTERSTITIAL_AT, null)
        assertNull(store.getLong(AdPolicy.KEY_LAST_INTERSTITIAL_AT))
    }

    @Test
    fun `first ever interstitial is allowed without previous timestamp`() {
        val (policy, _, _) = newPolicy()
        repeat(3) { policy.noteConversionFinished() }
        assertTrue(policy.canShowInterstitial())
    }
}
