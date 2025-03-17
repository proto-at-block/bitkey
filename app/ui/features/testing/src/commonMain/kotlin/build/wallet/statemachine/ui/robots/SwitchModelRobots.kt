package build.wallet.statemachine.ui.robots

import build.wallet.ui.model.switch.SwitchModel
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

fun SwitchModel.shouldBeEnabled() = checked.shouldBeTrue()

fun SwitchModel.shouldBeDisabled() = checked.shouldBeFalse()

fun SwitchModel.enable() {
  shouldBeDisabled()
  onCheckedChange(true)
}

fun SwitchModel.disable() {
  shouldBeEnabled()
  onCheckedChange(false)
}
