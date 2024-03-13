package build.wallet.statemachine.ui.robots

import build.wallet.statemachine.moneyhome.BaseMoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.lite.card.WALLETS_YOURE_PROTECTING_MESSAGE
import build.wallet.statemachine.ui.matchers.shouldBeEnabled
import build.wallet.statemachine.ui.matchers.shouldNotBeLoading
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf

fun BaseMoneyHomeBodyModel.protectedCustomersCard(): CardModel? {
  return cardsModel.cards.find { it.title.string == WALLETS_YOURE_PROTECTING_MESSAGE }
}

fun BaseMoneyHomeBodyModel.selectProtectedCustomer(protectedCustomer: String) {
  protectedCustomersCard()
    .shouldNotBeNull()
    .content
    .shouldNotBeNull()
    .shouldBeTypeOf<CardModel.CardContent.DrillList>()
    .items
    .single { it.title == protectedCustomer }
    .onClick
    .shouldNotBeNull()
    .invoke()
}

fun CardModel.click() =
  onClick
    .shouldNotBeNull()
    .invoke()

fun CardModel.clickTrailingButton() =
  trailingButton
    .shouldNotBeNull()
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
