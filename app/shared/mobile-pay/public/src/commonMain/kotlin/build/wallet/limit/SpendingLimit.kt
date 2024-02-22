package build.wallet.limit

import build.wallet.money.FiatMoney
import kotlinx.datetime.TimeZone

/**
 * A daily limit the customer can set, at or below which the hardware
 * will not be required to authorize transactions and the server will
 * instead provide the second signature.
 */
data class SpendingLimit(
  /**
   * Whether or not this Spending Limit is turned on.
   */
  val active: Boolean,
  /**
   * The amount, above which the hardware will be required to sign transactions.
   * Currently only supported as [FiatMoney].
   */
  val amount: FiatMoney,
  /**
   * The timezone in which the limit will reset at 3 A.M. (this is an arbitrary
   * time chosen to cause the least amount of disruption to customers).
   */
  val timezone: TimeZone,
)
