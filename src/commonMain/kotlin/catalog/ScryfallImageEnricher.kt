package catalog

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.CardVariant
import platform.currentTimeMillis

/**
 * Service for enriching catalog card variants with Scryfall image URLs.
 * Implements rate limiting to respect Scryfall's API limits (10 requests per second).
 */
object ScryfallImageEnricher {
    private const val RATE_LIMIT_DELAY_MS = 100L // 10 requests per second
    private val mutex = Mutex()
    private var lastRequestTime = 0L

    /**
     * Enrich a single card variant with its Scryfall image URL.
     * Uses collector number and set code for exact matching when available.
     * Falls back to name + set search if collector number is not available.
     *
     * @param variant The card variant to enrich
     * @param imageSize The image size to fetch (normal, small, large, png, art_crop, border_crop)
     * @param log Optional logging callback
     * @return The enriched card variant with imageUrl set, or the original variant if fetch fails
     */
    suspend fun enrichVariant(
        variant: CardVariant,
        imageSize: String = "normal",
        log: ((String) -> Unit)? = null
    ): CardVariant {
        // Skip if already has an image URL
        if (variant.imageUrl != null) return variant

        // Rate limiting — synchronized to prevent concurrent coroutines from bypassing the limit
        mutex.withLock {
            val now = currentTimeMillis()
            val timeSinceLastRequest = now - lastRequestTime
            if (timeSinceLastRequest < RATE_LIMIT_DELAY_MS) {
                delay(RATE_LIMIT_DELAY_MS - timeSinceLastRequest)
            }
            lastRequestTime = currentTimeMillis()
        }

        try {
            val imageUrl = if (variant.collectorNumber != null) {
                // Try exact match with collector number
                log?.invoke("Fetching image for ${variant.nameOriginal} (${variant.setCode} #${variant.collectorNumber})")
                ScryfallApi.getCardImageUrl(variant.setCode, variant.collectorNumber, imageSize)
            } else {
                // Fall back to name + set search
                log?.invoke("Fetching image for ${variant.nameOriginal} (${variant.setCode}) via search")
                val card = ScryfallApi.searchCard(variant.nameOriginal, variant.setCode)
                if (card != null) {
                    ScryfallApi.getCardImageUrl(card.set, card.collectorNumber, imageSize)
                } else null
            }

            if (imageUrl != null) {
                log?.invoke("Found image URL for ${variant.nameOriginal}")
                return variant.copy(imageUrl = imageUrl)
            } else {
                log?.invoke("No image found for ${variant.nameOriginal}")
            }
        } catch (e: Exception) {
            log?.invoke("Error fetching image for ${variant.nameOriginal}: ${e.message}")
        }

        return variant
    }

}

