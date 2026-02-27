package match

import model.CardVariant
import model.Catalog
import model.DeckEntry
import model.Section
import model.Seller
import model.VariantType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultiCatalogMatcherTest {

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

    private fun entry(name: String, qty: Int = 1): DeckEntry =
        DeckEntry(
            id = "test_${name.replace(" ", "_")}",
            originalLine = "$qty $name",
            qty = qty,
            cardName = name,
            section = Section.MAIN,
            include = true,
        )

    private fun defaultConfig() = MultiCatalogMatcher.Config()

    @Test
    fun proxyFirstPicksProxyWhenAvailableFromBothProxyAndReal() {
        val result = MultiCatalogMatcher.match(
            entries = listOf(entry("Lightning Bolt")),
            catalogs = mapOf(
                Seller.USEA to Catalog(listOf(variant("Lightning Bolt", Seller.USEA, 220))),
                Seller.MANAPOOL to Catalog(listOf(variant("Lightning Bolt", Seller.MANAPOOL, 150))),
            ),
            config = defaultConfig(),
        )
        assertEquals(1, result.size)
        assertNotNull(result[0].bestOption)
        assertTrue(result[0].bestOption!!.isProxy)
        assertEquals(Seller.USEA, result[0].bestOption!!.seller)
    }

    @Test
    fun fallsBackToRealCardWhenNoProxyAvailable() {
        val result = MultiCatalogMatcher.match(
            entries = listOf(entry("Tarmogoyf")),
            catalogs = mapOf(
                Seller.USEA to Catalog(emptyList()),
                Seller.MANAPOOL to Catalog(listOf(variant("Tarmogoyf", Seller.MANAPOOL, 5000))),
            ),
            config = defaultConfig(),
        )
        assertEquals(1, result.size)
        assertNotNull(result[0].bestOption)
        assertEquals(Seller.MANAPOOL, result[0].bestOption!!.seller)
        assertEquals(false, result[0].bestOption!!.isProxy)
    }

    @Test
    fun picksCheapestProxyAcrossSellers() {
        val result = MultiCatalogMatcher.match(
            entries = listOf(entry("Lightning Bolt")),
            catalogs = mapOf(
                Seller.USEA to Catalog(listOf(variant("Lightning Bolt", Seller.USEA, 220))),
                Seller.BOOTLEG_MAGE to Catalog(
                    listOf(variant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180))
                ),
            ),
            config = defaultConfig(),
        )
        assertEquals(Seller.BOOTLEG_MAGE, result[0].bestOption!!.seller)
        assertEquals(180, result[0].bestOption!!.priceCents)
    }

    @Test
    fun alternativesIncludeAllSellers() {
        val result = MultiCatalogMatcher.match(
            entries = listOf(entry("Lightning Bolt")),
            catalogs = mapOf(
                Seller.USEA to Catalog(listOf(variant("Lightning Bolt", Seller.USEA, 220))),
                Seller.BOOTLEG_MAGE to Catalog(
                    listOf(variant("Lightning Bolt", Seller.BOOTLEG_MAGE, 180))
                ),
                Seller.MANAPOOL to Catalog(
                    listOf(variant("Lightning Bolt", Seller.MANAPOOL, 150))
                ),
            ),
            config = defaultConfig(),
        )
        val sellers = result[0].alternatives.map { it.seller }.toSet()
        assertEquals(setOf(Seller.USEA, Seller.BOOTLEG_MAGE, Seller.MANAPOOL), sellers)
    }

    @Test
    fun realCardFallbackIsSetWhenBestIsProxy() {
        val result = MultiCatalogMatcher.match(
            entries = listOf(entry("Lightning Bolt")),
            catalogs = mapOf(
                Seller.USEA to Catalog(listOf(variant("Lightning Bolt", Seller.USEA, 220))),
                Seller.MANAPOOL to Catalog(listOf(variant("Lightning Bolt", Seller.MANAPOOL, 150))),
            ),
            config = defaultConfig(),
        )
        assertTrue(result[0].bestOption!!.isProxy)
        assertNotNull(result[0].realCardFallback)
        assertEquals(Seller.MANAPOOL, result[0].realCardFallback!!.seller)
    }

    @Test
    fun realCardFallbackIsNullWhenBestIsAlreadyReal() {
        val result = MultiCatalogMatcher.match(
            entries = listOf(entry("Tarmogoyf")),
            catalogs = mapOf(
                Seller.USEA to Catalog(emptyList()),
                Seller.MANAPOOL to Catalog(listOf(variant("Tarmogoyf", Seller.MANAPOOL, 5000))),
            ),
            config = defaultConfig(),
        )
        assertNull(result[0].realCardFallback)
    }

    @Test
    fun cardNotFoundInAnyCatalogReturnsNullBestOption() {
        val result = MultiCatalogMatcher.match(
            entries = listOf(entry("Nonexistent Card")),
            catalogs = mapOf(
                Seller.USEA to Catalog(emptyList()),
                Seller.MANAPOOL to Catalog(emptyList()),
            ),
            config = defaultConfig(),
        )
        assertNull(result[0].bestOption)
        assertTrue(result[0].alternatives.isEmpty())
    }

    @Test
    fun proxyFirstFalsePicksCheapestRegardlessOfSellerType() {
        val result = MultiCatalogMatcher.match(
            entries = listOf(entry("Lightning Bolt")),
            catalogs = mapOf(
                Seller.USEA to Catalog(listOf(variant("Lightning Bolt", Seller.USEA, 220))),
                Seller.MANAPOOL to Catalog(listOf(variant("Lightning Bolt", Seller.MANAPOOL, 150))),
            ),
            config = MultiCatalogMatcher.Config(proxyFirst = false),
        )
        // Real card is cheaper at 150 vs proxy at 220
        assertEquals(Seller.MANAPOOL, result[0].bestOption!!.seller)
        assertEquals(150, result[0].bestOption!!.priceCents)
    }

    @Test
    fun multipleEntriesMatchedIndependently() {
        val result = MultiCatalogMatcher.match(
            entries = listOf(entry("Lightning Bolt", 4), entry("Counterspell", 4)),
            catalogs = mapOf(
                Seller.USEA to Catalog(
                    listOf(
                        variant("Lightning Bolt", Seller.USEA, 220),
                        variant("Counterspell", Seller.USEA, 220),
                    )
                ),
                Seller.BOOTLEG_MAGE to Catalog(
                    listOf(
                        variant("Lightning Bolt", Seller.BOOTLEG_MAGE, 300),
                        variant("Counterspell", Seller.BOOTLEG_MAGE, 180),
                    )
                ),
            ),
            config = defaultConfig(),
        )
        assertEquals(2, result.size)
        // Lightning Bolt cheaper at USEA (220 vs 300)
        assertEquals(Seller.USEA, result[0].bestOption!!.seller)
        // Counterspell cheaper at Bootleg Mage (180 vs 220)
        assertEquals(Seller.BOOTLEG_MAGE, result[1].bestOption!!.seller)
    }
}
