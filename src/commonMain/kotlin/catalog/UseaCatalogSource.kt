package catalog

import model.CardVariant
import model.OrderItem
import model.Seller
import export.CsvGenerator
import model.DeckEntryMatch
import model.DeckEntry
import model.MatchStatus
import model.Section

class UseaCatalogSource(
    private val remoteCatalogDataSource: CatalogDataSource,
) : CatalogSource {
    override val seller = Seller.USEA

    override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> {
        val catalog = remoteCatalogDataSource.load(forceRefresh = true, log = log)
        return catalog?.variants?.map { it.copy(seller = Seller.USEA) } ?: emptyList()
    }

    override suspend fun search(cardName: String): List<CardVariant> {
        // USEA doesn't support on-demand search — all matching happens against cached catalog
        return emptyList()
    }

    override fun checkoutUrl(items: List<OrderItem>): String? = null // Email-based ordering

    override fun formatForExport(items: List<OrderItem>): String {
        // Reuse existing CsvGenerator by wrapping OrderItems as DeckEntryMatch objects
        val matches = items.map { item ->
            DeckEntryMatch(
                deckEntry = DeckEntry(
                    id = "export_${item.variant.sku}",
                    originalLine = "${item.qty} ${item.variant.nameOriginal}",
                    qty = item.qty,
                    cardName = item.variant.nameOriginal,
                    section = Section.MAIN,
                    include = true,
                ),
                status = MatchStatus.AUTO_MATCHED,
                selectedVariant = item.variant,
            )
        }
        return CsvGenerator.generateFoundCardsCsv(matches)
    }
}
