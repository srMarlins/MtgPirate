package catalog

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import model.Catalog
import java.nio.file.Files
import java.nio.file.Path
import java.net.HttpURLConnection
import java.net.URL
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CatalogFetcher {
    private val json = Json { prettyPrint = true }
    private const val URL_SOURCE = "https://www.usmtgproxy.com/wp-content/uploads/singlecardslist.html"
    private const val URL_API = "https://www.usmtgproxy.com/wp-content/uploads/single-card-list.csv"
    private val dataDir: Path = Path.of("data")
    private val cacheFile: Path = dataDir.resolve("catalog.json")

    private fun canonicalType(raw: String): String {
        val t = raw.trim().lowercase()
        return when {
            "foil" in t -> "Foil"
            "holo" in t -> "Holo"
            else -> "Regular"
        }
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
        // Keep same structure
        return if (changed) fixed else fixed
    }

    /**
     * Original load method (kept for compatibility). Force refresh bypasses cache.
     */
    suspend fun load(forceRefresh: Boolean = false, log: (String) -> Unit = {}): Catalog? = withContext(Dispatchers.IO) {
        // Remote-first: always attempt fetch; fallback handled inside loadWithCacheAge
        loadWithCacheAge(maxAge = Duration.ZERO, forceRefresh = forceRefresh, log = log)
    }

    /**
     * Load the catalog honoring a max cache age. If [forceRefresh] is true always hits network.
     * Provides basic logging via [log] callback.
     */
    fun loadWithCacheAge(maxAge: Duration, forceRefresh: Boolean = false, log: (String) -> Unit = {}): Catalog? {
        try {
            if (!Files.exists(dataDir)) Files.createDirectories(dataDir)
            val cacheExists = cacheFile.exists()
            val cacheFresh = if (cacheExists) {
                try {
                    val modified = Files.getLastModifiedTime(cacheFile).toMillis()
                    val ageMillis = System.currentTimeMillis() - modified
                    ageMillis < maxAge.inWholeMilliseconds
                } catch (_: Exception) { false }
            } else false
            val useCache = !forceRefresh && cacheExists && maxAge > Duration.ZERO && cacheFresh
            if (useCache) {
                log("Loading catalog from cache (fresh=$cacheFresh age<=${maxAge.inWholeSeconds}s)")
                return try {
                    val cached = json.decodeFromString<Catalog>(cacheFile.readText())
                    fillZeroPrices(cached)
                } catch (e: Exception) {
                    log("Failed to decode cached catalog: ${e.message}"); null
                }
            }
            // Attempt CSV first
            val catalog = tryCsvThenHtml(log)
            if (catalog != null) {
                cacheFile.writeText(json.encodeToString(catalog))
                log("Catalog parsed with ${catalog.variants.size} variants; cache updated")
                return catalog
            }
            log("All fetch strategies failed; falling back to cache if available")
            if (cacheFile.exists()) {
                return try {
                    val cached = json.decodeFromString<Catalog>(cacheFile.readText())
                    fillZeroPrices(cached).also { log("Loaded fallback cached catalog") }
                } catch (ex: Exception) {
                    log("Fallback cache decode failed: ${ex.message}"); null
                }
            }
            return null
        } catch (e: Exception) {
            log("Error fetching/parsing catalog: ${e.message}")
            if (cacheFile.exists()) {
                return try { json.decodeFromString<Catalog>(cacheFile.readText()).also { log("Loaded fallback cached catalog") } } catch (ex: Exception) {
                    log("Fallback cache decode failed: ${ex.message}"); null
                }
            }
            return null
        }
    }

    private fun fetchAllCsvPages(log: (String) -> Unit): String {
        // First try direct CSV endpoint (no pagination). Many WP uploads are a single file.
        log("Attempting direct CSV fetch: $URL_API")
        runCatching { fetchRaw(URL_API, log) }
            .onSuccess { csv -> if (csv.isNotBlank()) return csv }
            .onFailure { ex -> log("Direct CSV fetch failed: ${ex.message}") }

        // Fallback: attempt naive pagination but with safeguards against infinite loops.
        val allRows = mutableListOf<String>()
        var page = 1
        var header: String? = null
        val seenBodies = mutableSetOf<Int>()
        val maxPages = 10
        while (page <= maxPages) {
            val url = "$URL_API?page=$page"
            log("Fetching CSV page: $url")
            val csv = runCatching { fetchRaw(url, log) }.getOrNull() ?: break
            val lines = csv.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) break
            val bodyHash = lines.drop(1).joinToString("\n").hashCode()
            if (!seenBodies.add(bodyHash)) {
                log("Duplicate CSV page detected at page=$page; stopping pagination")
                break
            }
            if (header == null) {
                header = lines.first()
                allRows.add(header!!)
            }
            allRows.addAll(lines.drop(1))
            // Heuristic: if less than 20 rows, assume last page
            if (lines.size < 21) break
            page++
        }
        return allRows.joinToString("\n")
    }

    private fun canonicalizePriceMap(map: Map<String, Double>): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        for ((k, v) in map) {
            val t = canonicalType(k)
            // prefer larger value if duplicates, but any non-zero is fine
            val existing = out[t]
            if (existing == null || v > existing) out[t] = v
        }
        return out
    }

    private fun tryCsvThenHtml(log: (String) -> Unit): Catalog? {
        // 1. Try to fetch all CSV pages
        val allCsv = fetchAllCsvPages(log)
        if (allCsv.isNotBlank()) {
            val typePrices = canonicalizePriceMap(jsPriceMapFallback())
            val catalog = CatalogCsvParser.parse(allCsv, typePrices)
            if (catalog.variants.isNotEmpty()) return fillZeroPrices(catalog)
        }
        // 2. HTML page
        log("Attempting HTML fetch: $URL_SOURCE")
        val html = runCatching { fetchRaw(URL_SOURCE, log) }.onFailure { log("HTML fetch failed: ${it.message}") }.getOrNull() ?: return null
        val typeMap = canonicalizePriceMap(extractTypePrices(html).ifEmpty { jsPriceMapFallback() })
        val exampleCsv = extractExampleCsv(html)
        if (exampleCsv != null) {
            log("Parsing embedded example CSV block")
            val catalog = CatalogCsvParser.parse(exampleCsv, typeMap)
            if (catalog.variants.isNotEmpty()) return fillZeroPrices(catalog)
        }
        return runCatching { CatalogParser.parse(html) }.onSuccess { /* no-op */ }.onFailure { log("Table parse failed: ${it.message}") }.getOrNull()?.let { fillZeroPrices(it) }
    }

    private fun jsPriceMapFallback(): Map<String, Double> = mapOf(
        "Regular" to 2.2,
        "Holo" to 3.0,
        "Foil" to 3.5
    )

    private fun fetchRaw(urlStr: String, log: (String) -> Unit): String {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MtgPirate/1.0")
            setRequestProperty("Accept", "*/*")
        }
        val code = conn.responseCode
        log("HTTP GET $urlStr -> $code")
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code without body"
            throw IllegalStateException("Failed to fetch: $code $err")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    // Extract object literal CARD_TYPE_PRICES = { ... } mapping to Map<String, Double>
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

    // Extract EXAMPLE_CSV multi-line JS string assignment if present (e.g., const EXAMPLE_CSV = `...`)
    private fun extractExampleCsv(html: String): String? {
        val regex = Regex("EXAMPLE_CSV\\s*=\\s*`([\\s\\S]*?)`")
        val match = regex.find(html) ?: return null
        return match.groupValues[1].trim()
    }
}
