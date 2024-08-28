package build.wallet.limit

/**
 * Describes current status of the Mobile Pay.
 */
sealed interface MobilePayStatus {
  /**
   * Indicates that Mobile Pay is enabled.
   */
  data class MobilePayEnabled(
    /**
     * Currently active spending limit.
     */
    val activeSpendingLimit: SpendingLimit,
    /**
     * Current balance available to spend using Mobile Pay.
     */
    val balance: MobilePayBalance?,
  ) : MobilePayStatus

  /**
   * Indicates that Mobile Pay is disabled.
   */
  data class MobilePayDisabled(
    /**
     * Spending limit that was previously used, if any.
     */
    val mostRecentSpendingLimit: SpendingLimit?,
  ) : MobilePayStatus
}
