package build.wallet.ui.app.nfc

import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SignTransactionNfcScreenTests : FunSpec({
  test("custom background layout is used only when native sheet mode is disabled") {
    SignTransactionNfcBodyModel(
      onCancel = {},
      status = SignTransactionNfcBodyModel.Status.Searching,
      showNativeSheetOnIos = true,
      eventTrackerScreenInfo = null
    ).shouldUseCustomBackgroundLayout().shouldBe(false)

    SignTransactionNfcBodyModel(
      onCancel = {},
      status = SignTransactionNfcBodyModel.Status.Searching,
      showNativeSheetOnIos = false,
      eventTrackerScreenInfo = null
    ).shouldUseCustomBackgroundLayout().shouldBe(true)
  }

})
