package build.wallet.ui.app.send

import androidx.compose.runtime.Composable
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.send.TransactionDetailModelType
import build.wallet.statemachine.send.TransactionDetailsModel
import build.wallet.statemachine.send.TransferInitiatedBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class TransferInitiatedScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("transfer initiated screen") {
    paparazzi.snapshot {
      TransferInitiatedScreen(speedUp = false)
    }
  }

  test("transfer initiated screen - speed up") {
    paparazzi.snapshot {
      TransferInitiatedScreen(speedUp = true)
    }
  }
})

@Composable
private fun TransferInitiatedScreen(speedUp: Boolean) {
  FormScreen(
    TransferInitiatedBodyModel(
      onBack = {},
      recipientAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
      transactionDetails =
        TransactionDetailsModel(
          transactionSpeedText = "~30 minutes",
          transactionDetailModelType =
            if (speedUp) {
              TransactionDetailModelType.SpeedUp(
                transferAmountText = "$20.00",
                oldFeeAmountText = "$1.00",
                feeDifferenceText = "+$1.00"
              )
            } else {
              TransactionDetailModelType.Regular(
                transferAmountText = "$20.00",
                feeAmountText = "$1.36"
              )
            },
          totalAmountPrimaryText = "$21.36",
          totalAmountSecondaryText = "(0.0010 BTC)"
        ),
      onDone = {}
    )
  )
}
