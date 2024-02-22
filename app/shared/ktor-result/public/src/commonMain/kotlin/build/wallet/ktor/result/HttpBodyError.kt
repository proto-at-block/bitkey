package build.wallet.ktor.result

import io.ktor.client.call.HttpClientCall

sealed class HttpBodyError : NetworkingError() {
  /**
   * [HttpClientCall.body] can be only called once. This error indicates that [HttpClientCall.body]
   * was already called.
   */
  data class DoubleReceiveError(
    override val cause: Throwable,
  ) : HttpBodyError()

  /**
   * Indicates that we failed to deserialize body from response.
   */
  data class SerializationError(
    override val cause: Throwable,
  ) : HttpBodyError()

  /**
   * Indicates some error that we don't handle.
   */
  data class UnhandledError(
    override val cause: Throwable,
  ) : HttpBodyError()
}
