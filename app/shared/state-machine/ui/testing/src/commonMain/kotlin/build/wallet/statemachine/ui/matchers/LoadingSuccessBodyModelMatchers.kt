package build.wallet.statemachine.ui.matchers

import build.wallet.statemachine.core.LoadingSuccessBodyModel
import io.kotest.matchers.shouldBe

fun LoadingSuccessBodyModel.shouldHaveMessage(message: String): LoadingSuccessBodyModel =
  apply {
    this.message.shouldBe(message)
  }
