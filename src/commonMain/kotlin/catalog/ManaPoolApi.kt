package catalog

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ManaPool API client for fetching bulk card pricing data.
 * Uses the public /api/v1/prices/singles endpoint (no auth required).
 * See: https://manapool.com
 */
object ManaPoolApi {
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private const val BASE_URL = "https://manapool.com"

    private var httpClient: HttpClient? = null

    private fun getClient(): HttpClient {
        return httpClient ?: HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }.also { httpClient = it }
    }

    fun close() {
        httpClient?.close()
        httpClient = null
    }

    suspend fun fetchAllSingles(): List<ManaPoolSingle> {
        val client = getClient()
        val response = client.get("$BASE_URL/api/v1/prices/singles") {
            header(HttpHeaders.UserAgent, "DeckLoot/1.0 (KMP)")
            accept(ContentType.Application.Json)
        }
        if (response.status.isSuccess()) {
            val body = response.bodyAsText()
            val parsed = json.decodeFromString<ManaPoolPricesResponse>(body)
            return parsed.data
        } else {
            throw Exception("ManaPool API HTTP ${response.status.value}: ${response.status.description}")
        }
    }

    /**
     * Fetch the ManaPool catalog and emit [ManaPoolSingle] objects in batches.
     *
     * Unlike [fetchAllSingles], this parses the JSON body in chunks — extracting
     * groups of [batchSize] `{...}` objects from the `"data"` array and
     * deserializing each group as a JSON array in one `decodeFromString` call.
     * This avoids the ~511MB peak from deserializing all 95K objects at once
     * and reduces parse calls from 95K to ~190 (at batch size 500).
     *
     * Peak memory: ~45MB (body string) + ~1MB (chunk) ≈ ~46MB.
     */
    suspend fun streamSingles(
        batchSize: Int = 500,
        onBatch: suspend (List<ManaPoolSingle>) -> Unit,
    ) {
        val client = getClient()
        val response = client.get("$BASE_URL/api/v1/prices/singles") {
            header(HttpHeaders.UserAgent, "DeckLoot/1.0 (KMP)")
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            throw Exception("ManaPool API HTTP ${response.status.value}: ${response.status.description}")
        }

        val body = response.bodyAsText()
        parseDataArray(body, batchSize, onBatch)
    }

    /**
     * Iterate over the `"data":[...]` array in a JSON body string,
     * extracting objects in chunks of [batchSize] and deserializing each
     * chunk as a JSON array in a single `decodeFromString` call.
     *
     * This reduces ~95K individual parse calls to ~190 batch parses,
     * cutting parse time from ~30s to ~2-3s on Android.
     */
    internal suspend fun parseDataArray(
        body: String,
        batchSize: Int,
        onBatch: suspend (List<ManaPoolSingle>) -> Unit,
    ) {
        // Locate the start of "data":[ array
        val dataKeyIdx = body.indexOf("\"data\"")
        if (dataKeyIdx == -1) throw Exception("ManaPool JSON missing \"data\" key")

        var pos = body.indexOf('[', dataKeyIdx + 6)
        if (pos == -1) throw Exception("ManaPool JSON missing data array")
        pos++ // skip '['

        val len = body.length
        // Collect start/end positions for objects in each chunk
        val objectRanges = ArrayList<Pair<Int, Int>>(batchSize)

        while (pos < len) {
            // Skip whitespace and commas between objects
            while (pos < len) {
                val ch = body[pos]
                if (ch == '{') break
                if (ch == ']') {
                    // End of array — flush remaining chunk
                    if (objectRanges.isNotEmpty()) {
                        emitChunk(body, objectRanges, onBatch)
                    }
                    return
                }
                pos++
            }
            if (pos >= len) break

            // Extract one complete {...} object by tracking brace depth
            val objStart = pos
            var depth = 0
            var inString = false
            var escape = false
            while (pos < len) {
                val ch = body[pos]
                if (escape) {
                    escape = false
                } else if (ch == '\\' && inString) {
                    escape = true
                } else if (ch == '"') {
                    inString = !inString
                } else if (!inString) {
                    if (ch == '{') depth++
                    else if (ch == '}') {
                        depth--
                        if (depth == 0) {
                            pos++ // include closing brace
                            break
                        }
                    }
                }
                pos++
            }

            objectRanges.add(objStart to pos)

            if (objectRanges.size >= batchSize) {
                coroutineContext.ensureActive()
                emitChunk(body, objectRanges, onBatch)
                objectRanges.clear()
            }
        }

        // Flush any remainder
        if (objectRanges.isNotEmpty()) {
            emitChunk(body, objectRanges, onBatch)
        }
    }

    /**
     * Build a JSON array string from object ranges, parse as a batch.
     */
    private suspend fun emitChunk(
        body: String,
        ranges: List<Pair<Int, Int>>,
        onBatch: suspend (List<ManaPoolSingle>) -> Unit,
    ) {
        val sb = StringBuilder(ranges.size * 300)
        sb.append('[')
        for (i in ranges.indices) {
            if (i > 0) sb.append(',')
            sb.append(body, ranges[i].first, ranges[i].second)
        }
        sb.append(']')
        try {
            val parsed = json.decodeFromString<List<ManaPoolSingle>>(sb.toString())
            if (parsed.isNotEmpty()) onBatch(parsed)
        } catch (_: Exception) {
            // Fallback: parse individually so one bad object doesn't lose the batch
            val results = ArrayList<ManaPoolSingle>(ranges.size)
            for ((start, end) in ranges) {
                try {
                    results.add(json.decodeFromString<ManaPoolSingle>(body.substring(start, end)))
                } catch (_: Exception) {
                    // Skip malformed object
                }
            }
            if (results.isNotEmpty()) onBatch(results)
        }
    }
}

@Serializable
data class ManaPoolPricesResponse(
    val meta: ManaPoolMeta,
    val data: List<ManaPoolSingle>,
)

@Serializable
data class ManaPoolMeta(
    @SerialName("as_of") val asOf: String = "",
    @SerialName("base_url") val baseUrl: String = "",
)

@Serializable
data class ManaPoolSingle(
    val name: String,
    @SerialName("set_code") val setCode: String,
    val number: String? = null,
    @SerialName("scryfall_id") val scryfallId: String? = null,
    @SerialName("available_quantity") val availableQuantity: Int = 0,
    @SerialName("price_cents") val priceCents: Int? = null,
    @SerialName("price_cents_foil") val priceCentsFoil: Int? = null,
    @SerialName("price_cents_etched") val priceCentsEtched: Int? = null,
    @SerialName("price_market") val priceMarket: Int? = null,
    @SerialName("price_market_foil") val priceMarketFoil: Int? = null,
    val url: String? = null,
)
