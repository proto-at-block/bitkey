package build.wallet.ktor.result

import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse

/**
 * Represents a failure result produced by [HttpClient.catching].
 */
sealed class HttpError : NetworkingError() {
  /**
   * Represents client (40x) errors.
   */
  data class ClientError(
    val response: HttpResponse,
  ) : HttpError() {
    init {
      require(response.status.isClientError) {
        "Expected a client error but got ${response.status}"
      }
    }
  }

  /**
   * Represent server (50x) errors.
   */
  data class ServerError(
    val response: HttpResponse,
  ) : HttpError() {
    init {
      require(response.status.isServerError) {
        "Expected a server error but got ${response.status}"
      }
    }
  }

  /**
   * Represents I/O and internet connectivity errors.
   */
  data class NetworkError(
    override val cause: Throwable,
  ) : HttpError()

  /**
   * Represents misc http errors that we do not yet explicitly handle.
   */
  data class UnhandledError(
    val response: HttpResponse,
  ) : HttpError()

  /**
   * Represents misc exceptions that we do not yet explicitly handle.
   */
  data class UnhandledException(
    override val cause: Throwable,
  ) : HttpError()
}
