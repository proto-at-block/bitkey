package build.wallet.serialization.json

/**
 * Indicates an error that occurred during JSON encoding.
 */
data class JsonEncodingError(override val cause: Throwable) : Error()

/**
 * Indicates an error that occurred during JSON decoding.
 */
data class JsonDecodingError(override val cause: Throwable) : Error()
