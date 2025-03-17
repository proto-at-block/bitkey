package build.wallet.limit

import build.wallet.money.FiatMoney

/**
 * Describes Mobile Pay status of the currently activated keybox.
 */
sealed interface MobilePayData {
  /**
   * Mobile Pay is enabled.
   *
   * @property activeSpendingLimit current spending limit set on this account.
   * @property balance current balance information of a customer's Mobile Pay setup
   */
  data class MobilePayEnabledData(
    val activeSpendingLimit: SpendingLimit,
    val balance: MobilePayBalance?,
    val remainingFiatSpendingAmount: FiatMoney?,
  ) : MobilePayData

  /**
   * Mobile pay is disabled.
   *
   * @property mostRecentSpendingLimit a spending limit that was previous set on this account, if any.
   */
  data class MobilePayDisabledData(
    val mostRecentSpendingLimit: SpendingLimit?,
  ) : MobilePayData
}
