package build.wallet.configuration

import build.wallet.money.FiatMoney

/**
 * Configuration that determines the minimum and maximum limit that can be set for
 * a particular currency when using Mobile Pay, as well as snap values to be
 * used by UI slider when selecting the limit amount.
 */
data class MobilePayFiatConfig(
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
  init {
    val limitsAreUseSameCurrency = minimumLimit.currency == maximumLimit.currency
    require(limitsAreUseSameCurrency) {
      "Minimum and maximum limits must be in the same currency but were ${minimumLimit.currency} and ${maximumLimit.currency} respectively."
    }
    val snapValuesAreUseSameCurrency = snapValues.keys.all { it.currency == minimumLimit.currency }
    require(snapValuesAreUseSameCurrency) {
      "Snap values must be in the same currency as the limits but were ${snapValues.keys.map { it.currency }} and ${minimumLimit.currency} respectively."
    }
    val snapTolerancesAreUseSameCurrency = snapValues.values.all {
      it.value.currency == minimumLimit.currency
    }
    require(snapTolerancesAreUseSameCurrency) {
      "Snap tolerances must be in the same currency as the limits but were ${snapValues.values.map { it.value.currency }} and ${minimumLimit.currency} respectively."
    }

    val maximumLimitIsHigherThanMinimumLimit = maximumLimit > minimumLimit
    require(maximumLimitIsHigherThanMinimumLimit) {
      "Maximum limit must be higher than minimum limit but were $maximumLimit and $minimumLimit respectively."
    }
  }

  data class SnapTolerance(val value: FiatMoney)

  companion object {
    // A hard-coded configuration to use in the case of fallbacks.
    val USD = MobilePayFiatConfig(
      minimumLimit = FiatMoney.usd(cents = 0),
      maximumLimit = FiatMoney.usd(dollars = 200.0),
      snapValues = mapOf(
        FiatMoney.usd(20.00) to SnapTolerance(FiatMoney.usd(2.00)),
        FiatMoney.usd(25.00) to SnapTolerance(FiatMoney.usd(2.00)),
        FiatMoney.usd(50.00) to SnapTolerance(FiatMoney.usd(2.00)),
        FiatMoney.usd(60.00) to SnapTolerance(FiatMoney.usd(2.00)),
        FiatMoney.usd(70.00) to SnapTolerance(FiatMoney.usd(2.00)),
        FiatMoney.usd(80.00) to SnapTolerance(FiatMoney.usd(2.00)),
        FiatMoney.usd(90.00) to SnapTolerance(FiatMoney.usd(2.00)),
        FiatMoney.usd(100.00) to SnapTolerance(FiatMoney.usd(4.00)),
        FiatMoney.usd(120.00) to SnapTolerance(FiatMoney.usd(4.00)),
        FiatMoney.usd(150.00) to SnapTolerance(FiatMoney.usd(4.00)),
        FiatMoney.usd(175.00) to SnapTolerance(FiatMoney.usd(4.00)),
        FiatMoney.usd(200.00) to SnapTolerance(FiatMoney.usd(4.00))
      ).mapKeys { it.key }
    )
  }
}
