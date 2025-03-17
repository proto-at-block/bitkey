package build.wallet.statemachine.ui.robots

import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.ui.matchers.shouldBeEnabled
import build.wallet.statemachine.ui.matchers.shouldNotBeLoading
import build.wallet.ui.model.button.ButtonModel

val ChooseAccountAccessModel.setUpNewWalletButton: ButtonModel
  get() = buttons.first { it.text == "Set up a new wallet" }

val ChooseAccountAccessModel.moreOptionsButton: ButtonModel
  get() = buttons.first { it.text == "More options" }

fun ChooseAccountAccessModel.clickSetUpNewWalletButton() {
  setUpNewWalletButton
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}

fun ChooseAccountAccessModel.clickMoreOptionsButton() {
  moreOptionsButton
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}
