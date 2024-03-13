package build.wallet.statemachine.ui.matchers

import build.wallet.ui.model.button.ButtonModel
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

fun ButtonModel.shouldBeLoading(): ButtonModel =
  apply {
    isLoading.shouldBeTrue()
  }

fun ButtonModel.shouldNotBeLoading(): ButtonModel =
  apply {
    isLoading.shouldBeFalse()
  }

fun ButtonModel.shouldBeEnabled(): ButtonModel =
  apply {
    isEnabled.shouldBeTrue()
  }

fun ButtonModel.shouldBeDisabled(): ButtonModel =
  apply {
    isEnabled.shouldBeFalse()
  }
