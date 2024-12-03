package build.wallet.logging

/**
 * Logger that should be used by testing infrastructure and tests.
 * Helps us differentiate app logic logs from testing logs.
 */
inline fun logTesting(
  tag: String? = "Test",
  message: () -> String,
) {
  // TODO: docs
  logWarn(
    tag = tag,
    message = message
  )
}
