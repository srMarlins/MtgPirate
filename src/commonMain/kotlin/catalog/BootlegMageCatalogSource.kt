package catalog

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import match.NameNormalizer
import model.CardVariant
import model.OrderItem
import model.Seller
import model.VariantType
import kotlin.math.roundToInt

/**
 * Catalog source for Bootleg Mage (bootlegmage.com).
 *
 * Fetching strategy:
 *  1. Try WooCommerce REST API (`/wp-json/wc/v3/products`) for structured JSON.
 *  2. If blocked (401/403) or unavailable, fall back to HTML scraping with Ksoup.
 *
 * Product names follow the format "Card Name - SET - Variant"
 * (e.g., "Lightning Bolt - M11 - Foil").
 */
class BootlegMageCatalogSource(
    private val client: HttpClient? = null,
) : CatalogSource {

    override val seller = Seller.BOOTLEG_MAGE

    private companion object {
        const val BASE_URL = "https://bootlegmage.com"
        /** WooCommerce Store API (public, no auth required). */
        const val WC_STORE_API_PRODUCTS = "$BASE_URL/wp-json/wc/store/v1/products"
        /** Legacy WooCommerce REST API (requires authentication). */
        const val WC_API_PRODUCTS = "$BASE_URL/wp-json/wc/v3/products"
        const val SHOP_URL = "$BASE_URL/all-cards/"
        const val CHECKOUT_URL = "$BASE_URL/deck-import/"
        const val PER_PAGE = 100
        const val MAX_PAGES = 20
    }

    override suspend fun fetchCatalog(log: (String) -> Unit): List<CardVariant> =
        withContext(Dispatchers.Default) {
            val http = client ?: defaultClient()
            // Strategy 1: WooCommerce Store API (public, no auth required)
            val storeVariants = tryStoreApi(http, log)
            if (storeVariants.isNotEmpty()) return@withContext storeVariants

            // Strategy 2: Legacy WooCommerce REST API (may require auth)
            val apiVariants = tryWooCommerceApi(http, log)
            if (apiVariants.isNotEmpty()) return@withContext apiVariants

            // Strategy 3: HTML scraping fallback
            val htmlVariants = tryHtmlScrape(http, log)
            if (htmlVariants.isNotEmpty()) return@withContext htmlVariants

            log("Bootleg Mage: no products found via Store API, REST API, or HTML")
            emptyList()
        }

    override suspend fun search(cardName: String): List<CardVariant> {
        val http = client ?: defaultClient()
        // Try Store API search first (public, no auth)
        try {
            val response = http.get(WC_STORE_API_PRODUCTS) {
                parameter("search", cardName)
                parameter("per_page", PER_PAGE.toString())
                header(HttpHeaders.UserAgent, "MtgPirate/1.0 (KMP)")
                accept(ContentType.Application.Json)
            }
            if (response.status.isSuccess()) {
                val results = parseStoreApiJson(response.bodyAsText())
                if (results.isNotEmpty()) return results
            }
        } catch (_: Exception) { /* fall through */ }

        // Fallback to legacy REST API
        try {
            val response = http.get(WC_API_PRODUCTS) {
                parameter("search", cardName)
                parameter("per_page", PER_PAGE.toString())
                header(HttpHeaders.UserAgent, "MtgPirate/1.0 (KMP)")
                accept(ContentType.Application.Json)
            }
            if (response.status.isSuccess()) {
                val results = parseWooCommerceJson(response.bodyAsText())
                if (results.isNotEmpty()) return results
            }
        } catch (_: Exception) { /* fall through */ }

        // Final fallback to HTML search
        return tryHtmlSearch(http, cardName)
    }

    override fun checkoutUrl(items: List<OrderItem>): String = CHECKOUT_URL

    override fun formatForExport(items: List<OrderItem>): String {
        // Bootleg Mage deck import format: "qty CardName" per line
        return items.joinToString("\n") { "${it.qty} ${it.variant.nameOriginal}" }
    }

    // ---- WooCommerce Store API (public) ----

    private suspend fun tryStoreApi(
        http: HttpClient,
        log: (String) -> Unit,
    ): List<CardVariant> {
        val allVariants = mutableListOf<CardVariant>()
        var page = 1
        try {
            while (page <= MAX_PAGES) {
                log("Bootleg Mage: fetching Store API page $page")
                val response = http.get(WC_STORE_API_PRODUCTS) {
                    parameter("per_page", PER_PAGE.toString())
                    parameter("page", page.toString())
                    header(HttpHeaders.UserAgent, "MtgPirate/1.0 (KMP)")
                    accept(ContentType.Application.Json)
                }
                if (!response.status.isSuccess()) {
                    log("Bootleg Mage: Store API returned ${response.status.value}")
                    break
                }
                val body = response.bodyAsText()
                val variants = parseStoreApiJson(body)
                if (variants.isEmpty()) break
                allVariants.addAll(variants)
                log("Bootleg Mage: page $page yielded ${variants.size} variants (total ${allVariants.size})")
                if (variants.size < PER_PAGE) break
                page++
            }
        } catch (e: Exception) {
            log("Bootleg Mage: Store API error — ${e.message}")
            return emptyList()
        }
        return allVariants
    }

    /**
     * Parse WooCommerce Store API JSON product array.
     *
     * The Store API uses a different structure from the v3 REST API:
     * - Prices are in a `prices` object with `price` as cents string (e.g. "350" = $3.50)
     * - Product names use space-separated format: "Card Name SET Variant #Number [Foil]"
     */
    internal fun parseStoreApiJson(json: String): List<CardVariant> {
        if (json.isBlank()) return emptyList()
        val variants = mutableListOf<CardVariant>()
        try {
            val parser = Json { ignoreUnknownKeys = true }
            val array = parser.parseToJsonElement(json).jsonArray
            for (element in array) {
                val obj = element.jsonObject
                val rawName = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "0"
                val sku = obj["sku"]?.jsonPrimitive?.contentOrNull
                val permalink = obj["permalink"]?.jsonPrimitive?.contentOrNull

                // Store API nests prices: {"prices": {"price": "350", ...}}
                val pricesObj = obj["prices"]?.jsonObject
                val priceStr = pricesObj?.get("price")?.jsonPrimitive?.contentOrNull ?: "0"
                // Price is already in cents as a string
                val priceCents = priceStr.toIntOrNull() ?: 0

                val imageUrl = obj["images"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("src")?.jsonPrimitive?.contentOrNull

                val parsed = parseStoreProductName(rawName) ?: continue

                variants.add(
                    CardVariant(
                        nameOriginal = parsed.cardName,
                        nameNormalized = NameNormalizer.normalize(parsed.cardName),
                        setCode = parsed.setCode,
                        sku = sku ?: "BM-$id",
                        variantType = parsed.variantType,
                        priceInCents = if (priceCents > 0) priceCents else parsed.variantType.defaultPriceInCents,
                        imageUrl = imageUrl,
                        seller = Seller.BOOTLEG_MAGE,
                        purchaseUri = permalink,
                        collectorNumber = parsed.collectorNumber,
                    )
                )
            }
        } catch (_: Exception) {
            // Malformed JSON — return whatever we parsed so far
        }
        return variants
    }

    /**
     * Parse the new Store API product name format.
     *
     * Format: "Card Name SETCODE Description #CollectorNumber [Foil]"
     * Examples:
     *  - "Ocelot Pride SCH Textless #47 Foil"
     *  - "Visions of Beyond SLD Full Art #1670"
     *  - "Lightning Bolt M11 Regular #1"
     *
     * Strategy: work backwards from the end to extract known tokens,
     * then everything remaining is the card name.
     */
    internal fun parseStoreProductName(rawName: String): ParsedStoreProduct? {
        val trimmed = rawName.trim()
        if (trimmed.isBlank()) return null

        // Check for Foil at the end
        val isFoil = trimmed.endsWith("Foil", ignoreCase = true)
        var working = if (isFoil) trimmed.dropLast(4).trim() else trimmed

        // Check for collector number (#NNN or #NNNa)
        val collectorRegex = Regex("""#(\d+[a-zA-Z]?)$""")
        val collectorMatch = collectorRegex.find(working)
        val collectorNumber = collectorMatch?.groupValues?.get(1)
        if (collectorMatch != null) {
            working = working.removeRange(collectorMatch.range).trim()
        }

        // Now working is like "Ocelot Pride SCH Textless" or "Lightning Bolt M11 Regular"
        // We need to find the set code. Set codes are 2-5 uppercase letter/digit tokens.
        // Strategy: split into words, find the first word that looks like a set code
        // (2-5 chars, all uppercase letters/digits) that isn't part of the card name.
        val words = working.split(" ")

        // Try to find a set code token. Start from word index 1 (skip first word which is always card name).
        var setCodeIndex = -1
        for (i in 1 until words.size) {
            val w = words[i]
            if (w.length in 2..5 && w.all { it.isUpperCase() || it.isDigit() } && w.any { it.isLetter() }) {
                setCodeIndex = i
                break
            }
        }

        return if (setCodeIndex >= 0) {
            val cardName = words.subList(0, setCodeIndex).joinToString(" ")
            val setCode = words[setCodeIndex].uppercase()
            val variantType = if (isFoil) VariantType.FOIL else {
                // Check remaining words for Holo
                val remaining = words.subList(setCodeIndex + 1, words.size).joinToString(" ")
                if (remaining.contains("Holo", ignoreCase = true)) VariantType.HOLO
                else VariantType.REGULAR
            }
            ParsedStoreProduct(cardName, setCode, variantType, collectorNumber)
        } else {
            // No set code found — treat entire string as card name
            val variantType = if (isFoil) VariantType.FOIL else VariantType.REGULAR
            ParsedStoreProduct(working, "UNK", variantType, collectorNumber)
        }
    }

    /** Parsed components from a Store API product name. */
    internal data class ParsedStoreProduct(
        val cardName: String,
        val setCode: String,
        val variantType: VariantType,
        val collectorNumber: String? = null,
    )

    // ---- Legacy WooCommerce REST API ----

    private suspend fun tryWooCommerceApi(
        http: HttpClient,
        log: (String) -> Unit,
    ): List<CardVariant> {
        val allVariants = mutableListOf<CardVariant>()
        var page = 1
        try {
            while (page <= MAX_PAGES) {
                log("Bootleg Mage: fetching WooCommerce API page $page")
                val response = http.get(WC_API_PRODUCTS) {
                    parameter("per_page", PER_PAGE.toString())
                    parameter("page", page.toString())
                    header(HttpHeaders.UserAgent, "MtgPirate/1.0 (KMP)")
                    accept(ContentType.Application.Json)
                }
                val status = response.status.value
                if (status == 401 || status == 403) {
                    log("Bootleg Mage: WooCommerce API returned $status — falling back to HTML")
                    return emptyList()
                }
                if (!response.status.isSuccess()) {
                    log("Bootleg Mage: WooCommerce API returned $status")
                    break
                }
                val body = response.bodyAsText()
                val variants = parseWooCommerceJson(body)
                if (variants.isEmpty()) break
                allVariants.addAll(variants)
                log("Bootleg Mage: page $page yielded ${variants.size} variants (total ${allVariants.size})")
                if (variants.size < PER_PAGE) break
                page++
            }
        } catch (e: Exception) {
            log("Bootleg Mage: WooCommerce API error — ${e.message}")
            return emptyList()
        }
        return allVariants
    }

    /**
     * Parse WooCommerce JSON product array into [CardVariant] list.
     * Expected JSON: `[{"id":1,"name":"Card - SET - Variant","price":"2.20",
     *   "permalink":"...","images":[{"src":"..."}]}, ...]`
     */
    internal fun parseWooCommerceJson(json: String): List<CardVariant> {
        if (json.isBlank()) return emptyList()
        val variants = mutableListOf<CardVariant>()
        try {
            val parser = Json { ignoreUnknownKeys = true }
            val array = parser.parseToJsonElement(json).jsonArray
            for (element in array) {
                val obj = element.jsonObject
                val rawName = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val priceStr = obj["price"]?.jsonPrimitive?.contentOrNull ?: "0"
                val permalink = obj["permalink"]?.jsonPrimitive?.contentOrNull
                val imageUrl = obj["images"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("src")?.jsonPrimitive?.contentOrNull
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "0"

                val parsed = parseProductName(rawName) ?: continue
                val priceCents = parsePriceToCents(priceStr)

                variants.add(
                    CardVariant(
                        nameOriginal = parsed.cardName,
                        nameNormalized = NameNormalizer.normalize(parsed.cardName),
                        setCode = parsed.setCode,
                        sku = "BM-$id",
                        variantType = parsed.variantType,
                        priceInCents = if (priceCents > 0) priceCents else parsed.variantType.defaultPriceInCents,
                        imageUrl = imageUrl,
                        seller = Seller.BOOTLEG_MAGE,
                        purchaseUri = permalink,
                    )
                )
            }
        } catch (_: Exception) {
            // Malformed JSON — return whatever we parsed so far
        }
        return variants
    }

    // ---- HTML scraping fallback ----

    private suspend fun tryHtmlScrape(
        http: HttpClient,
        log: (String) -> Unit,
    ): List<CardVariant> {
        return try {
            log("Bootleg Mage: fetching shop HTML")
            val response = http.get(SHOP_URL) {
                header(HttpHeaders.UserAgent, "MtgPirate/1.0 (KMP)")
                accept(ContentType.Text.Html)
            }
            if (!response.status.isSuccess()) {
                log("Bootleg Mage: shop HTML returned ${response.status.value}")
                return emptyList()
            }
            parseShopHtml(response.bodyAsText())
        } catch (e: Exception) {
            log("Bootleg Mage: HTML scraping failed — ${e.message}")
            emptyList()
        }
    }

    private suspend fun tryHtmlSearch(
        http: HttpClient,
        cardName: String,
    ): List<CardVariant> {
        return try {
            val response = http.get(SHOP_URL) {
                parameter("s", cardName)
                parameter("post_type", "product")
                header(HttpHeaders.UserAgent, "MtgPirate/1.0 (KMP)")
                accept(ContentType.Text.Html)
            }
            if (!response.status.isSuccess()) return emptyList()
            parseShopHtml(response.bodyAsText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parse WooCommerce shop HTML page into [CardVariant] list.
     * Looks for `.product` elements with title and price.
     */
    internal fun parseShopHtml(html: String): List<CardVariant> {
        if (html.isBlank()) return emptyList()
        val doc = Ksoup.parse(html)
        val variants = mutableListOf<CardVariant>()

        // WooCommerce product listing: <li class="product"> or <div class="product">
        val products = doc.select("li.product, div.product")
        var index = 0
        for (product in products) {
            val titleEl = product.selectFirst(".woocommerce-loop-product__title")
                ?: product.selectFirst("h2")
            val rawName = titleEl?.text()?.trim() ?: continue

            val priceEl = product.selectFirst(".woocommerce-Price-amount")
                ?: product.selectFirst(".price")
            val priceText = priceEl?.text() ?: "0"

            val link = product.selectFirst("a")?.attr("href")
            val imageUrl = product.selectFirst("img")?.attr("src")

            val parsed = parseProductName(rawName) ?: continue
            val priceCents = parsePriceToCents(priceText)
            index++

            variants.add(
                CardVariant(
                    nameOriginal = parsed.cardName,
                    nameNormalized = NameNormalizer.normalize(parsed.cardName),
                    setCode = parsed.setCode,
                    sku = "BM-HTML-$index",
                    variantType = parsed.variantType,
                    priceInCents = if (priceCents > 0) priceCents else parsed.variantType.defaultPriceInCents,
                    imageUrl = imageUrl,
                    seller = Seller.BOOTLEG_MAGE,
                    purchaseUri = link,
                )
            )
        }
        return variants
    }

    // ---- Shared parsing helpers ----

    /** Parsed components from a Bootleg Mage product name. */
    internal data class ParsedProduct(
        val cardName: String,
        val setCode: String,
        val variantType: VariantType,
    )

    /**
     * Parse "Card Name - SET - Variant" format.
     * Falls back gracefully when parts are missing:
     *  - "Card Name - SET" -> Regular variant
     *  - "Card Name" -> set "UNK", Regular variant
     */
    internal fun parseProductName(rawName: String): ParsedProduct? {
        val trimmed = rawName.trim()
        if (trimmed.isBlank()) return null

        val parts = trimmed.split(" - ").map { it.trim() }
        return when {
            parts.size >= 3 -> ParsedProduct(
                cardName = parts.dropLast(2).joinToString(" - "),
                setCode = parts[parts.size - 2].uppercase(),
                variantType = VariantType.fromString(parts.last()),
            )
            parts.size == 2 -> ParsedProduct(
                cardName = parts[0],
                setCode = parts[1].uppercase(),
                variantType = VariantType.REGULAR,
            )
            else -> ParsedProduct(
                cardName = trimmed,
                setCode = "UNK",
                variantType = VariantType.REGULAR,
            )
        }
    }

    private fun parsePriceToCents(raw: String): Int {
        val cleaned = raw.replace("[^0-9.]".toRegex(), "")
        if (cleaned.isBlank()) return 0
        return try {
            (cleaned.toDouble() * 100).roundToInt()
        } catch (_: NumberFormatException) {
            0
        }
    }

    private fun defaultClient(): HttpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
        install(Logging) { level = LogLevel.INFO }
        expectSuccess = false // We handle status codes manually
    }
}
