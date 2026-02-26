package optimizer

import model.Seller

data class DiscountTier(val thresholdCents: Int, val discountPercent: Int)
data class ShippingTier(val thresholdCents: Int, val shippingCents: Int)

data class SellerDiscountConfig(
    val seller: Seller,
    val discountTiers: List<DiscountTier>,  // sorted descending by threshold
    val shippingTiers: List<ShippingTier>,  // sorted descending by threshold
    val defaultShippingCents: Int,
)

val USEA_CONFIG = SellerDiscountConfig(
    seller = Seller.USEA,
    discountTiers = listOf(
        DiscountTier(40000, 50),
        DiscountTier(30000, 35),
        DiscountTier(20000, 30),
        DiscountTier(16000, 25),
        DiscountTier(10000, 15),
        DiscountTier(6000, 5)
    ),
    shippingTiers = listOf(
        ShippingTier(30000, 0),
        ShippingTier(10000, 0)
    ),
    defaultShippingCents = 1000
)

val BOOTLEG_MAGE_CONFIG = SellerDiscountConfig(
    seller = Seller.BOOTLEG_MAGE,
    discountTiers = emptyList(),
    shippingTiers = emptyList(),
    defaultShippingCents = 1000
)

val TCGPLAYER_CONFIG = SellerDiscountConfig(
    seller = Seller.TCGPLAYER,
    discountTiers = emptyList(),
    shippingTiers = emptyList(),
    defaultShippingCents = 0
)

fun getDiscountConfig(seller: Seller): SellerDiscountConfig = when (seller) {
    Seller.USEA -> USEA_CONFIG
    Seller.BOOTLEG_MAGE -> BOOTLEG_MAGE_CONFIG
    Seller.TCGPLAYER -> TCGPLAYER_CONFIG
}
