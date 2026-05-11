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
   * Fiat spent is converted from server-reported spent sats. Fiat remaining is converted from
   * server-reported available sats, except when spent sats are zero, where remaining displays the
   * full [activeSpendingLimit] amount. Because the values are independently converted from sats and
   * exchange rates can move, spent plus remaining may exceed [activeSpendingLimit].
   *
   * @property activeSpendingLimit current spending limit set on this account.
   * @property remainingBitcoinSpendingAmount the amount of bitcoin remaining that can be spent
   * @property remainingFiatSpendingAmount fiat value of [remainingBitcoinSpendingAmount] in the
   * user's preferred currency
   * @property spentBitcoinAmount the amount of bitcoin already spent within the current window,
   * as reported by f8e
   * @property spentFiatAmount fiat value of [spentBitcoinAmount] in the user's preferred currency,
   * clamped to [activeSpendingLimit].
   */
  data class MobilePayEnabledData(
    val activeSpendingLimit: SpendingLimit?,
    val remainingBitcoinSpendingAmount: BitcoinMoney?,
    val remainingFiatSpendingAmount: FiatMoney?,
    val spentBitcoinAmount: BitcoinMoney?,
    val spentFiatAmount: FiatMoney?,
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
