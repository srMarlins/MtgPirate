package optimizer

import model.CardVariant
import model.OrderItem
import model.Seller
import model.SellerOrder
import model.ShoppingPlan
import model.VariantType
import match.NameNormalizer
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class ShoppingOptimizerTest {

    private fun variant(
        name: String,
        seller: Seller,
        priceCents: Int,
        variantType: VariantType = VariantType.REGULAR,
    ): CardVariant =
        CardVariant(
            nameOriginal = name,
            nameNormalized = NameNormalizer.normalize(name),
            setCode = "TST",
            sku = "${seller.name}-${name.replace(" ", "")}-${variantType.name}",
            variantType = variantType,
            priceInCents = priceCents,
            seller = seller,
        )

    private fun orderItem(name: String, seller: Seller, priceCents: Int, qty: Int = 1) =
        OrderItem(
            variant = variant(name, seller, priceCents),
            qty = qty,
            isProxy = seller.isProxy
        )

    @Ignore
    @Test
    fun `single seller order applies correct discount tier`() {
        val items = listOf(
            orderItem("Card A", Seller.USEA, 10000) // $100
        )
        val initialPlan = ShoppingPlan(
            orders = listOf(
                SellerOrder(
                    seller = Seller.USEA,
                    items = items,
                    subtotalCents = 10000,
                    discountPercent = 0,
                    shippingCents = 1000,
                    totalCents = 11000
                )
            ),
            totalPriceCents = 11000,
            savingsVsSingleSeller = 0
        )

        val optimized = ShoppingOptimizer.optimize(initialPlan)

        val useaOrder = optimized.orders.find { it.seller == Seller.USEA }!!
        // $100 is the 15% tier for USEA
        assertEquals(15, useaOrder.discountPercent)
        // Free normal shipping at $100
        assertEquals(0, useaOrder.shippingCents)
        assertEquals(8500, useaOrder.totalCents)
    }

    @Ignore
    @Test
    fun `threshold optimization moves cards to reach better tier`() {
        // USEA subtotal $380 (30% tier), BM $40
        // Moving $20 from BM to USEA hits $400 (50% tier)
        val useaItems = (1..38).map { orderItem("USEA Card $it", Seller.USEA, 1000) }
        val bmItems = (1..4).map { orderItem("BM Card $it", Seller.BOOTLEG_MAGE, 1000) }

        val initialPlan = ShoppingPlan(
            orders = listOf(
                SellerOrder(Seller.USEA, useaItems, 38000, 30, 0, 26600),
                SellerOrder(Seller.BOOTLEG_MAGE, bmItems, 4000, 0, 1000, 5000)
            ),
            totalPriceCents = 31600,
            savingsVsSingleSeller = 0
        )

        val optimized = ShoppingOptimizer.optimize(initialPlan)

        val useaOrder = optimized.orders.find { it.seller == Seller.USEA }!!
        assertEquals(50, useaOrder.discountPercent)
        assertEquals(40000, useaOrder.subtotalCents)

        val bmOrder = optimized.orders.find { it.seller == Seller.BOOTLEG_MAGE }!!
        assertEquals(2000, bmOrder.subtotalCents)

        // USEA: $400 * 0.5 = $200
        // BM: $20 + $10 shipping = $30
        // Total: $230 (23000 cents)
        assertEquals(23000, optimized.totalPriceCents)
    }

    @Ignore
    @Test
    fun `shipping included when below free shipping threshold`() {
        val items = listOf(
            orderItem("Cheap Card", Seller.USEA, 2000) // $20
        )
        val initialPlan = ShoppingPlan(
            orders = listOf(
                SellerOrder(
                    seller = Seller.USEA,
                    items = items,
                    subtotalCents = 2000,
                    discountPercent = 0,
                    shippingCents = 0,
                    totalCents = 2000
                )
            ),
            totalPriceCents = 2000,
            savingsVsSingleSeller = 0
        )

        val optimized = ShoppingOptimizer.optimize(initialPlan)

        val useaOrder = optimized.orders.find { it.seller == Seller.USEA }!!
        assertEquals(1000, useaOrder.shippingCents)
        assertEquals(3000, useaOrder.totalCents)
    }
}
