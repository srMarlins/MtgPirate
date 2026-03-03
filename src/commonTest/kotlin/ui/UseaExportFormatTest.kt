package ui

import model.CardVariant
import model.OrderItem
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UseaExportFormatTest {

    @Test
    fun `formatForGoogleSheet produces tab-separated format`() {
        val items = listOf(
            OrderItem(
                variant = CardVariant(
                    nameOriginal = "Bitterblossom",
                    nameNormalized = "bitterblossom",
                    setCode = "MM2",
                    sku = "XMC00003",
                    variantType = VariantType.REGULAR,
                    priceInCents = 220,
                    seller = Seller.USEA,
                ),
                qty = 1,
                isProxy = true,
            )
        )
        val result = formatForGoogleSheet(items)
        val lines = result.split("\n")
        assertEquals("Card Name\tSet\tSKU\tCard Type\tQty\tBase Price", lines[0])
        assertEquals("Bitterblossom MM2\tMM2\tXMC00003\t- Regular\t1\t\$2.20", lines[1])
    }

    @Test
    fun `formatForGoogleSheet handles foil and holo types`() {
        val items = listOf(
            OrderItem(
                variant = CardVariant(
                    nameOriginal = "Lightning Bolt",
                    nameNormalized = "lightning bolt",
                    setCode = "M11",
                    sku = "XMC00042",
                    variantType = VariantType.FOIL,
                    priceInCents = 350,
                    seller = Seller.USEA,
                ),
                qty = 4,
                isProxy = true,
            )
        )
        val result = formatForGoogleSheet(items)
        val lines = result.split("\n")
        assertTrue(lines[1].contains("- Foil"))
        assertTrue(lines[1].contains("\$3.50"))
    }

    @Test
    fun `formatForGoogleSheet handles multiple items`() {
        val items = listOf(
            OrderItem(
                variant = CardVariant(
                    nameOriginal = "Lightning Bolt",
                    nameNormalized = "lightning bolt",
                    setCode = "M11",
                    sku = "XMC00042",
                    variantType = VariantType.REGULAR,
                    priceInCents = 220,
                    seller = Seller.USEA,
                ),
                qty = 4,
                isProxy = true,
            ),
            OrderItem(
                variant = CardVariant(
                    nameOriginal = "Counterspell",
                    nameNormalized = "counterspell",
                    setCode = "ICE",
                    sku = "XMC00010",
                    variantType = VariantType.HOLO,
                    priceInCents = 300,
                    seller = Seller.USEA,
                ),
                qty = 2,
                isProxy = true,
            ),
        )
        val result = formatForGoogleSheet(items)
        val lines = result.split("\n")
        assertEquals(3, lines.size) // header + 2 items
        assertTrue(lines[1].contains("Lightning Bolt M11"))
        assertTrue(lines[2].contains("- Holo"))
    }
}
