package build.wallet.statemachine.moneyhome

import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel

interface BaseMoneyHomeBodyModel {
  val trailingToolbarAccessoryModel: ToolbarAccessoryModel
  val buttonsModel: MoneyHomeButtonsModel
  val cardsModel: MoneyHomeCardsModel
}
