package build.wallet.logging

/**
 * Whether a log entry contains sensitive data.
 */
sealed interface SensitiveDataResult {
  /**
   * No sensitive data was found by any detector.
   */
  data object NoneFound : SensitiveDataResult

  /**
   * Sensitive data was found by at least one detector.
   */
  data class Sensitive(val violations: List<SensitiveDataIndicator>) : SensitiveDataResult {
    val redactedTag = "REDACTED"
    val redactedMessage = "REDACTED Log - Possible sensitive data matching: [${violations.joinToString { it.name }}]"
  }
}
