package build.wallet.statemachine.ui.matchers

import build.wallet.ui.model.button.ButtonModel
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

fun ButtonModel?.shouldHaveText(value: String): ButtonModel =
  shouldNotBeNull().apply {
    text.shouldBe(value)
  }

fun ButtonModel?.shouldBeLoading(): ButtonModel =
  shouldNotBeNull().apply {
    isLoading.shouldBeTrue()
  }

fun ButtonModel?.shouldNotBeLoading(): ButtonModel =
  shouldNotBeNull().apply {
    isLoading.shouldBeFalse()
  }

fun ButtonModel?.shouldBeEnabled(): ButtonModel =
  shouldNotBeNull().apply {
    isEnabled.shouldBeTrue()
  }

fun ButtonModel?.shouldBeDisabled(): ButtonModel =
  shouldNotBeNull().apply {
    isEnabled.shouldBeFalse()
  }
