package build.wallet.logging

/**
 * Used to detect whether a log entry contains sensitive data.
 */
interface SensitiveDataIndicator {
  /**
   * Name of the indicator, used to log which indicators matched.
   */
  val name: String

  /**
   * Check whether the given log entry contains sensitive data.
   *
   * @param entry the log entry to validate before it is sent to any service.
   * @return true if the log contains sensitive data
   */
  fun match(entry: LogEntry): Boolean
}

/**
 * Anonymous implementation of [SensitiveDataIndicator]
 */
class SimpleIndicator(
  override val name: String,
  private val matcher: (LogEntry) -> Boolean,
) : SensitiveDataIndicator {
  override fun match(entry: LogEntry): Boolean = matcher(entry)
}

/**
 * Convert a Regex to a matcher to be used in a [SimpleIndicator].
 */
internal fun Regex.asLogMatcher(): (LogEntry) -> Boolean {
  return { entry -> this in entry.tag || this in entry.message }
}
