package build.wallet.statemachine.money.amount

import build.wallet.ui.model.Model

data class MoneyAmountModel(
  val primaryAmount: String,
  val secondaryAmount: String,
) : Model
