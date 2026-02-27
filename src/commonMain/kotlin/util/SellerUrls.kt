package util

import model.OrderItem
import model.Seller
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val BOOTLEG_MAGE_DECK_IMPORT_URL = "https://bootlegmage.com/deck-import/"

/**
 * Build TCGPlayer mass entry URL with card list pre-filled via the `c` query parameter.
 * Cards are separated by `||` which TCGPlayer renders as separate rows.
 * Format per card: "qty CardName [SET]"
 */
fun buildTcgPlayerUrl(items: List<OrderItem>): String {
    val cardList = items.joinToString("||") {
        "${it.qty} ${it.variant.nameOriginal} [${it.variant.setCode}]"
    }
    return "https://www.tcgplayer.com/massentry?c=${encodeUrlParameter(cardList)}"
}

/**
 * Build ManaPool add-deck URL with card list pre-filled via base64-encoded `deck` parameter.
 * Format per line: "qty CardName [set] collectorNumber"
 */
@OptIn(ExperimentalEncodingApi::class)
fun buildManaPoolUrl(items: List<OrderItem>): String {
    val deckText = items.joinToString("\n") {
        val cn = it.variant.collectorNumber
        if (cn != null) "${it.qty} ${it.variant.nameOriginal} [${it.variant.setCode}] $cn"
        else "${it.qty} ${it.variant.nameOriginal} [${it.variant.setCode}]"
    }
    val encoded = Base64.encode(deckText.encodeToByteArray())
    return "https://manapool.com/add-deck?deck=${encodeUrlParameter(encoded)}"
}

/**
 * Get the checkout/import URL for a given seller, if available.
 */
fun sellerCheckoutUrl(seller: Seller, items: List<OrderItem>): String? = when (seller) {
    Seller.TCGPLAYER -> buildTcgPlayerUrl(items)
    Seller.MANAPOOL -> buildManaPoolUrl(items)
    Seller.BOOTLEG_MAGE -> BOOTLEG_MAGE_DECK_IMPORT_URL
    Seller.USEA -> null
}
