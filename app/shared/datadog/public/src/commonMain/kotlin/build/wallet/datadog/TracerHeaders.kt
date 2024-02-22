package build.wallet.datadog

/**
 * Contains a map for sending tracer headers in a network request
 *
 * @property headers - A map with the key being a http header and the value being the value of the
 * header
 */
data class TracerHeaders(val headers: Map<HttpHeader, HttpHeaderValue> = emptyMap())

typealias HttpHeader = String
typealias HttpHeaderValue = String
