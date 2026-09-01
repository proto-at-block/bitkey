package build.wallet.statemachine.ui.robots

import build.wallet.statemachine.moneyhome.BaseMoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.cardOnClick
import build.wallet.statemachine.moneyhome.card.titleString
import build.wallet.statemachine.moneyhome.lite.card.WALLETS_YOURE_PROTECTING_MESSAGE
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf

fun MoneyHomeBodyModel.clickSettings() {
  trailingToolbarAccessoryModel
    .shouldNotBeNull()
    .shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
    .model
    .onClick()
}

fun BaseMoneyHomeBodyModel.protectedCustomersCard(): CardModel? {
  return cardsModel.cards.find { it.titleString == WALLETS_YOURE_PROTECTING_MESSAGE }
}

fun BaseMoneyHomeBodyModel.selectProtectedCustomer(protectedCustomer: String) {
  protectedCustomersCard()
    .shouldNotBeNull()
    .shouldBeTypeOf<CardModel.DrillList>()
    .items
    .single { it.title == protectedCustomer }
    .onClick
    .shouldNotBeNull()
    .invoke()
}

fun CardModel.click() =
  cardOnClick
    .shouldNotBeNull()
    .invoke()
