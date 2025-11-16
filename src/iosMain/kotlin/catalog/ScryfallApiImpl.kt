package catalog

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS implementation of Scryfall API client.
 * Uses NSURLSession for native iOS networking.
 */
@OptIn(ExperimentalForeignApi::class)
actual object ScryfallApiImpl {
    /**
     * Fetch a URL and return the response body as a string.
     * Uses NSURLSession for proper iOS networking with error handling.
     *
     * @param url The URL to fetch
     * @return The response body as a string
     * @throws Exception if the request fails
     */
    actual suspend fun fetchUrl(url: String): String = suspendCancellableCoroutine { continuation ->
        val nsUrl = NSURL.URLWithString(url) 
            ?: run {
                continuation.resumeWithException(IllegalArgumentException("Invalid URL: $url"))
                return@suspendCancellableCoroutine
            }
        
        val request = NSURLRequest.requestWithURL(nsUrl)
        val session = NSURLSession.sharedSession
        
        val task = session.dataTaskWithRequest(request) { data: NSData?, response: NSURLResponse?, error: NSError? ->
            when {
                error != null -> {
                    continuation.resumeWithException(
                        Exception("Network error: ${error.localizedDescription}")
                    )
                }
                data == null -> {
                    continuation.resumeWithException(
                        Exception("No data received from $url")
                    )
                }
                else -> {
                    val httpResponse = response as? NSHTTPURLResponse
                    val statusCode = httpResponse?.statusCode?.toInt() ?: 0
                    
                    if (statusCode in 200..299) {
                        // Convert NSData to String
                        val bytes = ByteArray(data.length.toInt())
                        data.bytes?.let { dataBytes ->
                            bytes.usePinned { pinnedBytes ->
                                platform.posix.memcpy(
                                    pinnedBytes.addressOf(0),
                                    dataBytes,
                                    data.length
                                )
                            }
                        }
                        val responseString = bytes.decodeToString()
                        continuation.resume(responseString)
                    } else {
                        continuation.resumeWithException(
                            Exception("HTTP error: $statusCode for URL: $url")
                        )
                    }
                }
            }
        }
        
        task.resume()
        
        continuation.invokeOnCancellation {
            task.cancel()
        }
    }
}

