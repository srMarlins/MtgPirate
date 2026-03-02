package catalog

import database.CatalogStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import model.CardVariant
import model.VariantType

@Serializable
data class WpProduct(
    val id: Int,
    val title: WpRendered? = null,
    val slug: String = "",
) {
    // Constructor for tests (title as plain string)
    constructor(id: Int, title: String, slug: String) : this(id, WpRendered(title), slug)

    val titleText: String get() = title?.rendered ?: ""
}

@Serializable
data class WpRendered(val rendered: String)

private const val BASE_URL = "https://www.agamecardshop.com"
private const val SEARCH_ENDPOINT = "$BASE_URL/wp-json/wp/v2/product"
private const val THROTTLE_MS = 50L

private val json = Json { ignoreUnknownKeys = true }

object AgamecardshopProductMapper {

    fun variantTypeToWcSuffix(type: VariantType): String = when (type) {
        VariantType.REGULAR -> "normal"
        VariantType.HOLO -> "hologram"
        VariantType.FOIL -> "foil"
    }

    fun matchProductId(variant: CardVariant, searchResults: List<WpProduct>): Int? {
        val setCode = variant.setCode.lowercase()
        val wcSuffix = variantTypeToWcSuffix(variant.variantType)

        return searchResults.firstOrNull { product ->
            val title = product.titleText.lowercase().ifEmpty { product.slug.lowercase() }
            title.contains(setCode) && title.contains(wcSuffix)
        }?.id
    }

    suspend fun mapAll(
        httpClient: HttpClient,
        catalogStore: CatalogStore,
        log: (String, String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val unmapped = catalogStore.getUseaVariantsWithoutWcProductId()
        if (unmapped.isEmpty()) {
            log("WC mapper: all USEA variants already mapped", "INFO")
            return@withContext
        }

        log("WC mapper: ${unmapped.size} unmapped USEA variants, starting mapping...", "INFO")

        val byName = unmapped.groupBy { it.nameNormalized }
        var mapped = 0
        var failed = 0

        for ((name, variants) in byName) {
            try {
                val searchResults = searchProducts(httpClient, variants.first().nameOriginal)
                for (variant in variants) {
                    val productId = matchProductId(variant, searchResults)
                    if (productId != null) {
                        catalogStore.updateWcProductId(productId, variant.seller.name, variant.sku)
                        mapped++
                    } else {
                        failed++
                    }
                }
                delay(THROTTLE_MS)
            } catch (e: Exception) {
                log("WC mapper: failed to search '$name': ${e.message}", "WARNING")
                failed += variants.size
            }
        }

        log("WC mapper: done — $mapped mapped, $failed unmatched out of ${unmapped.size}", "INFO")
    }

    internal suspend fun searchProducts(httpClient: HttpClient, cardName: String): List<WpProduct> {
        val response = httpClient.get(SEARCH_ENDPOINT) {
            parameter("search", cardName)
            parameter("per_page", "20")
            header(HttpHeaders.UserAgent, "DeckLoot/1.0 (KMP)")
        }
        val body = response.bodyAsText()
        return json.decodeFromString<List<WpProduct>>(body)
    }
}
