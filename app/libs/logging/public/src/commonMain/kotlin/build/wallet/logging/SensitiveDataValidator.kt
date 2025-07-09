package build.wallet.logging

import kotlin.text.RegexOption.IGNORE_CASE

// TODO: [W-5877] Add more robust sensitive data scanner
object SensitiveDataValidator {
  /**
   * Returns `true` if log tag or message contains sensitive data.
   * Also `true` if dev tag is used, potentially indicating that dev code has been checked in.
   */
  fun isSensitiveData(entry: LogEntry): Boolean {
    return indicators.any { indicator ->
      entry.tag.contains(indicator) ||
        entry.message.contains(indicator) ||
        entry.tag == DEV_TAG
    }
  }

  /** Naive way of catching logs that might contain sensitive data. */
  private val indicators: List<Regex> = listOf(
    // bitcoin private keys
    Regex("[tx]prv\\w{78,112}", IGNORE_CASE),
    // bitcoin addresses
    Regex("\\b(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,39}\\b", IGNORE_CASE),
    // naive check for names/aliases
    Regex("(alice|bob|aunt|uncle|cousin)", IGNORE_CASE)
  )
}
