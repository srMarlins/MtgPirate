package model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProTest {

    @Test
    fun proFeature_hasExpectedEntries() {
        assertEquals(5, ProFeature.entries.size)
        assertTrue(ProFeature.entries.contains(ProFeature.MULTI_SELLER))
    }

    @Test
    fun proStatus_free_isNotPro() {
        assertFalse(ProStatus.Free.isPro)
    }

    @Test
    fun proStatus_pro_isPro() {
        assertTrue(ProStatus.Pro.isPro)
    }

    @Test
    fun proStatus_loading_isNotPro() {
        assertFalse(ProStatus.Loading.isPro)
    }

    @Test
    fun purchaseResult_hasSuccessAndCancelled() {
        assertEquals("SUCCESS", PurchaseResult.SUCCESS.name)
        assertEquals("CANCELLED", PurchaseResult.CANCELLED.name)
        assertEquals("ERROR", PurchaseResult.ERROR.name)
    }
}
