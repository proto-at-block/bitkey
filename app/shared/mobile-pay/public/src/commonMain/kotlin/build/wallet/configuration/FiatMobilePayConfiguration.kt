package build.wallet.configuration

import build.wallet.money.FiatMoney

data class FiatMobilePayConfiguration(
  /**
   * The minimum Mobile Pay amount that can be chosen for the currency.
   */
  val minimumLimit: FiatMoney,
  /**
   * The maximum Mobile Pay amount that can be chosen for the currency.
   */
  val maximumLimit: FiatMoney,
  /**
   * Values that the slider UI should "snap" to if they are within the tolerance range,
   * specified in the currency's fractional unit denomination, i.e. for USD, $50 with
   * a tolerance of $2 means that we will "snap" to $50 if we are within $2 of it.
   */
  val snapValues: Map<FiatMoney, SnapTolerance> = emptyMap(),
) {
  data class SnapTolerance(val value: FiatMoney)

  companion object {
    // A hard-coded configuration to use in the case of fallbacks.
    val USD =
      FiatMobilePayConfiguration(
        minimumLimit = FiatMoney.Companion.usd(0),
        maximumLimit = FiatMoney.Companion.usd(200.0), // $200
        snapValues =
          mapOf(
            FiatMoney.Companion.usd(20.00) to SnapTolerance(FiatMoney.usd(2.00)),
            FiatMoney.Companion.usd(25.00) to SnapTolerance(FiatMoney.usd(2.00)),
            FiatMoney.Companion.usd(50.00) to SnapTolerance(FiatMoney.usd(2.00)),
            FiatMoney.Companion.usd(60.00) to SnapTolerance(FiatMoney.usd(2.00)),
            FiatMoney.Companion.usd(70.00) to SnapTolerance(FiatMoney.usd(2.00)),
            FiatMoney.Companion.usd(80.00) to SnapTolerance(FiatMoney.usd(2.00)),
            FiatMoney.Companion.usd(90.00) to SnapTolerance(FiatMoney.usd(2.00)),
            FiatMoney.Companion.usd(100.00) to SnapTolerance(FiatMoney.usd(4.00)),
            FiatMoney.Companion.usd(120.00) to SnapTolerance(FiatMoney.usd(4.00)),
            FiatMoney.Companion.usd(150.00) to SnapTolerance(FiatMoney.usd(4.00)),
            FiatMoney.Companion.usd(175.00) to SnapTolerance(FiatMoney.usd(4.00)),
            FiatMoney.Companion.usd(200.00) to SnapTolerance(FiatMoney.usd(4.00))
          ).mapKeys { it.key }
      )
  }
}
