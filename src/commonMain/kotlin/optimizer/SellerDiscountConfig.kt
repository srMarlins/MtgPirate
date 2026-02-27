package optimizer

import model.Seller

/**
 * Configuration for seller-specific discounts and shipping rules.
 */
data class DiscountTier(val minCents: Int, val discountPercent: Int)
data class ShippingTier(val minCents: Int, val shippingCents: Int, val label: String)

data class SellerDiscountConfig(
    val seller: Seller,
    val discountTiers: List<DiscountTier>,  // Sorted descending by minCents
    val shippingTiers: List<ShippingTier>,  // Sorted descending by minCents
)

val USEA_DISCOUNT_CONFIG = SellerDiscountConfig(
    seller = Seller.USEA,
    discountTiers = listOf(
        DiscountTier(400_00, 50),  // >$400 → 50%
        DiscountTier(300_00, 35),  // >$300 → 35%
        DiscountTier(200_00, 30),  // >$200 → 30%
        DiscountTier(160_00, 25),  // >$160 → 25%
        DiscountTier(100_00, 15),  // >$100 → 15%
        DiscountTier(60_00, 5),    // >$60 → 5%
    ),
    shippingTiers = listOf(
        ShippingTier(300_00, 0, "Express Free"),    // >$300 after discount
        ShippingTier(100_00, 0, "Normal Free"),     // >$100 after discount
        ShippingTier(0, 10_00, "Normal $10"),       // Otherwise $10
    ),
)

val BOOTLEG_MAGE_DISCOUNT_CONFIG = SellerDiscountConfig(
    seller = Seller.BOOTLEG_MAGE,
    discountTiers = emptyList(),  // TODO: Implement their specific tier structure
    shippingTiers = listOf(
        ShippingTier(0, 0, "Free Shipping"),  // Currently offers free shipping
    ),
)

@Suppress("DEPRECATION")
val TCGPLAYER_DISCOUNT_CONFIG = SellerDiscountConfig(
    seller = Seller.TCGPLAYER,
    discountTiers = emptyList(),
    shippingTiers = listOf(
        ShippingTier(0, 0, "Varies by seller"),
    ),
)

val MANAPOOL_DISCOUNT_CONFIG = SellerDiscountConfig(
    seller = Seller.MANAPOOL,
    discountTiers = emptyList(),
    shippingTiers = listOf(
        ShippingTier(0, 0, "Varies by seller"),
    ),
)

/**
 * Retrieve the discount configuration for a given seller.
 */
fun getDiscountConfig(seller: Seller): SellerDiscountConfig = when (seller) {
    Seller.USEA -> USEA_DISCOUNT_CONFIG
    Seller.BOOTLEG_MAGE -> BOOTLEG_MAGE_DISCOUNT_CONFIG
    @Suppress("DEPRECATION") Seller.TCGPLAYER -> TCGPLAYER_DISCOUNT_CONFIG
    Seller.MANAPOOL -> MANAPOOL_DISCOUNT_CONFIG
}
