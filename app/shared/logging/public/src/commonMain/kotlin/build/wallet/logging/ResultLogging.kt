package build.wallet.logging

import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.LogLevel.Info
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure

/**
 * Convenience side effect to log networking errors, separating the severity for
 * expected network connectivity errors, [HttpError.NetworkError], from other
 *  unexpected errors,  so we do not get alerted.
 *
 * Please use this instead of the more general [logFailure] for network-related
 * operations so we don't get alerted for expected issues with internet connectivity.
 */
inline fun <V, E : NetworkingError> Result<V, E>.logNetworkFailure(
  logLevelOverride: LogLevel? = null,
  errorLogLevel: LogLevel = LogLevel.Error,
  message: () -> String,
): Result<V, E> =
  onFailure { error ->
    val logLevel =
      logLevelOverride ?: when (error) {
        // Connectivity errors are expected, so we log them at a lower severity.
        is HttpError.NetworkError -> Info
        else -> errorLogLevel
      }

    log(
      level = logLevel,
      throwable = error.cause ?: HandledError(message())
    ) { "${message()}. $error" }
  }

/**
 * Convenience side effect to log unexpected failures at an error severity level
 * (which will trigger alerting via Bugsnag and Linear), or, if provided, at a
 * different severity level.
 *
 * Please use the more specific [logF8eFailure] or [logNetworkFailure] for network-related
 * operations so we don't get alerted for expected issues with internet connectivity.
 */
inline fun <V, E : Throwable> Result<V, E>.logFailure(
  logLevel: LogLevel = LogLevel.Error,
  message: () -> String,
): Result<V, E> =
  onFailure { error ->
    log(
      level = logLevel,
      throwable = error.cause ?: HandledError(message())
    ) { "${message()}. $error" }
  }

class HandledError(
  override val message: String,
) : Throwable(message)
