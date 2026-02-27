package database

import model.Seller
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class SellerCacheTtlTest {

    @Test
    fun isCacheFresh_withinTtl() {
        val nowMillis = 1_000_000_000L
        val fetchedAt = nowMillis - 1.hours.inWholeMilliseconds
        assertTrue(CacheUtils.isFresh(fetchedAt, ttl = 24.hours, nowMillis = nowMillis))
    }

    @Test
    fun isCacheFresh_expiredTtl() {
        val nowMillis = 1_000_000_000L
        val fetchedAt = nowMillis - 25.hours.inWholeMilliseconds
        assertFalse(CacheUtils.isFresh(fetchedAt, ttl = 24.hours, nowMillis = nowMillis))
    }

    @Test
    fun isCacheFresh_proxySeller7DayTtl() {
        val nowMillis = 1_000_000_000L
        val fetchedAt = nowMillis - 5.days.inWholeMilliseconds
        assertTrue(CacheUtils.isFresh(fetchedAt, ttl = 7.days, nowMillis = nowMillis))
    }

    @Test
    fun isCacheFresh_proxySeller7DayExpired() {
        val nowMillis = 1_000_000_000L
        val fetchedAt = nowMillis - 8.days.inWholeMilliseconds
        assertFalse(CacheUtils.isFresh(fetchedAt, ttl = 7.days, nowMillis = nowMillis))
    }

    @Test
    fun isCacheFresh_exactlyAtBoundaryIsNotFresh() {
        val nowMillis = 1_000_000_000L
        val fetchedAt = nowMillis - 24.hours.inWholeMilliseconds
        assertFalse(CacheUtils.isFresh(fetchedAt, ttl = 24.hours, nowMillis = nowMillis))
    }

    @Test
    fun ttlForSeller_proxyIs7Days() {
        assertEquals(7.days, CacheUtils.ttlForSeller(Seller.BOOTLEG_MAGE))
        assertEquals(7.days, CacheUtils.ttlForSeller(Seller.USEA))
    }

    @Test
    fun ttlForSeller_realIs24Hours() {
        assertEquals(24.hours, CacheUtils.ttlForSeller(Seller.MANAPOOL))
        assertEquals(24.hours, CacheUtils.ttlForSeller(Seller.TCGPLAYER))
    }
}
