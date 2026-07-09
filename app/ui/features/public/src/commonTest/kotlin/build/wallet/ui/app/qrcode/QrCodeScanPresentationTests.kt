package build.wallet.ui.app.qrcode

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class QrCodeScanPresentationTests : FunSpec({
  test("known Dynamic Island iPhone model identifiers are enabled") {
    listOf(
      "iPhone15,2",
      "iPhone15,3",
      "iPhone15,4",
      "iPhone15,5",
      "iPhone16,1",
      "iPhone16,2",
      "iPhone17,1",
      "iPhone17,2",
      "iPhone17,3",
      "iPhone17,4",
      "iPhone18,1",
      "iPhone18,2",
      "iPhone18,3",
      "iPhone18,4"
    ).forEach { modelIdentifier ->
      isKnownDynamicIslandIPhoneModel(modelIdentifier).shouldBe(true)
    }
  }

  test("known non-Dynamic Island iPhone model identifiers are disabled") {
    listOf(
      "iPhone14,7",
      "iPhone14,8",
      "iPhone17,5",
      "iPhone18,5",
      "iPad16,3",
      "arm64"
    ).forEach { modelIdentifier ->
      isKnownDynamicIslandIPhoneModel(modelIdentifier).shouldBe(false)
    }
  }
})
