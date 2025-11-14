package catalog

/**
 * iOS implementation of Scryfall API client.
 * Uses native iOS networking APIs.
 */
actual object ScryfallApiImpl {
    /**
     * Fetch a URL and return the response body as a string.
     * Note: This is a placeholder implementation for iOS.
     * A full implementation would use NSURLSession or similar.
     */
    actual suspend fun fetchUrl(url: String): String {
        // TODO: Implement native iOS networking with NSURLSession
        // For now, return empty string as placeholder
        throw NotImplementedError("iOS native networking not yet implemented")
    }
}
