package build.wallet.memfault

import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.HandledError
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure

/**
 * Convenience side effect to log Memfault-specific networking errors, separating the severity for
 * expected network errors, like [HttpError.NetworkError] and 409 conflict, from other
 * unexpected errors, so we do not get alerted.
 *
 * Please use this instead of the more general [logFailure] for Memfault network-related
 * operations so we don't get alerted for expected issues.
 */
inline fun <V, E : NetworkingError> Result<V, E>.logMemfaultNetworkFailure(
  logLevelOverride: LogLevel? = null,
  errorLogLevel: LogLevel = LogLevel.Error,
  message: () -> String,
): Result<V, E> =
  onFailure { error ->
    val logLevel =
      logLevelOverride ?: when (error) {
        // Connectivity errors are expected, so we log them at a lower severity.
        is HttpError.NetworkError -> LogLevel.Info
        is HttpError.ClientError ->
          when (error.response.status.value) {
            // Log 409 Conflict errors as warnings.
            409 -> LogLevel.Warn
            else -> errorLogLevel
          }
        else -> errorLogLevel
      }

    log(
      level = logLevel,
      throwable = error.cause ?: HandledError(message())
    ) { "${message()}. $error" }
  }
