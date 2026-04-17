package build.wallet.ui.app.nfc

import build.wallet.statemachine.fwup.FwupNfcBodyModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FwupNfcScreenTests : FunSpec({
  test("iOS FWUP system theme follows native sheet mode") {
    FwupNfcBodyModel(
      onCancel = {},
      status = FwupNfcBodyModel.Status.Searching(),
      showNativeSheetOnIos = true,
      eventTrackerScreenInfo = null
    ).shouldFollowIosSystemTheme(
      designSystemV2Enabled = true
    ).shouldBe(true)

    FwupNfcBodyModel(
      onCancel = {},
      status = FwupNfcBodyModel.Status.Searching(),
      showNativeSheetOnIos = false,
      eventTrackerScreenInfo = null
    ).shouldFollowIosSystemTheme(
      designSystemV2Enabled = true
    ).shouldBe(false)
  }
})
