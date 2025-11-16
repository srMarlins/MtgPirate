package catalog

/**
 * iOS implementation of Scryfall API client.
 * 
 * Note: This is a minimal implementation. For production use, implement with:
 * - platform.Foundation.NSURL and NSURLSession for networking
 * - kotlinx.cinterop for Objective-C/Swift interop
 * - Proper error handling and rate limiting
 * 
 * Since the iOS UI currently relies on cached catalog data from the database,
 * Scryfall API integration is optional for basic functionality.
 */
actual object ScryfallApiImpl {
    /**
     * Fetch a URL and return the response body as a string.
     * 
     * Production implementation example:
     * ```kotlin
     * import platform.Foundation.*
     * import kotlinx.cinterop.*
     * 
     * val url = NSURL.URLWithString(urlString)
     * val request = NSURLRequest.requestWithURL(url)
     * // Use NSURLSession to fetch...
     * ```
     */
    actual suspend fun fetchUrl(url: String): String {
        throw NotImplementedError(
            "iOS native networking not implemented. " +
            "App relies on cached catalog data in the database. " +
            "Implement with NSURLSession for live API access."
        )
    }
}
