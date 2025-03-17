package build.wallet.statemachine.ui

import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.ui.matchers.shouldBeEnabled
import build.wallet.statemachine.ui.matchers.shouldNotBeLoading
import build.wallet.statemachine.ui.robots.click

fun FormBodyModel.clickPrimaryButton() {
  primaryButton.click()
}

fun FormBodyModel.clickSecondaryButton() {
  secondaryButton.click()
}

fun PairNewHardwareBodyModel.clickPrimaryButton() {
  primaryButton
    .shouldBeEnabled()
    .shouldNotBeLoading()
    .onClick()
}
