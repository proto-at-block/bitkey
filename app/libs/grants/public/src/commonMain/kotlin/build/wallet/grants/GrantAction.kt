package build.wallet.grants

/**
 * Specifies the security-sensitive action the hardware should take if the server grants permission.
 */
enum class GrantAction(val value: Int) {
  FINGERPRINT_RESET(1),
  TRANSACTION_VERIFICATION(2),
}
