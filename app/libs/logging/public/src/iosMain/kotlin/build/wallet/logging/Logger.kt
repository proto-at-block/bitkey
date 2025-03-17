package build.wallet.logging

import com.rickclephas.kmp.nserrorkt.asThrowable
import platform.Foundation.NSError

/**
 * Convenience wrapper around Kermit logger, with better Swift interop.
 *
 * Primarily, instead of accepting Kotlin [Throwable] directly, this function accepts [NSError], and
 * internally maps it to Kotlin [Throwable].
 */
@Suppress("Unused") // Used by iOS
inline fun log(
  level: LogLevel,
  tag: String?,
  error: NSError?,
  message: () -> String,
) {
  logInternal(
    level = level,
    tag = tag,
    throwable = error?.asThrowable(),
    message = message
  )
}
