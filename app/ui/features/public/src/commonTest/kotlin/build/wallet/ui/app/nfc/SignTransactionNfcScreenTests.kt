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

  test("iOS system theme follows native sheet mode") {
    SignTransactionNfcBodyModel(
      onCancel = {},
      status = SignTransactionNfcBodyModel.Status.Searching,
      showNativeSheetOnIos = true,
      eventTrackerScreenInfo = null
    ).shouldFollowIosSystemTheme(
      designSystemV2Enabled = true
    ).shouldBe(true)

    SignTransactionNfcBodyModel(
      onCancel = {},
      status = SignTransactionNfcBodyModel.Status.Searching,
      showNativeSheetOnIos = false,
      eventTrackerScreenInfo = null
    ).shouldFollowIosSystemTheme(
      designSystemV2Enabled = true
    ).shouldBe(false)
  }

  test("detailed iOS instructions show only when native sheet mode is enabled") {
    SignTransactionNfcBodyModel(
      onCancel = {},
      status = SignTransactionNfcBodyModel.Status.Searching,
      showNativeSheetOnIos = true,
      eventTrackerScreenInfo = null
    ).shouldShowDetailedIosInstructions(
      designSystemV2Enabled = true
    ).shouldBe(true)

    SignTransactionNfcBodyModel(
      onCancel = {},
      status = SignTransactionNfcBodyModel.Status.Searching,
      showNativeSheetOnIos = false,
      eventTrackerScreenInfo = null
    ).shouldShowDetailedIosInstructions(
      designSystemV2Enabled = true
    ).shouldBe(false)
  }

  test("detailed iOS instructions stay disabled for non-instruction states") {
    SignTransactionNfcBodyModel(
      onCancel = {},
      status = SignTransactionNfcBodyModel.Status.Success,
      showNativeSheetOnIos = true,
      eventTrackerScreenInfo = null
    ).shouldShowDetailedIosInstructions(
      designSystemV2Enabled = true
    ).shouldBe(false)
  }
})
