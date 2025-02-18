package build.wallet.statemachine.money.amount

import build.wallet.ui.model.Model

data class MoneyAmountEntryModel(
  val primaryAmount: String,
  val primaryAmountGhostedSubstringRange: IntRange?,
  val secondaryAmount: String?,
) : Model
