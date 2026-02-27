package catalog

import io.ktor.client.HttpClient
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
    private val json = Json {
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
            header(HttpHeaders.UserAgent, "MtgPirate/1.0 (KMP)")
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
