package build.wallet.limit

import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney

/**
 * Describes Mobile Pay status of the currently activated keybox.
 */
sealed interface MobilePayData {
  /**
   * Mobile Pay is enabled.
   *
   * @property activeSpendingLimit current spending limit set on this account.
   * @property remainingBitcoinSpendingAmount the amount of bitcoin remaining that can be spent
   * @property remainingFiatSpendingAmount the fiat value of [remainingBitcoinSpendingAmount] in the
   * user's preferred currency
   */
  data class MobilePayEnabledData(
    val activeSpendingLimit: SpendingLimit?,
    val remainingBitcoinSpendingAmount: BitcoinMoney?,
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
