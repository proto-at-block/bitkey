package build.wallet.ui.app.send

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class HardwareConfirmationHelpScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension(maxPercentDifference = 0.05)

  test("send hardware confirmation screen") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationScreenModel(
          onBack = {},
          onConfirm = {},
          onHelpClick = {},
          content = HardwareConfirmationContent.SignTransaction
        )
      )
    }
  }

  test("send hardware confirmation help screen") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationHelpBodyModel(
          onBack = {},
          content = HardwareConfirmationContent.SignTransaction.helpContent!!,
          devicePlatform = Android
        )
      )
    }
  }

  test("firmware update help screen") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationHelpBodyModel(
          onBack = {},
          content = HardwareConfirmationContent.FirmwareUpdate.helpContent!!,
          devicePlatform = Android
        )
      )
    }
  }

  test("send transaction hardware confirmation screen") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationScreenModel(
          onBack = {},
          onConfirm = {},
          onHelpClick = {},
          content = HardwareConfirmationContent.SendTransaction
        )
      )
    }
  }

  test("consolidate utxos hardware confirmation screen") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationScreenModel(
          onBack = {},
          onConfirm = {},
          onHelpClick = {},
          content = HardwareConfirmationContent.ConsolidateUtxos
        )
      )
    }
  }

  test("send transaction hardware confirmation screen with destination address") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationScreenModel(
          onBack = {},
          onConfirm = {},
          onHelpClick = {},
          content = HardwareConfirmationContent.SendTransaction.copy(
            recipientAddress = BitcoinAddress("bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc")
          ),
          isHardwareFake = true
        )
      )
    }
  }
})
