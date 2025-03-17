package build.wallet.limit

import build.wallet.money.BitcoinMoney

/**
 * Data structure representing the latest status of the user's Mobile Pay setup.
 *
 * @property spent The amount of money the user has spent within their daily spending window.
 * @property available The amount of money the user will be able to spend within their daily spending window.
 * @property limit The amount of money the user has configured to be able to spend within their spending window.
 */
data class MobilePayBalance(
  val spent: BitcoinMoney,
  val available: BitcoinMoney,
  val limit: SpendingLimit,
)
