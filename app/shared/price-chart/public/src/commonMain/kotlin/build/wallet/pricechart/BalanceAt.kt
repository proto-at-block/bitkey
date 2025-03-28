package build.wallet.pricechart

import kotlinx.datetime.Instant

data class BalanceAt(
  val date: Instant,
  val balance: Double,
  val fiatBalance: Double,
) : DataPoint {
  override val x: Long = date.epochSeconds
  override val y: Double = fiatBalance
}
