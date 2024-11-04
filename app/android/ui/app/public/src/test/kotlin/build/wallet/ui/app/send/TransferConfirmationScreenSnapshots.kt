@file:Suppress("TestFunctionName")

package build.wallet.ui.app.send

import androidx.compose.runtime.Composable
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.send.TransactionDetailModelType
import build.wallet.statemachine.send.TransactionDetailsModel
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.TransferConfirmationScreenVariant
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class TransferConfirmationScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("transfer confirmation screen - required hardware, confirmation enabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.Regular,
        requiresHardware = true,
        confirmButtonEnabled = true
      )
    }
  }

  test("transfer confirmation screen - hardware not required, confirmation enabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.Regular,
        requiresHardware = false,
        confirmButtonEnabled = true
      )
    }
  }

  test("transfer confirmation screen - hardware required, confirmation disabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.Regular,
        requiresHardware = true,
        confirmButtonEnabled = false
      )
    }
  }

  test("transfer confirmation screen - hardware not required, confirmation disabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.Regular,
        requiresHardware = false,
        confirmButtonEnabled = false
      )
    }
  }

  test("transfer confirmation screen - pending fee bump") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant = TransferConfirmationScreenVariant.SpeedUp,
        requiresHardware = false,
        confirmButtonEnabled = false
      )
    }
  }
})

@Composable
private fun TransferConfirmationScreen(
  variant: TransferConfirmationScreenVariant,
  requiresHardware: Boolean,
  confirmButtonEnabled: Boolean,
) {
  FormScreen(
    model = TransferConfirmationScreenModel(
      variant = variant,
      onBack = {},
      onCancel = {},
      recipientAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
      transactionDetails = TransactionDetailsModel(
        transactionSpeedText = "~30 minutes",
        transactionDetailModelType = when (variant) {
          TransferConfirmationScreenVariant.SpeedUp ->
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
          TransferConfirmationScreenVariant.Regular ->
            TransactionDetailModelType.Regular(
              transferAmountText = "$20.00",
              transferAmountSecondaryText = "0.0003 BTC",
              feeAmountText = "$1.36",
              feeAmountSecondaryText = "0.00002 BTC",
              totalAmountPrimaryText = "$21.36",
              totalAmountSecondaryText = "0.0010 BTC"
            )
          is TransferConfirmationScreenVariant.Sell ->
            TransactionDetailModelType.Sell(
              transferAmountText = "$20.00",
              feeAmountText = "$1.36",
              feeAmountSecondaryText = "234 sats",
              totalAmountPrimaryText = "$21.36",
              totalAmountSecondaryText = "(0.0010 BTC)",
              transferAmountSecondaryText = "1,234 sats"
            )
        },
        amountLabel = "amountLabel"
      ),
      requiresHardware = requiresHardware,
      confirmButtonEnabled = confirmButtonEnabled,
      onConfirmClick = {},
      onNetworkFeesClick = {},
      onArrivalTimeClick = when (variant) {
        TransferConfirmationScreenVariant.Regular -> { -> }
        else -> null
      }
    )
  )
}
