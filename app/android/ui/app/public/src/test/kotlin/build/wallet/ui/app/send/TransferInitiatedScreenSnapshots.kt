package build.wallet.ui.app.send

import androidx.compose.runtime.Composable
import build.wallet.bitcoin.address.BitcoinAddress
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
      recipientAddress = BitcoinAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"),
      transactionDetails =
        TransactionDetailsModel(
          transactionSpeedText = "~30 minutes",
          transactionDetailModelType =
            if (speedUp) {
              TransactionDetailModelType.SpeedUp(
                transferAmountText = "$20.00",
                transferAmountSecondaryText = "0.0003 BTC",
                oldFeeAmountText = "$1.36",
                oldFeeAmountSecondaryText = "0.00002 BTC",
                feeDifferenceText = "+$1.00",
                feeDifferenceSecondaryText = "0.00001 BTC",
                totalAmountPrimaryText = "$22.36",
                totalAmountSecondaryText = "0.0010 BTC",
                totalFeeText = "$2.36",
                totalFeeSecondaryText = "0.00003 BTC"
              )
            } else {
              TransactionDetailModelType.Regular(
                transferAmountText = "$20.00",
                transferAmountSecondaryText = "0.0003 BTC",
                feeAmountText = "$1.36",
                feeAmountSecondaryText = "0.00002 BTC",
                totalAmountPrimaryText = "$21.36",
                totalAmountSecondaryText = "0.0010 BTC"
              )
            }
        ),
      onDone = {}
    )
  )
}
