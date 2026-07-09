package build.wallet.wallet.migration

/**
 * Domain-level availability result for the W3 hardware auth key paired during upgrade.
 *
 * All statuses mean the key can continue through the upgrade for the current account. Reuse by
 * another account or pending recovery is represented as [MigrationError.HardwareAuthKeyAlreadyInUse].
 */
enum class HardwareAuthKeyAvailabilityStatus {
  /** The key is not known to F8e. */
  Available,

  /** The key is already claimed by this account, but is not its active hardware auth key. */
  ClaimedByCurrentAccount,

  /** The key is already this account's active hardware auth key. */
  ActiveOnCurrentAccount,
}
