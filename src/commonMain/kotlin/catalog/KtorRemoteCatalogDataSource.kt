package catalog

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import model.Catalog
import model.VariantType

/**
 * Multiplatform remote catalog data source implemented with Ktor.
 * Works on both desktop and iOS via platform-specific Ktor engines.
 *
 * Parsing helpers (fillZeroPrices, extractTypePrices, etc.) live in [CatalogUtils]
 * so they are shared across the codebase without duplication.
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
                    val typePrices = CatalogUtils.canonicalizePriceMap(CatalogUtils.jsPriceMapFallback())
                    val catalog = CatalogCsvParser.parse(allCsv, typePrices)
                    if (catalog.variants.isNotEmpty()) return@withContext CatalogUtils.fillZeroPrices(catalog)
                }

                // Try HTML page
                log("Attempting HTML fetch: $urlSource")
                val html =
                    runCatching { fetchRaw(http, urlSource, log) }.onFailure { log("HTML fetch failed: ${it.message}") }
                        .getOrNull() ?: return@withContext null
                val typeMap = CatalogUtils.canonicalizePriceMap(
                    CatalogUtils.extractTypePrices(html).ifEmpty { CatalogUtils.jsPriceMapFallback() }
                )
                val exampleCsv = CatalogUtils.extractExampleCsv(html)
                if (exampleCsv != null) {
                    log("Parsing embedded example CSV block")
                    val catalog = CatalogCsvParser.parse(exampleCsv, typeMap)
                    if (catalog.variants.isNotEmpty()) return@withContext CatalogUtils.fillZeroPrices(catalog)
                }
                return@withContext runCatching { CatalogParser.parse(html) }
                    .onFailure { log("Table parse failed: ${it.message}") }
                    .getOrNull()?.let { CatalogUtils.fillZeroPrices(it) }
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

    private suspend fun fetchAllCsvPages(client: HttpClient, log: (String) -> Unit): String {
        // Try direct CSV
        log("Attempting direct CSV fetch: $urlApi")
        runCatching { fetchRaw(client, urlApi, log) }
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
            val csv = runCatching { fetchRaw(client, url, log) }.getOrNull() ?: break
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

    private suspend fun fetchRaw(client: HttpClient, url: String, log: (String) -> Unit): String {
        val resp = client.get(url) {
            header(HttpHeaders.UserAgent, "DeckLoot/1.0 (KMP)")
            accept(ContentType.Any)
        }
        val code = resp.status.value
        log("HTTP GET $url -> $code")
        if (code !in 200..299) throw IllegalStateException("Failed to fetch: $code")
        return resp.bodyAsText()
    }
}

