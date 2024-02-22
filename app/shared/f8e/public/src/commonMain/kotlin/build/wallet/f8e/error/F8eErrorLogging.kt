package build.wallet.f8e.error

import build.wallet.ktor.result.HttpError
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure

/**
 * Convenience side effect to log F8e errors, separating the severity for
 * expected network connectivity errors, [HttpError.NetworkError], as well as expected
 * 4xx client errors, from other unexpected errors.
 *
 * Please use this instead of the more general [logNetworkFailure] or [logFailure] for
 * f8e network calls that have known error codes so we don't get alerted for expected
 * 4xx errors.
 */
inline fun <V, E : F8eError<*>> Result<V, E>.logF8eFailure(
  logLevelOverride: LogLevel? = null,
  message: () -> String,
): Result<V, E> =
  onFailure { error ->
    val logLevel =
      logLevelOverride ?: when (error) {
        // Connectivity errors are expected, so we log them at a lower severity.
        is F8eError.ConnectivityError<*> -> LogLevel.Info
        // Specific client (4xx) errors are expected, so we log them at a lower severity.
        is F8eError.SpecificClientError<*> -> LogLevel.Warn
        else -> LogLevel.Error
      }

    log(logLevel, throwable = error.error) { "${message()}. $error" }
  }
