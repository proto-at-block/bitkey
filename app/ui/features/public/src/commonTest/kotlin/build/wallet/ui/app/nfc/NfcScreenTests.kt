package build.wallet.ui.app.nfc

import build.wallet.statemachine.nfc.NfcBodyModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class NfcScreenTests : FunSpec({
  test("custom iOS success state keeps its status copy visible") {
    nfcIosStatusHeadline(
      status = NfcBodyModel.Status.Success,
      text = "Tap complete"
    ).shouldBe("Tap complete")

    nfcIosStatusSubtitle(
      status = NfcBodyModel.Status.Success,
      text = "Tap complete"
    ).shouldBeNull()
  }

  test("connected state omits the subtitle when it would duplicate the headline") {
    nfcIosStatusHeadline(
      status = NfcBodyModel.Status.Connected(onCancel = {}),
      text = "Connected"
    ).shouldBe("Connected")

    nfcIosStatusSubtitle(
      status = NfcBodyModel.Status.Connected(onCancel = {}),
      text = "Connected"
    ).shouldBeNull()
  }

  test("spinner connected state keeps the instructional subtitle") {
    nfcIosStatusHeadline(
      status = NfcBodyModel.Status.Connected(onCancel = {}, showProgressSpinner = true),
      text = "This can take up to 1 minute..."
    ).shouldBe("Keep holding...")

    nfcIosStatusSubtitle(
      status = NfcBodyModel.Status.Connected(onCancel = {}, showProgressSpinner = true),
      text = "This can take up to 1 minute..."
    ).shouldBe("This can take up to 1 minute...")
  }
})
