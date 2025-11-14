package catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Platform-specific implementation for HTTP requests.
 */
expect object ScryfallApiImpl {
    suspend fun fetchUrl(url: String): String
}

/**
 * Scryfall API client for fetching card images.
 * See: https://scryfall.com/docs/api
 */
object ScryfallApi {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private const val BASE_URL = "https://api.scryfall.com"

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
        val lang: String? = "en"
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
     * Fetch a URL and return the response body as a string.
     * This delegates to the platform-specific implementation.
     */
    private suspend fun fetchUrl(url: String): String {
        return ScryfallApiImpl.fetchUrl(url)
    }
}

