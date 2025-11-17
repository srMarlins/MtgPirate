package catalog

import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import model.Catalog

/**
 * Multiplatform remote catalog data source implemented with Ktor.
 * This mirrors the desktop fetching strategy but uses HttpClient so it runs on iOS.
 */
class KtorRemoteCatalogDataSource(private val client: HttpClient? = null) : CatalogDataSource {
    private val urlSource = "https://www.usmtgproxy.com/wp-content/uploads/singlecardslist.html"
    private val urlApi = "https://www.usmtgproxy.com/wp-content/uploads/single-card-list.csv"

    override suspend fun load(forceRefresh: Boolean, log: (String) -> Unit): Catalog? =
        withContext(Dispatchers.Default) {
            val http = client ?: defaultClient()
            try {
                // Try CSV first (possibly paginated)
                val allCsv = fetchAllCsvPages(http, log)
                if (allCsv.isNotBlank()) {
                    val typePrices = canonicalizePriceMap(jsPriceMapFallback())
                    val catalog = CatalogCsvParser.parse(allCsv, typePrices)
                    if (catalog.variants.isNotEmpty()) return@withContext fillZeroPrices(catalog)
                }

                // Try HTML page
                log("Attempting HTML fetch: $urlSource")
                val html =
                    runCatching { fetchRaw(http, urlSource, log) }.onFailure { log("HTML fetch failed: ${it.message}") }
                        .getOrNull() ?: return@withContext null
                val typeMap = canonicalizePriceMap(extractTypePrices(html).ifEmpty { jsPriceMapFallback() })
                val exampleCsv = extractExampleCsv(html)
                if (exampleCsv != null) {
                    log("Parsing embedded example CSV block")
                    val catalog = CatalogCsvParser.parse(exampleCsv, typeMap)
                    if (catalog.variants.isNotEmpty()) return@withContext fillZeroPrices(catalog)
                }
                return@withContext runCatching { CatalogParser.parse(html) }.onFailure { log("Table parse failed: ${it.message}") }
                    .getOrNull()?.let { fillZeroPrices(it) }
            } catch (e: Exception) {
                log("Error fetching/parsing catalog: ${e.message}")
                return@withContext null
            }
        }

    private fun defaultClient(): HttpClient = HttpClient {
        install(Logging) { level = LogLevel.INFO }
        // keep configuration minimal; engine provided by platform-specific deps
        expectSuccess = true
    }

    private fun fillZeroPrices(catalog: Catalog): Catalog {
        val centsMap = mapOf(
            "Regular" to 220,
            "Holo" to 300,
            "Foil" to 350
        )
        var changed = false
        val updated = catalog.variants.map { v ->
            if (v.priceInCents <= 0) {
                changed = true
                val t = canonicalType(v.variantType)
                v.copy(priceInCents = centsMap[t] ?: 0, variantType = t)
            } else v.copy(variantType = canonicalType(v.variantType))
        }
        val fixed = Catalog(updated)
        return if (changed) fixed else fixed
    }

    private fun canonicalType(raw: String): String {
        val t = raw.trim().lowercase()
        return when {
            "foil" in t -> "Foil"
            "holo" in t -> "Holo"
            else -> "Regular"
        }
    }

    private fun fetchAllCsvPages(client: HttpClient, log: (String) -> Unit): String {
        // Try direct CSV
        log("Attempting direct CSV fetch: $urlApi")
        runCatching { runBlockingLike { fetchRaw(client, urlApi, log) } }
            .onSuccess { csv -> if (csv.isNotBlank()) return csv }
            .onFailure { ex -> log("Direct CSV fetch failed: ${ex.message}") }

        val allRows = mutableListOf<String>()
        var page = 1
        var header: String? = null
        val seenBodies = mutableSetOf<Int>()
        val maxPages = 10
        while (page <= maxPages) {
            val url = "$urlApi?page=$page"
            log("Fetching CSV page: $url")
            val csv = runCatching { runBlockingLike { fetchRaw(client, url, log) } }.getOrNull() ?: break
            val lines = csv.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) break
            val bodyHash = lines.drop(1).joinToString("\n").hashCode()
            if (!seenBodies.add(bodyHash)) {
                log("Duplicate CSV page detected at page=$page; stopping pagination")
                break
            }
            if (header == null) {
                header = lines.first()
                allRows.add(header)
            }
            allRows.addAll(lines.drop(1))
            if (lines.size < 21) break
            page++
        }
        return allRows.joinToString("\n")
    }

    // Helper to call suspend fetchRaw inside non-suspend context used above
    private fun <T> runBlockingLike(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }

    private suspend fun fetchRaw(client: HttpClient, url: String, log: (String) -> Unit): String {
        val resp = client.get(url) {
            header(HttpHeaders.UserAgent, "MtgPirate/1.0 (KMP)")
            accept(ContentType.Any)
        }
        val code = resp.status.value
        log("HTTP GET $url -> $code")
        if (code !in 200..299) throw IllegalStateException("Failed to fetch: $code")
        return resp.bodyAsText()
    }

    private fun extractTypePrices(html: String): Map<String, Double> {
        val regex = Regex("CARD_TYPE_PRICES\\s*=\\s*\\{([^}]+)\\}")
        val match = regex.find(html) ?: return emptyMap()
        val body = match.groupValues[1]
        return body.split(',').mapNotNull { entry ->
            val parts = entry.split(':').map { it.trim() }
            if (parts.size == 2) {
                val key = parts[0].trim().trim('"', '\'')
                val value = parts[1].replace("[^0-9.]".toRegex(), "").toDoubleOrNull()
                if (key.isNotBlank() && value != null) key to value else null
            } else null
        }.toMap()
    }

    private fun extractExampleCsv(html: String): String? {
        val regex = Regex("EXAMPLE_CSV\\s*=\\s*`([\\s\\S]*?)`")
        val match = regex.find(html) ?: return null
        return match.groupValues[1].trim()
    }

    private fun canonicalizePriceMap(map: Map<String, Double>): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        for ((k, v) in map) {
            val t = canonicalType(k)
            val existing = out[t]
            if (existing == null || v > existing) out[t] = v
        }
        return out
    }

    private fun jsPriceMapFallback(): Map<String, Double> = mapOf(
        "Regular" to 2.2,
        "Holo" to 3.0,
        "Foil" to 3.5
    )
}

