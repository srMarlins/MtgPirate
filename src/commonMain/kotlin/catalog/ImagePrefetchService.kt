package catalog

import database.CatalogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import model.CardVariant

/**
 * Coroutine-based batch prefetch pipeline for card images.
 *
 * Accepts batches of [CardVariant] objects and resolves their image URLs
 * via the Scryfall collection API using a multi-pass identification pipeline:
 *
 * 1. **Pass 1**: Cards with collector numbers -> exact set + collector_number lookup
 * 2. **Pass 2**: Remaining unmatched -> name + set lookup
 * 3. **Pass 3**: Still remaining -> name-only lookup
 *
 * Matched image URLs (small + normal) are persisted to the database via [CatalogStore].
 */
class ImagePrefetchService(
    private val catalogStore: CatalogStore,
    private val scryfallApi: ScryfallApi = ScryfallApi,
    private val log: (String, String) -> Unit = { _, _ -> },
) {
    private val channel = Channel<List<CardVariant>>(capacity = Channel.BUFFERED)
    private val inFlightSkus = mutableSetOf<String>()
    private val mutex = Mutex()

    /**
     * Queue a batch of variants for image prefetching.
     * Variants that already have both image URLs or are already in-flight are filtered out.
     */
    suspend fun requestPrefetch(variants: List<CardVariant>) {
        val toFetch = mutex.withLock {
            variants.filter { v ->
                val needsImages = v.smallImageUrl == null || v.imageUrl == null
                val notInFlight = v.sku !in inFlightSkus
                if (needsImages && notInFlight) {
                    inFlightSkus.add(v.sku)
                    true
                } else {
                    false
                }
            }
        }
        if (toFetch.isNotEmpty()) {
            channel.send(toFetch)
        }
    }

    /**
     * Launch a coroutine that processes queued batches from the channel.
     * Should be called once at startup.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            for (batch in channel) {
                try {
                    processBatch(batch)
                } catch (e: Exception) {
                    log("ImagePrefetchService", "Batch processing failed: ${e.message}")
                } finally {
                    mutex.withLock {
                        batch.forEach { inFlightSkus.remove(it.sku) }
                    }
                }
            }
        }
    }

    private suspend fun processBatch(variants: List<CardVariant>) {
        val matched = mutableMapOf<String, ScryfallApi.ImageUrlPair>()
        var remaining = variants.toList()

        // Pass 1: Exact match by set + collector number
        val exactIds = buildExactIdentifiers(remaining)
        if (exactIds.isNotEmpty()) {
            log("ImagePrefetchService", "Pass 1: fetching ${exactIds.size} by set+collector_number")
            val results = scryfallApi.getCollection(exactIds)
            val pass1Matched = matchBatchResults(remaining, results)
            matched.putAll(pass1Matched)
            remaining = remaining.filter { it.sku !in matched }
        }

        // Pass 2: Name + set match
        if (remaining.isNotEmpty()) {
            val nameSetIds = buildNameSetIdentifiers(remaining)
            if (nameSetIds.isNotEmpty()) {
                log("ImagePrefetchService", "Pass 2: fetching ${nameSetIds.size} by name+set")
                val results = scryfallApi.getCollection(nameSetIds)
                val pass2Matched = matchBatchResults(remaining, results)
                matched.putAll(pass2Matched)
                remaining = remaining.filter { it.sku !in matched }
            }
        }

        // Pass 3: Name-only match
        if (remaining.isNotEmpty()) {
            val nameOnlyIds = buildNameOnlyIdentifiers(remaining)
            if (nameOnlyIds.isNotEmpty()) {
                log("ImagePrefetchService", "Pass 3: fetching ${nameOnlyIds.size} by name only")
                val results = scryfallApi.getCollection(nameOnlyIds)
                val pass3Matched = matchBatchResults(remaining, results)
                matched.putAll(pass3Matched)
            }
        }

        // Persist matched URLs to the database
        for ((sku, urls) in matched) {
            val variant = variants.first { it.sku == sku }
            if (urls.normal != null) {
                catalogStore.updateVariantImageUrls(
                    sku = sku,
                    imageUrl = urls.normal,
                    smallImageUrl = urls.small,
                    seller = variant.seller.name,
                )
            }
        }

        log(
            "ImagePrefetchService",
            "Batch complete: ${matched.size}/${variants.size} variants resolved"
        )
    }

    companion object {
        /**
         * Build identifiers for exact match: set + collector_number.
         * Only includes variants that have a non-null collector number.
         */
        fun buildExactIdentifiers(variants: List<CardVariant>): List<Map<String, String>> {
            return variants
                .filter { it.collectorNumber != null }
                .map { v ->
                    mapOf(
                        "set" to v.setCode.lowercase(),
                        "collector_number" to v.collectorNumber!!,
                    )
                }
        }

        /**
         * Build identifiers for name + set match.
         */
        fun buildNameSetIdentifiers(variants: List<CardVariant>): List<Map<String, String>> {
            return variants.map { v ->
                mapOf(
                    "name" to v.nameOriginal,
                    "set" to v.setCode.lowercase(),
                )
            }
        }

        /**
         * Build identifiers for name-only match (no set filter).
         */
        fun buildNameOnlyIdentifiers(variants: List<CardVariant>): List<Map<String, String>> {
            return variants.map { v ->
                mapOf("name" to v.nameOriginal)
            }
        }

        /**
         * Match Scryfall batch results back to variants.
         *
         * First tries to match by set + collector number (exact).
         * Falls back to matching by name (case-insensitive).
         *
         * @return Map of SKU to ImageUrlPair for matched variants
         */
        fun matchBatchResults(
            variants: List<CardVariant>,
            scryfallCards: List<ScryfallApi.ScryfallCard>,
        ): Map<String, ScryfallApi.ImageUrlPair> {
            val result = mutableMapOf<String, ScryfallApi.ImageUrlPair>()

            // Index scryfall cards for faster lookup
            val bySetAndNumber = scryfallCards.associateBy { "${it.set}:${it.collectorNumber}" }
            val byName = scryfallCards.groupBy { it.name.lowercase() }

            for (variant in variants) {
                if (variant.sku in result) continue

                // Try exact match by set + collector number
                if (variant.collectorNumber != null) {
                    val key = "${variant.setCode.lowercase()}:${variant.collectorNumber}"
                    val card = bySetAndNumber[key]
                    if (card != null) {
                        val urls = ScryfallApi.extractImageUrlPair(card)
                        result[variant.sku] = urls
                        continue
                    }
                }

                // Fall back to name match (case-insensitive)
                val nameMatches = byName[variant.nameOriginal.lowercase()]
                if (!nameMatches.isNullOrEmpty()) {
                    val card = nameMatches.first()
                    val urls = ScryfallApi.extractImageUrlPair(card)
                    result[variant.sku] = urls
                }
            }

            return result
        }
    }
}
