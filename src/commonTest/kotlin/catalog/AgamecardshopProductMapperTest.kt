package catalog

import model.CardVariant
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgamecardshopProductMapperTest {

    @Test
    fun `matchProductId finds exact match by set code and variant type`() {
        val searchResults = listOf(
            WpProduct(id = 42, title = "Lightning Bolt M11 normal", slug = "lightning-bolt-m11-normal"),
            WpProduct(id = 381, title = "Lightning Bolt Magic Player Rewards 2010 foil", slug = "lightning-bolt-mpr-foil"),
        )
        val variant = testVariant("Lightning Bolt", "M11", VariantType.REGULAR)
        val result = AgamecardshopProductMapper.matchProductId(variant, searchResults)
        assertEquals(42, result)
    }

    @Test
    fun `matchProductId maps HOLO to hologram`() {
        val searchResults = listOf(
            WpProduct(id = 959, title = "Bitterblossom MM2 hologram", slug = "bitterblossom-mm2-hologram"),
            WpProduct(id = 166, title = "Bitterblossom Morningtide normal", slug = "bitterblossom-morningtide-normal"),
        )
        val variant = testVariant("Bitterblossom", "MM2", VariantType.HOLO)
        val result = AgamecardshopProductMapper.matchProductId(variant, searchResults)
        assertEquals(959, result)
    }

    @Test
    fun `matchProductId returns null when no match found`() {
        val searchResults = listOf(
            WpProduct(id = 959, title = "Bitterblossom MM2 hologram", slug = "bitterblossom-mm2-hologram"),
        )
        val variant = testVariant("Bitterblossom", "MM2", VariantType.REGULAR)
        val result = AgamecardshopProductMapper.matchProductId(variant, searchResults)
        assertNull(result)
    }

    @Test
    fun `matchProductId handles case-insensitive set codes`() {
        val searchResults = listOf(
            WpProduct(id = 42, title = "Lightning Bolt m11 normal", slug = "lightning-bolt-m11-normal"),
        )
        val variant = testVariant("Lightning Bolt", "M11", VariantType.REGULAR)
        val result = AgamecardshopProductMapper.matchProductId(variant, searchResults)
        assertEquals(42, result)
    }

    @Test
    fun `variantTypeToWcSuffix maps correctly`() {
        assertEquals("normal", AgamecardshopProductMapper.variantTypeToWcSuffix(VariantType.REGULAR))
        assertEquals("hologram", AgamecardshopProductMapper.variantTypeToWcSuffix(VariantType.HOLO))
        assertEquals("foil", AgamecardshopProductMapper.variantTypeToWcSuffix(VariantType.FOIL))
    }

    private fun testVariant(name: String, set: String, type: VariantType) = CardVariant(
        nameOriginal = name,
        nameNormalized = name.lowercase(),
        setCode = set,
        sku = "XMC-TEST",
        variantType = type,
        priceInCents = 220,
        seller = Seller.USEA,
    )
}
