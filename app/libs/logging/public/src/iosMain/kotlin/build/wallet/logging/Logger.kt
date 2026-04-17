package build.wallet.logging

import com.rickclephas.kmp.nserrorkt.asThrowable
import platform.Foundation.NSError

/**
 * Convenience wrapper around Kermit logger, with better Swift interop.
 *
 * Primarily, instead of accepting Kotlin [Throwable] directly, this function accepts [NSError], and
 * internally maps it to Kotlin [Throwable].
 */
@Suppress("Unused", "TooGenericExceptionCaught") // Used by iOS
inline fun log(
  level: LogLevel,
  tag: String?,
  error: NSError?,
  message: () -> String,
) {
  val throwable = try {
    error?.asThrowable()
  } catch (conversionError: Throwable) {
    error?.let {
      Exception(
        message =
          "Failed to convert NSError to Throwable " +
            "(domain=${it.domain}, code=${it.code}, description=${it.localizedDescription})",
        cause = conversionError
      )
    } ?: Exception("Unknown error converting NSError to Throwable")
  }

  logInternal(
    level = level,
    tag = tag,
    throwable = throwable,
    message = message
  )
}
