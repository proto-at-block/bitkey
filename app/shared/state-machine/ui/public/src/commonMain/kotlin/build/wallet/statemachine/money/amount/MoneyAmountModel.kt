package build.wallet.statemachine.money.amount

import build.wallet.ui.model.Model
import dev.zacsweers.redacted.annotations.Redacted

@Redacted
data class MoneyAmountModel(
  val primaryAmount: String,
  val secondaryAmount: String,
) : Model()
