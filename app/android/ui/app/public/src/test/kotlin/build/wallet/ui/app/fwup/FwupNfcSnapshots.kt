package build.wallet.ui.app.fwup

import androidx.compose.runtime.CompositionLocalProvider
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.platform.device.DeviceInfo
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.ui.app.LocalDeviceInfo
import build.wallet.ui.app.nfc.FwupNfcScreen
import build.wallet.ui.app.nfc.FwupNfcScreenInternal
import io.kotest.core.spec.style.FunSpec

class FwupNfcSnapshots : FunSpec({
  val paparazzi = paparazziExtension(maxPercentDifference = 0.8)
  val androidDeviceInfo = DeviceInfo(
    deviceModel = "Pixel 8",
    devicePlatform = DevicePlatform.Android,
    isEmulator = true
  )

  test("fwup nfc progress with zero progress") {
    paparazzi.snapshot {
      FwupNfcScreenInternal(
        model =
          FwupNfcBodyModel(
            onCancel = {},
            status =
              FwupNfcBodyModel.Status.InProgress(fwupProgress = 0f),
            eventTrackerScreenInfo = null
          )
      )
    }
  }

  test("fwup nfc progress with some progress") {
    paparazzi.snapshot {
      FwupNfcScreenInternal(
        model =
          FwupNfcBodyModel(
            onCancel = {},
            status =
              FwupNfcBodyModel.Status.InProgress(fwupProgress = 33f),
            eventTrackerScreenInfo = null
          )
      )
    }
  }

  test("fwup nfc lost connection") {
    paparazzi.snapshot {
      FwupNfcScreenInternal(
        model =
          FwupNfcBodyModel(
            onCancel = {},
            status =
              FwupNfcBodyModel.Status.LostConnection(fwupProgress = 5f),
            eventTrackerScreenInfo = null
          )
      )
    }
  }

  test("fwup nfc success") {
    paparazzi.snapshot {
      FwupNfcScreenInternal(
        model =
          FwupNfcBodyModel(
            onCancel = null,
            status = FwupNfcBodyModel.Status.Success(),
            eventTrackerScreenInfo = null
          )
      )
    }
  }
  test("fwup nfc ready to update dsv2") {
    paparazzi.snapshot {
      CompositionLocalProvider(LocalDeviceInfo provides androidDeviceInfo) {
        FwupNfcScreen(
          model =
            FwupNfcBodyModel(
              onCancel = {},
              status = FwupNfcBodyModel.Status.Searching(),
              eventTrackerScreenInfo = null
            )
        )
      }
    }
  }

  test("fwup nfc progress with some progress dsv2") {
    paparazzi.snapshot {
      CompositionLocalProvider(LocalDeviceInfo provides androidDeviceInfo) {
        FwupNfcScreen(
          model =
            FwupNfcBodyModel(
              onCancel = {},
              status = FwupNfcBodyModel.Status.InProgress(fwupProgress = 33f),
              eventTrackerScreenInfo = null
            )
        )
      }
    }
  }
})
