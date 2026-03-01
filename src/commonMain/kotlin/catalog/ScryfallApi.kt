package catalog

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Scryfall API client for fetching card images.
 * Uses Ktor HttpClient for multiplatform compatibility.
 * See: https://scryfall.com/docs/api
 */
object ScryfallApi {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private const val BASE_URL = "https://api.scryfall.com"

    /**
     * Create a configured HttpClient for Scryfall API requests.
     * Can be overridden with a custom client for testing or specific configuration.
     */
    private var httpClient: HttpClient? = null

    private fun getClient(): HttpClient {
        return httpClient ?: HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }.also { httpClient = it }
    }

    /**
     * Close the underlying HttpClient to release resources.
     * Should be called when the API is no longer needed.
     */
    fun close() {
        httpClient?.close()
        httpClient = null
    }

    @Serializable
    data class ImageUris(
        val small: String? = null,
        val normal: String? = null,
        val large: String? = null,
        val png: String? = null,
        @SerialName("art_crop")
        val artCrop: String? = null,
        @SerialName("border_crop")
        val borderCrop: String? = null
    )

    @Serializable
    data class CardFace(
        val name: String? = null,
        @SerialName("image_uris")
        val imageUris: ImageUris? = null
    )

    @Serializable
    data class ScryfallPrices(
        val usd: String? = null,
        @SerialName("usd_foil") val usdFoil: String? = null,
    )

    @Serializable
    data class ScryfallPurchaseUris(
        val tcgplayer: String? = null,
        val cardmarket: String? = null,
        val cardhoarder: String? = null,
    )

    @Serializable
    data class ScryfallCard(
        val id: String,
        val name: String,
        val set: String,
        @SerialName("collector_number")
        val collectorNumber: String,
        @SerialName("image_uris")
        val imageUris: ImageUris? = null,
        @SerialName("card_faces")
        val cardFaces: List<CardFace>? = null,
        val prices: ScryfallPrices? = null,
        @SerialName("purchase_uris")
        val purchaseUris: ScryfallPurchaseUris? = null,
        val lang: String? = "en"
    )

    data class ImageUrlPair(
        val small: String?,
        val normal: String?
    )

    /**
     * Fetch card by set code and collector number.
     * Returns the image URL (normal size by default) or null if not found.
     *
     * @param setCode The set code (e.g., "SLD", "MAR")
     * @param collectorNumber The collector number (e.g., "1425", "34")
     * @param imageSize The image size to return (small, normal, large, png, art_crop, border_crop)
     * @return The image URL or null if not found
     */
    suspend fun getCardImageUrl(
        setCode: String,
        collectorNumber: String,
        imageSize: String = "normal"
    ): String? {
        return try {
            val card = getCard(setCode, collectorNumber)
            extractImageUrl(card, imageSize)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch card data from Scryfall by set code and collector number.
     *
     * @param setCode The set code (e.g., "SLD", "MAR")
     * @param collectorNumber The collector number (e.g., "1425", "34")
     * @return The Scryfall card data
     * @throws Exception if the card is not found or the API request fails
     */
    suspend fun getCard(setCode: String, collectorNumber: String): ScryfallCard {
        val url = "$BASE_URL/cards/${setCode.lowercase()}/$collectorNumber"
        val response = fetchUrl(url)
        return json.decodeFromString<ScryfallCard>(response)
    }

    /**
     * Search for a card by name and optional set code.
     * Returns the first match or null if not found.
     *
     * @param cardName The card name
     * @param setCode Optional set code filter
     * @return The first matching card or null
     */
    suspend fun searchCard(cardName: String, setCode: String? = null): ScryfallCard? {
        return try {
            val query = if (setCode != null) {
                "!\"$cardName\" set:${setCode.lowercase()}"
            } else {
                "!\"$cardName\""
            }
            val encodedQuery = query.replace(" ", "+").replace("\"", "%22")
            val url = "$BASE_URL/cards/search?q=$encodedQuery&unique=prints"
            val response = fetchUrl(url)
            val jsonObj = json.parseToJsonElement(response).jsonObject
            val dataArray = jsonObj["data"]?.jsonArray
            if (dataArray != null && dataArray.isNotEmpty()) {
                json.decodeFromJsonElement(ScryfallCard.serializer(), dataArray[0])
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch multiple cards from Scryfall using their identifiers.
     * Supports names, set/collector number, or Scryfall IDs.
     * Max 75 identifiers per request as per Scryfall API.
     *
     * @param identifiers List of identifier maps (e.g., mapOf("name" to "Lightning Bolt"))
     * @return List of Scryfall cards found
     */
    suspend fun getCollection(identifiers: List<Map<String, String>>): List<ScryfallCard> {
        val allCards = mutableListOf<ScryfallCard>()
        val client = getClient()

        identifiers.chunked(75).forEach { chunk ->
            try {
                delay(75) // Respect Scryfall rate limit (50-100ms)
                val response = client.post("$BASE_URL/cards/collection") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        putJsonArray("identifiers") {
                            chunk.forEach { id ->
                                addJsonObject {
                                    id.forEach { (k, v) -> put(k, v) }
                                }
                            }
                        }
                    })
                }

                if (response.status.isSuccess()) {
                    val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
                    val data = body["data"]?.jsonArray
                    data?.forEach {
                        allCards.add(json.decodeFromJsonElement(ScryfallCard.serializer(), it))
                    }
                }
            } catch (e: Exception) {
                println("ScryfallApi.getCollection chunk failed: ${e.message}")
            }
        }
        return allCards
    }

    /**
     * Extract the image URL from a Scryfall card object.
     * Handles both single-faced and double-faced cards.
     *
     * @param card The Scryfall card data
     * @param imageSize The image size (small, normal, large, png, art_crop, border_crop)
     * @return The image URL or null if not available
     */
    private fun extractImageUrl(card: ScryfallCard, imageSize: String = "normal"): String? {
        // Try direct image_uris first (for single-faced cards)
        card.imageUris?.let { uris ->
            return when (imageSize) {
                "small" -> uris.small
                "large" -> uris.large
                "png" -> uris.png
                "art_crop" -> uris.artCrop
                "border_crop" -> uris.borderCrop
                else -> uris.normal
            }
        }

        // For double-faced/split cards, use the first face
        card.cardFaces?.firstOrNull()?.imageUris?.let { uris ->
            return when (imageSize) {
                "small" -> uris.small
                "large" -> uris.large
                "png" -> uris.png
                "art_crop" -> uris.artCrop
                "border_crop" -> uris.borderCrop
                else -> uris.normal
            }
        }

        return null
    }

    /**
     * Extract both small and normal image URLs from a Scryfall card.
     * Handles both single-faced and double-faced cards.
     *
     * @param card The Scryfall card data
     * @return An ImageUrlPair containing both small and normal URLs
     */
    fun extractImageUrlPair(card: ScryfallCard): ImageUrlPair {
        // Try direct image_uris first (for single-faced cards)
        card.imageUris?.let { uris ->
            return ImageUrlPair(small = uris.small, normal = uris.normal)
        }

        // For double-faced/split cards, use the first face
        card.cardFaces?.firstOrNull()?.imageUris?.let { uris ->
            return ImageUrlPair(small = uris.small, normal = uris.normal)
        }

        return ImageUrlPair(small = null, normal = null)
    }

    /**
     * Fetch a URL and return the response body as a string.
     * Uses Ktor HttpClient for multiplatform compatibility.
     */
    private suspend fun fetchUrl(url: String): String {
        val client = getClient()
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, "DeckLoot/1.0")
            accept(ContentType.Application.Json)
        }
        
        if (response.status.isSuccess()) {
            return response.bodyAsText()
        } else {
            throw Exception("HTTP ${response.status.value}: ${response.status.description}")
        }
    }
}

