package build.wallet.ui.app.nfc

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel.Status.*
import build.wallet.statemachine.nfc.NfcHelpBodyModel
import build.wallet.ui.app.LocalDeviceInfo
import io.kotest.core.spec.style.FunSpec

class NfcScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension(maxPercentDifference = 0.8)
  val androidDeviceInfo = DeviceInfo(
    deviceModel = "Pixel 8",
    devicePlatform = DevicePlatform.Android,
    isEmulator = true
  )

  test("NFC searching") {
    paparazzi.snapshot {
      NfcScreenSearchingPreview()
    }
  }

  test("NFC connected") {
    paparazzi.snapshot {
      NfcScreenConnectedPreview()
    }
  }

  test("NFC success") {
    paparazzi.snapshot {
      NfcScreenSuccessPreview()
    }
  }

  test("NFC connected with spinner") {
    paparazzi.snapshot {
      NfcScreenConnectedWithSpinnerPreview()
    }
  }

  test("NFC searching Android DSV2") {
    paparazzi.snapshot {
      CompositionLocalProvider(LocalDeviceInfo provides androidDeviceInfo) {
        NfcScreen(
          model = NfcBodyModel(
            text = "Hold your Bitkey to the back of your phone",
            status = Searching { },
            onHelpClick = {},
            eventTrackerScreenInfo = null
          )
        )
      }
    }
  }

  test("NFC help Android DSV2") {
    paparazzi.snapshot {
      NfcHelpBodyModel(onBack = {}).render(Modifier)
    }
  }

  test("NFC connected Android DSV2") {
    paparazzi.snapshot {
      CompositionLocalProvider(LocalDeviceInfo provides androidDeviceInfo) {
        NfcScreen(
          model = NfcBodyModel(
            text = "Hold your Bitkey to the back of your phone",
            status = Connected(onCancel = {}),
            onHelpClick = {},
            eventTrackerScreenInfo = null
          )
        )
      }
    }
  }

  test("NFC connected with spinner Android DSV2") {
    paparazzi.snapshot {
      CompositionLocalProvider(LocalDeviceInfo provides androidDeviceInfo) {
        NfcScreen(
          model = NfcBodyModel(
            text = "This can take up to 1 minute…",
            status = Connected(onCancel = {}, showProgressSpinner = true),
            onHelpClick = {},
            eventTrackerScreenInfo = null
          )
        )
      }
    }
  }

  test("NFC success Android DSV2") {
    paparazzi.snapshot {
      CompositionLocalProvider(LocalDeviceInfo provides androidDeviceInfo) {
        NfcScreen(
          model = NfcBodyModel(
            text = "Success",
            status = Success,
            eventTrackerScreenInfo = null
          )
        )
      }
    }
  }
})
