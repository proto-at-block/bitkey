package build.wallet.integration.statemachine.create

import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.ui.model.button.ButtonModel

val ChooseAccountAccessModel.setUpNewWalletButton: ButtonModel
  get() = buttons.first { it.text == "Set up a new wallet" }

val ChooseAccountAccessModel.moreOptionsButton: ButtonModel
  get() = buttons.first { it.text == "More options" }
