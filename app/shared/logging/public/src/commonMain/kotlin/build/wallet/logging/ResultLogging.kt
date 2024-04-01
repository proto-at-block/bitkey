package build.wallet.logging

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure

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
