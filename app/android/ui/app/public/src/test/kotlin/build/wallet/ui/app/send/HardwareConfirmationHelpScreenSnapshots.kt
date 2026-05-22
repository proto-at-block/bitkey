package build.wallet.ui.app.send

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.kotest.paparazzi.paparazziExtension
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
          content = HardwareConfirmationContent.SignTransaction.helpContent!!
        )
      )
    }
  }

  test("send hardware confirmation help screen - design system v2") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationHelpBodyModel(
          onBack = {},
          content = HardwareConfirmationContent.SignTransaction.helpContent!!
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

  test("send transaction hardware confirmation screen - design system v2") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationScreenModel(
          onBack = {},
          onConfirm = {},
          onHelpClick = {},
          content = HardwareConfirmationContent.SendTransaction,
          isHardwareFake = true
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

  test("consolidate utxos hardware confirmation screen - design system v2") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationScreenModel(
          onBack = {},
          onConfirm = {},
          onHelpClick = {},
          content = HardwareConfirmationContent.ConsolidateUtxos,
          isHardwareFake = true
        )
      )
    }
  }

  test("send transaction hardware confirmation screen with destination address - design system v2") {
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
