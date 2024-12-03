package build.wallet.logging

// TODO: [W-5877] Add more robust sensitive data scanner
object SensitiveDataValidator {
  /** Naive way of catching logs that might contain sensitive data. */
  private fun indicators(): List<Regex> {
    return listOf(
      Regex("[tx]prv\\w{78,112}", RegexOption.IGNORE_CASE)
    )
  }

  fun isSensitiveData(entry: LogEntry): Boolean {
    return indicators().any { indicator ->
      entry.tag.contains(indicator) ||
        entry.message.contains(indicator) ||
        entry.tag == DEV_TAG
    }
  }
}
