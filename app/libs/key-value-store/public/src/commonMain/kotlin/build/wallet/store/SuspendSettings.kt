package build.wallet.store

import build.wallet.catchingResult
import com.github.michaelbull.result.Result
import com.russhwolf.settings.coroutines.SuspendSettings

/**
 * Wraps [SuspendSettings.clear] into [Result].
 */
suspend fun SuspendSettings.clearWithResult(): Result<Unit, Throwable> = catchingResult { clear() }

/**
 * Wraps [SuspendSettings.remove] into [Result].
 */
suspend fun SuspendSettings.removeWithResult(key: String): Result<Unit, Throwable> =
  catchingResult { remove(key) }

/**
 * Wraps [SuspendSettings.getStringOrNull] into [Result].
 */
suspend fun SuspendSettings.getStringOrNullWithResult(key: String): Result<String?, Throwable> =
  catchingResult { getStringOrNull(key) }

/**
 * Wraps [SuspendSettings.putString] into [Result].
 */
suspend fun SuspendSettings.putStringWithResult(
  key: String,
  value: String,
): Result<Unit, Throwable> = catchingResult { putString(key, value) }

/**
 * Similar to MutableMap.getOrPut. Gets the string value for key. If null, creates a string
 * from `block`, sets the value, then returns the value.
 */
suspend fun SuspendSettings.getOrPutString(
  key: String,
  block: suspend () -> String,
): String {
  val currentVal = getStringOrNull(key)
  return if (currentVal == null) {
    val newVal = block()
    putString(key, newVal)
    newVal
  } else {
    currentVal
  }
}
