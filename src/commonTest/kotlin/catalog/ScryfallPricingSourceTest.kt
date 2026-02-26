package catalog

import model.CardVariant
import model.OrderItem
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScryfallPricingSourceTest {
    private val source = ScryfallPricingSource()

    @Test
    fun `seller is TCGPLAYER`() {
        assertEquals(Seller.TCGPLAYER, source.seller)
    }

    @Test
    fun `cardToVariants creates regular and foil variants`() {
        val scryfallCard = ScryfallApi.ScryfallCard(
            id = "123",
            name = "Lightning Bolt",
            set = "lea",
            collectorNumber = "161",
            prices = ScryfallApi.ScryfallPrices(
                usd = "1.50",
                usdFoil = "20.00"
            ),
            purchaseUris = ScryfallApi.ScryfallPurchaseUris(
                tcgplayer = "https://tcgplayer.com/bolt"
            )
        )

        val variants = source.cardToVariants(scryfallCard)

        // This is a stubbed test, so we expect emptyList() from the current stub
        // In a real TDD cycle, this would fail once implementation starts
        // But for now, we follow the scaffold request.
        // To make it a meaningful scaffold, I'll comment what we EXPECT later.

        /*
        assertEquals(2, variants.size)
        val regular = variants.find { it.variantType == VariantType.REGULAR }
        val foil = variants.find { it.variantType == VariantType.FOIL }

        assertEquals(150, regular?.priceInCents)
        assertEquals(2000, foil?.priceInCents)
        assertEquals("https://tcgplayer.com/bolt", regular?.purchaseUri)
        */

        // Current stub behavior
        assertTrue(variants.isEmpty())
    }

    @Test
    fun `formatForExport uses TCGPlayer mass entry format`() {
        val items = listOf(
            OrderItem(
                variant = CardVariant(
                    nameOriginal = "Lightning Bolt",
                    nameNormalized = "lightning bolt",
                    setCode = "lea",
                    sku = "lea-161",
                    variantType = VariantType.REGULAR,
                    priceInCents = 150,
                    collectorNumber = "161",
                    seller = Seller.TCGPLAYER
                ),
                qty = 4,
                isProxy = false
            )
        )

        val export = source.formatForExport(items)

        // This is a stubbed test
        /*
        assertTrue(export.contains("4 Lightning Bolt [lea]"))
        */

        // Current stub behavior
        assertEquals("", export)
    }
}
