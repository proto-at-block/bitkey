package build.wallet.ui.app.nfc

import androidx.compose.runtime.CompositionLocalProvider
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel
import build.wallet.ui.app.LocalDeviceInfo
import io.kotest.core.spec.style.FunSpec

class SignTransactionNfcScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension(maxPercentDifference = 0.8)
  val androidDeviceInfo = DeviceInfo(
    deviceModel = "Pixel 8",
    devicePlatform = DevicePlatform.Android,
    isEmulator = true
  )

  test("sign transaction nfc ready dsv2") {
    paparazzi.snapshot {
      CompositionLocalProvider(LocalDeviceInfo provides androidDeviceInfo) {
        SignTransactionNfcScreen(
          model =
            SignTransactionNfcBodyModel(
              onCancel = {},
              status = SignTransactionNfcBodyModel.Status.Searching,
              eventTrackerScreenInfo = null
            )
        )
      }
    }
  }

  test("sign transaction nfc keep holding dsv2") {
    paparazzi.snapshot {
      CompositionLocalProvider(LocalDeviceInfo provides androidDeviceInfo) {
        SignTransactionNfcScreen(
          model =
            SignTransactionNfcBodyModel(
              onCancel = {},
              status = SignTransactionNfcBodyModel.Status.Signing,
              eventTrackerScreenInfo = null
            )
        )
      }
    }
  }
})
