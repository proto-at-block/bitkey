@file:Suppress("TestFunctionName")

package build.wallet.ui.app.send

import androidx.compose.runtime.Composable
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.send.TransactionDetailModelType
import build.wallet.statemachine.send.TransactionDetailsModel
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.TransferConfirmationUiProps
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class TransferConfirmationScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("transfer confirmation screen - required hardware, confirmation enabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant =
          TransferConfirmationUiProps.Variant.Regular(
            selectedPriority = EstimatedTransactionPriority.FASTEST
          ),
        requiresHardware = true,
        confirmButtonEnabled = true
      )
    }
  }

  test("transfer confirmation screen - hardware not required, confirmation enabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant =
          TransferConfirmationUiProps.Variant.Regular(
            selectedPriority = EstimatedTransactionPriority.FASTEST
          ),
        requiresHardware = false,
        confirmButtonEnabled = true
      )
    }
  }

  test("transfer confirmation screen - hardware required, confirmation disabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant =
          TransferConfirmationUiProps.Variant.Regular(
            selectedPriority = EstimatedTransactionPriority.FASTEST
          ),
        requiresHardware = true,
        confirmButtonEnabled = false
      )
    }
  }

  test("transfer confirmation screen - hardware not required, confirmation disabled") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant =
          TransferConfirmationUiProps.Variant.Regular(
            selectedPriority = EstimatedTransactionPriority.FASTEST
          ),
        requiresHardware = false,
        confirmButtonEnabled = false
      )
    }
  }

  test("transfer confirmation screen - pending fee bump") {
    paparazzi.snapshot {
      TransferConfirmationScreen(
        variant =
          TransferConfirmationUiProps.Variant.SpeedUp(
            txid = "abc",
            oldFee = Fee(amount = BitcoinMoney.sats(125), feeRate = FeeRate(satsPerVByte = 1f)),
            newFeeRate = FeeRate(2f)
          ),
        requiresHardware = false,
        confirmButtonEnabled = false
      )
    }
  }
})

@Composable
private fun TransferConfirmationScreen(
  variant: TransferConfirmationUiProps.Variant,
  requiresHardware: Boolean,
  confirmButtonEnabled: Boolean,
) {
  FormScreen(
    model =
      TransferConfirmationScreenModel(
        variant = variant,
        onBack = {},
        onCancel = {},
        recipientAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
        transactionDetails =
          TransactionDetailsModel(
            transactionSpeedText = "~30 minutes",
            transactionDetailModelType =
              when (variant) {
                is TransferConfirmationUiProps.Variant.SpeedUp ->
                  TransactionDetailModelType.SpeedUp(
                    transferAmountText = "$20.00",
                    oldFeeAmountText = "$1.36",
                    feeDifferenceText = "+$1.00",
                    totalAmountPrimaryText = "$22.36",
                    totalAmountSecondaryText = "(0.0010 BTC)"
                  )
                is TransferConfirmationUiProps.Variant.Regular ->
                  TransactionDetailModelType.Regular(
                    transferAmountText = "$20.00",
                    feeAmountText = "$1.36",
                    totalAmountPrimaryText = "$21.36",
                    totalAmountSecondaryText = "(0.0010 BTC)"
                  )
              }
          ),
        requiresHardware = requiresHardware,
        confirmButtonEnabled = confirmButtonEnabled,
        onConfirmClick = {},
        onNetworkFeesClick = {},
        onArrivalTimeClick =
          when (variant) {
            is TransferConfirmationUiProps.Variant.Regular -> { -> }
            else -> null
          }
      ).body as FormBodyModel
  )
}
