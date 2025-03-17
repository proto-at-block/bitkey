package build.wallet.money.formatter

import dev.zacsweers.redacted.annotations.Redacted

/**
 * A simple container for display amounts; see [MoneyDisplayFormatter.amountDisplayText].
 */
@Redacted
data class AmountDisplayText(
  val primaryAmountText: String,
  val secondaryAmountText: String?,
)
